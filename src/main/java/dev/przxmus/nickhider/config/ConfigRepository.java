package dev.przxmus.nickhider.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import dev.przxmus.nickhider.NickHider;

public final class ConfigRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configPath;
    private PrivacyConfig current = new PrivacyConfig();

    public ConfigRepository(Path configPath) {
        this.configPath = configPath;
    }

    public synchronized PrivacyConfig get() {
        return current.copy();
    }

    public synchronized void reload() {
        PrivacyConfig loaded = readFile();
        List<ConfigValidationError> errors = ConfigValidator.validate(loaded);
        if (!errors.isEmpty()) {
            NickHider.LOGGER.warn("Invalid config at {}. Reverting to defaults. Errors: {}", configPath, joinFallbackMessages(errors));
            loaded = new PrivacyConfig();
            writeFile(loaded);
        }

        current = loaded;
    }

    public synchronized void save(PrivacyConfig next) {
        List<ConfigValidationError> errors = ConfigValidator.validate(next);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid privacy config: " + joinFallbackMessages(errors));
        }

        writeFile(next);
        current = next.copy();
    }

    private PrivacyConfig readFile() {
        if (!Files.exists(configPath)) {
            PrivacyConfig defaults = new PrivacyConfig();
            writeFile(defaults);
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            PrivacyConfig loaded = GSON.fromJson(reader, PrivacyConfig.class);
            return loaded == null ? new PrivacyConfig() : loaded;
        } catch (IOException | JsonParseException ex) {
            NickHider.LOGGER.warn("Failed to read config {}, using defaults", configPath, ex);
            return new PrivacyConfig();
        }
    }

    private void writeFile(PrivacyConfig config) {
        try {
            Files.createDirectories(configPath.getParent());
            Path tempPath = configPath.resolveSibling(configPath.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            Files.move(tempPath, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to write config file " + configPath, ex);
        }
    }

    private static String joinFallbackMessages(List<ConfigValidationError> errors) {
        return errors.stream().map(ConfigValidationError::fallbackMessage).collect(Collectors.joining(" | "));
    }
}
