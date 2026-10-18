package dev.firmages.core.boss;

/**
 * The ten segments of The Primordial's halo, one per Age (round G4, SPEC §39.2): the order the ring fires in, the key
 * of the row in {@code balance/boss_shots.json} and the index into {@link PrimordialGeometry#NODE_COLORS}.
 */
public enum ShotAge {
    STONE, BRONZE, IRON, ARCANE, INDUSTRIAL, ELECTRIC, INFORMATION, SPACE, QUANTUM, SINGULARITY;

    public static final int COUNT = values().length;
    /**
     * Segment colours: Age 0..8 in the ceremony beam colour (BEAM_FALLBACK in dev/gen_textures.py, the same the model's
     * nodes are painted with), Age 9 the white-violet of the spire tip and the lit eye. {@code PrimordialGeometry.NODE_COLORS}
     * is this table. Pure Java (the JUnit tests run without Minecraft).
     */
    public static final int[] COLORS = {0xFFB347, 0xFFD54A, 0xE06030, 0x9B59FF, 0xF0F0F0, 0x40E0FF, 0x40FF80, 0xE0E8FF, 0xE0B0FF, 0xF4F0FF};

    public final String key = name().toLowerCase(java.util.Locale.ROOT);

    /** The segment's colour (the Age's ceremony beam colour; Singularity the white-violet of the spire tip). */
    public int color() {
        return COLORS[ordinal()];
    }

    /** The segment named {@code key} ({@code stone} .. {@code singularity}), Stone for an unknown name. */
    public static ShotAge byKey(String key) {
        for (ShotAge a : values()) if (a.key.equals(key)) return a;
        return STONE;
    }

    public static ShotAge byIndex(int i) {
        ShotAge[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
