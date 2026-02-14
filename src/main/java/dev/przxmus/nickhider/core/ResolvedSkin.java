package dev.przxmus.nickhider.core;

public record ResolvedSkin(
        Object textureLocation,
        String modelName,
        Object capeTextureLocation,
        Object elytraTextureLocation
) {
    public static final String MODEL_DEFAULT = "default";
    public static final String MODEL_SLIM = "slim";

    public ResolvedSkin(Object textureLocation, String modelName) {
        this(textureLocation, modelName, null, null);
    }
}
