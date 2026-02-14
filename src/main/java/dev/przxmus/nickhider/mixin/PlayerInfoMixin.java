package dev.przxmus.nickhider.mixin;

import com.mojang.authlib.GameProfile;
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
public abstract class PlayerInfoMixin {
    @Shadow
    public abstract GameProfile getProfile();

    @Inject(method = "getSkinLocation", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void nickhider$overrideSkinLocation(CallbackInfoReturnable cir) {
        GameProfile profile = this.getProfile();
        java.util.UUID profileId = ProfileCompat.id(profile);
        if (profileId == null) {
            return;
        }

        NickHider.runtime().replacementSkin(profileId)
                .map(ResolvedSkin::textureLocation)
                .ifPresent(cir::setReturnValue);
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void nickhider$overrideSkinModel(CallbackInfoReturnable<String> cir) {
        GameProfile profile = this.getProfile();
        java.util.UUID profileId = ProfileCompat.id(profile);
        if (profileId == null) {
            return;
        }

        NickHider.runtime().replacementSkin(profileId)
                .map(ResolvedSkin::modelName)
                .ifPresent(cir::setReturnValue);
    }

    @Inject(method = "getCapeLocation", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void nickhider$overrideCapeLocation(CallbackInfoReturnable cir) {
        GameProfile profile = this.getProfile();
        java.util.UUID profileId = ProfileCompat.id(profile);
        if (profileId == null || !NickHider.runtime().shouldOverrideCape(profileId)) {
            return;
        }

        NickHider.runtime().replacementCape(profileId)
                .map(ResolvedSkin::capeTextureLocation)
                .ifPresentOrElse(cir::setReturnValue, () -> cir.setReturnValue(null));
    }

    @Inject(method = "getElytraLocation", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void nickhider$overrideElytraLocation(CallbackInfoReturnable cir) {
        GameProfile profile = this.getProfile();
        java.util.UUID profileId = ProfileCompat.id(profile);
        if (profileId == null || !NickHider.runtime().shouldOverrideCape(profileId)) {
            return;
        }

        NickHider.runtime().replacementCape(profileId)
                .map(ResolvedSkin::elytraTextureLocation)
                .ifPresentOrElse(cir::setReturnValue, () -> cir.setReturnValue(null));
    }
}
