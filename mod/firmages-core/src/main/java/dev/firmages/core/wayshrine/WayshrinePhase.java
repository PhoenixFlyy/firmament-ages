package dev.firmages.core.wayshrine;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** Blockstate {@code phase} of a Wayshrine core (SPEC-himmel §28.5, §28.7). */
public enum WayshrinePhase implements StringRepresentable {
    /** The restored form does not stand (worldgen state). */
    RUIN,
    /** The restored form stands; offering, rite and prayer light it. */
    RESTORED,
    /** Lit for good: it follows every new Age; {@code intact} says whether its form stands now. */
    LIT;

    private final String id = name().toLowerCase(Locale.ROOT);

    @Override
    public String getSerializedName() {
        return id;
    }

    public static WayshrinePhase byId(String s) {
        for (WayshrinePhase p : values()) {
            if (p.id.equals(s)) return p;
        }
        return RUIN;
    }
}
