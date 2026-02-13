package pl.przxmus.nickhider.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.przxmus.nickhider.config.PrivacyConfig;

class TextSanitizationFlowTest {
    @TempDir
    Path tempDir;

    @Test
    void replacementMapContainsLocalAndOthersAccordingToToggles() {
        PlayerAliasService service = new PlayerAliasService(tempDir.resolve("ids.json"));
        PrivacyConfig config = new PrivacyConfig();
        config.hideLocalName = true;
        config.hideOtherNames = true;

        UUID local = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID other = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        Map<UUID, String> names = new LinkedHashMap<>();
        names.put(local, "LocalPlayer");
        names.put(other, "RemotePlayer");

        Map<String, String> replacements = service.buildReplacementMap(local, names, config);

        assertEquals("Player", replacements.get("LocalPlayer"));
        assertFalse(replacements.get("RemotePlayer").isBlank());
    }
}
