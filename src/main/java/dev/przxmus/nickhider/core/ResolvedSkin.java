package dev.przxmus.nickhider.core;

import net.minecraft.resources.ResourceLocation;

public record ResolvedSkin(
        ResourceLocation textureLocation,
        String modelName,
        ResourceLocation capeTextureLocation,
        ResourceLocation elytraTextureLocation
) {
    public static final String MODEL_DEFAULT = "default";
    public static final String MODEL_SLIM = "slim";

    public ResolvedSkin(ResourceLocation textureLocation, String modelName) {
        this(textureLocation, modelName, null, null);
    }
}
