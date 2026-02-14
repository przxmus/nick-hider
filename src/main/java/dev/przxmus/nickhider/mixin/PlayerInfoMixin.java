package dev.przxmus.nickhider.mixin;

import com.mojang.authlib.GameProfile;
import java.util.Objects;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
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

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void nickhider$overrideSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        GameProfile profile = this.getProfile();
        if (profile == null || profile.getId() == null) {
            return;
        }

        PlayerSkin original = cir.getReturnValue();
        if (original == null) {
            return;
        }

        ResolvedSkin skinReplacement = NickHider.runtime().replacementSkin(profile.getId()).orElse(null);
        boolean shouldOverrideCape = NickHider.runtime().shouldOverrideCape(profile.getId());
        ResolvedSkin capeReplacement = NickHider.runtime().replacementCape(profile.getId()).orElse(null);

        PlayerSkin.Model model = skinReplacement == null
                ? original.model()
                : PlayerSkin.Model.byName(skinReplacement.modelName());
        var texture = skinReplacement == null ? original.texture() : skinReplacement.textureLocation();
        var capeTexture = original.capeTexture();
        var elytraTexture = original.elytraTexture();

        if (shouldOverrideCape) {
            capeTexture = capeReplacement == null ? null : capeReplacement.capeTextureLocation();
            elytraTexture = capeReplacement == null ? null : capeReplacement.elytraTextureLocation();
        }

        if (Objects.equals(texture, original.texture())
                && Objects.equals(capeTexture, original.capeTexture())
                && Objects.equals(elytraTexture, original.elytraTexture())
                && model == original.model()) {
            return;
        }

        cir.setReturnValue(new PlayerSkin(texture, original.textureUrl(), capeTexture, elytraTexture, model, original.secure()));
    }
}
