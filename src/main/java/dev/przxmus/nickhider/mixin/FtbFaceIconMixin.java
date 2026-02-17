package dev.przxmus.nickhider.mixin;

import com.mojang.authlib.GameProfile;
import dev.przxmus.nickhider.NickHider;
import dev.przxmus.nickhider.core.ProfileCompat;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.icon.FaceIcon", remap = false)
public abstract class FtbFaceIconMixin {
    private static final ThreadLocal<Boolean> REENTRY = ThreadLocal.withInitial(() -> false);
    private static final AtomicBoolean MASK_APPLIED_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean DRAW_MASK_APPLIED_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean REFLECTION_FAILURE_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean DRAW_HOOK_FAILURE_LOGGED = new AtomicBoolean(false);
    private static volatile Method faceGetterSingle;
    private static volatile Method faceGetterDual;
    private static volatile Method iconWithUvMethod;
    private static volatile Constructor<?> imageIconConstructor;
    private static volatile Field profileField;
    private static volatile Field skinField;
    private static volatile Field headField;
    private static volatile Field hatField;
    private static volatile Field imageIconTextureField;

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

    @Inject(
            method = "draw(Lnet/minecraft/client/gui/GuiGraphics;IIII)V",
            at = @At("HEAD"),
            require = 0
    )
    private void nickhider$applyMaskedSkinBeforeDraw(GuiGraphics graphics, int x, int y, int w, int h, CallbackInfo ci) {
        try {
            var runtime = NickHider.runtimeOrNull();
            if (runtime == null) {
                return;
            }

            GameProfile profile = nickhider$extractProfile(this);
            if (profile == null) {
                return;
            }

            UUID profileId = ProfileCompat.id(profile);
            if (profileId == null) {
                return;
            }

            String profileName = ProfileCompat.name(profile);
            var replacement = runtime.replacementSkin(profileId, profileName);
            if (replacement.isEmpty()) {
                return;
            }

            Object textureLocation = replacement.get().textureLocation();
            if (textureLocation == null) {
                return;
            }

            Object currentTexture = nickhider$currentSkinTexture(this);
            if (Objects.equals(currentTexture, textureLocation)) {
                return;
            }

            if (nickhider$applyFaceTexture(this, textureLocation)) {
                if (DRAW_MASK_APPLIED_LOGGED.compareAndSet(false, true)) {
                    NickHider.LOGGER.info("[NH-FTB-HOOK] Applied FaceIcon runtime skin override");
                }
            }
        } catch (Throwable throwable) {
            if (DRAW_HOOK_FAILURE_LOGGED.compareAndSet(false, true)) {
                NickHider.LOGGER.warn("[NH-FTB-HOOK] Failed to apply FaceIcon draw-time skin override", throwable);
            } else {
                NickHider.LOGGER.debug("[NH-FTB-HOOK] FaceIcon draw-time skin override failed", throwable);
            }
        }
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
        if (originalUuid == null) {
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

    private static GameProfile nickhider$extractProfile(Object faceIcon) throws ReflectiveOperationException {
        Object profile = nickhider$resolveProfileField(faceIcon).get(faceIcon);
        return profile instanceof GameProfile gameProfile ? gameProfile : null;
    }

    private static boolean nickhider$applyFaceTexture(Object faceIcon, Object textureLocation) throws ReflectiveOperationException {
        Constructor<?> iconCtor = nickhider$resolveImageIconConstructor(textureLocation.getClass());
        Method withUv = nickhider$resolveWithUvMethod();

        Object imageIcon = iconCtor.newInstance(textureLocation);
        Object head = withUv.invoke(imageIcon, 8F, 8F, 8F, 8F, 64F, 64F);
        Object hat = withUv.invoke(imageIcon, 40F, 8F, 8F, 8F, 64F, 64F);

        nickhider$resolveSkinField(faceIcon).set(faceIcon, imageIcon);
        nickhider$resolveHeadField(faceIcon).set(faceIcon, head);
        nickhider$resolveHatField(faceIcon).set(faceIcon, hat);
        return true;
    }

    private static Method nickhider$resolveWithUvMethod() throws ReflectiveOperationException {
        Method cached = iconWithUvMethod;
        if (cached != null) {
            return cached;
        }

        Class<?> iconClass = Class.forName("dev.ftb.mods.ftblibrary.icon.Icon");
        Method resolved = iconClass.getMethod("withUV", float.class, float.class, float.class, float.class, float.class, float.class);
        iconWithUvMethod = resolved;
        return resolved;
    }

    private static Constructor<?> nickhider$resolveImageIconConstructor(Class<?> textureClass) throws ReflectiveOperationException {
        Constructor<?> cached = imageIconConstructor;
        if (cached != null && cached.getParameterCount() == 1 && cached.getParameterTypes()[0].isAssignableFrom(textureClass)) {
            return cached;
        }

        Class<?> imageIconClass = Class.forName("dev.ftb.mods.ftblibrary.icon.ImageIcon");
        for (Constructor<?> constructor : imageIconClass.getConstructors()) {
            if (constructor.getParameterCount() != 1) {
                continue;
            }
            Class<?> parameter = constructor.getParameterTypes()[0];
            if (parameter.isAssignableFrom(textureClass)) {
                imageIconConstructor = constructor;
                return constructor;
            }
        }

        Constructor<?> resolved = imageIconClass.getConstructor(textureClass);
        imageIconConstructor = resolved;
        return resolved;
    }

    private static Field nickhider$resolveProfileField(Object faceIcon) throws ReflectiveOperationException {
        Field cached = profileField;
        if (cached != null) {
            return cached;
        }

        Field resolved = faceIcon.getClass().getField("profile");
        profileField = resolved;
        return resolved;
    }

    private static Field nickhider$resolveSkinField(Object faceIcon) throws ReflectiveOperationException {
        Field cached = skinField;
        if (cached != null) {
            return cached;
        }

        Field resolved = faceIcon.getClass().getField("skin");
        skinField = resolved;
        return resolved;
    }

    private static Field nickhider$resolveHeadField(Object faceIcon) throws ReflectiveOperationException {
        Field cached = headField;
        if (cached != null) {
            return cached;
        }

        Field resolved = faceIcon.getClass().getField("head");
        headField = resolved;
        return resolved;
    }

    private static Field nickhider$resolveHatField(Object faceIcon) throws ReflectiveOperationException {
        Field cached = hatField;
        if (cached != null) {
            return cached;
        }

        Field resolved = faceIcon.getClass().getField("hat");
        hatField = resolved;
        return resolved;
    }

    private static Object nickhider$currentSkinTexture(Object faceIcon) throws ReflectiveOperationException {
        Object skinIcon = nickhider$resolveSkinField(faceIcon).get(faceIcon);
        if (skinIcon == null) {
            return null;
        }

        Class<?> skinClass = skinIcon.getClass();
        if (!"dev.ftb.mods.ftblibrary.icon.ImageIcon".equals(skinClass.getName())) {
            return null;
        }

        Field textureField = nickhider$resolveImageIconTextureField(skinClass);
        return textureField.get(skinIcon);
    }

    private static Field nickhider$resolveImageIconTextureField(Class<?> imageIconClass) throws ReflectiveOperationException {
        Field cached = imageIconTextureField;
        if (cached != null) {
            return cached;
        }

        Field resolved = imageIconClass.getField("texture");
        imageIconTextureField = resolved;
        return resolved;
    }
}
