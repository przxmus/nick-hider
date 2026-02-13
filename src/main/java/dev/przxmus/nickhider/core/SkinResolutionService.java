package dev.przxmus.nickhider.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringUtil;
import com.mojang.blaze3d.platform.NativeImage;
import dev.przxmus.nickhider.NickHider;

public final class SkinResolutionService {
    private static final Gson GSON = new Gson();

    private final Path cacheDirectory;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final Map<String, ResolvedSkin> memoryCache = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public SkinResolutionService(Path cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "nickhider-skin-fetcher");
            thread.setDaemon(true);
            return thread;
        });
    }

    public ResolvedSkin resolveOrFallback(String username, UUID fallbackUuid) {
        if (StringUtil.isNullOrEmpty(username)) {
            return defaultSkin(fallbackUuid);
        }

        String normalized = username.toLowerCase(Locale.ROOT);
        ResolvedSkin cached = memoryCache.get(normalized);
        if (cached != null) {
            return cached;
        }

        ResolvedSkin diskCached = loadFromDisk(normalized);
        if (diskCached != null) {
            memoryCache.put(normalized, diskCached);
            return diskCached;
        }

        enqueueFetch(normalized);
        return defaultSkin(fallbackUuid);
    }

    public void clearRuntimeCache() {
        memoryCache.clear();
    }

    private void enqueueFetch(String normalizedUsername) {
        if (!inFlight.add(normalizedUsername)) {
            return;
        }

        CompletableFuture.runAsync(() -> fetchAndStore(normalizedUsername), executor)
                .whenComplete((unused, throwable) -> {
                    inFlight.remove(normalizedUsername);
                    if (throwable != null) {
                        NickHider.LOGGER.warn("Skin fetch failed for {}", normalizedUsername, throwable);
                    }
                });
    }

    private void fetchAndStore(String normalizedUsername) {
        try {
            Files.createDirectories(cacheDirectory);

            String uuidNoDashes = lookupUuid(normalizedUsername);
            if (uuidNoDashes == null) {
                return;
            }

            SkinLookupResult lookupResult = lookupSkin(uuidNoDashes);
            if (lookupResult == null || lookupResult.skinUrl == null) {
                return;
            }

            HttpRequest imageRequest = HttpRequest.newBuilder(URI.create(lookupResult.skinUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<byte[]> imageResponse = httpClient.send(imageRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (imageResponse.statusCode() != 200) {
                return;
            }

            Path pngPath = texturePath(normalizedUsername);
            Path metaPath = metaPath(normalizedUsername);

            Files.write(pngPath, imageResponse.body());

            JsonObject meta = new JsonObject();
            meta.addProperty("model", lookupResult.modelName);
            try (Writer writer = Files.newBufferedWriter(metaPath, StandardCharsets.UTF_8)) {
                GSON.toJson(meta, writer);
            }
        } catch (Exception ex) {
            NickHider.LOGGER.warn("Failed to fetch skin for {}", normalizedUsername, ex);
        }
    }

    private String lookupUuid(String username) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                .GET()
                .timeout(Duration.ofSeconds(8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            return null;
        }

        JsonObject object = JsonParser.parseString(response.body()).getAsJsonObject();
        return object.has("id") ? object.get("id").getAsString() : null;
    }

    private SkinLookupResult lookupSkin(String uuidNoDashes) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuidNoDashes + "?unsigned=false"))
                .GET()
                .timeout(Duration.ofSeconds(8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            return null;
        }

        JsonObject profile = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!profile.has("properties")) {
            return null;
        }

        for (var element : profile.getAsJsonArray("properties")) {
            JsonObject property = element.getAsJsonObject();
            if (!"textures".equals(property.get("name").getAsString())) {
                continue;
            }

            String encoded = property.get("value").getAsString();
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            JsonObject texturesRoot = JsonParser.parseString(decoded).getAsJsonObject();
            if (!texturesRoot.has("textures") || !texturesRoot.getAsJsonObject("textures").has("SKIN")) {
                return null;
            }

            JsonObject skin = texturesRoot.getAsJsonObject("textures").getAsJsonObject("SKIN");
            String url = skin.get("url").getAsString();
            String model = ResolvedSkin.MODEL_DEFAULT;
            if (skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")) {
                model = skin.getAsJsonObject("metadata").get("model").getAsString();
            }

            return new SkinLookupResult(url, model);
        }

        return null;
    }

    private ResolvedSkin loadFromDisk(String normalizedUsername) {
        Path pngPath = texturePath(normalizedUsername);
        Path metaPath = metaPath(normalizedUsername);

        if (!Files.exists(pngPath) || !Files.exists(metaPath)) {
            return null;
        }

        String modelName = ResolvedSkin.MODEL_DEFAULT;
        try (Reader reader = Files.newBufferedReader(metaPath, StandardCharsets.UTF_8)) {
            JsonObject meta = JsonParser.parseReader(reader).getAsJsonObject();
            if (meta.has("model")) {
                modelName = meta.get("model").getAsString();
            }
        } catch (Exception ex) {
            NickHider.LOGGER.warn("Failed to read skin metadata {}", metaPath, ex);
        }

        try (InputStream input = Files.newInputStream(pngPath)) {
            NativeImage image = NativeImage.read(input);
            ResourceLocation location = new ResourceLocation(NickHider.MOD_ID, "cached_skin/" + normalizedUsername);
            Minecraft minecraft = Minecraft.getInstance();
            TextureManager textureManager = minecraft.getTextureManager();
            textureManager.register(location, new DynamicTexture(image));
            return new ResolvedSkin(location, modelName);
        } catch (IOException ex) {
            NickHider.LOGGER.warn("Failed to load cached skin {}", pngPath, ex);
            return null;
        }
    }

    private Path texturePath(String normalizedUsername) {
        return cacheDirectory.resolve(normalizedUsername + ".png");
    }

    private Path metaPath(String normalizedUsername) {
        return cacheDirectory.resolve(normalizedUsername + ".json");
    }

    private ResolvedSkin defaultSkin(UUID fallbackUuid) {
        ResourceLocation location = DefaultPlayerSkin.getDefaultSkin(fallbackUuid);
        String modelName = DefaultPlayerSkin.getSkinModelName(fallbackUuid);
        return new ResolvedSkin(location, modelName);
    }

    private record SkinLookupResult(String skinUrl, String modelName) {}
}
