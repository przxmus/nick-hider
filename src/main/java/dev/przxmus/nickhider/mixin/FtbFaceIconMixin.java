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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.icon.FaceIcon", remap = false)
public abstract class FtbFaceIconMixin {
    private static final AtomicBoolean APPLY_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean(false);

    private static volatile Method iconWithUvMethod;
    private static volatile Constructor<?> imageIconConstructor;
    private static volatile Field profileField;
    private static volatile Field skinField;
    private static volatile Field headField;
    private static volatile Field hatField;
    private static volatile Field imageIconTextureField;

    @Inject(
            method = "getFace(Lcom/mojang/authlib/GameProfile;)Ldev/ftb/mods/ftblibrary/icon/FaceIcon;",
            at = @At("RETURN"),
            require = 0
    )
    private static void nickhider$overrideSingleGetFace(GameProfile profile, CallbackInfoReturnable<Object> cir) {
        nickhider$applyMaskedTexture(cir.getReturnValue(), profile);
    }

    @Inject(
            method = "getFace(Lcom/mojang/authlib/GameProfile;Z)Ldev/ftb/mods/ftblibrary/icon/FaceIcon;",
            at = @At("RETURN"),
            require = 0
    )
    private static void nickhider$overrideDualGetFace(GameProfile profile, boolean isClient, CallbackInfoReturnable<Object> cir) {
        nickhider$applyMaskedTexture(cir.getReturnValue(), profile);
    }

    @Inject(
            method = "lambda$new$0(Lnet/minecraft/client/resources/PlayerSkin;Ljava/lang/Throwable;)V",
            at = @At("RETURN"),
            require = 0
    )
    private void nickhider$overrideAsyncLoadedFaceSkin(CallbackInfo ci) {
        try {
            GameProfile profile = nickhider$extractProfile(this);
            nickhider$applyMaskedTexture(this, profile);
        } catch (Throwable ex) {
            nickhider$logFailure(ex);
        }
    }

    @Inject(
            method = "draw(Lnet/minecraft/client/gui/GuiGraphics;IIII)V",
            at = @At("HEAD"),
            require = 0
    )
    private void nickhider$overrideBeforeDraw(CallbackInfo ci) {
        try {
            GameProfile profile = nickhider$extractProfile(this);
            nickhider$applyMaskedTexture(this, profile);
        } catch (Throwable ex) {
            nickhider$logFailure(ex);
        }
    }

    private static void nickhider$applyMaskedTexture(Object faceIcon, GameProfile profile) {
        if (faceIcon == null || profile == null) {
            return;
        }

        try {
            var runtime = NickHider.runtimeOrNull();
            if (runtime == null) {
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

            Object currentTexture = nickhider$currentSkinTexture(faceIcon);
            if (Objects.equals(currentTexture, textureLocation)) {
                return;
            }

            nickhider$applyFaceTexture(faceIcon, textureLocation);
            if (APPLY_LOGGED.compareAndSet(false, true)) {
                NickHider.LOGGER.info("[NH-FTB-HOOK] Applied FaceIcon texture override");
            }
        } catch (Throwable ex) {
            nickhider$logFailure(ex);
        }
    }

    private static void nickhider$logFailure(Throwable throwable) {
        if (FAILURE_LOGGED.compareAndSet(false, true)) {
            NickHider.LOGGER.warn("[NH-FTB-HOOK] Failed to override FaceIcon texture", throwable);
        } else {
            NickHider.LOGGER.debug("[NH-FTB-HOOK] FaceIcon texture override failed", throwable);
        }
    }

    private static GameProfile nickhider$extractProfile(Object faceIcon) throws ReflectiveOperationException {
        Object profile = nickhider$resolveProfileField(faceIcon).get(faceIcon);
        return profile instanceof GameProfile gameProfile ? gameProfile : null;
    }

    private static void nickhider$applyFaceTexture(Object faceIcon, Object textureLocation) throws ReflectiveOperationException {
        Constructor<?> iconCtor = nickhider$resolveImageIconConstructor(textureLocation.getClass());
        Method withUv = nickhider$resolveWithUvMethod();

        Object imageIcon = iconCtor.newInstance(textureLocation);
        Object head = withUv.invoke(imageIcon, 8F, 8F, 8F, 8F, 64F, 64F);
        Object hat = withUv.invoke(imageIcon, 40F, 8F, 8F, 8F, 64F, 64F);

        nickhider$resolveSkinField(faceIcon).set(faceIcon, imageIcon);
        nickhider$resolveHeadField(faceIcon).set(faceIcon, head);
        nickhider$resolveHatField(faceIcon).set(faceIcon, hat);
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
