package pl.przxmus.nickhider.core;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import pl.przxmus.nickhider.config.ConfigRepository;
import pl.przxmus.nickhider.config.PrivacyConfig;

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
        return textSanitizer.sanitize(text, configRepository.get());
    }

    public Optional<ResolvedSkin> replacementSkin(UUID targetUuid) {
        PrivacyConfig config = configRepository.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || targetUuid == null) {
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

    public String replacementName(UUID targetUuid, String originalName) {
        PrivacyConfig config = configRepository.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || targetUuid == null || originalName == null) {
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
