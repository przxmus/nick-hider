package pl.przxmus.nickhider.core;

import net.minecraft.resources.ResourceLocation;

public record ResolvedSkin(ResourceLocation textureLocation, String modelName) {
    public static final String MODEL_DEFAULT = "default";
    public static final String MODEL_SLIM = "slim";
}
