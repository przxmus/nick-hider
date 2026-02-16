package dev.przxmus.nickhider.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import dev.przxmus.nickhider.NickHider;
import dev.przxmus.nickhider.config.PrivacyConfig;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringUtil;

/*? if >=1.21.1 {*/
/*import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.resources.PlayerSkin;
*/
/*?}*/

public final class SkinResolutionService {
    private static final String LOOKUP_PRIMARY_BASE = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";
    private static final String LOOKUP_LEGACY_BASE = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String EXTERNAL_SKIN_BASE = "https://mineskin.eu/skin/";
    private static final String EXTERNAL_CAPE_LOOKUP_BASE = "https://api.capes.dev/load/";
    private static final String EXTERNAL_FALLBACK_USER_AGENT = "NickHider/0.0.2 (+https://github.com/przxmus/nick-hider)";

    private static final long SWR_TTL_MS = Duration.ofMinutes(10).toMillis();
    private static final long LOOKUP_CACHE_TTL_MS = Duration.ofMinutes(10).toMillis();

    private static final long MIN_COOLDOWN_MS = Duration.ofSeconds(30).toMillis();
    private static final long MAX_COOLDOWN_MS = Duration.ofMinutes(10).toMillis();

    private static final long RETRY_BACKOFF_BASE_MS = 600L;
    private static final long RETRY_BACKOFF_MAX_MS = 5_000L;

    private static final int MAX_ATTEMPTS = 3;

    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration LOOKUP_CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration SKIN_LOAD_TIMEOUT = Duration.ofSeconds(12);
    private static final long FAILURE_LOG_THROTTLE_MS = Duration.ofSeconds(45).toMillis();

    private static final UUID NIL_UUID = new UUID(0L, 0L);
    private static final AtomicBoolean DEFAULT_SKIN_FALLBACK_WARNED = new AtomicBoolean(false);

    private final Path cacheDirectory;
    private final Executor executor;
    private final Clock clock;
    private final Sleeper sleeper;
    private final Jitter jitter;
    private final HttpClient externalHttpClient;
    private final UsernameLookup usernameLookup;
    private final SkinLoader skinLoader;
    private final DefaultSkinProvider defaultSkinProvider;

    private final Map<String, SourceState> sourceStates = new ConcurrentHashMap<>();
    private final Map<String, LookupCacheEntry> lookupCache = new ConcurrentHashMap<>();

    public SkinResolutionService(Path cacheDirectory) {
        this(
                cacheDirectory,
                createDefaultExecutor(),
                System::currentTimeMillis,
                millis -> Thread.sleep(millis),
                boundExclusive -> boundExclusive <= 0 ? 0L : ThreadLocalRandom.current().nextLong(boundExclusive),
                null,
                null,
                null
        );
        clearLegacyDiskCacheFiles();
    }

