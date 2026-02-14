package dev.przxmus.nickhider.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import dev.przxmus.nickhider.NickHider;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererNameTagMixin {
    @ModifyVariable(
            method = "renderNameTag",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2,
            require = 0
    )
    private Component nickhider$replaceNametagText(Component displayName, Entity entity) {
        if (!(entity instanceof Player)) {
            return displayName;
        }

        var runtime = NickHider.runtimeOrNull();
        if (runtime == null) {
            return displayName;
        }

        String sanitized = runtime.sanitizeText(displayName.getString());
        if (sanitized.equals(displayName.getString())) {
            return displayName;
        }

        return Component.literal(sanitized).withStyle(displayName.getStyle());
    }
}
