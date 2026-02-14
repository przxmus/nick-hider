package dev.przxmus.nickhider.core;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.util.UUID;

public final class ProfileCompat {
    private ProfileCompat() {}

    public static UUID id(GameProfile profile) {
        if (profile == null) {
            return null;
        }
        Object value = invokeNoArg(profile, "getId");
        if (value == null) {
            value = invokeNoArg(profile, "id");
        }
        return value instanceof UUID ? (UUID) value : null;
    }

    public static String name(GameProfile profile) {
        if (profile == null) {
            return null;
        }
        Object value = invokeNoArg(profile, "getName");
        if (value == null) {
            value = invokeNoArg(profile, "name");
        }
        return value instanceof String ? (String) value : null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
