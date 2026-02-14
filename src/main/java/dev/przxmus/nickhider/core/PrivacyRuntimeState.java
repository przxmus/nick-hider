package dev.przxmus.nickhider.core;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StringUtil;
import dev.przxmus.nickhider.config.ConfigRepository;
import dev.przxmus.nickhider.config.PrivacyConfig;

public final class PrivacyRuntimeState {
    private final ConfigRepository configRepository;
    private final PlayerAliasService aliasService;
    private final SkinResolutionService skinResolutionService;
    private final TextSanitizer textSanitizer;

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

    public String sanitizeText(String text) {
        PrivacyConfig config = configRepository.get();
        if (!config.enabled) {
            return text;
        }
        return textSanitizer.sanitize(text, config);
    }

    public Optional<ResolvedSkin> replacementSkin(UUID targetUuid) {
        PrivacyConfig config = configRepository.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (!config.enabled || minecraft.player == null || targetUuid == null) {
            return Optional.empty();
        }

        boolean local = targetUuid.equals(minecraft.player.getUUID());
        if (local && !config.hideLocalSkin) {
            return Optional.empty();
        }
        if (!local && !config.hideOtherSkins) {
            return Optional.empty();
        }

        String sourceUser = local ? config.localSkinUser : config.othersSkinUser;
        return Optional.of(skinResolutionService.resolveOrFallback(sourceUser, targetUuid));
    }

    public boolean shouldOverrideCape(UUID targetUuid) {
        PrivacyConfig config = configRepository.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (!config.enabled || minecraft.player == null || targetUuid == null) {
            return false;
        }

        if (targetUuid.equals(minecraft.player.getUUID())) {
            return config.hideLocalCape;
        }
        return config.hideOtherCapes;
    }

    public Optional<ResolvedSkin> replacementCape(UUID targetUuid) {
        if (!shouldOverrideCape(targetUuid)) {
            return Optional.empty();
        }

        PrivacyConfig config = configRepository.get();
        Minecraft minecraft = Minecraft.getInstance();
        boolean local = targetUuid.equals(minecraft.player.getUUID());
        String sourceUser = local ? preferredCapeSource(config.localCapeUser, config.localSkinUser) : preferredCapeSource(config.othersCapeUser, config.othersSkinUser);
        if (StringUtil.isNullOrEmpty(sourceUser)) {
            return Optional.empty();
        }

        ResolvedSkin replacement = skinResolutionService.resolveOrFallback(sourceUser, targetUuid);
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

        if (targetUuid.equals(minecraft.player.getUUID())) {
            return config.hideLocalName ? config.localName : originalName;
        }

        if (!config.hideOtherNames) {
            return originalName;
        }

        return config.othersNameTemplate.replace("[ID]", aliasService.getOrCreateShortId(targetUuid));
    }

    public void onWorldChange() {
        skinResolutionService.clearRuntimeCache();
    }
}
