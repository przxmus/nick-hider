package dev.przxmus.nickhider.mixin;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.przxmus.nickhider.NickHider;
import dev.przxmus.nickhider.core.ProfileCompat;
import dev.przxmus.nickhider.core.ResolvedSkin;

@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
    private static final AtomicBoolean HOOK_FAILURE_LOGGED = new AtomicBoolean(false);

    @Shadow
    public abstract GameProfile getProfile();

    @Inject(method = "getSkinLocation", at = @At("RETURN"), cancellable = true, require = 0)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void nickhider$overrideSkinLocation(CallbackInfoReturnable cir) {
        try {
            var runtime = NickHider.runtimeOrNull();
            if (runtime == null) {
                return;
            }

            GameProfile profile = this.getProfile();
            java.util.UUID profileId = ProfileCompat.id(profile);
            String profileName = ProfileCompat.name(profile);
            if (profileId == null) {
                return;
            }

            runtime.replacementSkin(profileId, profileName)
                    .map(ResolvedSkin::textureLocation)
                    .ifPresent(cir::setReturnValue);
        } catch (Throwable throwable) {
            nickhider$logHookFailure("getSkinLocation", throwable);
        }
    }

    @Inject(method = "getModelName", at = @At("RETURN"), cancellable = true, require = 0)
    private void nickhider$overrideSkinModel(CallbackInfoReturnable<String> cir) {
        try {
            var runtime = NickHider.runtimeOrNull();
            if (runtime == null) {
                return;
            }

            GameProfile profile = this.getProfile();
            java.util.UUID profileId = ProfileCompat.id(profile);
            String profileName = ProfileCompat.name(profile);
            if (profileId == null) {
                return;
            }

            runtime.replacementSkin(profileId, profileName)
                    .map(ResolvedSkin::modelName)
                    .ifPresent(cir::setReturnValue);
        } catch (Throwable throwable) {
            nickhider$logHookFailure("getModelName", throwable);
        }
    }

    @Inject(method = "getCapeLocation", at = @At("RETURN"), cancellable = true, require = 0)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void nickhider$overrideCapeLocation(CallbackInfoReturnable cir) {
        try {
            var runtime = NickHider.runtimeOrNull();
            if (runtime == null) {
                return;
            }

            GameProfile profile = this.getProfile();
            java.util.UUID profileId = ProfileCompat.id(profile);
            String profileName = ProfileCompat.name(profile);
            if (profileId == null || !runtime.shouldOverrideCape(profileId, profileName)) {
                return;
            }

            runtime.replacementCape(profileId, profileName)
                    .map(ResolvedSkin::capeTextureLocation)
                    .ifPresentOrElse(cir::setReturnValue, () -> cir.setReturnValue(null));
        } catch (Throwable throwable) {
            nickhider$logHookFailure("getCapeLocation", throwable);
        }
    }

    @Inject(method = "getElytraLocation", at = @At("RETURN"), cancellable = true, require = 0)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void nickhider$overrideElytraLocation(CallbackInfoReturnable cir) {
        try {
            var runtime = NickHider.runtimeOrNull();
            if (runtime == null) {
                return;
            }

            GameProfile profile = this.getProfile();
            java.util.UUID profileId = ProfileCompat.id(profile);
            String profileName = ProfileCompat.name(profile);
            if (profileId == null || !runtime.shouldOverrideCape(profileId, profileName)) {
                return;
            }

            runtime.replacementCape(profileId, profileName)
                    .map(ResolvedSkin::elytraTextureLocation)
                    .ifPresentOrElse(cir::setReturnValue, () -> cir.setReturnValue(null));
        } catch (Throwable throwable) {
            nickhider$logHookFailure("getElytraLocation", throwable);
        }
    }

    private static void nickhider$logHookFailure(String hook, Throwable throwable) {
        if (HOOK_FAILURE_LOGGED.compareAndSet(false, true)) {
            NickHider.LOGGER.warn("Nick Hider skin hook failed in {}; falling back to vanilla behavior", hook, throwable);
        } else {
            NickHider.LOGGER.debug("Nick Hider skin hook failed in {}", hook, throwable);
        }
    }

    /*? if >=1.20.2 {*/
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void nickhider$overrideSkinRecord(CallbackInfoReturnable cir) {
        try {
            var runtime = NickHider.runtimeOrNull();
            if (runtime == null) {
                return;
            }

            GameProfile profile = this.getProfile();
            java.util.UUID profileId = ProfileCompat.id(profile);
            String profileName = ProfileCompat.name(profile);
            if (profileId == null) {
                return;
            }

            Object current = cir.getReturnValue();
            if (current == null) {
                return;
            }

            var replacementSkin = runtime.replacementSkin(profileId, profileName);
            boolean overrideCape = runtime.shouldOverrideCape(profileId, profileName);
            var replacementCape = overrideCape
                    ? runtime.replacementCape(profileId, profileName)
                    : java.util.Optional.<ResolvedSkin>empty();
            if (replacementSkin.isEmpty() && !overrideCape) {
                return;
            }

            if (hasNoArgMethod(current, "body")) {
                Object body = readObject(current, "body");
                Object model = readObject(current, "model");
                if (replacementSkin.isPresent()) {
                    ResolvedSkin skin = replacementSkin.get();
                    Object mappedTexture = asLocation(skin.textureLocation());
                    if (mappedTexture != null) {
                        body = replaceTextureAsset(body, mappedTexture);
                    }
                    model = resolveModel(skin.modelName(), model);
                }

                Object cape = readObject(current, "cape");
                Object elytra = readObject(current, "elytra");
                if (overrideCape) {
                    if (replacementCape.isPresent()) {
                        ResolvedSkin capeSkin = replacementCape.get();
                        Object capeTexture = asLocation(capeSkin.capeTextureLocation());
                        Object elytraTexture = asLocation(capeSkin.elytraTextureLocation());
                        cape = replaceTextureAsset(cape, capeTexture);
                        elytra = replaceTextureAsset(elytra, elytraTexture);
                    } else {
                        cape = null;
                        elytra = null;
                    }
                }

                Object replacement = instantiateSkinModern(
                        current,
                        body,
                        cape,
                        elytra,
                        model,
                        readBoolean(current, "secure")
                );
                if (replacement != null) {
                    cir.setReturnValue(replacement);
                }
                return;
            }

            Object texture = readLocation(current, "texture");
            Object model = readObject(current, "model");
            if (replacementSkin.isPresent()) {
                ResolvedSkin skin = replacementSkin.get();
                Object mappedTexture = asLocation(skin.textureLocation());
                if (mappedTexture != null) {
                    texture = mappedTexture;
                }
                model = resolveModel(skin.modelName(), model);
            }

            Object cape = readLocation(current, "capeTexture");
            Object elytra = readLocation(current, "elytraTexture");
            if (overrideCape) {
                if (replacementCape.isPresent()) {
                    ResolvedSkin capeSkin = replacementCape.get();
                    cape = asLocation(capeSkin.capeTextureLocation());
                    elytra = asLocation(capeSkin.elytraTextureLocation());
                } else {
                    cape = null;
                    elytra = null;
                }
            }

            Object replacement = instantiateSkin(
                    current,
                    texture,
                    readString(current, "textureUrl"),
                    cape,
                    elytra,
                    model,
                    readBoolean(current, "secure")
            );
            if (replacement != null) {
                cir.setReturnValue(replacement);
            }
        } catch (Throwable throwable) {
            nickhider$logHookFailure("getSkin", throwable);
        }
    }

    private static Object readLocation(Object instance, String methodName) {
        Object value = readObject(instance, methodName);
        return asLocation(value);
    }

    private static Object asLocation(Object value) {
        if (value == null) {
            return null;
        }
        String className = value.getClass().getName();
        if (className.equals("net.minecraft.resources.ResourceLocation")
                || className.equals("net.minecraft.util.Identifier")
                || className.equals("net.minecraft.resources.Identifier")) {
            return value;
        }
        return null;
    }

    private static String readString(Object instance, String methodName) {
        Object value = readObject(instance, methodName);
        return value instanceof String string ? string : "";
    }

    private static boolean readBoolean(Object instance, String methodName) {
        Object value = readObject(instance, methodName);
        return value instanceof Boolean bool && bool;
    }

    private static Object readObject(Object instance, String methodName) {
        try {
            Method method = instance.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(instance);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean hasNoArgMethod(Object instance, String methodName) {
        try {
            instance.getClass().getMethod(methodName);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private static Object replaceTextureAsset(Object fallbackAsset, Object texturePath) {
        if (texturePath == null) {
            return null;
        }

        Object id = texturePath;
        if (fallbackAsset != null) {
            Object existingId = readLocation(fallbackAsset, "id");
            if (existingId != null) {
                id = existingId;
            }
            Object recreated = createTextureAsset(fallbackAsset.getClass(), id, texturePath);
            if (recreated != null) {
                return recreated;
            }
        }

        try {
            Class<?> resourceTextureClass = Class.forName("net.minecraft.core.ClientAsset$ResourceTexture");
            Object created = createTextureAsset(resourceTextureClass, id, texturePath);
            if (created != null) {
                return created;
            }
        } catch (ClassNotFoundException ignored) {}

        return fallbackAsset;
    }

    private static Object createTextureAsset(Class<?> textureClass, Object id, Object texturePath) {
        Class<?> locationClass = texturePath != null ? texturePath.getClass() : null;
        if (locationClass == null && id != null) {
            locationClass = id.getClass();
        }
        if (locationClass == null) {
            locationClass = findLocationClass();
        }
        if (locationClass == null) {
            return null;
        }

        try {
            Constructor<?> fullCtor = textureClass.getDeclaredConstructor(locationClass, locationClass);
            fullCtor.setAccessible(true);
            return fullCtor.newInstance(id, texturePath);
        } catch (ReflectiveOperationException ignored) {}

        try {
            Constructor<?> simpleCtor = textureClass.getDeclaredConstructor(locationClass);
            simpleCtor.setAccessible(true);
            return simpleCtor.newInstance(texturePath);
        } catch (ReflectiveOperationException ignored) {}

        return null;
    }

    private static Class<?> findLocationClass() {
        String[] candidates = new String[] {
                "net.minecraft.resources.ResourceLocation",
                "net.minecraft.util.Identifier",
                "net.minecraft.resources.Identifier"
        };
        for (String candidate : candidates) {
            try {
                return Class.forName(candidate);
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }

    private static Object instantiateSkin(
            Object current,
            Object texture,
            String textureUrl,
            Object cape,
            Object elytra,
            Object model,
            boolean secure
    ) {
        Class<?> skinClass = current.getClass();
        for (Constructor<?> constructor : skinClass.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length != 6) {
                continue;
            }
            try {
                constructor.setAccessible(true);
                return constructor.newInstance(texture, textureUrl, cape, elytra, model, secure);
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }

    private static Object instantiateSkinModern(
            Object current,
            Object body,
            Object cape,
            Object elytra,
            Object model,
            boolean secure
    ) {
        Class<?> skinClass = current.getClass();
        for (Constructor<?> constructor : skinClass.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length != 5) {
                continue;
            }
            try {
                constructor.setAccessible(true);
                return constructor.newInstance(body, cape, elytra, model, secure);
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }

    private static Object resolveModel(String modelName, Object fallback) {
        if (fallback == null) {
            return null;
        }
        Class<?> modelClass = fallback.getClass();
        if (ResolvedSkin.MODEL_SLIM.equalsIgnoreCase(modelName)) {
            Object slim = enumConstant(modelClass, "SLIM");
            if (slim != null) {
                return slim;
            }
        }
        if (ResolvedSkin.MODEL_DEFAULT.equalsIgnoreCase(modelName)) {
            Object wide = enumConstant(modelClass, "WIDE");
            if (wide != null) {
                return wide;
            }
        }

        try {
            Method byName = modelClass.getMethod("byName", String.class);
            byName.setAccessible(true);
            Object resolved = byName.invoke(null, modelName);
            if (resolved != null) {
                return resolved;
            }
        } catch (ReflectiveOperationException ignored) {}
        return fallback;
    }

    private static Object enumConstant(Class<?> enumClass, String name) {
        if (!enumClass.isEnum()) {
            return null;
        }
        Object[] constants = enumClass.getEnumConstants();
        if (constants == null) {
            return null;
        }
        for (Object constant : constants) {
            if (constant instanceof Enum<?> enumValue && enumValue.name().equals(name)) {
                return constant;
            }
        }
        return null;
    }
    /*?}*/
}
