package dev.przxmus.nickhider.core;

import dev.przxmus.nickhider.config.PrivacyConfig;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class IdentityMaskingService {
    private final PlayerAliasService aliasService;

    public IdentityMaskingService(PlayerAliasService aliasService) {
        this.aliasService = Objects.requireNonNull(aliasService, "aliasService");
    }

    public MaskedProfile maskForName(PrivacyConfig config, boolean localTarget, UUID originalUuid, String originalName) {
        if (config == null || !config.enabled || originalUuid == null || originalName == null || originalName.isBlank()) {
            return new MaskedProfile(originalUuid, originalName);
        }

        String maskedName = maskedNameForName(config, localTarget, originalUuid, originalName);
        if (Objects.equals(maskedName, originalName)) {
            return new MaskedProfile(originalUuid, originalName);
        }

        return new MaskedProfile(syntheticUuid(maskedName), maskedName);
    }

    public MaskedProfile maskForHead(PrivacyConfig config, boolean localTarget, UUID originalUuid, String originalName) {
        if (config == null || !config.enabled || originalUuid == null || originalName == null || originalName.isBlank()) {
            return new MaskedProfile(originalUuid, originalName);
        }

        boolean hideSkin = localTarget ? config.hideLocalSkin : config.hideOtherSkins;
        if (!hideSkin) {
            return new MaskedProfile(originalUuid, originalName);
        }

        String renderedName = maskedNameForName(config, localTarget, originalUuid, originalName);
        return new MaskedProfile(syntheticUuid(renderedName), renderedName);
    }

    UUID syntheticUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private String maskedNameForName(PrivacyConfig config, boolean localTarget, UUID originalUuid, String originalName) {
        if (localTarget) {
            return config.hideLocalName ? config.localName : originalName;
        }

        if (!config.hideOtherNames) {
            return originalName;
        }

        return config.othersNameTemplate.replace("[ID]", aliasService.getOrCreateShortId(originalUuid));
    }
}
