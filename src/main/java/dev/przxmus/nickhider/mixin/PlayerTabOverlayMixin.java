package dev.przxmus.nickhider.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.przxmus.nickhider.NickHider;
import dev.przxmus.nickhider.core.ProfileCompat;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void nickhider$replaceDisplayedTabName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        GameProfile profile = info.getProfile();
        java.util.UUID profileId = ProfileCompat.id(profile);
        String profileName = ProfileCompat.name(profile);
        if (profileId == null || profileName == null) {
            return;
        }

        String replacement = NickHider.runtime().replacementName(profileId, profileName);
        if (!replacement.equals(profileName)) {
            cir.setReturnValue(Component.literal(replacement));
        }
    }
}
