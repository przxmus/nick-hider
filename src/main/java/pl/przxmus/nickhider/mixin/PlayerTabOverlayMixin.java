package pl.przxmus.nickhider.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.przxmus.nickhider.NickHider;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void nickhider$replaceDisplayedTabName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        GameProfile profile = info.getProfile();
        if (profile == null || profile.getId() == null || profile.getName() == null) {
            return;
        }

        String replacement = NickHider.runtime().replacementName(profile.getId(), profile.getName());
        if (!replacement.equals(profile.getName())) {
            cir.setReturnValue(Component.literal(replacement));
        }
    }
}