    SkinResolutionService(
            Path cacheDirectory,
            Executor executor,
            Clock clock,
            Sleeper sleeper,
            Jitter jitter,
            UsernameLookup usernameLookup,
            SkinLoader skinLoader,
            DefaultSkinProvider defaultSkinProvider
    ) {
        this.cacheDirectory = cacheDirectory;
        this.executor = executor;
        this.clock = clock;
        this.sleeper = sleeper;
        this.jitter = jitter;
        this.externalHttpClient = HttpClient.newBuilder()
                .connectTimeout(LOOKUP_CONNECT_TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.usernameLookup = usernameLookup != null ? usernameLookup : createHttpUsernameLookup();
        this.skinLoader = skinLoader != null ? skinLoader : this::loadSkinViaMinecraft;
        this.defaultSkinProvider = defaultSkinProvider != null ? defaultSkinProvider : this::defaultSkinInternal;
    }

    public ResolvedSkin resolveOrFallback(String username, UUID fallbackUuid) {
        return resolveOrFallback(username, fallbackUuid, false);
    }

    public ResolvedSkin resolveOrFallback(String username, UUID fallbackUuid, boolean allowExternalFallbacks) {
        if (StringUtil.isNullOrEmpty(username)) {
            return defaultSkin(fallbackUuid);
        }

        String normalizedUsername = normalize(username);
        SourceState state = sourceStates.computeIfAbsent(normalizedUsername, key -> new SourceState());

        long now = clock.nowMs();
        ResolvedSkin lastGood;
        boolean shouldFetch;

        synchronized (state) {
            lastGood = state.lastGood;
            boolean stale = lastGood != null && (now - state.lastSuccessAtMs) >= SWR_TTL_MS;
            boolean canAttempt = now >= state.nextRetryAtMs;
            shouldFetch = canAttempt && !state.inFlight.get() && (lastGood == null || stale);
        }

        if (shouldFetch) {
            enqueueFetch(normalizedUsername, fallbackUuid, allowExternalFallbacks, state);
        }

        return lastGood != null ? lastGood : defaultSkin(fallbackUuid);
    }

    public void clearRuntimeCache() {
        sourceStates.clear();
        lookupCache.clear();
    }

    public void forceRefreshSources(PrivacyConfig config) {
        if (config == null) {
            return;
        }

        Set<String> sources = new LinkedHashSet<>();
        addSourceUsername(sources, config.localSkinUser);
        addSourceUsername(sources, config.othersSkinUser);
        addSourceUsername(sources, preferredCapeSource(config.localCapeUser, config.localSkinUser));
        addSourceUsername(sources, preferredCapeSource(config.othersCapeUser, config.othersSkinUser));

        for (String normalizedUsername : sources) {
            SourceState state = sourceStates.computeIfAbsent(normalizedUsername, key -> new SourceState());
            synchronized (state) {
                state.nextRetryAtMs = 0L;
            }
            enqueueFetch(normalizedUsername, sourceFallbackUuid(normalizedUsername), config.enableExternalFallbacks, state);
        }
    }

    public String statusSummary() {
        long now = clock.nowMs();

        boolean anyFetching = false;
        boolean anyLastGoodWithFailure = false;
        long shortestRateLimit = Long.MAX_VALUE;

        for (SourceState state : sourceStates.values()) {
            if (state.inFlight.get()) {
                anyFetching = true;
            }

            synchronized (state) {
                if (state.lastGood != null && state.lastErrorCode != ErrorCode.NONE) {
                    anyLastGoodWithFailure = true;
                }

                if (state.lastErrorCode == ErrorCode.RATE_LIMIT && state.nextRetryAtMs > now) {
                    shortestRateLimit = Math.min(shortestRateLimit, state.nextRetryAtMs - now);
                }
            }
        }

        if (anyFetching) {
            return "Fetching";
        }

        if (shortestRateLimit != Long.MAX_VALUE) {
            long retrySeconds = Math.max(1L, TimeUnit.MILLISECONDS.toSeconds(shortestRateLimit));
            return "Rate limited (retry in " + retrySeconds + "s)";
        }

        if (anyLastGoodWithFailure) {
            return "Using last good";
        }

        return "Idle";
    }

    private void enqueueFetch(String normalizedUsername, UUID fallbackUuid, boolean allowExternalFallbacks, SourceState state) {
        if (!state.inFlight.compareAndSet(false, true)) {
            return;
        }

        logFetchStarted(normalizedUsername, state);
        CompletableFuture.runAsync(() -> fetchAndStore(normalizedUsername, fallbackUuid, allowExternalFallbacks, state), executor)
                .whenComplete((unused, throwable) -> {
                    state.inFlight.set(false);
                    if (throwable != null) {
                        FetchFailure failure = FetchFailure.internal("NH-SKIN-INTERNAL", throwable.getMessage(), 0L, false);
                        applyFailure(normalizedUsername, state, failure);
                    }
                });
    }

    private void fetchAndStore(String normalizedUsername, UUID fallbackUuid, boolean allowExternalFallbacks, SourceState state) {
        FetchFailure finalFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ProfileIdentity identity = usernameLookup.lookup(normalizedUsername);
                ResolvedSkin resolved = skinLoader.load(identity, fallbackUuid);
                if (resolved == null) {
                    throw FetchException.internal("NH-SKIN-EMPTY", "Resolved skin is null", false);
                }

                applySuccess(normalizedUsername, state, resolved, false);
                return;
            } catch (FetchException ex) {
                finalFailure = ex.toFailure();
                if (ex.retryable() && attempt < MAX_ATTEMPTS) {
                    sleepBackoff(attempt);
                    continue;
                }
                break;
            } catch (RuntimeException ex) {
                finalFailure = classifyThrowable("NH-SKIN-INTERNAL", ex).toFailure();
                if (finalFailure.retryable() && attempt < MAX_ATTEMPTS) {
                    sleepBackoff(attempt);
                    continue;
                }
                break;
            }
        }

        if (allowExternalFallbacks) {
            try {
                ResolvedSkin external = loadExternalFallback(normalizedUsername, fallbackUuid);
                applySuccess(normalizedUsername, state, external, true);
                return;
            } catch (FetchException externalFailure) {
                finalFailure = externalFailure.toFailure();
            } catch (RuntimeException externalFailure) {
                finalFailure = classifyThrowable("NH-SKIN-EXT", externalFailure).toFailure();
            }
        }

