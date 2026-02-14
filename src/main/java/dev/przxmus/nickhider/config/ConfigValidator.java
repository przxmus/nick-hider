package dev.przxmus.nickhider.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ConfigValidator {
    private static final Pattern MC_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("^[A-Za-z0-9_\\[\\]-]{3,16}$");

    private ConfigValidator() {}

    public static List<ConfigValidationError> validate(PrivacyConfig config) {
        List<ConfigValidationError> errors = new ArrayList<>();
        String localName = safe(config.localName);
        String localSkinUser = safe(config.localSkinUser);
        String localCapeUser = safe(config.localCapeUser);
        String othersSkinUser = safe(config.othersSkinUser);
        String othersCapeUser = safe(config.othersCapeUser);
        String othersNameTemplate = safe(config.othersNameTemplate);

        if (!MC_NAME_PATTERN.matcher(localName).matches()) {
            errors.add(error("nickhider.config.error.local_name_invalid", "Local replacement name must match [A-Za-z0-9_]{3,16}."));
        }

        if (!localSkinUser.isEmpty() && !MC_NAME_PATTERN.matcher(localSkinUser).matches()) {
            errors.add(error("nickhider.config.error.local_skin_user_invalid", "Local skin source must be empty or match [A-Za-z0-9_]{3,16}."));
        }

        if (!localCapeUser.isEmpty() && !MC_NAME_PATTERN.matcher(localCapeUser).matches()) {
            errors.add(error("nickhider.config.error.local_cape_user_invalid", "Local cape source must be empty or match [A-Za-z0-9_]{3,16}."));
        }

        if (!othersSkinUser.isEmpty() && !MC_NAME_PATTERN.matcher(othersSkinUser).matches()) {
            errors.add(error("nickhider.config.error.others_skin_user_invalid", "Other-players skin source must be empty or match [A-Za-z0-9_]{3,16}."));
        }

        if (!othersCapeUser.isEmpty() && !MC_NAME_PATTERN.matcher(othersCapeUser).matches()) {
            errors.add(error("nickhider.config.error.others_cape_user_invalid", "Other-players cape source must be empty or match [A-Za-z0-9_]{3,16}."));
        }

        if (!TEMPLATE_PATTERN.matcher(othersNameTemplate).matches()) {
            errors.add(error("nickhider.config.error.others_template_invalid", "Other-players template must match [A-Za-z0-9_[]-]{3,16}."));
        }

        if (!othersNameTemplate.contains("[ID]")) {
            errors.add(error("nickhider.config.error.others_template_missing_id", "Other-players template must contain [ID]."));
        }

        String expanded = othersNameTemplate.replace("[ID]", "abcdef");
        if (expanded.length() > 16) {
            errors.add(error("nickhider.config.error.others_template_too_long", "Other-players template resolves to more than 16 characters."));
        }

        return errors;
    }

    private static ConfigValidationError error(String key, String fallbackMessage) {
        return new ConfigValidationError(key, fallbackMessage);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
