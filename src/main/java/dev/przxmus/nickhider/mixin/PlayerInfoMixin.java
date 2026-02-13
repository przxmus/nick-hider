package dev.przxmus.nickhider.mixin;

import com.mojang.authlib.GameProfile;
import java.util.Optional;
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

        Optional<ResolvedSkin> replacement = NickHider.runtime().replacementSkin(profile.getId());
        replacement.ifPresent(resolvedSkin -> cir.setReturnValue(resolvedSkin.textureLocation()));
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void nickhider$overrideSkinModel(CallbackInfoReturnable<String> cir) {
        GameProfile profile = this.getProfile();
        if (profile == null || profile.getId() == null) {
            return;
        }

        Optional<ResolvedSkin> replacement = NickHider.runtime().replacementSkin(profile.getId());
        replacement.ifPresent(resolvedSkin -> cir.setReturnValue(resolvedSkin.modelName()));
    }
}
