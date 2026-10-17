package dev.firmages.core.immersion;

import com.google.gson.JsonParser;
import dev.firmages.core.age.AgeId;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §18.10: keys and screens by mod Age (Felix 2026-10-17, round G2). */
class KeyGateTest {
    private static final KeyGate.Table SMALL = KeyGate.parse(JsonParser.parseString("""
        {"mods": {"psi": "age_6", "mekanism": "age_6", "mekanismgenerators": "age_7", "create": "age_1", "odd": "age_x"},
         "keys": {"psimisc.keybind": "psi", "key.mekanism.mode": "mekanism", "key.odd.x": "odd", "key.ghost.x": "ghost"},
         "packages": {"vazkii.psi": "psi", "mekanism": "mekanism", "mekanism.generators": "mekanismgenerators",
                      "com.simibubi.create": "create", "": "create"}}"""));

    private static KeyGate.Table shipped() throws IOException {
        return KeyGate.parse(JsonParser.parseString(
            Files.readString(Path.of("../..").resolve("kubejs/assets/firmages/key_gates.json"), StandardCharsets.UTF_8)));
    }

    @Test
    void lockedBeforeTheModsAgeOpenFromItAndInCreative() {
        for (AgeId age : AgeId.all()) {
            boolean info = age.ordinal() >= AgeId.AGE_6.ordinal();
            assertEquals(!info, KeyGate.keyLocked(SMALL, "psimisc.keybind", age, false), "Psi's C in survival, " + age);
            assertFalse(KeyGate.keyLocked(SMALL, "psimisc.keybind", age, true), "creative, " + age);
            assertEquals(!info, KeyGate.screenLocked(SMALL, "vazkii.psi.client.gui.GuiSocketSelect", age, false), "Psi's screen, " + age);
        }
        assertFalse(KeyGate.locked(Optional.empty(), AgeId.DAWN, false), "a mod without an Age is never locked");
    }

    @Test
    void neverOpenIsLockedInEveryAgeExceptCreative() {
        KeyGate.Table t = KeyGate.parse(JsonParser.parseString("""
            {"mods": {"progressivestages": "never", "psi": "age_6"},
             "keys": {"key.progressivestages.open_tree": "progressivestages", "psimisc.keybind": "psi"},
             "packages": {"com.enviouse.progressivestages": "progressivestages"}}"""));
        assertEquals(java.util.Set.of("progressivestages"), t.never());
        assertEquals(1, t.mods().size(), "never is no Age");
        for (AgeId age : AgeId.all()) {
            assertTrue(KeyGate.keyLocked(t, "key.progressivestages.open_tree", age, false), "stage tree key in survival, " + age);
            assertFalse(KeyGate.keyLocked(t, "key.progressivestages.open_tree", age, true), "creative, " + age);
            assertTrue(KeyGate.screenLocked(t, "com.enviouse.progressivestages.client.gui.StageTreeScreen", age, false), "stage tree, " + age);
            assertFalse(KeyGate.screenLocked(t, "com.enviouse.progressivestages.client.gui.StageTreeScreen", age, true), "creative, " + age);
        }
        assertFalse(KeyGate.keyLocked(t, "psimisc.keybind", AgeId.AGE_6, false), "the Age mods as before");
    }

    @Test
    void parseSkipsUnknownAgesModsAndBlankPackages() {
        assertEquals(4, SMALL.mods().size(), "age_x dropped");
        assertEquals(List.of("key.mekanism.mode", "psimisc.keybind").stream().sorted().toList(), SMALL.keys().keySet().stream().sorted().toList(),
            "keys of an unknown Age or an unlisted mod dropped");
        assertFalse(SMALL.packages().containsKey(""));
        assertEquals(KeyGate.Table.EMPTY, KeyGate.parse(JsonParser.parseString("[]")));
        assertEquals(KeyGate.Table.EMPTY, KeyGate.parse(null));
        assertFalse(KeyGate.keyLocked(KeyGate.Table.EMPTY, "psimisc.keybind", AgeId.DAWN, false), "no table: nothing locked");
    }

    @Test
    void classesByLongestPackageOnWholeParts() {
        assertEquals(Optional.of("mekanism"), SMALL.modOfClass("mekanism.client.gui.GuiModuleTweaker"));
        assertEquals(Optional.of("mekanismgenerators"), SMALL.modOfClass("mekanism.generators.client.gui.GuiFissionReactor"));
        assertEquals(Optional.of("create"), SMALL.modOfClass("com.simibubi.create.content.equipment.toolbox.RadialToolboxMenu"));
        assertEquals(Optional.empty(), SMALL.modOfClass("com.simibubi.createaddition.Foo"), "a longer package name is another mod");
        assertEquals(Optional.empty(), SMALL.modOfClass("mekanismx.Foo"));
        assertEquals(Optional.empty(), SMALL.modOfClass("net.minecraft.client.gui.screens.inventory.InventoryScreen"));
        assertEquals(Optional.empty(), SMALL.modOfClass("Unpackaged"));
        assertEquals(Optional.empty(), SMALL.modOfClass(null));
        assertTrue(KeyGate.screenLocked(SMALL, "mekanism.generators.client.gui.GuiFissionReactor", AgeId.AGE_6, false), "generators: age_7");
        assertFalse(KeyGate.screenLocked(SMALL, "mekanism.client.gui.GuiModuleTweaker", AgeId.AGE_6, false));
    }

