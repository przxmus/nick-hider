package dev.przxmus.nickhider.core;

import com.mojang.authlib.GameProfile;
import java.util.Objects;
import java.util.UUID;

public record MaskedProfile(UUID uuid, String name) {
    public boolean isSameAs(UUID otherUuid, String otherName) {
        return Objects.equals(uuid, otherUuid) && Objects.equals(name, otherName);
    }

    public GameProfile toGameProfile() {
        if (uuid == null || name == null || name.isBlank()) {
            return null;
        }
        return new GameProfile(uuid, name);
    }
}
