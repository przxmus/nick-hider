package dev.przxmus.nickhider.core;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.util.StringUtil;
import dev.przxmus.nickhider.NickHider;
import dev.przxmus.nickhider.config.ConfigRepository;
import dev.przxmus.nickhider.config.PrivacyConfig;

public final class PrivacyRuntimeState {
    private static final int SKIN_HOOK_FAILURE_THRESHOLD = 5;
    private static final long SKIN_HOOK_CIRCUIT_BREAKER_MS = 60_000L;

    private final ConfigRepository configRepository;
    private final IdentityMaskingService identityMaskingService;
    private final SkinResolutionService skinResolutionService;
    private final TextSanitizer textSanitizer;

    private final AtomicInteger skinHookFailures = new AtomicInteger(0);
    private volatile long skinHookDisabledUntilMs;

    public PrivacyRuntimeState(
            ConfigRepository configRepository,
            IdentityMaskingService identityMaskingService,
            SkinResolutionService skinResolutionService,
            TextSanitizer textSanitizer
    ) {
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository");
        this.identityMaskingService = Objects.requireNonNull(identityMaskingService, "identityMaskingService");
        this.skinResolutionService = Objects.requireNonNull(skinResolutionService, "skinResolutionService");
        this.textSanitizer = Objects.requireNonNull(textSanitizer, "textSanitizer");
    }

    public PrivacyConfig config() {
        return configRepository.get();
    }

    public void reloadConfig() {
        configRepository.reload();
        refreshSkinSourcesAfterConfigChange();
    }

    public void saveConfig(PrivacyConfig config) {
        configRepository.save(config);
        refreshSkinSourcesAfterConfigChange();
    }

    private void refreshSkinSourcesAfterConfigChange() {
        skinResolutionService.clearRuntimeCache();
        skinResolutionService.forceRefreshSources(configRepository.get());
    }

    public String skinCapeStatusSummary() {
        if (isSkinCapeCircuitOpen()) {
            long seconds = Math.max(1L, (skinHookDisabledUntilMs - System.currentTimeMillis()) / 1000L);
            return "Temporarily disabled (" + seconds + "s)";
        }
        return skinResolutionService.statusSummary();
    }

    public void reportSkinCapeHookFailure(String hook, Throwable throwable) {
        int failures = skinHookFailures.incrementAndGet();
        if (failures < SKIN_HOOK_FAILURE_THRESHOLD) {
            return;
        }

        skinHookFailures.set(0);
        skinHookDisabledUntilMs = System.currentTimeMillis() + SKIN_HOOK_CIRCUIT_BREAKER_MS;
        NickHider.LOGGER.warn(
                "[NH-HOOK-CB] Nick Hider disabled skin/cape overrides for {}ms after repeated hook failures (last hook={})",
                SKIN_HOOK_CIRCUIT_BREAKER_MS,
                hook,
                throwable
        );
    }

    public String sanitizeText(String text) {
        PrivacyConfig config = configRepository.get();
        if (!config.enabled) {
            return text;
        }
        return textSanitizer.sanitize(text, config);
    }

    public Optional<ResolvedSkin> replacementSkin(UUID targetUuid) {
        return replacementSkin(targetUuid, null);
    }

    public Optional<ResolvedSkin> replacementSkin(UUID targetUuid, String targetName) {
        PrivacyConfig config = configRepository.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (!config.enabled || minecraft.player == null || targetUuid == null || isSkinCapeCircuitOpen()) {
            return Optional.empty();
        }

        boolean local = isLocalTarget(targetUuid, targetName, minecraft);
        if (local && !config.hideLocalSkin) {
            return Optional.empty();
        }
        if (!local && !config.hideOtherSkins) {
            return Optional.empty();
        }

        String sourceUser = local ? config.localSkinUser : config.othersSkinUser;
        return Optional.of(skinResolutionService.resolveOrFallback(sourceUser, targetUuid, config.enableExternalFallbacks));
    }

    public boolean shouldOverrideCape(UUID targetUuid) {
        return shouldOverrideCape(targetUuid, null);
    }

    public boolean shouldOverrideCape(UUID targetUuid, String targetName) {
        PrivacyConfig config = configRepository.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (!config.enabled || minecraft.player == null || targetUuid == null || isSkinCapeCircuitOpen()) {
            return false;
        }

        if (isLocalTarget(targetUuid, targetName, minecraft)) {
            return config.hideLocalCape;
        }
        return config.hideOtherCapes;
    }

    public Optional<ResolvedSkin> replacementCape(UUID targetUuid) {
        return replacementCape(targetUuid, null);
    }

