package dev.przxmus.nickhider.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import dev.przxmus.nickhider.NickHider;
import dev.przxmus.nickhider.config.PrivacyConfig;

public final class PlayerAliasService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long MAX_ID_SPACE = 2_176_782_336L; // 36^6

    private final Path cachePath;
    private final Map<UUID, String> uuidToShortId = new HashMap<>();
    private final Map<String, UUID> shortIdToUuid = new HashMap<>();

    public PlayerAliasService(Path cachePath) {
        this.cachePath = cachePath;
        loadCache();
    }

    public synchronized Map<String, String> buildReplacementMap(UUID localPlayerUuid, Map<UUID, String> namesByUuid, PrivacyConfig config) {
        if (!config.enabled) {
            return Map.of();
        }

        Map<String, String> replacements = new LinkedHashMap<>();

        for (Map.Entry<UUID, String> entry : namesByUuid.entrySet()) {
            UUID uuid = entry.getKey();
            String original = entry.getValue();

            if (original == null || original.isBlank()) {
                continue;
            }

            if (uuid.equals(localPlayerUuid)) {
                if (config.hideLocalName && !config.localName.equals(original)) {
                    replacements.put(original, config.localName);
                }
                continue;
            }

            if (!config.hideOtherNames) {
                continue;
            }

            String replacement = config.othersNameTemplate.replace("[ID]", getOrCreateShortId(uuid));
            if (!replacement.equals(original)) {
                replacements.put(original, replacement);
            }
        }

        List<Map.Entry<String, String>> entries = replacements.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed())
                .toList();

        LinkedHashMap<String, String> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        return sorted;
    }

    public synchronized String getOrCreateShortId(UUID uuid) {
        String existing = uuidToShortId.get(uuid);
        if (existing != null) {
            return existing;
        }

        int salt = 0;
        String candidate;
        do {
            candidate = candidateId(uuid, salt++);
        } while (shortIdToUuid.containsKey(candidate) && !uuid.equals(shortIdToUuid.get(candidate)));

        uuidToShortId.put(uuid, candidate);
        shortIdToUuid.put(candidate, uuid);
        persistCache();
        return candidate;
    }

    private String candidateId(UUID uuid, int salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((uuid + ":" + salt).getBytes(StandardCharsets.UTF_8));

            long value = 0L;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (bytes[i] & 0xFFL);
            }

            long modded = Long.remainderUnsigned(value, MAX_ID_SPACE);
            String encoded = Long.toString(modded, 36).toLowerCase(Locale.ROOT);
            return encoded.isEmpty() ? "0" : encoded;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private void loadCache() {
        if (!Files.exists(cachePath)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(cachePath, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return;
            }

            JsonObject aliases = root.getAsJsonObject("aliases");
            if (aliases == null) {
                return;
            }

            for (String key : aliases.keySet()) {
                UUID uuid = UUID.fromString(key);
                String shortId = aliases.get(key).getAsString();
                uuidToShortId.put(uuid, shortId);
                shortIdToUuid.put(shortId, uuid);
            }
        } catch (IOException | JsonParseException | IllegalArgumentException ex) {
            NickHider.LOGGER.warn("Failed to load alias cache from {}", cachePath, ex);
            uuidToShortId.clear();
            shortIdToUuid.clear();
        }
    }

    private void persistCache() {
        JsonObject root = new JsonObject();
        JsonObject aliases = new JsonObject();

        for (Map.Entry<UUID, String> entry : uuidToShortId.entrySet()) {
            aliases.addProperty(entry.getKey().toString(), entry.getValue());
        }

        root.add("aliases", aliases);

        try {
            Files.createDirectories(cachePath.getParent());
            Path temp = cachePath.resolveSibling(cachePath.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            Files.move(temp, cachePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            NickHider.LOGGER.error("Failed to persist alias cache at {}", cachePath, ex);
        }
    }
}
