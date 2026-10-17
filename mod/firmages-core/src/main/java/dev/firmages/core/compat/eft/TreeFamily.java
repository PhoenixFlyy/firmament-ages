package dev.firmages.core.compat.eft;

/**
 * Which blocks belong to one tree for Enhanced Falling Trees (SPEC §20.2). Pure Java (unit-tested), used by
 * {@code mixin/eft/DefaultTreeMixin}.
 *
 * <p>TerraFirmaCraft 4.2.11 grows its trees from templates whose trunk is {@code <ns>:wood/log/<species>} and whose
 * branches are {@code <ns>:wood/wood/<species>} (oak/1: 9 logs and 9 wood blocks; kapok/4: 21 logs and 20 wood blocks
 * [verified, structure NBT of the jar]); ArborFirmaCraft and The Beneath name their trees the same way
 * ({@code dev/wood_classes.toml}, loot tables under {@code blocks/wood/log} and {@code blocks/wood/wood}). Enhanced
 * Falling Trees 0.7.0 joins only blocks identical to the broken one ({@code DefaultTree.isMatchingLog}), which left every
 * branch hanging in the air. Log and wood of one species count as one tree here.
 */
public final class TreeFamily {
    /**
     * Height limit of a felled tree above the broken block. EFT 0.7.0 stops at 32 ({@code DefaultTree.MAX_TREE_HEIGHT},
     * inlined in {@code isWithinDistance}); the pack's tallest trees are taller: {@code tfc:sequoia_large} as
     * ArborFirmaCraft 2.1.3 redefines it (four stacked layers, up to 43 blocks, 425 trunk and branch blocks),
     * {@code tfc:kapok_large} 42, {@code afc:coast_redwood_large} 40 (maximum trunk plus maximum stacked layers of the
     * {@code configured_feature/tree} files and their templates, measured from the jars).
     */
    public static final int MAX_HEIGHT = 64;
    private static final String LOG = "wood/log/";
    private static final String WOOD = "wood/wood/";

    private TreeFamily() {}

    /** {@code "ns:species"} of a trunk or branch block id ({@code ns:wood/log/s}, {@code ns:wood/wood/s}), else null. */
    public static String species(String blockId) {
        if (blockId == null) return null;
        int colon = blockId.indexOf(':');
        if (colon <= 0) return null;
        String path = blockId.substring(colon + 1);
        String rest;
        if (path.startsWith(LOG)) rest = path.substring(LOG.length());
        else if (path.startsWith(WOOD)) rest = path.substring(WOOD.length());
        else return null;
        if (rest.isEmpty() || rest.indexOf('/') >= 0) return null;
        return blockId.substring(0, colon) + ":" + rest;
    }

    /** Both blocks are trunk or branch of the same species (same namespace). */
    public static boolean sameTree(String a, String b) {
        String s = species(a);
        return s != null && s.equals(species(b));
    }
}
