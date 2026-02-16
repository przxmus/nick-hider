package dev.przxmus.nickhider.mixin;

import com.mojang.authlib.GameProfile;
import dev.przxmus.nickhider.NickHider;
import dev.przxmus.nickhider.core.ProfileCompat;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.icon.FaceIcon", remap = false)
public abstract class FtbFaceIconMixin {
    private static final ThreadLocal<Boolean> REENTRY = ThreadLocal.withInitial(() -> false);
    private static final AtomicBoolean MASK_APPLIED_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean REFLECTION_FAILURE_LOGGED = new AtomicBoolean(false);
    private static volatile Method faceGetterSingle;
    private static volatile Method faceGetterDual;

    @Inject(
            method = "getFace(Lcom/mojang/authlib/GameProfile;)Ldev/ftb/mods/ftblibrary/icon/FaceIcon;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void nickhider$maskSingleArgumentProfile(GameProfile profile, CallbackInfoReturnable<Object> cir) {
        nickhider$replaceFaceIcon(profile, null, cir);
    }

    @Inject(
            method = "getFace(Lcom/mojang/authlib/GameProfile;Z)Ldev/ftb/mods/ftblibrary/icon/FaceIcon;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void nickhider$maskDualArgumentProfile(GameProfile profile, boolean isClient, CallbackInfoReturnable<Object> cir) {
        nickhider$replaceFaceIcon(profile, isClient, cir);
    }

    private static void nickhider$replaceFaceIcon(GameProfile profile, Boolean isClient, CallbackInfoReturnable<Object> cir) {
        if (profile == null || Boolean.TRUE.equals(REENTRY.get())) {
            return;
        }

        var runtime = NickHider.runtimeOrNull();
        if (runtime == null) {
            return;
        }

        UUID originalUuid = ProfileCompat.id(profile);
        String originalName = ProfileCompat.name(profile);
        if (originalUuid == null || originalName == null || originalName.isBlank()) {
            return;
        }

        GameProfile masked = runtime.maskProfileForHead(originalUuid, originalName);
        if (masked == null) {
            return;
        }

        UUID maskedUuid = ProfileCompat.id(masked);
        String maskedName = ProfileCompat.name(masked);
        if (Objects.equals(maskedUuid, originalUuid) && Objects.equals(maskedName, originalName)) {
            return;
        }

        Object replacement = nickhider$invokeFaceGetter(masked, isClient);
        if (replacement != null) {
            cir.setReturnValue(replacement);
            if (MASK_APPLIED_LOGGED.compareAndSet(false, true)) {
                NickHider.LOGGER.info("[NH-FTB-HOOK] Applied FaceIcon head masking");
            }
        }
    }

    private static Object nickhider$invokeFaceGetter(GameProfile profile, Boolean isClient) {
        try {
            REENTRY.set(true);
            Method method = isClient == null ? nickhider$resolveSingleGetter() : nickhider$resolveDualGetter();
            if (method == null) {
                return null;
            }
            return isClient == null ? method.invoke(null, profile) : method.invoke(null, profile, isClient.booleanValue());
        } catch (ReflectiveOperationException ex) {
            if (REFLECTION_FAILURE_LOGGED.compareAndSet(false, true)) {
                NickHider.LOGGER.warn("[NH-FTB-HOOK] Failed to invoke FaceIcon getter reflectively", ex);
            } else {
                NickHider.LOGGER.debug("[NH-FTB-HOOK] Reflective FaceIcon invocation failed", ex);
            }
            return null;
        } finally {
            REENTRY.set(false);
        }
    }

    private static Method nickhider$resolveSingleGetter() throws ReflectiveOperationException {
        Method cached = faceGetterSingle;
        if (cached != null) {
            return cached;
        }

        Class<?> faceIconClass = Class.forName("dev.ftb.mods.ftblibrary.icon.FaceIcon");
        Method resolved = faceIconClass.getMethod("getFace", GameProfile.class);
        faceGetterSingle = resolved;
        return resolved;
    }

    private static Method nickhider$resolveDualGetter() throws ReflectiveOperationException {
        Method cached = faceGetterDual;
        if (cached != null) {
            return cached;
        }

        Class<?> faceIconClass = Class.forName("dev.ftb.mods.ftblibrary.icon.FaceIcon");
        Method resolved = faceIconClass.getMethod("getFace", GameProfile.class, boolean.class);
        faceGetterDual = resolved;
        return resolved;
    }
}
