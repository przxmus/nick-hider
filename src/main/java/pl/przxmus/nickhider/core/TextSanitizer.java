package pl.przxmus.nickhider.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import pl.przxmus.nickhider.config.PrivacyConfig;

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
        names.put(localPlayer.getUUID(), localPlayer.getGameProfile().getName());

        if (minecraft.getConnection() != null) {
            for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                if (info.getProfile().getId() == null || info.getProfile().getName() == null) {
                    continue;
                }
                names.put(info.getProfile().getId(), info.getProfile().getName());
            }
        }

        Map<String, String> replacements = aliasService.buildReplacementMap(localPlayer.getUUID(), names, config);
        String sanitized = text;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            sanitized = sanitized.replace(entry.getKey(), entry.getValue());
        }

        return sanitized;
    }
}