    public Optional<ResolvedSkin> replacementCape(UUID targetUuid, String targetName) {
        if (!shouldOverrideCape(targetUuid, targetName)) {
            return Optional.empty();
        }

        PrivacyConfig config = configRepository.get();
        Minecraft minecraft = Minecraft.getInstance();
        boolean local = isLocalTarget(targetUuid, targetName, minecraft);
        String sourceUser = local ? preferredCapeSource(config.localCapeUser, config.localSkinUser) : preferredCapeSource(config.othersCapeUser, config.othersSkinUser);
        if (StringUtil.isNullOrEmpty(sourceUser)) {
            return Optional.empty();
        }

        ResolvedSkin replacement = skinResolutionService.resolveOrFallback(sourceUser, targetUuid, config.enableExternalFallbacks);
        if (replacement.capeTextureLocation() == null && replacement.elytraTextureLocation() == null) {
            return Optional.empty();
        }
        return Optional.of(replacement);
    }

    private static String preferredCapeSource(String capeSourceUser, String skinSourceUser) {
        if (!StringUtil.isNullOrEmpty(capeSourceUser)) {
            return capeSourceUser;
        }
        return skinSourceUser;
    }

    public String replacementName(UUID targetUuid, String originalName) {
        PrivacyConfig config = configRepository.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (!config.enabled || minecraft.player == null || targetUuid == null || originalName == null) {
            return originalName;
        }

        boolean local = isLocalTarget(targetUuid, originalName, minecraft);
        return identityMaskingService.maskForName(config, local, targetUuid, originalName).name();
    }

    public GameProfile maskProfileForName(UUID targetUuid, String originalName) {
        return maskProfile(targetUuid, originalName, false);
    }

    public GameProfile maskProfileForHead(UUID targetUuid, String originalName) {
        return maskProfile(targetUuid, originalName, true);
    }

    private GameProfile maskProfile(UUID targetUuid, String originalName, boolean forHead) {
        PrivacyConfig config = configRepository.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (!config.enabled || minecraft.player == null || targetUuid == null) {
            return null;
        }

        String resolvedName = resolveRenderableName(targetUuid, originalName, minecraft);
        if (resolvedName == null || resolvedName.isBlank()) {
            return null;
        }

        boolean local = isLocalTarget(targetUuid, resolvedName, minecraft);
        MaskedProfile maskedProfile = forHead
                ? identityMaskingService.maskForHead(config, local, targetUuid, resolvedName)
                : identityMaskingService.maskForName(config, local, targetUuid, resolvedName);

        if (maskedProfile.isSameAs(targetUuid, resolvedName)) {
            return null;
        }

        return maskedProfile.toGameProfile();
    }

    private static String resolveRenderableName(UUID targetUuid, String originalName, Minecraft minecraft) {
        if (originalName != null && !originalName.isBlank()) {
            return originalName;
        }

        if (minecraft.getConnection() != null) {
            for (var info : minecraft.getConnection().getOnlinePlayers()) {
                UUID infoUuid = ProfileCompat.id(info.getProfile());
                if (targetUuid.equals(infoUuid)) {
                    String infoName = ProfileCompat.name(info.getProfile());
                    if (infoName != null && !infoName.isBlank()) {
                        return infoName;
                    }
                }
            }
        }

        User user = minecraft.getUser();
        if (user != null) {
            UUID accountUuid = user.getProfileId();
            if (targetUuid.equals(accountUuid)) {
                String accountName = user.getName();
                if (accountName != null && !accountName.isBlank()) {
                    return accountName;
                }
            }
        }

        return targetUuid.toString().replace("-", "").substring(0, 16);
    }

    private static boolean isLocalTarget(UUID targetUuid, String targetName, Minecraft minecraft) {
        User user = minecraft.getUser();
        if (user != null) {
            UUID accountUuid = user.getProfileId();
            if (accountUuid != null && accountUuid.equals(targetUuid)) {
                return true;
            }
        }

        if (minecraft.player != null && targetUuid.equals(minecraft.player.getUUID())) {
            return true;
        }

        if (targetName == null || targetName.isBlank() || user == null) {
            return false;
        }

        String accountName = user.getName();
        return accountName != null && !accountName.isBlank() && targetName.equalsIgnoreCase(accountName);
    }

    public void onWorldJoin() {
        skinResolutionService.clearRuntimeCache();
        skinResolutionService.forceRefreshSources(configRepository.get());
        resetSkinCapeCircuitBreaker();
    }

    public void onWorldLeave() {
        skinResolutionService.clearRuntimeCache();
        resetSkinCapeCircuitBreaker();
    }

    private void resetSkinCapeCircuitBreaker() {
        skinHookFailures.set(0);
        skinHookDisabledUntilMs = 0L;
    }

    private boolean isSkinCapeCircuitOpen() {
        return System.currentTimeMillis() < skinHookDisabledUntilMs;
    }
}
