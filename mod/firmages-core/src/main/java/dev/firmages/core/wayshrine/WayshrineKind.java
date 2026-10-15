package dev.firmages.core.wayshrine;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The eight Wayshrine types (SPEC-himmel §28.4 with S6B: the Wind Shrine of the Drifting Isles replaces the Glacier
 * Cairn), as the {@code kind} of the Wayshrine Stone. The type data itself is data-driven ({@code firmages_wayshrine/type});
 * a type outside this list (the GameTest stand-in) gives a Forest Ring stone.
 */
public enum WayshrineKind implements StringRepresentable {
    FOREST_RING, STEPPE_STONES, SWAMP_ALTAR, DESERT_ARCH, MOUNTAIN_SPIRE, WIND_SHRINE, REEF_ALTAR, VOLCANIC_RING;

    private final String id = name().toLowerCase(Locale.ROOT);

    public String id() {
        return id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** The kind of a type id, Forest Ring for an unknown one. */
    public static WayshrineKind byType(String type) {
        for (WayshrineKind k : values()) {
            if (k.id.equals(type)) return k;
        }
        return FOREST_RING;
    }
}
