package dev.firmages.core.immersion;

/**
 * Items of an Age the player does not have go by one name, "Something Unknown" ({@value #NAME_KEY}, the text of
 * ProgressiveStages' {@code tooltip_masked_name}), wherever the game names them (SPEC §21.15, Felix S1a 2026-10-18):
 * item tooltips in every screen (containers, EMI, JEI, a chat {@code [item]} hover), the {@code [item]} text in chat,
 * the name tag of an item entity or an item frame, and Jade's line on an item entity or an item frame. No mod line
 * and no other line stays, only ProgressiveStages' lock block. The Age index is the recipe viewer's
 * ({@link EmiAgeView}), as for {@link UnknownBlocks}; players in creative mode see every name. Pure Java
 * (unit-tested); the client half is {@code client/immersion/UnknownItemsClient}, Jade's {@code FirmagesJadePlugin}.
 */
public final class UnknownItems {
    public static final String NAME_KEY = "firmages.unknown_item";

    private UnknownItems() {}

    /**
     * @param view     the player's Age view, or null before it exists
     * @param item     the item (registry object), or null for an empty stack
     * @param creative the player is in creative mode
     * @return the item is named "Something Unknown"
     */
    public static boolean masked(EmiAgeView view, Object item, boolean creative) {
        if (creative || view == null || item == null) return false;
        return view.itemLocked(item);
    }
}
