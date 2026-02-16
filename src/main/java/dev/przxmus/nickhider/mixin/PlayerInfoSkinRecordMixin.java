package dev.przxmus.nickhider.mixin;

import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.przxmus.nickhider.NickHider;
import dev.przxmus.nickhider.core.ProfileCompat;
import dev.przxmus.nickhider.core.ResolvedSkin;

/*? if >=1.21.1 {*/
/*import net.minecraft.client.resources.PlayerSkin;
*/
/*?}*/

@Mixin(PlayerInfo.class)
public abstract class PlayerInfoSkinRecordMixin {
    private static final AtomicBoolean HOOK_FAILURE_LOGGED = new AtomicBoolean(false);

    @Shadow
    public abstract GameProfile getProfile();

    /*? if >=1.21.1 {*/
    /*@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
    private void nickhider$overrideSkinRecord(CallbackInfoReturnable<PlayerSkin> cir) {
        try {
            var runtime = NickHider.runtimeOrNull();
            if (runtime == null) {
                return;
            }

            GameProfile profile = this.getProfile();
            java.util.UUID profileId = ProfileCompat.id(profile);
            String profileName = ProfileCompat.name(profile);
            if (profileId == null) {
                return;
            }

            PlayerSkin current = cir.getReturnValue();
            if (current == null) {
                return;
            }

            Optional<ResolvedSkin> replacementSkin = runtime.replacementSkin(profileId, profileName);
            boolean overrideCape = runtime.shouldOverrideCape(profileId, profileName);
            Optional<ResolvedSkin> replacementCape = overrideCape
                    ? runtime.replacementCape(profileId, profileName)
                    : Optional.empty();

            if (replacementSkin.isEmpty() && !overrideCape) {
                return;
            }

            ResourceLocation texture = current.texture();
            String textureUrl = current.textureUrl();
            ResourceLocation cape = current.capeTexture();
            ResourceLocation elytra = current.elytraTexture();
            PlayerSkin.Model model = current.model();
            boolean secure = current.secure();

            if (replacementSkin.isPresent()) {
                ResolvedSkin skin = replacementSkin.get();
                ResourceLocation mappedTexture = asLocation(skin.textureLocation());
                if (mappedTexture != null) {
                    texture = mappedTexture;
                }
                model = resolveModel(skin.modelName(), model);
            }

            if (overrideCape) {
                if (replacementCape.isPresent()) {
                    ResolvedSkin capeSkin = replacementCape.get();
                    cape = asLocation(capeSkin.capeTextureLocation());
                    elytra = asLocation(capeSkin.elytraTextureLocation());
                } else {
                    cape = null;
                    elytra = null;
                }
            }

            cir.setReturnValue(new PlayerSkin(texture, textureUrl, cape, elytra, model, secure));
        } catch (Throwable throwable) {
            nickhider$logHookFailure("getSkin", throwable);
        }
    }

    private static ResourceLocation asLocation(Object value) {
        return value instanceof ResourceLocation location ? location : null;
    }

    private static PlayerSkin.Model resolveModel(String modelName, PlayerSkin.Model fallback) {
        if (fallback == null || modelName == null || modelName.isBlank()) {
            return fallback;
        }

        if (ResolvedSkin.MODEL_SLIM.equalsIgnoreCase(modelName)) {
            return PlayerSkin.Model.SLIM;
        }
        if (ResolvedSkin.MODEL_DEFAULT.equalsIgnoreCase(modelName)) {
            return PlayerSkin.Model.WIDE;
        }

        try {
            PlayerSkin.Model resolved = PlayerSkin.Model.byName(modelName);
            return resolved != null ? resolved : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
    */
    /*?}*/

    private static void nickhider$logHookFailure(String hook, Throwable throwable) {
        var runtime = NickHider.runtimeOrNull();
        if (runtime != null) {
            runtime.reportSkinCapeHookFailure(hook, throwable);
        }

        if (HOOK_FAILURE_LOGGED.compareAndSet(false, true)) {
            NickHider.LOGGER.warn("[NH-HOOK-FAIL] Nick Hider skin hook failed in {}; falling back to vanilla behavior", hook, throwable);
        } else {
            NickHider.LOGGER.debug("[NH-HOOK-FAIL] Nick Hider skin hook failed in {}", hook, throwable);
        }
    }
}
