package dev.firmages.core.immersion;

import com.google.gson.JsonParser;
import dev.firmages.core.age.AgeId;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §21: what EMI and Ponder hide at the player's Ages, and the generated namespace map. */
class EmiAgeViewTest {
    private static final Map<String, AgeId> NS = Map.of("minecraft", AgeId.DAWN, "tfc", AgeId.DAWN, "create", AgeId.AGE_1,
        "mekanism", AgeId.AGE_6, "ad_astra", AgeId.AGE_7, "draconicevolution", AgeId.AGE_4, "sgjourney", AgeId.AGE_9);

    // Items and fluids are opaque keys; the view compares them by identity, so each test uses these instances.
    private static final String COGWHEEL = new String("create:cogwheel");
    private static final String OSMIUM = new String("mekanism:ingot_osmium");
    private static final String DUST = new String("mekanism:dust_iron"); // tagged earlier than its mod (age_2)
    private static final String BARRIER = new String("minecraft:barrier"); // disabled
    private static final String UNTAGGED = new String("ad_astra:new_item"); // no Age tag: its namespace decides
    private static final String STICK = new String("minecraft:stick");
    private static final List<String> ITEMS = List.of(COGWHEEL, OSMIUM, DUST, BARRIER, UNTAGGED, STICK);
    private static final Map<String, String> ITEM_TAGS = Map.of(COGWHEEL, "age_1", OSMIUM, "age_6", DUST, "age_2", BARRIER, "disabled",
        STICK, "dawn");

    private static final String WATER = new String("minecraft:water");
    private static final String FUEL = new String("ad_astra:fuel");
    private static final String TFC_BRONZE = new String("tfc:metal/bronze"); // a dawn namespace, an age_1 metal
    private static final String MEK_FLUID = new String("mekanism:hydrogen"); // untagged
    private static final List<String> FLUIDS = List.of(WATER, FUEL, TFC_BRONZE, MEK_FLUID);
    private static final Map<String, String> FLUID_TAGS = Map.of(WATER, "dawn", FUEL, "age_7", TFC_BRONZE, "age_1");

    private static EmiAgeView view(String... stages) {
        return EmiAgeView.build(List.of(stages), NS, ITEMS, s -> s, ITEM_TAGS::get, FLUIDS, s -> s, FLUID_TAGS::get);
    }

    @Test
    void unlockedAgesFailClosed() {
        assertEquals(EnumSet.of(AgeId.DAWN), EmiAgeView.unlocked(List.of()), "no stages: Dawn only");
        assertEquals(EnumSet.of(AgeId.DAWN), EmiAgeView.unlocked(null));
        assertEquals(EnumSet.of(AgeId.DAWN, AgeId.AGE_0, AgeId.AGE_1),
            EmiAgeView.unlocked(List.of("progressivestages:age_0", "age_1", "mob_1", "tool_ie_capacitor")), "other stages are ignored");
        assertEquals(AgeId.AGE_1, view("age_0", "age_1").age());
    }

    @Test
    void itemsFollowTheirAgeTag() {
        EmiAgeView bronze = view("age_0", "age_1");
        assertFalse(bronze.itemLocked(COGWHEEL));
        assertTrue(bronze.itemLocked(OSMIUM));
        assertTrue(bronze.itemLocked(DUST), "the age_2 tag decides, not the age_6 mod");
        assertFalse(bronze.itemLocked(STICK));
        assertTrue(bronze.itemLocked(BARRIER), "disabled is locked at every Age");
        assertTrue(bronze.itemLocked(UNTAGGED), "untagged: the namespace Age (age_7)");
        EmiAgeView iron = view("age_0", "age_1", "age_2");
        assertFalse(iron.itemLocked(DUST));
        assertTrue(iron.itemLocked(OSMIUM));
        EmiAgeView all = view("age_0", "age_1", "age_2", "age_3", "age_4", "age_5", "age_6", "age_7", "age_8", "age_9");
        assertFalse(all.itemLocked(OSMIUM));
        assertFalse(all.itemLocked(UNTAGGED));
        assertTrue(all.itemLocked(BARRIER));
        assertEquals(1, all.lockedItemCount());
    }

    @Test
    void fluidsFollowTheirAgeTagElseTheirMod() {
        EmiAgeView dawn = view();
        assertFalse(dawn.fluidLocked(WATER));
        assertTrue(dawn.fluidLocked(TFC_BRONZE), "the metal's Age, although tfc is a Dawn namespace");
        assertTrue(dawn.fluidLocked(FUEL));
        assertTrue(dawn.fluidLocked(MEK_FLUID), "untagged: mekanism is age_6");
        EmiAgeView bronze = view("age_0", "age_1");
        assertFalse(bronze.fluidLocked(TFC_BRONZE));
        assertTrue(bronze.fluidLocked(FUEL));
        assertEquals(2, bronze.lockedFluidCount());
    }

