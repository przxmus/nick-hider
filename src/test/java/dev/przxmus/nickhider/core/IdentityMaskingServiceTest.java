package dev.przxmus.nickhider.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.przxmus.nickhider.config.PrivacyConfig;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IdentityMaskingServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void syntheticUuidIsDeterministicAndValid() {
        IdentityMaskingService service = newService();
        UUID first = service.syntheticUuid("AliasPlayer");
        UUID second = service.syntheticUuid("AliasPlayer");
        UUID different = service.syntheticUuid("AnotherAlias");

        assertEquals(first, second);
        assertNotEquals(first, different);
        assertEquals(first, UUID.fromString(first.toString()));
    }

    @Test
    void nameMaskingUsesNameTogglesAndChangesUuid() {
        IdentityMaskingService service = newService();
        PrivacyConfig config = new PrivacyConfig();
        config.enabled = true;
        config.hideLocalName = true;
        config.localName = "MaskedLocal";
        config.hideOtherNames = true;
        config.othersNameTemplate = "Player_[ID]";

        UUID localUuid = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        MaskedProfile local = service.maskForName(config, true, localUuid, "RealLocal");
        assertEquals("MaskedLocal", local.name());
        assertEquals(service.syntheticUuid("MaskedLocal"), local.uuid());

        UUID otherUuid = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        String shortId = new PlayerAliasService(tempDir.resolve("ids-shadow.json")).getOrCreateShortId(otherUuid);
        MaskedProfile other = service.maskForName(config, false, otherUuid, "RealOther");
        assertEquals("Player_" + shortId, other.name());
        assertEquals(service.syntheticUuid(other.name()), other.uuid());
    }

    @Test
    void headMaskingUsesSkinToggles() {
        IdentityMaskingService service = newService();
        PrivacyConfig config = new PrivacyConfig();
        config.enabled = true;
        config.hideLocalName = false;
        config.hideLocalSkin = true;
        config.hideOtherNames = true;
        config.hideOtherSkins = false;
        config.othersNameTemplate = "Player_[ID]";

        UUID localUuid = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        MaskedProfile localHead = service.maskForHead(config, true, localUuid, "RealLocal");
        assertEquals("RealLocal", localHead.name());
        assertEquals(service.syntheticUuid("RealLocal"), localHead.uuid());

        UUID otherUuid = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        MaskedProfile otherHead = service.maskForHead(config, false, otherUuid, "RealOther");
        assertEquals(otherUuid, otherHead.uuid());
        assertEquals("RealOther", otherHead.name());
    }

    private IdentityMaskingService newService() {
        return new IdentityMaskingService(new PlayerAliasService(tempDir.resolve("ids.json")));
    }
}
