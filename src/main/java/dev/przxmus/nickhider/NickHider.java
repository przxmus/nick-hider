package dev.przxmus.nickhider;

import com.mojang.logging.LogUtils;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import dev.przxmus.nickhider.config.ConfigRepository;
import dev.przxmus.nickhider.core.IdentityMaskingService;
import dev.przxmus.nickhider.core.PlayerAliasService;
import dev.przxmus.nickhider.core.PrivacyRuntimeState;
import dev.przxmus.nickhider.core.SkinResolutionService;
import dev.przxmus.nickhider.core.TextSanitizer;

public final class NickHider {
    public static final String MOD_ID = "nickhider";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static PrivacyRuntimeState runtimeState;

    private NickHider() {}

    public static synchronized void bootstrap(Path configDir) {
        if (runtimeState != null) {
            return;
        }

        ConfigRepository configRepository = new ConfigRepository(configDir.resolve(MOD_ID + ".json"));
        PlayerAliasService aliasService = new PlayerAliasService(configDir.resolve(MOD_ID + "-ids.json"));
        IdentityMaskingService identityMaskingService = new IdentityMaskingService(aliasService);
        SkinResolutionService skinResolutionService = new SkinResolutionService(configDir.resolve(MOD_ID + "-cache").resolve("skins"));
        TextSanitizer textSanitizer = new TextSanitizer(aliasService);

        runtimeState = new PrivacyRuntimeState(configRepository, identityMaskingService, skinResolutionService, textSanitizer);
        runtimeState.reloadConfig();
    }

    public static PrivacyRuntimeState runtime() {
        return Objects.requireNonNull(runtimeState, "NickHider runtime is not bootstrapped");
    }

    public static synchronized PrivacyRuntimeState runtimeOrNull() {
        return runtimeState;
    }
}
