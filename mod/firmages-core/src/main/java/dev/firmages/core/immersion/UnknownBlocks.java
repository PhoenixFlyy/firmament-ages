package dev.firmages.core.immersion;

/**
 * Jade on a block of an Age the player does not have (SPEC §21.14, Felix 2026-10-17): the overlay names it "Unknown"
 * ({@value #NAME_KEY}) with the sigil {@code firmages:textures/gui/unknown_sigil.png} as its icon, and shows nothing
 * else (no mod name, fluid, inventory, energy or progress). The Age index is the recipe viewer's ({@link EmiAgeView},
 * the synced {@code firmages:age_items/<age>} tags, else the mod's Age from {@code age_namespaces.json}): a block is
 * judged by its item, and a block without one (fluids, technical blocks) by its namespace. Players in creative mode
 * see every block as it is, as with ProgressiveStages' creative bypass. Pure Java (unit-tested); the Jade side is
 * {@code compat/jade/FirmagesJadePlugin.Unknown}.
 */
public final class UnknownBlocks {
    public static final String NAME_KEY = "firmages.jade.unknown";
    /** Path of the icon in the {@code firmages} namespace (16x16; the contract with the art round). */
    public static final String SIGIL = "textures/gui/unknown_sigil.png";

    private UnknownBlocks() {}

    /**
     * @param view     the player's Age view
     * @param item     the block's item, or null when it has none ({@code Items.AIR})
     * @param blockId  the block's id ({@code ns:path}), for blocks without an item
     * @param creative the player is in creative mode
     * @return Jade shows "Unknown" and nothing else
     */
    public static boolean unknown(EmiAgeView view, Object item, String blockId, boolean creative) {
        if (creative || view == null) return false;
        return item != null ? view.itemLocked(item) : view.idLocked(blockId);
    }
}
