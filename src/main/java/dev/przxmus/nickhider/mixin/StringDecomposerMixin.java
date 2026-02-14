package dev.przxmus.nickhider.mixin;

import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import dev.przxmus.nickhider.NickHider;

@Mixin(StringDecomposer.class)
public class StringDecomposerMixin {
    @ModifyArg(
            method = "iterateFormatted(Ljava/lang/String;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/StringDecomposer;iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
                    ordinal = 0
            ),
            index = 0,
            require = 0
    )
    private static String nickhider$sanitizeText(String text) {
        var runtime = NickHider.runtimeOrNull();
        if (runtime == null) {
            return text;
        }
        return runtime.sanitizeText(text);
    }
}
