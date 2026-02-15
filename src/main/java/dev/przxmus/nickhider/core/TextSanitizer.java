package dev.przxmus.nickhider.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import dev.przxmus.nickhider.config.PrivacyConfig;

public final class TextSanitizer {
    private final PlayerAliasService aliasService;

    public TextSanitizer(PlayerAliasService aliasService) {
        this.aliasService = aliasService;
    }

    public String sanitize(String text, PrivacyConfig config) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        if (localPlayer == null) {
            return text;
        }

        Map<UUID, String> names = new LinkedHashMap<>();
        String localName = ProfileCompat.name(localPlayer.getGameProfile());
        if (localName != null) {
            names.put(localPlayer.getUUID(), localName);
        }

        if (minecraft.getConnection() != null) {
            for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                UUID profileId = ProfileCompat.id(info.getProfile());
                String profileName = ProfileCompat.name(info.getProfile());
                if (profileId == null || profileName == null) {
                    continue;
                }
                names.put(profileId, profileName);
            }
        }

        UUID localIdentityUuid = resolveLocalIdentityUuid(minecraft, localPlayer, names);
        Map<String, String> replacements = aliasService.buildReplacementMap(localIdentityUuid, names, config);
        String sanitized = text;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            sanitized = sanitized.replace(entry.getKey(), entry.getValue());
        }

        return sanitized;
    }

    private static UUID resolveLocalIdentityUuid(Minecraft minecraft, LocalPlayer localPlayer, Map<UUID, String> names) {
        User user = minecraft.getUser();
        if (user != null) {
            UUID accountUuid = user.getProfileId();
            if (accountUuid != null && names.containsKey(accountUuid)) {
                return accountUuid;
            }

            String accountName = user.getName();
            if (accountName != null && !accountName.isBlank()) {
                for (Map.Entry<UUID, String> entry : names.entrySet()) {
                    if (accountName.equalsIgnoreCase(entry.getValue())) {
                        return entry.getKey();
                    }
                }
            }

            if (accountUuid != null) {
                return accountUuid;
            }
        }

        return localPlayer.getUUID();
    }
}
