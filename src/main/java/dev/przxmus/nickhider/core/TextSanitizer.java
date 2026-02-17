package dev.przxmus.nickhider.core;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import dev.przxmus.nickhider.config.PrivacyConfig;

public final class TextSanitizer {
    private static final Pattern STANDALONE_UUID_PREFIX_PATTERN = Pattern.compile("(?i)\\b[0-9a-f]{8}\\b(?!-)");

    private final PlayerAliasService aliasService;
    private final IdentityMaskingService identityMaskingService;

    public TextSanitizer(PlayerAliasService aliasService, IdentityMaskingService identityMaskingService) {
        this.aliasService = aliasService;
        this.identityMaskingService = identityMaskingService;
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
        Map<String, String> replacements = new LinkedHashMap<>(aliasService.buildReplacementMap(localIdentityUuid, names, config));
        Map<UUID, UUID> uuidMasks = appendUuidReplacements(replacements, names, localIdentityUuid, config);

        List<Map.Entry<String, String>> ordered = replacements.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed())
                .toList();

        String sanitized = text;
        for (Map.Entry<String, String> entry : ordered) {
            sanitized = sanitized.replace(entry.getKey(), entry.getValue());
        }

        sanitized = replaceStandaloneUuidPrefixes(sanitized, uuidMasks);
        return sanitized;
    }

    private Map<UUID, UUID> appendUuidReplacements(
            Map<String, String> replacements,
            Map<UUID, String> namesByUuid,
            UUID localIdentityUuid,
            PrivacyConfig config
    ) {
        Map<UUID, UUID> uuidMasks = new LinkedHashMap<>();
        for (Map.Entry<UUID, String> entry : namesByUuid.entrySet()) {
            UUID originalUuid = entry.getKey();
            String originalName = entry.getValue();
            if (originalUuid == null || originalName == null || originalName.isBlank()) {
                continue;
            }

            boolean localTarget = originalUuid.equals(localIdentityUuid);
            MaskedProfile maskedProfile = identityMaskingService.maskForName(config, localTarget, originalUuid, originalName);
            UUID maskedUuid = maskedProfile.uuid();
            if (maskedUuid == null || maskedUuid.equals(originalUuid)) {
                continue;
            }

            uuidMasks.put(originalUuid, maskedUuid);
            addUuidForms(replacements, originalUuid, maskedUuid);
        }

        return uuidMasks;
    }

    private static void addUuidForms(Map<String, String> replacements, UUID original, UUID masked) {
        String originalDashed = original.toString();
        String maskedDashed = masked.toString();
        String originalCompact = originalDashed.replace("-", "");
        String maskedCompact = maskedDashed.replace("-", "");

        putReplacement(replacements, originalDashed, maskedDashed);
        putReplacement(replacements, originalDashed.toUpperCase(), maskedDashed.toUpperCase());
        putReplacement(replacements, originalCompact, maskedCompact);
        putReplacement(replacements, originalCompact.toUpperCase(), maskedCompact.toUpperCase());

        String originalIntArray = uuidToNbtIntArrayString(original);
        String maskedIntArray = uuidToNbtIntArrayString(masked);
        putReplacement(replacements, originalIntArray, maskedIntArray);
    }

    private static String replaceStandaloneUuidPrefixes(String text, Map<UUID, UUID> uuidMasks) {
        if (text == null || text.isEmpty() || uuidMasks.isEmpty()) {
            return text;
        }

        Map<String, String> lowerPrefixMap = new HashMap<>();
        for (Map.Entry<UUID, UUID> entry : uuidMasks.entrySet()) {
            String originalPrefix = entry.getKey().toString().substring(0, 8);
            String maskedPrefix = entry.getValue().toString().substring(0, 8);
            if (!originalPrefix.equals(maskedPrefix)) {
                lowerPrefixMap.put(originalPrefix, maskedPrefix);
            }
        }

        if (lowerPrefixMap.isEmpty()) {
            return text;
        }

        Matcher matcher = STANDALONE_UUID_PREFIX_PATTERN.matcher(text);
        StringBuffer out = new StringBuffer(text.length());
        while (matcher.find()) {
            String token = matcher.group();
            String replacement = lowerPrefixMap.get(token.toLowerCase(Locale.ROOT));
            if (replacement == null) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(token));
                continue;
            }

            boolean allUpper = token.equals(token.toUpperCase(Locale.ROOT));
            String normalizedReplacement = allUpper ? replacement.toUpperCase(Locale.ROOT) : replacement;
            matcher.appendReplacement(out, Matcher.quoteReplacement(normalizedReplacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String uuidToNbtIntArrayString(UUID uuid) {
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        int a = (int) (most >> 32);
        int b = (int) most;
        int c = (int) (least >> 32);
        int d = (int) least;
        return "[I; " + a + ", " + b + ", " + c + ", " + d + "]";
    }

    private static void putReplacement(Map<String, String> replacements, String original, String masked) {
        if (original == null || masked == null || original.isBlank() || original.equals(masked)) {
            return;
        }
        replacements.put(original, masked);
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
