package dev.firmages.core.immersion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Which item tooltip lines stay (SPEC §18.2, Felix 2026-10-14). Pure Java (unit-tested); the client handler in
 * {@code ImmersionClient} feeds it plain text and translation keys.
 * <ul>
 * <li>TerraFirmaCraft's size and weight line ({@code ItemSizeManager.addTooltipInfo}: "⚖" + weight + " ⇲" + size,
 * gray), which TFC 4.2.11 adds to every item and has no config for, is recognised by its two enum keys.</li>
 * <li>A locked item keeps only its name and ProgressiveStages' block, which PS appends in this order: an empty line,
 * the "locked" message, the required-stage line, the current-stage line and an optional stage description.</li>
 * </ul>
 */
public final class TooltipTrim {
    /** TFC {@code Helpers.translateEnum(Weight)} and {@code (Size)} [verified, javap and lang, TFC 4.2.11]. */
    public static final String WEIGHT_KEY_PREFIX = "tfc.enum.weight.";
    public static final String SIZE_KEY_PREFIX = "tfc.enum.size.";
    /** PS lines after the "locked" message that are always there: required stage, current stage. */
    private static final int PS_LINES_AFTER_LOCKED = 2;

    private TooltipTrim() {}

    /** True for TFC's size and weight line: one line that names both a weight and a size. */
    public static boolean isSizeWeight(Collection<String> translationKeys) {
        boolean weight = false;
        boolean size = false;
        for (String k : translationKeys) {
            if (k.startsWith(WEIGHT_KEY_PREFIX)) weight = true;
            else if (k.startsWith(SIZE_KEY_PREFIX)) size = true;
        }
        return weight && size;
    }

    /**
     * The indices to keep of a locked item's tooltip: line 0 (the name, masked by PS) and PS's block, found by its
     * "locked" message. Without that message (PS added nothing) only the name stays.
     *
     * @param plain           the plain text of each line, formatting stripped
     * @param lockedMessages  the plain text of PS's three "locked" messages (item, recipe, item and recipe)
     * @param descriptionLine the plain text of PS's stage-description line for this item, or null if it adds none
     */
    public static List<Integer> lockedKeep(List<String> plain, Set<String> lockedMessages, String descriptionLine) {
        List<Integer> keep = new ArrayList<>();
        if (plain.isEmpty()) return keep;
        keep.add(0);
        int at = -1;
        for (int i = 1; i < plain.size(); i++) {
            if (lockedMessages.contains(plain.get(i).trim())) {
                at = i;
                break;
            }
        }
        if (at < 0) return keep;
        if (at - 1 >= 1 && plain.get(at - 1).isBlank()) keep.add(at - 1);
        int last = Math.min(plain.size() - 1, at + PS_LINES_AFTER_LOCKED);
        for (int i = at; i <= last; i++) keep.add(i);
        int desc = last + 1;
        if (descriptionLine != null && desc < plain.size() && plain.get(desc).trim().equals(descriptionLine.trim())) keep.add(desc);
        return keep;
    }
}
