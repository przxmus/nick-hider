package dev.przxmus.nickhider.core;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final PlayerAliasService aliasService;
    private final SkinResolutionService skinResolutionService;
    private final TextSanitizer textSanitizer;

    private final AtomicInteger skinHookFailures = new AtomicInteger(0);
    private volatile long skinHookDisabledUntilMs;

    public PrivacyRuntimeState(
            ConfigRepository configRepository,
            PlayerAliasService aliasService,
            SkinResolutionService skinResolutionService,
            TextSanitizer textSanitizer
    ) {
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository");
        this.aliasService = Objects.requireNonNull(aliasService, "aliasService");
        this.skinResolutionService = Objects.requireNonNull(skinResolutionService, "skinResolutionService");
        this.textSanitizer = Objects.requireNonNull(textSanitizer, "textSanitizer");
    }

    public PrivacyConfig config() {
        return configRepository.get();
    }

    public void reloadConfig() {
        configRepository.reload();
        skinResolutionService.clearRuntimeCache();
    }

    public void saveConfig(PrivacyConfig config) {
        configRepository.save(config);
        skinResolutionService.clearRuntimeCache();
    }

    public void refreshSkinCapeNow() {
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
                "Nick Hider disabled skin/cape overrides for {}ms after repeated hook failures (last hook: {})",
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

        if (isLocalTarget(targetUuid, originalName, minecraft)) {
            return config.hideLocalName ? config.localName : originalName;
        }

        if (!config.hideOtherNames) {
            return originalName;
        }

        return config.othersNameTemplate.replace("[ID]", aliasService.getOrCreateShortId(targetUuid));
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

    public void onWorldChange() {
        skinResolutionService.clearRuntimeCache();
        skinHookFailures.set(0);
        skinHookDisabledUntilMs = 0L;
    }

    private boolean isSkinCapeCircuitOpen() {
        return System.currentTimeMillis() < skinHookDisabledUntilMs;
    }
}
