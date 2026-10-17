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
        Map<String, Object> vessel = stack("x:small_vessel", "Slot", 2, "components", c("tfc:contents", l(stack("x:hide"), stack("x:totem"))));
        Map<String, Object> chest = c("id", "x:chest", "Items", l(box, bundle, vessel));
        assertEquals(3, PlacedContents.filter(chest, NODES, LATE, true));
        assertEquals(3, list(chest.get("Items")).size(), "the shulker box, the bundle and the vessel stay");
        assertEquals(List.of(c("slot", 1, "item", stack("x:hide"))), map(box.get("components")).get("minecraft:container"));
        assertEquals(List.of(), map(bundle.get("components")).get("minecraft:bundle_contents"));
        assertEquals(List.of(stack("x:hide")), map(vessel.get("components")).get("tfc:contents"));
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
}
