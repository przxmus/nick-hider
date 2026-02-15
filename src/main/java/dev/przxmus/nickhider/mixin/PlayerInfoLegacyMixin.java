package dev.przxmus.nickhider.mixin;

import com.mojang.authlib.GameProfile;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.przxmus.nickhider.NickHider;
import dev.przxmus.nickhider.core.ProfileCompat;
import dev.przxmus.nickhider.core.ResolvedSkin;

@Mixin(PlayerInfo.class)
public abstract class PlayerInfoLegacyMixin {
    private static final AtomicBoolean HOOK_FAILURE_LOGGED = new AtomicBoolean(false);

    @Shadow
    public abstract GameProfile getProfile();

    /*? if <=1.20.1 {*/
    @Inject(method = "getSkinLocation", at = @At("RETURN"), cancellable = true, require = 0)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void nickhider$overrideSkinLocation(CallbackInfoReturnable cir) {
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

            runtime.replacementSkin(profileId, profileName)
                    .map(ResolvedSkin::textureLocation)
                    .ifPresent(cir::setReturnValue);
        } catch (Throwable throwable) {
            nickhider$logHookFailure("getSkinLocation", throwable);
        }
    }

    @Inject(method = "getModelName", at = @At("RETURN"), cancellable = true, require = 0)
    private void nickhider$overrideSkinModel(CallbackInfoReturnable<String> cir) {
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

            runtime.replacementSkin(profileId, profileName)
                    .map(ResolvedSkin::modelName)
                    .ifPresent(cir::setReturnValue);
        } catch (Throwable throwable) {
            nickhider$logHookFailure("getModelName", throwable);
        }
    }

    @Inject(method = "getCapeLocation", at = @At("RETURN"), cancellable = true, require = 0)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void nickhider$overrideCapeLocation(CallbackInfoReturnable cir) {
        try {
            var runtime = NickHider.runtimeOrNull();
            if (runtime == null) {
                return;
            }

            GameProfile profile = this.getProfile();
            java.util.UUID profileId = ProfileCompat.id(profile);
            String profileName = ProfileCompat.name(profile);
            if (profileId == null || !runtime.shouldOverrideCape(profileId, profileName)) {
                return;
            }

            runtime.replacementCape(profileId, profileName)
                    .map(ResolvedSkin::capeTextureLocation)
                    .ifPresentOrElse(cir::setReturnValue, () -> cir.setReturnValue(null));
        } catch (Throwable throwable) {
            nickhider$logHookFailure("getCapeLocation", throwable);
        }
    }

    @Inject(method = "getElytraLocation", at = @At("RETURN"), cancellable = true, require = 0)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void nickhider$overrideElytraLocation(CallbackInfoReturnable cir) {
        try {
            var runtime = NickHider.runtimeOrNull();
            if (runtime == null) {
                return;
            }

            GameProfile profile = this.getProfile();
            java.util.UUID profileId = ProfileCompat.id(profile);
            String profileName = ProfileCompat.name(profile);
            if (profileId == null || !runtime.shouldOverrideCape(profileId, profileName)) {
                return;
            }

            runtime.replacementCape(profileId, profileName)
                    .map(ResolvedSkin::elytraTextureLocation)
                    .ifPresentOrElse(cir::setReturnValue, () -> cir.setReturnValue(null));
        } catch (Throwable throwable) {
            nickhider$logHookFailure("getElytraLocation", throwable);
        }
    }
    /*?}*/

    private static void nickhider$logHookFailure(String hook, Throwable throwable) {
        var runtime = NickHider.runtimeOrNull();
        if (runtime != null) {
            runtime.reportSkinCapeHookFailure(hook, throwable);
        }

        if (HOOK_FAILURE_LOGGED.compareAndSet(false, true)) {
            NickHider.LOGGER.warn("Nick Hider skin hook failed in {}; falling back to vanilla behavior", hook, throwable);
        } else {
            NickHider.LOGGER.debug("Nick Hider skin hook failed in {}", hook, throwable);
        }
    }
}
