package pl.przxmus.nickhider.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ConfigValidator {
    private static final Pattern MC_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("^[A-Za-z0-9_\\[\\]-]{3,16}$");

    private ConfigValidator() {}

    public static List<String> validate(PrivacyConfig config) {
        List<String> errors = new ArrayList<>();

        if (!MC_NAME_PATTERN.matcher(config.localName).matches()) {
            errors.add("Local replacement name must match [A-Za-z0-9_]{3,16}.");
        }

        if (!config.localSkinUser.isEmpty() && !MC_NAME_PATTERN.matcher(config.localSkinUser).matches()) {
            errors.add("Local skin source must be empty or match [A-Za-z0-9_]{3,16}.");
        }

        if (!config.othersSkinUser.isEmpty() && !MC_NAME_PATTERN.matcher(config.othersSkinUser).matches()) {
            errors.add("Other-players skin source must be empty or match [A-Za-z0-9_]{3,16}.");
        }

        if (!TEMPLATE_PATTERN.matcher(config.othersNameTemplate).matches()) {
            errors.add("Other-players template must match [A-Za-z0-9_[]-]{3,16}.");
        }

        if (!config.othersNameTemplate.contains("[ID]")) {
            errors.add("Other-players template must contain [ID].");
        }

        String expanded = config.othersNameTemplate.replace("[ID]", "abcdef");
        if (expanded.length() > 16) {
            errors.add("Other-players template resolves to more than 16 characters.");
        }

        return errors;
    }
}
