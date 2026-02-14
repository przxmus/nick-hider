package dev.przxmus.nickhider.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import dev.przxmus.nickhider.config.ConfigValidator;
import dev.przxmus.nickhider.config.PrivacyConfig;

class ConfigValidatorTest {
    @Test
    void acceptsDefaultConfig() {
        PrivacyConfig config = new PrivacyConfig();
        assertTrue(ConfigValidator.validate(config).isEmpty());
    }

    @Test
    void rejectsInvalidLocalName() {
        PrivacyConfig config = new PrivacyConfig();
        config.localName = "a";
        assertFalse(ConfigValidator.validate(config).isEmpty());
    }

    @Test
    void rejectsTemplateWithoutIdToken() {
        PrivacyConfig config = new PrivacyConfig();
        config.othersNameTemplate = "Player";
        assertFalse(ConfigValidator.validate(config).isEmpty());
    }

    @Test
    void rejectsInvalidLocalCapeSource() {
        PrivacyConfig config = new PrivacyConfig();
        config.localCapeUser = "x";
        assertFalse(ConfigValidator.validate(config).isEmpty());
    }

    @Test
    void rejectsInvalidOthersCapeSource() {
        PrivacyConfig config = new PrivacyConfig();
        config.othersCapeUser = "bad-user";
        assertFalse(ConfigValidator.validate(config).isEmpty());
    }
}
