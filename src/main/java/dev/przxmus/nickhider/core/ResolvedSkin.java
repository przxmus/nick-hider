package dev.przxmus.nickhider.core;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

public record ResolvedSkin(
        ResourceLocation textureLocation,
        String modelName,
        @Nullable ResourceLocation capeTextureLocation,
        @Nullable ResourceLocation elytraTextureLocation
) {
    public static final String MODEL_DEFAULT = "default";
    public static final String MODEL_SLIM = "slim";

    public ResolvedSkin(ResourceLocation textureLocation, String modelName) {
        this(textureLocation, modelName, null, null);
    }
}
