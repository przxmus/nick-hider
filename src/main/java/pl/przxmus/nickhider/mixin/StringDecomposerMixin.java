package pl.przxmus.nickhider.mixin;

import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import pl.przxmus.nickhider.NickHider;

@Mixin(StringDecomposer.class)
public class StringDecomposerMixin {
    @ModifyArg(
            method = "iterateFormatted(Ljava/lang/String;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/StringDecomposer;iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
                    ordinal = 0
            ),
            index = 0
    )
    private static String nickhider$sanitizeText(String text) {
        return NickHider.runtime().sanitizeText(text);
    }
}