        if (finalFailure == null) {
            finalFailure = FetchFailure.internal("NH-SKIN-INTERNAL", "Unknown fetch failure", 0L, false);
        }
        applyFailure(normalizedUsername, state, finalFailure);
    }

    private void applySuccess(String normalizedUsername, SourceState state, ResolvedSkin resolved, boolean viaExternalFallback) {
        boolean recovered;
        boolean firstSuccess;
        boolean externalModeChanged;
        int suppressedFailures;

        synchronized (state) {
            recovered = state.lastErrorCode != ErrorCode.NONE;
            firstSuccess = state.lastGood == null;
            externalModeChanged = state.lastSuccessViaExternal != viaExternalFallback;
            suppressedFailures = state.suppressedFailureLogs;

            state.lastGood = resolved;
            state.lastSuccessAtMs = clock.nowMs();
            state.nextRetryAtMs = 0L;
            state.lastErrorCode = ErrorCode.NONE;
            state.consecutiveFailures = 0;
            state.suppressedFailureLogs = 0;
            state.lastFailureSignature = "";
            state.lastFailureLogAtMs = 0L;
            state.lastSuccessViaExternal = viaExternalFallback;
            state.lifecycle = FetchLifecycle.SUCCESS;
        }

        if (viaExternalFallback) {
            NickHider.LOGGER.info(
                    "[NH-SKIN-EXT] Using external fallback skin/cape source for {}",
                    normalizedUsername
            );
            return;
        }

        if (recovered) {
            if (suppressedFailures > 0) {
                NickHider.LOGGER.info(
                        "[NH-SKIN-RECOVERED] Skin/cape source {} recovered after {} throttled failures",
                        normalizedUsername,
                        suppressedFailures
                );
            } else {
                NickHider.LOGGER.info(
                        "[NH-SKIN-RECOVERED] Skin/cape source {} recovered",
                        normalizedUsername
                );
            }
            return;
        }

        if (firstSuccess || externalModeChanged) {
            NickHider.LOGGER.info("[NH-SKIN-READY] Skin/cape source {} is ready", normalizedUsername);
        }
    }

    private void applyFailure(String normalizedUsername, SourceState state, FetchFailure failure) {
        long now = clock.nowMs();
        long cooldownMs;
        boolean hasLastGood;
        int suppressedFailuresToReport = 0;
        boolean shouldLog;

        synchronized (state) {
            int nextFailures = state.consecutiveFailures + 1;
            state.consecutiveFailures = nextFailures;
            cooldownMs = computeCooldownMs(failure.code(), nextFailures, failure.retryAfterMs());
            state.nextRetryAtMs = now + cooldownMs;
            state.lastErrorCode = failure.code();
            state.lifecycle = FetchLifecycle.FAILURE;
            hasLastGood = state.lastGood != null;

            String failureSignature = failure.signature();
            if (failureSignature.equals(state.lastFailureSignature)
                    && (now - state.lastFailureLogAtMs) < FAILURE_LOG_THROTTLE_MS) {
                state.suppressedFailureLogs++;
                shouldLog = false;
            } else {
                suppressedFailuresToReport = state.suppressedFailureLogs;
                state.suppressedFailureLogs = 0;
                state.lastFailureSignature = failureSignature;
                state.lastFailureLogAtMs = now;
                shouldLog = true;
            }
            state.lastSuccessViaExternal = false;
        }

        if (!shouldLog) {
            return;
        }

        long retrySeconds = Math.max(1L, TimeUnit.MILLISECONDS.toSeconds(cooldownMs));
        String lastGoodState = hasLastGood ? "keeping last-known-good" : "no last-known-good";
        String throttledState = suppressedFailuresToReport > 0
                ? ", throttled-similar=" + suppressedFailuresToReport
                : "";
        String detail = failure.detail() == null || failure.detail().isBlank()
                ? ""
                : " detail=" + failure.detail();

        NickHider.LOGGER.warn(
                "[{}] Skin/cape fetch failed for {} (error={}, retry={}s, {}{}{})",
                failure.diagnosticCode(),
                normalizedUsername,
                failure.code(),
                retrySeconds,
                lastGoodState,
                throttledState,
                detail
        );
    }

    private void logFetchStarted(String normalizedUsername, SourceState state) {
        boolean shouldLog;
        synchronized (state) {
            shouldLog = state.lifecycle != FetchLifecycle.FETCHING;
            state.lifecycle = FetchLifecycle.FETCHING;
        }
        if (shouldLog) {
            NickHider.LOGGER.info("[NH-SKIN-FETCH] Fetching skin/cape for {}", normalizedUsername);
        }
    }

    private void sleepBackoff(int attempt) {
        long base = Math.min(RETRY_BACKOFF_MAX_MS, RETRY_BACKOFF_BASE_MS << Math.max(0, attempt - 1));
        long total = base + jitter.nextJitterMs(Math.max(1L, base / 2L));

        try {
            sleeper.sleep(total);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private long computeCooldownMs(ErrorCode code, int consecutiveFailures, long retryAfterMs) {
        if (code == ErrorCode.NOT_FOUND) {
            return MAX_COOLDOWN_MS;
        }

        long exponential = MIN_COOLDOWN_MS;
        if (consecutiveFailures > 1) {
            int shift = Math.min(consecutiveFailures - 1, 5);
            exponential = Math.min(MAX_COOLDOWN_MS, MIN_COOLDOWN_MS << shift);
        }

        long cooldown = clamp(exponential, MIN_COOLDOWN_MS, MAX_COOLDOWN_MS);
        if (code == ErrorCode.RATE_LIMIT && retryAfterMs > 0L) {
            cooldown = Math.max(cooldown, clamp(retryAfterMs, MIN_COOLDOWN_MS, MAX_COOLDOWN_MS));
        }

        return cooldown;
    }

    private UsernameLookup createHttpUsernameLookup() {
        HttpClient primaryClient = HttpClient.newBuilder()
                .connectTimeout(LOOKUP_CONNECT_TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        HttpClient legacyClient = HttpClient.newBuilder()
                .connectTimeout(LOOKUP_CONNECT_TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        return normalizedUsername -> {
            long now = clock.nowMs();
            LookupCacheEntry cached = lookupCache.get(normalizedUsername);
            if (cached != null && cached.expiresAtMs() > now) {
                return cached.identity();
            }

            try {
                ProfileIdentity identity = lookupEndpoint(primaryClient, LOOKUP_PRIMARY_BASE, normalizedUsername, true);
                lookupCache.put(normalizedUsername, new LookupCacheEntry(identity, now + LOOKUP_CACHE_TTL_MS));
                return identity;
            } catch (FetchException primaryFailure) {
                if (primaryFailure.code() == ErrorCode.NOT_FOUND) {
                    throw primaryFailure;
                }

                try {
                    ProfileIdentity identity = lookupEndpoint(legacyClient, LOOKUP_LEGACY_BASE, normalizedUsername, false);
                    lookupCache.put(normalizedUsername, new LookupCacheEntry(identity, now + LOOKUP_CACHE_TTL_MS));
                    return identity;
                } catch (FetchException legacyFailure) {
                    throw preferFailure(primaryFailure, legacyFailure);
                }
            }
        };
    }

    private ProfileIdentity lookupEndpoint(HttpClient client, String base, String normalizedUsername, boolean primary) throws FetchException {
        String encodedUsername = URLEncoder.encode(normalizedUsername, StandardCharsets.UTF_8);
        URI uri = URI.create(base + encodedUsername);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(LOOKUP_TIMEOUT)
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw FetchException.network("NH-SKIN-INTERRUPTED", "Lookup interrupted", true);
        } catch (IOException ex) {
            throw classifyThrowable("NH-SKIN-IO", ex);
        }

        int status = response.statusCode();
        if (status == 200) {
            return parseLookupResponse(response.body(), normalizedUsername);
        }

        if (status == 404) {
            throw FetchException.notFound("NH-SKIN-404", "Username not found");
        }

        if (status == 429) {
            long retryAfterMs = parseRetryAfterMs(response.headers(), clock.nowMs());
            throw FetchException.rateLimited("NH-SKIN-429", "Lookup rate limited", retryAfterMs, true);
        }

        if (status >= 500) {
            long retryAfterMs = parseRetryAfterMs(response.headers(), clock.nowMs());
            throw FetchException.network("NH-SKIN-5XX", "Lookup server error " + status, true, retryAfterMs);
        }

        String endpoint = primary ? "primary" : "legacy";
        throw FetchException.internal(
                "NH-SKIN-HTTP",
                "Unexpected " + endpoint + " lookup response " + status,
                status >= 408 && status < 500
        );
    }

    private ResolvedSkin loadSkinViaMinecraft(ProfileIdentity identity, UUID fallbackUuid) throws FetchException {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            throw FetchException.internal("NH-SKIN-MC", "Minecraft instance unavailable", true);
        }

        SkinManager skinManager = minecraft.getSkinManager();
        if (skinManager == null) {
            throw FetchException.internal("NH-SKIN-MC", "SkinManager unavailable", true);
        }

        UUID sourceUuid = identity.uuid() != null ? identity.uuid() : sourceFallbackUuid(identity.username());
        String sourceName = identity.username() == null || identity.username().isBlank()
                ? sourceUuid.toString()
                : identity.username();

        GameProfile profile = new GameProfile(sourceUuid, sourceName);
        return loadWithSkinManager(minecraft, skinManager, profile, fallbackUuid != null ? fallbackUuid : sourceUuid);
    }

    /*? if <=1.20.1 {*/
    private ResolvedSkin loadWithSkinManager(Minecraft minecraft, SkinManager skinManager, GameProfile profile, UUID fallbackUuid) throws FetchException {
        try {
            MinecraftSessionService sessionService = minecraft.getMinecraftSessionService();
            GameProfile filledProfile = sessionService.fillProfileProperties(profile, false);
            Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = sessionService.getTextures(filledProfile, false);

            MinecraftProfileTexture skinTexture = textures.get(MinecraftProfileTexture.Type.SKIN);
            String modelName = modelNameFromSkinTexture(skinTexture, fallbackUuid);
            Object skinLocation = skinTexture != null
                    ? skinManager.registerTexture(skinTexture, MinecraftProfileTexture.Type.SKIN)
                    : resolveDefaultSkinLocation(fallbackUuid, modelName);

            Object capeLocation = registerOptionalTexture(skinManager, textures.get(MinecraftProfileTexture.Type.CAPE), MinecraftProfileTexture.Type.CAPE);
            Object elytraLocation = registerOptionalTexture(skinManager, textures.get(MinecraftProfileTexture.Type.ELYTRA), MinecraftProfileTexture.Type.ELYTRA);
            return new ResolvedSkin(skinLocation, modelName, capeLocation, elytraLocation);
        } catch (Exception ex) {
            throw classifyThrowable("NH-SKIN-120", ex);
        }
    }

    private static Object registerOptionalTexture(
            SkinManager skinManager,
            MinecraftProfileTexture texture,
            MinecraftProfileTexture.Type type
    ) {
        if (texture == null) {
            return null;
        }
        return skinManager.registerTexture(texture, type);
    }

    private static String modelNameFromSkinTexture(MinecraftProfileTexture skinTexture, UUID fallbackUuid) {
        if (skinTexture == null) {
            return resolveDefaultSkinModel(fallbackUuid);
        }

        String metadataModel = skinTexture.getMetadata("model");
        if (metadataModel == null || metadataModel.isBlank()) {
            return ResolvedSkin.MODEL_DEFAULT;
        }

        return ResolvedSkin.MODEL_SLIM.equalsIgnoreCase(metadataModel)
                ? ResolvedSkin.MODEL_SLIM
                : ResolvedSkin.MODEL_DEFAULT;
    }
    /*?}*/

    /*? if >=1.21.1 {*/
    /*private ResolvedSkin loadWithSkinManager(Minecraft minecraft, SkinManager skinManager, GameProfile profile, UUID fallbackUuid) throws FetchException {
        try {
            MinecraftSessionService sessionService = minecraft.getMinecraftSessionService();
            if (sessionService == null) {
                throw FetchException.internal("NH-SKIN-121-MC", "MinecraftSessionService unavailable", true);
            }

            ProfileResult profileResult = sessionService.fetchProfile(profile.getId(), false);
            if (profileResult == null || profileResult.profile() == null) {
                throw FetchException.notFound("NH-SKIN-121-PROFILE", "Session profile lookup returned no data");
            }

            GameProfile hydratedProfile = profileResult.profile();
            if (!hydratedProfile.getProperties().containsKey("textures")
                    || hydratedProfile.getProperties().get("textures").isEmpty()) {
                throw FetchException.notFound("NH-SKIN-121-TEXTURES", "Session profile has no textures property");
            }

            PlayerSkin playerSkin = skinManager.getOrLoad(hydratedProfile).get(SKIN_LOAD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (playerSkin == null || playerSkin.texture() == null) {
                throw FetchException.internal("NH-SKIN-121-SKIN", "SkinManager returned empty player skin", true);
            }

            String model = playerSkin.model() != null
                    ? playerSkin.model().id()
                    : ResolvedSkin.MODEL_DEFAULT;

            return new ResolvedSkin(
                    playerSkin.texture(),
                    model,
                    playerSkin.capeTexture(),
                    playerSkin.elytraTexture()
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw FetchException.network("NH-SKIN-INTERRUPTED", "Skin load interrupted", true);
        } catch (TimeoutException ex) {
            throw FetchException.network("NH-SKIN-TIMEOUT", "Skin load timed out", true);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            throw classifyThrowable("NH-SKIN-121", cause);
        } catch (RuntimeException ex) {
            throw classifyThrowable("NH-SKIN-121", ex);
        }
    }
    */
    /*?}*/

    private ResolvedSkin loadExternalFallback(String normalizedUsername, UUID fallbackUuid) throws FetchException {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            throw FetchException.internal("NH-SKIN-EXT-MC", "Minecraft instance unavailable", true);
        }

        TextureManager textureManager = minecraft.getTextureManager();
        if (textureManager == null) {
            throw FetchException.internal("NH-SKIN-EXT-MC", "TextureManager unavailable", true);
        }

        UUID resolvedFallbackUuid = fallbackUuid != null ? fallbackUuid : sourceFallbackUuid(normalizedUsername);
        String modelName = resolveDefaultSkinModel(resolvedFallbackUuid);
        ResourceLocation defaultSkin = asResourceLocation(resolveDefaultSkinLocation(resolvedFallbackUuid, modelName));

        String encodedUsername = URLEncoder.encode(normalizedUsername, StandardCharsets.UTF_8);
        String skinUrl = EXTERNAL_SKIN_BASE + encodedUsername;
        probeTextureReachable(skinUrl, "NH-SKIN-EXT-SKIN");

        ResourceLocation skinLocation = registerHttpTexture(
                textureManager,
                "external_skin/" + normalizedUsername,
                skinUrl,
                defaultSkin
        );

        String capeUrl = lookupExternalCapeUrl(normalizedUsername);
        ResourceLocation capeLocation = null;
        if (capeUrl != null && !capeUrl.isBlank()) {
            probeTextureReachable(capeUrl, "NH-SKIN-EXT-CAPE");
            capeLocation = registerHttpTexture(
                    textureManager,
                    "external_cape/" + normalizedUsername,
                    capeUrl,
                    skinLocation
            );
        }

        return new ResolvedSkin(skinLocation, modelName, capeLocation, null);
    }

    private ResourceLocation registerHttpTexture(
            TextureManager textureManager,
            String path,
            String textureUrl,
            ResourceLocation fallbackTexture
    ) throws FetchException {
        ResourceLocation location = createResourceLocation(NickHider.MOD_ID, path);

        try {
            Files.createDirectories(cacheDirectory);
            HttpTexture httpTexture = new HttpTexture(
                    cacheDirectory.toFile(),
                    textureUrl,
                    fallbackTexture,
                    true,
                    () -> {}
            );
            textureManager.register(location, httpTexture);
            return location;
        } catch (RuntimeException | IOException ex) {
            throw classifyThrowable("NH-SKIN-EXT-TEX", ex);
        }
    }

    private void probeTextureReachable(String textureUrl, String diagnosticCode) throws FetchException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(textureUrl))
                .GET()
                .timeout(LOOKUP_TIMEOUT)
                .header("User-Agent", EXTERNAL_FALLBACK_USER_AGENT)
                .build();

        HttpResponse<Void> response;
        try {
            response = externalHttpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw FetchException.network(diagnosticCode, "External fallback probe interrupted", true);
        } catch (IOException ex) {
            throw classifyThrowable(diagnosticCode, ex);
        }

        int status = response.statusCode();
        if (status == 200) {
            return;
        }
        if (status == 404) {
            throw FetchException.notFound(diagnosticCode, "External texture not found");
        }
        if (status == 429) {
            long retryAfterMs = parseRetryAfterMs(response.headers(), clock.nowMs());
            throw FetchException.rateLimited(diagnosticCode, "External texture rate limited", retryAfterMs, true);
        }
        if (status >= 500) {
            throw FetchException.network(diagnosticCode, "External texture server error " + status, true);
        }
        throw FetchException.internal(diagnosticCode, "External texture request failed with " + status, false);
    }

    private String lookupExternalCapeUrl(String normalizedUsername) throws FetchException {
        String encodedUsername = URLEncoder.encode(normalizedUsername, StandardCharsets.UTF_8);
        URI uri = URI.create(EXTERNAL_CAPE_LOOKUP_BASE + encodedUsername);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(LOOKUP_TIMEOUT)
                .header("User-Agent", EXTERNAL_FALLBACK_USER_AGENT)
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response;
        try {
            response = externalHttpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw FetchException.network("NH-SKIN-EXT-CAPES", "External cape lookup interrupted", true);
        } catch (IOException ex) {
            throw classifyThrowable("NH-SKIN-EXT-CAPES", ex);
        }

        int status = response.statusCode();
        if (status == 404) {
            return null;
        }
        if (status == 429) {
            long retryAfterMs = parseRetryAfterMs(response.headers(), clock.nowMs());
            throw FetchException.rateLimited("NH-SKIN-EXT-CAPES-429", "External cape lookup rate limited", retryAfterMs, true);
        }
        if (status >= 500) {
            throw FetchException.network("NH-SKIN-EXT-CAPES-5XX", "External cape lookup server error " + status, true);
        }
        if (status != 200) {
            throw FetchException.internal("NH-SKIN-EXT-CAPES-HTTP", "External cape lookup failed with " + status, false);
        }

        try {
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            String[] preferred = new String[] {"minecraft", "optifine", "minecraftcapes", "labymod", "5zig", "tlauncher", "skinmc"};
            for (String key : preferred) {
                String url = extractCapeUrl(root.getAsJsonObject(key));
                if (url != null) {
                    return url;
                }
            }

            for (var entry : root.entrySet()) {
                if (!(entry.getValue() instanceof JsonObject object)) {
                    continue;
                }
                String url = extractCapeUrl(object);
                if (url != null) {
                    return url;
                }
            }
            return null;
        } catch (RuntimeException ex) {
            throw FetchException.internal("NH-SKIN-EXT-CAPES-PARSE", "External cape lookup parse failure", false);
        }
    }

    private static String extractCapeUrl(JsonObject object) {
        if (object == null || !readBoolean(object, "exists")) {
            return null;
        }

        JsonObject imageUrls = object.has("imageUrls") && object.get("imageUrls").isJsonObject()
                ? object.getAsJsonObject("imageUrls")
                : null;
        if (imageUrls != null && imageUrls.has("base") && imageUrls.get("base").isJsonObject()) {
            JsonObject base = imageUrls.getAsJsonObject("base");
            String baseFull = readString(base, "full");
            if (baseFull != null && !baseFull.isBlank()) {
                return baseFull;
            }
        }

        String imageUrl = readString(object, "imageUrl");
        if (imageUrl != null && !imageUrl.isBlank()) {
            return imageUrl;
        }

        String capeUrl = readString(object, "capeUrl");
        if (capeUrl != null && !capeUrl.isBlank()) {
            return capeUrl;
        }
        return null;
    }

    private static boolean readBoolean(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsBoolean();
    }

    private static String readString(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return null;
        }
        return object.get(key).getAsString();
    }

    private static ResourceLocation createResourceLocation(String namespace, String path) throws FetchException {
        try {
            ResourceLocation location = ResourceLocation.tryBuild(namespace, path);
            if (location == null) {
                throw new IllegalArgumentException("invalid resource location");
            }
            return location;
        } catch (RuntimeException ex) {
            throw FetchException.internal("NH-SKIN-EXT-RL", "Invalid resource location path " + namespace + ":" + path, false);
        }
    }

    private static ResourceLocation asResourceLocation(Object value) throws FetchException {
        if (value instanceof ResourceLocation location) {
            return location;
        }
        throw FetchException.internal("NH-SKIN-EXT-RL", "Expected ResourceLocation fallback texture", false);
    }

    private ResolvedSkin defaultSkin(UUID fallbackUuid) {
        return defaultSkinProvider.defaultSkin(fallbackUuid != null ? fallbackUuid : NIL_UUID);
    }

    private ResolvedSkin defaultSkinInternal(UUID fallbackUuid) {
        UUID resolvedUuid = fallbackUuid != null ? fallbackUuid : NIL_UUID;
        String modelName = resolveDefaultSkinModel(resolvedUuid);
        Object texture = resolveDefaultSkinLocation(resolvedUuid, modelName);
        return new ResolvedSkin(texture, modelName, null, null);
    }

    private void clearLegacyDiskCacheFiles() {
        if (!Files.isDirectory(cacheDirectory)) {
            return;
        }

        try (var stream = Files.list(cacheDirectory)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return fileName.endsWith(".png") || fileName.endsWith(".json");
                    })
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            NickHider.LOGGER.debug("Failed to clean legacy skin cache file {}", path, ex);
                        }
                    });
        } catch (IOException ex) {
            NickHider.LOGGER.debug("Failed to scan legacy skin cache directory {}", cacheDirectory, ex);
        }
    }

    private static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private static void addSourceUsername(Set<String> targets, String username) {
        if (!StringUtil.isNullOrEmpty(username)) {
            targets.add(normalize(username));
        }
    }

    private static String preferredCapeSource(String capeSourceUser, String skinSourceUser) {
        if (!StringUtil.isNullOrEmpty(capeSourceUser)) {
            return capeSourceUser;
        }
        return skinSourceUser;
    }

    private static UUID sourceFallbackUuid(String normalizedUsername) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + normalizedUsername).getBytes(StandardCharsets.UTF_8));
    }

    private static ProfileIdentity parseLookupResponse(String body, String fallbackName) throws FetchException {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (!json.has("id")) {
                throw FetchException.internal("NH-SKIN-PARSE", "Lookup response missing id", false);
            }

            String rawId = json.get("id").getAsString();
            UUID uuid = parseDashedOrCompactUuid(rawId);
            String name = json.has("name") ? json.get("name").getAsString() : fallbackName;
            return new ProfileIdentity(uuid, name);
        } catch (FetchException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw FetchException.internal("NH-SKIN-PARSE", "Lookup response parse failure", false);
        }
    }

    private static UUID parseDashedOrCompactUuid(String value) throws FetchException {
        if (value == null || value.isBlank()) {
            throw FetchException.internal("NH-SKIN-UUID", "UUID value is empty", false);
        }

        String normalized = value.trim();
        if (normalized.length() == 32) {
            normalized = normalized.substring(0, 8) + "-"
                    + normalized.substring(8, 12) + "-"
                    + normalized.substring(12, 16) + "-"
                    + normalized.substring(16, 20) + "-"
                    + normalized.substring(20);
        }

        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException ex) {
            throw FetchException.internal("NH-SKIN-UUID", "Invalid UUID value", false);
        }
    }

    private static long parseRetryAfterMs(HttpHeaders headers, long nowMs) {
        String value = headers.firstValue("Retry-After").orElse(null);
        if (value == null || value.isBlank()) {
            return 0L;
        }

        String trimmed = value.trim();
        try {
            long seconds = Long.parseLong(trimmed);
            return Math.max(0L, TimeUnit.SECONDS.toMillis(seconds));
        } catch (NumberFormatException ignored) {
            // RFC 7231 date form.
        }

        try {
            ZonedDateTime retryAt = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
            return Math.max(0L, retryAt.toInstant().toEpochMilli() - nowMs);
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static long clamp(long value, long min, long max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private static FetchException classifyThrowable(String baseCode, Throwable throwable) {
        if (throwable instanceof FetchException fetchException) {
            return fetchException;
        }

        if (containsGoAway(throwable)) {
            return FetchException.network("NH-SKIN-GOAWAY", safeMessage(throwable), true);
        }

        String message = safeMessage(throwable).toLowerCase(Locale.ROOT);
        if (message.contains("timeout") || message.contains("timed out")) {
            return FetchException.network("NH-SKIN-TIMEOUT", safeMessage(throwable), true);
        }

        if (throwable instanceof IOException) {
            return FetchException.network("NH-SKIN-NETWORK", safeMessage(throwable), true);
        }

        return FetchException.internal(baseCode, safeMessage(throwable), false);
    }

    private static boolean containsGoAway(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toUpperCase(Locale.ROOT).contains("GOAWAY")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return throwable == null ? "unknown" : throwable.getClass().getSimpleName();
        }
        return throwable.getMessage();
    }

    private static FetchException preferFailure(FetchException first, FetchException second) {
        if (second.code() == ErrorCode.NOT_FOUND) {
            return second;
        }
        if (first.code() == ErrorCode.RATE_LIMIT) {
            return first;
        }
        return second;
    }

    private static ExecutorService createDefaultExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "nickhider-skin-fetcher");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static Object resolveDefaultSkinLocation(UUID fallbackUuid, String modelName) {
        try {
            /*? if <=1.20.1 {*/
            return DefaultPlayerSkin.getDefaultSkin(fallbackUuid);
            /*?}*/
            /*? if >=1.21.1 {*/
            /*return DefaultPlayerSkin.get(fallbackUuid).texture();
            */
            /*?}*/
        } catch (RuntimeException ignored) {}

        String fallbackPath = ResolvedSkin.MODEL_SLIM.equalsIgnoreCase(modelName)
                ? "textures/entity/player/slim/alex.png"
                : "textures/entity/player/wide/steve.png";

        if (DEFAULT_SKIN_FALLBACK_WARNED.compareAndSet(false, true)) {
            NickHider.LOGGER.warn("Falling back to hardcoded default skin path for model {}", modelName);
        }

        try {
            Class<?> resourceLocationClass = Class.forName("net.minecraft.resources.ResourceLocation");
            return resourceLocationClass.getMethod("parse", String.class)
                    .invoke(null, "minecraft:" + fallbackPath);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to resolve default skin location", ex);
        }
    }

    private static String resolveDefaultSkinModel(UUID fallbackUuid) {
        try {
            /*? if <=1.20.1 {*/
            return DefaultPlayerSkin.getSkinModelName(fallbackUuid);
            /*?}*/
            /*? if >=1.21.1 {*/
            /*return DefaultPlayerSkin.get(fallbackUuid).model().id();
            */
            /*?}*/
        } catch (RuntimeException ignored) {}
        return ResolvedSkin.MODEL_DEFAULT;
    }

    interface Clock {
        long nowMs();
    }

    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    interface Jitter {
        long nextJitterMs(long boundExclusive);
    }

    interface UsernameLookup {
        ProfileIdentity lookup(String normalizedUsername) throws FetchException;
    }

    interface SkinLoader {
        ResolvedSkin load(ProfileIdentity identity, UUID fallbackUuid) throws FetchException;
    }

    interface DefaultSkinProvider {
        ResolvedSkin defaultSkin(UUID fallbackUuid);
    }

    static final class FetchException extends Exception {
        private final ErrorCode code;
        private final boolean retryable;
        private final String diagnosticCode;
        private final long retryAfterMs;

        private FetchException(ErrorCode code, String diagnosticCode, String message, boolean retryable, long retryAfterMs) {
            super(message);
            this.code = code;
            this.retryable = retryable;
            this.diagnosticCode = diagnosticCode;
            this.retryAfterMs = retryAfterMs;
        }

        static FetchException rateLimited(String diagnosticCode, String message, long retryAfterMs, boolean retryable) {
            return new FetchException(ErrorCode.RATE_LIMIT, diagnosticCode, message, retryable, retryAfterMs);
        }

        static FetchException network(String diagnosticCode, String message, boolean retryable) {
            return new FetchException(ErrorCode.NETWORK, diagnosticCode, message, retryable, 0L);
        }

        static FetchException network(String diagnosticCode, String message, boolean retryable, long retryAfterMs) {
            return new FetchException(ErrorCode.NETWORK, diagnosticCode, message, retryable, retryAfterMs);
        }

        static FetchException notFound(String diagnosticCode, String message) {
            return new FetchException(ErrorCode.NOT_FOUND, diagnosticCode, message, false, 0L);
        }

        static FetchException internal(String diagnosticCode, String message, boolean retryable) {
            return new FetchException(ErrorCode.INTERNAL, diagnosticCode, message, retryable, 0L);
        }

        ErrorCode code() {
            return code;
        }

        boolean retryable() {
            return retryable;
        }

        FetchFailure toFailure() {
            return new FetchFailure(code, diagnosticCode, getMessage(), retryAfterMs, retryable);
        }
    }

    private enum ErrorCode {
        NONE,
        RATE_LIMIT,
        NETWORK,
        NOT_FOUND,
        INTERNAL
    }

    record ProfileIdentity(UUID uuid, String username) {}

    private record LookupCacheEntry(ProfileIdentity identity, long expiresAtMs) {}

    private record FetchFailure(
            ErrorCode code,
            String diagnosticCode,
            String detail,
            long retryAfterMs,
            boolean retryable
    ) {
        static FetchFailure internal(String diagnosticCode, String detail, long retryAfterMs, boolean retryable) {
            return new FetchFailure(ErrorCode.INTERNAL, diagnosticCode, detail, retryAfterMs, retryable);
        }

        String signature() {
            return diagnosticCode + "|" + code;
        }
    }

    private enum FetchLifecycle {
        IDLE,
        FETCHING,
        SUCCESS,
        FAILURE
    }

    private static final class SourceState {
        private final AtomicBoolean inFlight = new AtomicBoolean(false);
        private ResolvedSkin lastGood;
        private long lastSuccessAtMs;
        private long nextRetryAtMs;
        private int consecutiveFailures;
        private ErrorCode lastErrorCode = ErrorCode.NONE;
        private int suppressedFailureLogs;
        private String lastFailureSignature = "";
        private long lastFailureLogAtMs;
        private boolean lastSuccessViaExternal;
        private FetchLifecycle lifecycle = FetchLifecycle.IDLE;
    }
}
