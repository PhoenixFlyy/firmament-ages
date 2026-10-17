package dev.firmages.core.loot;

import java.util.List;
import java.util.function.Predicate;

/**
 * Prefilled containers of structure templates (SPEC §19.5), pure Java: walks the block entity data a template places
 * and removes every item stack the predicate drops, nested contents included. A stack is a compound with a string
 * {@code id} and a numeric {@code count} (or the legacy {@code Count}); the block entity's own compound never counts as
 * one. What a removal leaves behind keeps every reader working:
 * <ul>
 *   <li>a stack in a list ({@code Items} of chests, barrels and TFC inventories, {@code minecraft:bundle_contents},
 *       {@code tfc:contents}): the element goes;</li>
 *   <li>a slot entry in a list whose stack goes ({@code {slot, item}} of {@code minecraft:container}): the entry goes;</li>
 *   <li>a stack in a field (a pedestal's {@code itemStack}, a pot's {@code item}): the field goes (an NBT reader then
 *       finds no item).</li>
 * </ul>
 * A stack that stays is walked too, so a shulker box, bundle or TFC vessel keeps only what may stay. {@link Nodes}
 * adapts the tree: NBT in the game, plain maps and lists in JUnit.
 */
public final class PlacedContents {
    private PlacedContents() {}

    /** The tree operations the walk needs; compounds and lists are whatever the adapter says they are. */
    public interface Nodes {
        boolean isCompound(Object node);

        boolean isList(Object node);

        /** The keys of a compound (a copy: the walk removes keys while it goes). */
        List<String> keys(Object compound);

        Object get(Object compound, String key);

        /** The string value of a key, or null when the key holds no string. */
        String string(Object compound, String key);

        boolean isNumber(Object compound, String key);

        void remove(Object compound, String key);

        int size(Object list);

        Object get(Object list, int index);

        void remove(Object list, int index);
    }

    /** True when the node is an item stack: a compound with a string id and a numeric count. */
    public static boolean isStack(Object node, Nodes n) {
        return n.isCompound(node) && n.string(node, "id") != null && (n.isNumber(node, "count") || n.isNumber(node, "Count"));
    }

    /**
     * Removes (with {@code apply}) or only counts (without) every stack under {@code root} whose item id {@code drop}
     * accepts, at any depth. {@code root} itself is the block entity's data and never a stack.
     */
    public static int filter(Object root, Nodes n, Predicate<String> drop, boolean apply) {
        if (n.isCompound(root)) return compound(root, n, drop, apply);
        if (n.isList(root)) return list(root, n, drop, apply);
        return 0;
    }

    private static int compound(Object c, Nodes n, Predicate<String> drop, boolean apply) {
        int removed = 0;
        for (String key : n.keys(c)) {
            Object v = n.get(c, key);
            if (isStack(v, n) && drop.test(n.string(v, "id"))) {
                removed++;
                if (apply) n.remove(c, key);
            } else {
                removed += filter(v, n, drop, apply);
            }
        }
        return removed;
    }

    private static int list(Object l, Nodes n, Predicate<String> drop, boolean apply) {
        int removed = 0;
        for (int i = n.size(l) - 1; i >= 0; i--) {
            Object e = n.get(l, i);
            if (isStack(e, n) && drop.test(n.string(e, "id"))) {
                removed++;
                if (apply) n.remove(l, i);
            } else if (slotEntryDropped(e, n, drop)) {
                removed++;
                if (apply) n.remove(l, i);
            } else {
                removed += filter(e, n, drop, apply);
            }
        }
        return removed;
    }

    /** A slot entry ({@code {slot, item}}, not a stack itself) whose stack goes. */
    private static boolean slotEntryDropped(Object e, Nodes n, Predicate<String> drop) {
        if (!n.isCompound(e) || isStack(e, n) || !(n.isNumber(e, "slot") || n.isNumber(e, "Slot"))) return false;
        for (String key : n.keys(e)) {
            Object v = n.get(e, key);
            if (isStack(v, n) && drop.test(n.string(v, "id"))) return true;
        }
        return false;
    }
}
