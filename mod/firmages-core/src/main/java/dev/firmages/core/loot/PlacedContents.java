package dev.firmages.core.loot;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Prefilled containers and entities of structure templates and nested loot (SPEC §19.5), pure Java: walks the data a
 * template places, or a rolled stack's component patch, and removes every item stack the predicate drops, nested
 * contents included. A stack is a compound with a string
 * {@code id} and a numeric {@code count} (or the legacy {@code Count}); the block entity's own compound never counts as
 * one. What a removal leaves behind keeps every reader working:
 * <ul>
 *   <li>a stack in a list ({@code Items} of chests, barrels and TFC inventories, {@code minecraft:bundle_contents}): the
 *       element goes;</li>
 *   <li>a stack in a list that keeps its size ({@link #keepsSize}: the {@code items} of TFC's small vessel component
 *       {@code tfc:vessel}, exactly four optional stacks, and the other positional lists of optional stacks: a mob's
 *       or an armor stand's {@code ArmorItems} and {@code HandItems}, TFC's {@code tfc:contents}, {@code tfc:barrel}
 *       and {@code tfc:crucible}): the element becomes an empty compound, which {@code ItemStack.OPTIONAL_CODEC} and
 *       {@code ItemStack.parseOptional} read as an empty slot;</li>
 *   <li>a slot entry in a list whose stack goes ({@code {slot, item}} of {@code minecraft:container}): the entry goes;</li>
 *   <li>a stack in a field (a pedestal's {@code itemStack}, a pot's {@code item}): the field goes (an NBT reader then
 *       finds no item).</li>
 * </ul>
 * A stack that stays is walked too, so a shulker box, bundle or TFC vessel keeps only what may stay. The same walk
 * serves three roots: a template's block entity data ({@link #filter}), a template's entity data
 * ({@link #filterEntity}) and the component patch of a rolled loot stack ({@link #filter}).
 * {@link Nodes} adapts the tree: NBT in the game, plain maps and lists in JUnit.
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

        /** Replaces the element at {@code index} by an empty compound. */
        void empty(Object list, int index);
    }

    /**
     * A list that keeps its size, because its readers take the stacks by position and read an empty compound as an
     * empty slot:
     * <ul>
     *   <li>TFC 4.2.11's small vessel component {@code tfc:vessel} holds {@code items} as
     *       {@code ItemStack.OPTIONAL_CODEC.listOf(4, 4)} [verified, javap {@code VesselComponent}], so a shorter list
     *       makes the whole vessel an invalid item ("List is too short", copy boot of round G2); the {@code items} of
     *       {@code tfc:barrel} and {@code tfc:crucible} and the list {@code tfc:contents} ({@code ItemListComponent})
     *       are {@code ItemStack.OPTIONAL_CODEC.listOf()} [verified, javap], one entry per slot;</li>
     *   <li>{@code ArmorItems} (feet, legs, chest, head) and {@code HandItems} (main hand, offhand) of a mob or an
     *       armor stand: {@code Mob} and {@code ArmorStand} read entry i into slot i with {@code ItemStack.parseOptional}
     *       [verified, 21.1.252 sources], so a removed helmet must not move the chestplate.</li>
     * </ul>
     */
    static boolean keepsSize(String parentKey, String key) {
        if ("ArmorItems".equals(key) || "HandItems".equals(key) || "tfc:contents".equals(key)) return true;
        return "items".equals(key) && ("tfc:vessel".equals(parentKey) || "tfc:barrel".equals(parentKey) || "tfc:crucible".equals(parentKey));
    }

    /**
     * Keys of an entity's data the walk leaves alone: {@code Offers}, a villager's or a wandering trader's trades.
     * {@code MerchantOffer} needs its {@code buy} and {@code sell} stacks, so a removed one would cost the entity its
     * trades; trades are no contents (SPEC §19.5, §34.4).
     */
    static final Set<String> ENTITY_SKIP = Set.of("Offers");

    /** True when the node is an item stack: a compound with a string id and a numeric count. */
    public static boolean isStack(Object node, Nodes n) {
        return n.isCompound(node) && n.string(node, "id") != null && (n.isNumber(node, "count") || n.isNumber(node, "Count"));
    }

    /**
     * Removes (with {@code apply}) or only counts (without) every stack under {@code root} whose item id {@code drop}
     * accepts, at any depth. {@code root} itself is the block entity's data and never a stack.
     */
    public static int filter(Object root, Nodes n, Predicate<String> drop, boolean apply) {
        return node(root, null, n, drop, apply);
    }

    /**
     * {@link #filter} for the data of an entity a template places (an armor stand's or a mob's equipment, an item
     * frame's or an item entity's {@code Item}, the {@code Items} of a chest minecart or a donkey, a piglin's
     * {@code Inventory}): every key but {@link #ENTITY_SKIP}. {@code entity} itself is never a stack.
     */
    public static int filterEntity(Object entity, Nodes n, Predicate<String> drop, boolean apply) {
        if (!n.isCompound(entity)) return 0;
        int removed = 0;
        for (String key : n.keys(entity)) {
            if (!ENTITY_SKIP.contains(key)) removed += entry(entity, null, key, n, drop, apply);
        }
        return removed;
    }

    /** True for an item entity ({@code minecraft:item}) whose {@code Item} is gone: the template places nothing there. */
    public static boolean itemEntityEmpty(Object entity, Nodes n) {
        return n.isCompound(entity) && "minecraft:item".equals(n.string(entity, "id")) && !isStack(n.get(entity, "Item"), n);
    }

    /** A node under the key {@code key} of its compound (null for the root and for list elements). */
    private static int node(Object o, String key, Nodes n, Predicate<String> drop, boolean apply) {
        if (n.isCompound(o)) return compound(o, key, n, drop, apply);
        if (n.isList(o)) return list(o, false, n, drop, apply);
        return 0;
    }

    private static int compound(Object c, String ownKey, Nodes n, Predicate<String> drop, boolean apply) {
        int removed = 0;
        for (String key : n.keys(c)) removed += entry(c, ownKey, key, n, drop, apply);
        return removed;
    }

    /** The value under {@code key} of the compound {@code c}, which sits under {@code ownKey} (null: root or list element). */
    private static int entry(Object c, String ownKey, String key, Nodes n, Predicate<String> drop, boolean apply) {
        Object v = n.get(c, key);
        if (isStack(v, n) && drop.test(n.string(v, "id"))) {
            if (apply) n.remove(c, key);
            return 1;
        }
        if (n.isList(v)) return list(v, keepsSize(ownKey, key), n, drop, apply);
        return node(v, key, n, drop, apply);
    }

    private static int list(Object l, boolean keepSize, Nodes n, Predicate<String> drop, boolean apply) {
        int removed = 0;
        for (int i = n.size(l) - 1; i >= 0; i--) {
            Object e = n.get(l, i);
            if (isStack(e, n) && drop.test(n.string(e, "id"))) {
                removed++;
                if (apply && keepSize) n.empty(l, i);
                else if (apply) n.remove(l, i);
            } else if (slotEntryDropped(e, n, drop)) {
                removed++;
                if (apply) n.remove(l, i);
            } else {
                removed += node(e, null, n, drop, apply);
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
