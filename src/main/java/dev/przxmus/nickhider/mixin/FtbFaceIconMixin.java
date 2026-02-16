package dev.przxmus.nickhider.mixin;

import com.mojang.authlib.GameProfile;
import dev.przxmus.nickhider.NickHider;
import dev.przxmus.nickhider.core.ProfileCompat;
import java.util.Objects;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.icon.FaceIcon", remap = false)
public abstract class FtbFaceIconMixin {
    @ModifyVariable(
            method = "getFace(Lcom/mojang/authlib/GameProfile;)Ldev/ftb/mods/ftblibrary/icon/FaceIcon;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private static GameProfile nickhider$maskSingleArgumentProfile(GameProfile profile) {
        return nickhider$maskHeadProfile(profile);
    }

    @ModifyVariable(
            method = "getFace(Lcom/mojang/authlib/GameProfile;Z)Ldev/ftb/mods/ftblibrary/icon/FaceIcon;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private static GameProfile nickhider$maskDualArgumentProfile(GameProfile profile) {
        return nickhider$maskHeadProfile(profile);
    }

    private static GameProfile nickhider$maskHeadProfile(GameProfile profile) {
        if (profile == null) {
            return null;
        }

        var runtime = NickHider.runtimeOrNull();
        if (runtime == null) {
            return profile;
        }

        UUID originalUuid = ProfileCompat.id(profile);
        String originalName = ProfileCompat.name(profile);
        if (originalUuid == null || originalName == null || originalName.isBlank()) {
            return profile;
        }

        GameProfile masked = runtime.maskProfileForHead(originalUuid, originalName);
        if (masked == null) {
            return profile;
        }

        UUID maskedUuid = ProfileCompat.id(masked);
        String maskedName = ProfileCompat.name(masked);
        return Objects.equals(maskedUuid, originalUuid) && Objects.equals(maskedName, originalName)
                ? profile
                : masked;
    }
}
