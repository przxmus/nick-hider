package dev.przxmus.nickhider.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.przxmus.nickhider.NickHider;
import dev.przxmus.nickhider.core.ResolvedSkin;

@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
    @Shadow
    public abstract GameProfile getProfile();

    @Inject(method = "getSkinLocation", at = @At("HEAD"), cancellable = true)
    private void nickhider$overrideSkinLocation(CallbackInfoReturnable<ResourceLocation> cir) {
        GameProfile profile = this.getProfile();
        if (profile == null || profile.getId() == null) {
            return;
        }

        NickHider.runtime().replacementSkin(profile.getId())
                .map(ResolvedSkin::textureLocation)
                .ifPresent(cir::setReturnValue);
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void nickhider$overrideSkinModel(CallbackInfoReturnable<String> cir) {
        GameProfile profile = this.getProfile();
        if (profile == null || profile.getId() == null) {
            return;
        }

        NickHider.runtime().replacementSkin(profile.getId())
                .map(ResolvedSkin::modelName)
                .ifPresent(cir::setReturnValue);
    }

    @Inject(method = "getCapeLocation", at = @At("HEAD"), cancellable = true)
    private void nickhider$overrideCapeLocation(CallbackInfoReturnable<ResourceLocation> cir) {
        GameProfile profile = this.getProfile();
        if (profile == null || profile.getId() == null || !NickHider.runtime().shouldOverrideCape(profile.getId())) {
            return;
        }

        NickHider.runtime().replacementCape(profile.getId())
                .map(ResolvedSkin::capeTextureLocation)
                .ifPresentOrElse(cir::setReturnValue, () -> cir.setReturnValue(null));
    }

    @Inject(method = "getElytraLocation", at = @At("HEAD"), cancellable = true)
    private void nickhider$overrideElytraLocation(CallbackInfoReturnable<ResourceLocation> cir) {
        GameProfile profile = this.getProfile();
        if (profile == null || profile.getId() == null || !NickHider.runtime().shouldOverrideCape(profile.getId())) {
            return;
        }

        NickHider.runtime().replacementCape(profile.getId())
                .map(ResolvedSkin::elytraTextureLocation)
                .ifPresentOrElse(cir::setReturnValue, () -> cir.setReturnValue(null));
    }
}