    /** The shipped pack file (dev/gen_key_gates.py from the 1.0.2 client jars) holds the examples of the round. */
    @Test
    void shippedTableHoldsTheExamples() throws IOException {
        KeyGate.Table t = shipped();
        record Key(String name, String mod, AgeId age) {}
        for (Key k : List.of(new Key("psimisc.keybind", "psi", AgeId.AGE_6), new Key("key.ars_nouveau.open_book", "ars_nouveau", AgeId.AGE_3),
            new Key("key.ponder.ponder", "ponder", AgeId.AGE_1), new Key("create.keyinfo.toolbelt", "create", AgeId.AGE_1),
            new Key("key.mekanism.module_tweaker", "mekanism", AgeId.AGE_6), new Key("key.ad_astra.open_radio", "ad_astra", AgeId.AGE_7),
            new Key("key.jetpack.toggle_active.description", "flightlib", AgeId.AGE_4),
            new Key("key.draconicevolution.tool_modules", "draconicevolution", AgeId.AGE_7),
            new Key("key.occultism.backpack", "occultism", AgeId.AGE_3), new Key("key.theurgy.change_item_mode", "theurgy", AgeId.AGE_3),
            new Key("supplementaries.keybind.quiver", "supplementaries", AgeId.AGE_1))) {
            assertEquals(Optional.of(k.mod()), t.modOfKey(k.name()), k.name());
            assertEquals(Optional.of(k.age()), t.modOfKey(k.name()).flatMap(t::ageOf), k.name());
        }
        // ProgressiveStages' stage tree (key unbound by default, also /stage gui): no Age opens it, creative has it
        assertEquals(Optional.of("progressivestages"), t.modOfKey("key.progressivestages.open_tree"));
        assertTrue(KeyGate.keyLocked(t, "key.progressivestages.open_tree", AgeId.AGE_9, false));
        assertTrue(KeyGate.screenLocked(t, "com.enviouse.progressivestages.client.gui.StageTreeScreen", AgeId.AGE_9, false));
        assertFalse(KeyGate.screenLocked(t, "com.enviouse.progressivestages.client.gui.StageTreeScreen", AgeId.DAWN, true));
        for (String[] s : new String[][] {{"vazkii.psi.client.gui.GuiSocketSelect", "age_6"},
            {"com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook", "age_3"}, {"net.createmod.ponder.foundation.ui.PonderUI", "age_1"},
            {"com.simibubi.create.content.equipment.toolbox.RadialToolboxMenu", "age_1"}, {"mekanism.client.gui.GuiModuleTweaker", "age_6"},
            {"earth.terrarium.adastra.client.screens.PlanetsScreen", "age_7"}, {"net.povstalec.sgjourney.client.screens.AutoDialerScreen", "age_9"},
            {"com.brandon3055.draconicevolution.client.gui.modular.itemconfig.ConfigurableItemGui", "age_7"},
            {"com.klikli_dev.occultism.client.gui.storage.StorageRemoteGui", "age_3"},
            {"dev.shadowsoffire.apotheosis.client.WorldTierSelectScreen", "age_3"}}) {
            assertEquals(AgeId.byId(s[1]), t.modOfClass(s[0]).flatMap(t::ageOf), s[0]);
            assertTrue(KeyGate.screenLocked(t, s[0], AgeId.DAWN, false), s[0] + " in Dawn");
            assertFalse(KeyGate.screenLocked(t, s[0], AgeId.DAWN, true), s[0] + " in creative");
        }
        // never locked: vanilla, Dawn mods, the exempt mods (own gates, the Chronicle, items, client tools)
        for (String key : List.of("key.forward", "key.inventory", "tfc.key.place_block", "key.firmages.show_age", "key.ftbquests.quests",
            "key.ftbchunks.map", "key.journeymap.map_toggle_alt", "key.jade.show_overlay", "key.curios.open.desc", "key.jei.showRecipe",
            "key.sophisticatedbackpacks.open_backpack", "key.immersiveengineering.magnetEquip", "key.exposure.camera_controls",
            "iris.keybind.shaderPackSelection", "key.emotecraft.fastchoose")) {
            assertEquals(Optional.empty(), t.modOfKey(key), key);
        }
        for (String mod : List.of("tfc", "firmages", "jade", "emi", "jei", "journeymap", "curios", "ftbquests", "ftbchunks",
            "sophisticatedbackpacks", "sophisticatedcore", "betterf3", "exposure", "immersiveengineering", "iris", "sodium", "controlling")) {
            assertFalse(t.mods().containsKey(mod), mod);
        }
        for (String cls : List.of("net.dries007.tfc.client.screen.KnappingScreen", "dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen",
            "journeymap.client.ui.fullscreen.Fullscreen", "snownee.jade.gui.WailaConfigScreen", "dev.emi.emi.screen.RecipeScreen",
            "net.minecraft.client.gui.screens.inventory.InventoryScreen", "vazkii.patchouli.client.book.gui.GuiBookLanding")) {
            assertFalse(KeyGate.screenLocked(t, cls, AgeId.DAWN, false), cls);
        }
    }
}
