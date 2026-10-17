package dev.firmages.core.loot;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The recursive filter of prefilled template containers (SPEC §19.5) on plain maps and lists shaped like the NBT. */
class PlacedContentsTest {
    /** Maps are compounds, lists are lists, numbers are numeric tags. */
    private static final PlacedContents.Nodes NODES = new PlacedContents.Nodes() {
        @Override public boolean isCompound(Object node) { return node instanceof Map; }
        @Override public boolean isList(Object node) { return node instanceof List; }
        @Override public List<String> keys(Object c) { return new ArrayList<>(map(c).keySet()); }
        @Override public Object get(Object c, String key) { return map(c).get(key); }
        @Override public String string(Object c, String key) { return map(c).get(key) instanceof String s ? s : null; }
        @Override public boolean isNumber(Object c, String key) { return map(c).get(key) instanceof Number; }
        @Override public void remove(Object c, String key) { map(c).remove(key); }
        @Override public int size(Object l) { return list(l).size(); }
        @Override public Object get(Object l, int i) { return list(l).get(i); }
        @Override public void remove(Object l, int i) { list(l).remove(i); }
        @Override public void empty(Object l, int i) { list(l).set(i, new LinkedHashMap<String, Object>()); }
    };
    private static final Predicate<String> LATE = Set.of("x:totem", "x:hidden")::contains;

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) {
        return (List<Object>) o;
    }

    private static Map<String, Object> c(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static List<Object> l(Object... es) {
        return new ArrayList<>(List.of(es));
    }

    private static Map<String, Object> stack(String id, Object... more) {
        Map<String, Object> s = c("id", id, "count", 1);
        for (int i = 0; i < more.length; i += 2) s.put((String) more[i], more[i + 1]);
        return s;
    }

    @Test
    void chestSlotsGoAndTheBlockEntityStays() {
        Map<String, Object> chest = c("id", "x:chest", "Items", l(stack("x:hide", "Slot", 0), stack("x:totem", "Slot", 1), stack("x:hidden", "Slot", 2)));
        assertEquals(2, PlacedContents.filter(chest, NODES, LATE, false), "dry run counts");
        assertEquals(3, list(chest.get("Items")).size(), "dry run changes nothing");
        assertEquals(2, PlacedContents.filter(chest, NODES, LATE, true));
        assertEquals(List.of(stack("x:hide", "Slot", 0)), chest.get("Items"));
        assertEquals("x:chest", chest.get("id"), "the block entity's own id is no stack");
        assertFalse(PlacedContents.isStack(c("id", "x:totem"), NODES), "no count, no stack (a spawner's entity)");
    }

    @Test
    void nestedContainersAreCleanedNotLost() {
        Map<String, Object> box = stack("x:shulker_box", "Slot", 0, "components", c("minecraft:container",
            l(c("slot", 0, "item", stack("x:totem")), c("slot", 1, "item", stack("x:hide")))));
        Map<String, Object> bundle = stack("x:bundle", "Slot", 1, "components", c("minecraft:bundle_contents", l(stack("x:totem"))));
        Map<String, Object> vessel = stack("x:small_vessel", "Slot", 2, "components", c("x:contents", l(stack("x:hide"), stack("x:totem"))));
        Map<String, Object> chest = c("id", "x:chest", "Items", l(box, bundle, vessel));
        assertEquals(3, PlacedContents.filter(chest, NODES, LATE, true));
        assertEquals(3, list(chest.get("Items")).size(), "the shulker box, the bundle and the vessel stay");
        assertEquals(List.of(c("slot", 1, "item", stack("x:hide"))), map(box.get("components")).get("minecraft:container"));
        assertEquals(List.of(), map(bundle.get("components")).get("minecraft:bundle_contents"));
        assertEquals(List.of(stack("x:hide")), map(vessel.get("components")).get("x:contents"));
    }

    /** TFC's small vessel keeps its four slots (ItemStack.OPTIONAL_CODEC.listOf(4, 4)); a removed stack becomes {}. */
    @Test
    void tfcVesselKeepsItsFourSlots() {
        Map<String, Object> contents = c("fluid", c("amount", 0, "content", c()), "items",
            l(stack("x:totem"), stack("x:hide"), c(), stack("x:hidden")));
        Map<String, Object> vessel = stack("tfc:ceramic/vessel", "Slot", 0, "components", c("tfc:vessel", contents));
        Map<String, Object> placed = c("id", "tfc:placed_item", "inventory", c("Size", 4, "Items", l(vessel)));
        assertEquals(2, PlacedContents.filter(placed, NODES, LATE, false), "dry run counts");
        assertEquals(2, PlacedContents.filter(placed, NODES, LATE, true));
        assertEquals(l(c(), stack("x:hide"), c(), c()), contents.get("items"), "four slots, the late ones empty");
        assertEquals(1, list(map(placed.get("inventory")).get("Items")).size(), "the vessel stays");
        assertTrue(PlacedContents.keepsSize("tfc:vessel", "items"));
        assertFalse(PlacedContents.keepsSize("minecraft:bundle_contents", "items"));
        assertFalse(PlacedContents.keepsSize("tfc:vessel", "fluid"));
    }

    @Test
    void deepNestingAndFieldStacks() {
        Map<String, Object> inner = stack("x:bundle", "components", c("minecraft:bundle_contents", l(stack("x:totem"), stack("x:hide"))));
        Map<String, Object> outer = stack("x:shulker_box", "Slot", 0, "components", c("minecraft:container", l(c("slot", 0, "item", inner))));
        Map<String, Object> vessel = c("id", "x:large_vessel", "inventory", c("Size", 9, "Items", l(outer)));
        assertEquals(1, PlacedContents.filter(vessel, NODES, LATE, true), "the totem two levels down");
        assertEquals(List.of(stack("x:hide")), map(inner.get("components")).get("minecraft:bundle_contents"));
        Map<String, Object> pedestal = c("id", "x:pedestal", "itemStack", stack("x:hidden"), "armorStack", c("id", "minecraft:air", "count", 0));
        assertEquals(1, PlacedContents.filter(pedestal, NODES, LATE, true));
        assertFalse(pedestal.containsKey("itemStack"), "a field stack goes with its field");
        assertTrue(pedestal.containsKey("armorStack"), "air stays");
    }

    @Test
    void legacyCountAndKeptStacks() {
        Map<String, Object> barrel = c("id", "x:barrel", "Items", l(c("Slot", 0, "id", "x:totem", "Count", 2), c("Slot", 1, "id", "x:hide", "Count", 1)));
        assertEquals(1, PlacedContents.filter(barrel, NODES, LATE, true));
        assertEquals(List.of(c("Slot", 1, "id", "x:hide", "Count", 1)), barrel.get("Items"));
        Map<String, Object> clean = c("id", "x:chest", "Items", l(stack("x:hide", "Slot", 0)));
        assertEquals(0, PlacedContents.filter(clean, NODES, LATE, true), "nothing to drop, nothing changes");
        assertEquals(0, PlacedContents.filter(clean, NODES, id -> false, true));
    }

    /** H1: a template mob's equipment (Ad Astra's Pygro with gold armor) keeps its slots; ArmorItems and HandItems are read by position. */
    @Test
    void entityEquipmentKeepsItsSlots() {
        Map<String, Object> pygro = c("id", "x:pygro", "ArmorItems", l(c(), stack("x:totem"), c(), stack("x:hidden")),
            "HandItems", l(stack("x:hide"), stack("x:totem")), "Inventory", l(stack("x:totem"), stack("x:hide")), "Health", 16);
        assertEquals(4, PlacedContents.filterEntity(pygro, NODES, LATE, false), "dry run counts");
        assertEquals(stack("x:totem"), list(pygro.get("ArmorItems")).get(1), "dry run changes nothing");
        assertEquals(4, PlacedContents.filterEntity(pygro, NODES, LATE, true));
        assertEquals(l(c(), c(), c(), c()), pygro.get("ArmorItems"), "four armor slots, the late ones empty");
        assertEquals(l(stack("x:hide"), c()), pygro.get("HandItems"), "main hand kept, the late offhand empty");
        assertEquals(l(stack("x:hide")), pygro.get("Inventory"), "an inventory list shrinks");
        assertFalse(PlacedContents.itemEntityEmpty(pygro, NODES), "a mob is no item entity");
        assertTrue(PlacedContents.keepsSize(null, "ArmorItems") && PlacedContents.keepsSize(null, "HandItems"));
    }

    /** H1: an item entity whose stack goes is not placed; an item frame stays, empty; a kept item entity stays. */
    @Test
    void itemEntitiesAndFrames() {
        Map<String, Object> ingot = c("id", "minecraft:item", "Item", stack("x:totem"), "Age", 4971);
        assertEquals(1, PlacedContents.filterEntity(ingot, NODES, LATE, true));
        assertTrue(PlacedContents.itemEntityEmpty(ingot, NODES), "the item entity has nothing left to place");
        Map<String, Object> hide = c("id", "minecraft:item", "Item", stack("x:hide"));
        assertEquals(0, PlacedContents.filterEntity(hide, NODES, LATE, true));
        assertFalse(PlacedContents.itemEntityEmpty(hide, NODES), "a kept item entity is placed");
        Map<String, Object> frame = c("id", "minecraft:item_frame", "Item", stack("x:hidden"), "Facing", 2);
        assertEquals(1, PlacedContents.filterEntity(frame, NODES, LATE, true));
        assertFalse(frame.containsKey("Item"), "the frame's item goes with its field");
        assertFalse(PlacedContents.itemEntityEmpty(frame, NODES), "the frame itself is placed");
    }

    /** H1: a chest minecart's and a donkey's inventory, nested contents; a villager's trades stay whole. */
    @Test
    void entityInventoriesAndTrades() {
        Map<String, Object> box = stack("x:shulker_box", "Slot", 1, "components", c("minecraft:container", l(c("slot", 0, "item", stack("x:totem")))));
        Map<String, Object> cart = c("id", "minecraft:chest_minecart", "Items", l(stack("x:totem", "Slot", 0), box));
        assertEquals(2, PlacedContents.filterEntity(cart, NODES, LATE, true), "the loose totem and the one in the box");
        assertEquals(l(box), cart.get("Items"));
        assertEquals(List.of(), map(box.get("components")).get("minecraft:container"));
        Map<String, Object> donkey = c("id", "minecraft:donkey", "ChestedHorse", 1, "SaddleItem", stack("x:hidden"),
            "Items", l(stack("x:hide", "Slot", 2), stack("x:totem", "Slot", 3)));
        assertEquals(2, PlacedContents.filterEntity(donkey, NODES, LATE, true));
        assertFalse(donkey.containsKey("SaddleItem"));
        assertEquals(l(stack("x:hide", "Slot", 2)), donkey.get("Items"));
        Map<String, Object> offer = c("buy", stack("x:hide"), "sell", stack("x:totem"), "maxUses", 12);
        Map<String, Object> villager = c("id", "minecraft:villager", "Offers", c("Recipes", l(offer)), "Inventory", l(stack("x:totem")));
        assertEquals(1, PlacedContents.filterEntity(villager, NODES, LATE, true), "only the inventory");
        assertEquals(stack("x:totem"), offer.get("sell"), "a trade keeps both stacks (PlacedContents.ENTITY_SKIP)");
        assertEquals(List.of(), villager.get("Inventory"));
    }

    /**
     * H2: the component patch of a rolled loot stack (the root AgeLoot walks): a shulker box's slot entries, a
     * bundle's list, TFC's positional lists (tfc:contents, the items of tfc:barrel) keep their size, a field stack goes
     * with its component.
     */
    @Test
    void rolledStackComponents() {
        Map<String, Object> barrel = c("items", l(stack("x:totem"), stack("x:hide")), "fluid", c("amount", 0), "sealedTick", 0);
        Map<String, Object> patch = c(
            "minecraft:container", l(c("slot", 0, "item", stack("x:totem")), c("slot", 1, "item", stack("x:hide"))),
            "minecraft:bundle_contents", l(stack("x:hidden"), stack("x:hide")),
            "tfc:contents", l(stack("x:totem"), stack("x:hide"), c()),
            "tfc:barrel", barrel,
            "x:held", stack("x:hidden"),
            "!minecraft:food", c());
        assertEquals(5, PlacedContents.filter(patch, NODES, LATE, false), "dry run counts");
        assertEquals(5, PlacedContents.filter(patch, NODES, LATE, true));
        assertEquals(l(c("slot", 1, "item", stack("x:hide"))), patch.get("minecraft:container"));
        assertEquals(l(stack("x:hide")), patch.get("minecraft:bundle_contents"), "a bundle's list shrinks (ItemStack.CODEC, no empty entries)");
        assertEquals(l(c(), stack("x:hide"), c()), patch.get("tfc:contents"), "tfc:contents keeps its slots");
        assertEquals(l(c(), stack("x:hide")), barrel.get("items"), "tfc:barrel keeps its slots");
        assertFalse(patch.containsKey("x:held"), "a field stack goes with its component");
        assertTrue(patch.containsKey("!minecraft:food"), "a removed-component marker is no stack");
        assertTrue(PlacedContents.keepsSize("tfc:crucible", "items") && PlacedContents.keepsSize("components", "tfc:contents"));
        assertFalse(PlacedContents.keepsSize("x:other", "items"));
    }
}
