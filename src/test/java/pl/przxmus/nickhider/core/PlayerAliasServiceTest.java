package pl.przxmus.nickhider.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerAliasServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void generatesDeterministicShortId() {
        PlayerAliasService service = new PlayerAliasService(tempDir.resolve("ids.json"));
        UUID uuid = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        String first = service.getOrCreateShortId(uuid);
        String second = service.getOrCreateShortId(uuid);

        assertEquals(first, second);
        assertTrue(first.length() <= 6);
        assertTrue(first.matches("^[0-9a-z]+$"));
    }

    @Test
    void createsDifferentIdsForDifferentUuids() {
        PlayerAliasService service = new PlayerAliasService(tempDir.resolve("ids.json"));

        String one = service.getOrCreateShortId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        String two = service.getOrCreateShortId(UUID.fromString("22222222-2222-2222-2222-222222222222"));

        assertNotEquals(one, two);
    }
}