    @Test
    void namespacesCategoriesAndLoot() {
        EmiAgeView bronze = view("age_0", "age_1");
        assertTrue(bronze.namespaceLocked("mekanism"), "chemicals and Mekanism's client-built categories");
        assertFalse(bronze.namespaceLocked("create"));
        assertFalse(bronze.namespaceLocked("emi"), "unknown namespaces are never locked");
        assertFalse(bronze.namespaceLocked(null));
        assertTrue(bronze.idLocked("ad_astra:astrodux"), "a Patchouli book id of a locked mod");
        assertFalse(bronze.idLocked("tfc:field_guide"));
        assertFalse(bronze.idLocked("stick"), "no namespace means minecraft");
        assertTrue(bronze.categoryLocked("mekanism:boiler"));
        assertFalse(bronze.categoryLocked("minecraft:crafting"));
        assertTrue(bronze.recipeLocked("mekanism:sps", "mekanism:sps/antimatter"));
        assertTrue(bronze.recipeLocked("emi_loot:mob_drops", "emi_loot:/mob_drops/draconicevolution/entities/draconic_guardian"), "a later mod's mob");
        assertFalse(bronze.recipeLocked("emi_loot:mob_drops", "emi_loot:/mob_drops/minecraft/entities/zombie"));
        assertFalse(bronze.recipeLocked("emi_loot:chest_loot", null));
        assertFalse(bronze.recipeLocked("create:mixing", "mekanism:compat/create/mixing"), "only the category's mod counts");
        assertEquals(Set.of("mekanism", "ad_astra", "draconicevolution", "sgjourney"), bronze.lockedNamespaces());
        assertFalse(view("age_0", "age_1", "age_2", "age_3", "age_4").recipeLocked("emi_loot:mob_drops",
            "emi_loot:/mob_drops/draconicevolution/entities/draconic_guardian"));
    }

    @Test
    void lootNamespace() {
        assertEquals("draconicevolution", EmiAgeView.lootNamespace("emi_loot:/mob_drops/draconicevolution/entities/draconic_guardian"));
        assertEquals("minecraft", EmiAgeView.lootNamespace("emi_loot:/chest_loot/minecraft/chests/simple_dungeon"));
        assertEquals(null, EmiAgeView.lootNamespace("emi_loot:/odd"));
        assertEquals(null, EmiAgeView.lootNamespace("minecraft:crafting/stick"));
    }

    @Test
    void openViewLocksNothing() {
        assertFalse(EmiAgeView.OPEN.itemLocked(OSMIUM));
        assertFalse(EmiAgeView.OPEN.namespaceLocked("sgjourney"));
        assertEquals(AgeId.AGE_9, EmiAgeView.OPEN.age());
    }

    @Test
    void parseNamespacesSkipsUnknownAges() {
        Map<String, AgeId> m = EmiAgeView.parseNamespaces(JsonParser.parseString(
            "{\"_comment\": \"x\", \"namespaces\": {\"create\": \"age_1\", \"odd\": \"age_42\", \"tfc\": \"dawn\"}}"));
        assertEquals(Map.of("create", AgeId.AGE_1, "tfc", AgeId.DAWN), m);
        assertTrue(EmiAgeView.parseNamespaces(JsonParser.parseString("[]")).isEmpty());
        assertTrue(EmiAgeView.parseNamespaces(null).isEmpty());
    }

    /** The shipped kubejs/assets/firmages/age_namespaces.json equals dev/age_map.toml [mods] (gen_stage_locks.py ran). */
    @Test
    void generatedNamespaceMapMatchesTheAgeMap() throws IOException {
        Path pack = Path.of("../..");
        Map<String, AgeId> shipped = EmiAgeView.parseNamespaces(JsonParser.parseString(
            Files.readString(pack.resolve("kubejs/assets/firmages/age_namespaces.json"), StandardCharsets.UTF_8)));
        Map<String, AgeId> source = new LinkedHashMap<>();
        String toml = Files.readString(pack.resolve("dev/age_map.toml"), StandardCharsets.UTF_8);
        int start = toml.indexOf("\n[mods]\n");
        int end = toml.indexOf("\n[", start + 1);
        Matcher m = Pattern.compile("(?m)^([a-z0-9_.-]+)\\s*=\\s*\"([a-z0-9_]+)\"").matcher(toml.substring(start, end));
        while (m.find()) source.put(m.group(1), AgeId.byId(m.group(2)).orElseThrow());
        assertTrue(source.size() > 100, "parsed [mods]: " + source.size());
        assertEquals(source, shipped, "run python dev/gen_stage_locks.py");
        assertEquals(AgeId.AGE_6, shipped.get("mekanism"));
        assertEquals(AgeId.DAWN, shipped.get("tfc"));
        assertEquals(AgeId.AGE_9, shipped.get("sgjourney"));
    }
}
