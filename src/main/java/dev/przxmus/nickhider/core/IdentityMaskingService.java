package dev.przxmus.nickhider.core;

import dev.przxmus.nickhider.config.PrivacyConfig;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.util.StringUtil;

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

        String headName = headMaskName(config, localTarget, originalUuid, originalName);
        UUID headUuid = syntheticUuid(headName);
        if (headUuid.equals(originalUuid)) {
            headUuid = UUID.nameUUIDFromBytes(("NickHiderSkin:" + originalUuid).getBytes(StandardCharsets.UTF_8));
        }
        return new MaskedProfile(headUuid, headName);
    }

    UUID syntheticUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private String headMaskName(PrivacyConfig config, boolean localTarget, UUID originalUuid, String originalName) {
        String sourceUser = localTarget ? config.localSkinUser : config.othersSkinUser;
        if (!StringUtil.isNullOrEmpty(sourceUser)) {
            return sourceUser.trim();
        }

        String maskedFromNameRules = maskedNameForName(config, localTarget, originalUuid, originalName);
        if (!Objects.equals(maskedFromNameRules, originalName)) {
            return maskedFromNameRules;
        }

        return "nh_" + aliasService.getOrCreateShortId(originalUuid);
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
