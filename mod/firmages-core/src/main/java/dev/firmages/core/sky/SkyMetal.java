package dev.firmages.core.sky;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.Optional;

/** The two sky metals of Starfall (SPEC §25.5); the {@code metal} property of the starfall core. */
public enum SkyMetal implements StringRepresentable {
    METEORIC_IRON,
    STARSTEEL;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return id();
    }

    public static Optional<SkyMetal> byId(String id) {
        for (SkyMetal m : values()) if (m.id().equals(id)) return Optional.of(m);
        return Optional.empty();
    }
}
