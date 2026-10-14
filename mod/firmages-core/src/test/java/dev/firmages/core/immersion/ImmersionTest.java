package dev.firmages.core.immersion;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeSnapshot;
import dev.firmages.core.compat.tfc.NutritionFloor;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §18: the pure decisions behind the immersion features (Age status, EMI gate, TFC tabs, HUD fade, chat, nutrition). */
class ImmersionTest {
    private static final Map<Integer, String> OFFERINGS = Map.of(0, "firmages:hearthstone", 1, "firmages:sky_disc", 8, "firmages:quantum_core");
    private static final IntFunction<Optional<String>> OFFERING = t -> Optional.ofNullable(OFFERINGS.get(t));
    private static final Function<InventoryTab, Optional<AgeId>> NO_TABS = t -> Optional.empty();
    private static final Function<InventoryTab, Optional<AgeId>> DEFAULT_TABS = t -> t.fromAge(t.defaultValue());
    private static final Optional<AgeId> DWELLER_UNTIL = Optional.of(AgeId.AGE_2);
    private static final Optional<AgeId> TOOL_UNTIL = Optional.of(AgeId.AGE_0);

    private static AgeSnapshot ages(AgeId... ids) {
        return AgeSnapshot.of(List.of(ids), 1, 0);
    }

    @Test
    void reloadPendingIsSetByTheServerOnly() {
        AgeStatus s = AgeStatus.of(ages(AgeId.AGE_0, AgeId.AGE_1), OFFERING, AgeId.AGE_1, AgeId.AGE_1, NO_TABS, DWELLER_UNTIL, TOOL_UNTIL);
        assertFalse(s.reloadPending(), "of() knows nothing of the scheduler");
        AgeStatus pending = s.withReloadPending(true);
        assertTrue(pending.reloadPending());
        assertEquals(s, pending.withReloadPending(false), "every other field is kept");
        assertFalse(AgeStatus.UNKNOWN.reloadPending(), "before the first status ProgressiveStages reloads EMI as usual");
    }

    @Test
    void statusAtDawnHidesEverything() {
        AgeStatus s = AgeStatus.of(AgeSnapshot.DAWN_ONLY, OFFERING, AgeId.AGE_0, AgeId.AGE_0, NO_TABS, DWELLER_UNTIL, TOOL_UNTIL);
        assertEquals(AgeId.DAWN, s.age());
        assertEquals("", s.goalItem(), "Dawn's goal is the First Spark quest, no item");
        assertFalse(s.recipeViewer());
        assertFalse(s.blockInfo());
    }

    @Test
    void statusOpensViewerAndOverlayAtTheirAge() {
        AgeStatus stone = AgeStatus.of(ages(AgeId.AGE_0), OFFERING, AgeId.AGE_0, AgeId.AGE_1, NO_TABS, DWELLER_UNTIL, TOOL_UNTIL);
        assertEquals(AgeId.AGE_0, stone.age());
        assertEquals("firmages:hearthstone", stone.goalItem());
        assertTrue(stone.recipeViewer());
        assertFalse(stone.blockInfo(), "Jade threshold age_1 not reached in age_0");

        AgeStatus bronze = AgeStatus.of(ages(AgeId.AGE_0, AgeId.AGE_1), OFFERING, AgeId.AGE_0, AgeId.AGE_1, NO_TABS, DWELLER_UNTIL, TOOL_UNTIL);
        assertEquals(AgeId.AGE_1, bronze.age());
        assertEquals("firmages:sky_disc", bronze.goalItem());
        assertTrue(bronze.blockInfo());
    }

    @Test
    void statusUsesTheHighestAgeAndHasNoGoalInTheLastAge() {
        // a gap (age_3 granted by an admin without age_2) still counts the highest Age
        assertEquals(AgeId.AGE_3, AgeStatus.current(ages(AgeId.AGE_0, AgeId.AGE_3)));
        assertEquals("firmages:quantum_core", AgeStatus.of(ages(AgeId.AGE_8), OFFERING, AgeId.AGE_0, AgeId.AGE_0, NO_TABS, DWELLER_UNTIL, TOOL_UNTIL).goalItem());
        assertEquals("", AgeStatus.of(ages(AgeId.AGE_9), OFFERING, AgeId.AGE_0, AgeId.AGE_0, NO_TABS, DWELLER_UNTIL, TOOL_UNTIL).goalItem());
        AgeStatus sing = AgeStatus.of(ages(AgeId.AGE_9), OFFERING, AgeId.AGE_0, AgeId.AGE_0, NO_TABS, DWELLER_UNTIL, TOOL_UNTIL);
        assertEquals("firmages.hud.goal.age_9", sing.goalKey(), "before the Gathering: Goal: the Gathering");
        assertEquals("firmages.hud.goal.age_9_gathered", sing.withGathered(true).goalKey(), "after it: Goal: Beyond the Firmament");
        assertEquals("firmages.hud.goal.dawn", AgeStatus.of(AgeSnapshot.DAWN_ONLY, OFFERING, AgeId.AGE_0, AgeId.AGE_0, NO_TABS, DWELLER_UNTIL, TOOL_UNTIL).goalKey());
        assertEquals("", AgeStatus.of(ages(AgeId.AGE_2), OFFERING, AgeId.AGE_0, AgeId.AGE_0, NO_TABS, DWELLER_UNTIL, TOOL_UNTIL).goalItem(), "missing offering -> no item");
    }

    @Test
    void parseFallsBack() {
        assertEquals(AgeId.AGE_4, AgeStatus.parse("progressivestages:age_4", AgeId.AGE_0));
        assertEquals(AgeId.AGE_0, AgeStatus.parse("stone", AgeId.AGE_0));
        assertTrue(AgeStatus.reached(AgeId.AGE_0, AgeId.AGE_0));
        assertFalse(AgeStatus.reached(AgeId.DAWN, AgeId.AGE_0));
    }

    @Test
    void tabDefaults() {
        // Felix 2026-10-13: calendar from the Stone Age, climate from the Iron Age, nutrition and Field Guide never
        assertEquals("age_0", InventoryTab.CALENDAR.defaultValue());
        assertEquals("age_2", InventoryTab.CLIMATE.defaultValue());
        assertEquals("never", InventoryTab.NUTRITION.defaultValue());
        assertEquals("never", InventoryTab.FIELD_GUIDE.defaultValue());
        assertEquals(List.of("calendarTabFromAge", "climateTabFromAge", "nutritionTabFromAge", "fieldGuideTabFromAge"),
            List.of(InventoryTab.values()).stream().map(InventoryTab::configKey).toList());
        for (InventoryTab t : InventoryTab.values()) assertTrue(InventoryTab.isValid(t.defaultValue()), t + " default is valid");
    }

    @Test
    void tabConfigParsing() {
        assertEquals(Optional.of(AgeId.AGE_4), InventoryTab.CALENDAR.fromAge("age_4"));
        assertEquals(Optional.of(AgeId.AGE_4), InventoryTab.CALENDAR.fromAge("progressivestages:age_4"));
        assertEquals(Optional.of(AgeId.DAWN), InventoryTab.NUTRITION.fromAge("dawn"));
        assertEquals(Optional.empty(), InventoryTab.CALENDAR.fromAge("never"));
        assertEquals(Optional.empty(), InventoryTab.CALENDAR.fromAge(" Never "));
        // an unknown value counts as the tab's default
        assertEquals(Optional.of(AgeId.AGE_0), InventoryTab.CALENDAR.fromAge("stone"));
        assertEquals(Optional.of(AgeId.AGE_2), InventoryTab.CLIMATE.fromAge(null));
        assertEquals(Optional.empty(), InventoryTab.FIELD_GUIDE.fromAge("book"));
        assertTrue(InventoryTab.isValid("never"));
        assertTrue(InventoryTab.isValid("age_9"));
        assertFalse(InventoryTab.isValid("stone"));
        assertFalse(InventoryTab.isValid(""));
        assertFalse(InventoryTab.isValid(2));
    }

    @Test
    void tabsOpenByAgeWithTheDefaults() {
        for (AgeId age : AgeId.all()) {
            Set<InventoryTab> expected = EnumSet.noneOf(InventoryTab.class);
            if (age.ordinal() >= AgeId.AGE_0.ordinal()) expected.add(InventoryTab.CALENDAR);
            if (age.ordinal() >= AgeId.AGE_2.ordinal()) expected.add(InventoryTab.CLIMATE);
            assertEquals(expected, InventoryTab.open(age, DEFAULT_TABS), "tabs in " + age);
        }
        AgeStatus dawn = AgeStatus.of(AgeSnapshot.DAWN_ONLY, OFFERING, AgeId.AGE_1, AgeId.AGE_0, DEFAULT_TABS, DWELLER_UNTIL, TOOL_UNTIL);
        assertEquals(Set.of(), dawn.tabs(), "Dawn: only the Inventory tab");
        AgeStatus iron = AgeStatus.of(ages(AgeId.AGE_0, AgeId.AGE_1, AgeId.AGE_2), OFFERING, AgeId.AGE_1, AgeId.AGE_0, DEFAULT_TABS, DWELLER_UNTIL, TOOL_UNTIL);
        assertEquals(Set.of(InventoryTab.CALENDAR, InventoryTab.CLIMATE), iron.tabs());
        assertEquals(AgeStatus.UNKNOWN.tabs(), Set.of(), "before the first status no tab opens");
        // all four open from Dawn when configured so
        assertEquals(EnumSet.allOf(InventoryTab.class), InventoryTab.open(AgeId.DAWN, t -> Optional.of(AgeId.DAWN)));
    }

    @Test
    void tabMaskRoundTrip() {
        for (int mask = 0; mask < 16; mask++) assertEquals(mask, InventoryTab.mask(InventoryTab.fromMask(mask)));
        assertEquals(Set.of(InventoryTab.CALENDAR, InventoryTab.CLIMATE), InventoryTab.fromMask(InventoryTab.mask(EnumSet.of(InventoryTab.CALENDAR, InventoryTab.CLIMATE))));
        assertEquals(Set.of(), InventoryTab.fromMask(0));
    }

    @Test
    void viewerGateForcesOffWhileLockedAndSwitchesOnOnce() {
        // locked: always off, also after the player toggled EMI on
        assertEquals(Boolean.FALSE, ViewerGate.target(false, null, true));
        assertEquals(Boolean.FALSE, ViewerGate.target(false, false, true));
        assertNull(ViewerGate.target(false, false, false));
        // first status after joining an open world: on
        assertEquals(Boolean.TRUE, ViewerGate.target(true, null, false));
        // the Age opens it: on once
        assertEquals(Boolean.TRUE, ViewerGate.target(true, false, false));
        // later the player's own toggle stays
        assertNull(ViewerGate.target(true, true, false));
        assertNull(ViewerGate.target(true, true, true));
    }

    @Test
    void viewerOpensForOperatorsAndCreativeInEveryAge() {
        assertFalse(ViewerGate.open(false, false, false)); // survival player in Dawn or the Stone Age
        assertTrue(ViewerGate.open(false, true, false));   // operator in survival
        assertTrue(ViewerGate.open(false, false, true));   // creative
        assertTrue(ViewerGate.open(true, false, false));   // the Age opened it
    }

    @Test
    void nutritionKeys() {
        assertTrue(ViewerGate.isNutritionKey("tfc.tooltip.nutrition"));
        assertTrue(ViewerGate.isNutritionKey("tfc.tooltip.nutrition_water"));
        assertTrue(ViewerGate.isNutritionKey("tfc.tooltip.hold_shift_for_nutrition_info"));
        assertTrue(ViewerGate.isNutritionKey("tfc.enum.nutrient.grain"));
        assertFalse(ViewerGate.isNutritionKey("tfc.tooltip.food_expiry_date"), "decay stays visible");
        assertFalse(ViewerGate.isNutritionKey("tfc.tooltip.food_trait.salted"));
    }

    @Test
    void hudFades() {
        long six = 6000;
        long t0 = 10_000;
        assertEquals(1f, HudFade.alpha(HudFade.Mode.FADE, t0, t0, six, false, Long.MIN_VALUE));
        assertEquals(1f, HudFade.alpha(HudFade.Mode.FADE, t0 + 4999, t0, six, false, Long.MIN_VALUE));
        assertEquals(0.5f, HudFade.alpha(HudFade.Mode.FADE, t0 + 5500, t0, six, false, Long.MIN_VALUE), 1e-4);
        assertEquals(0f, HudFade.alpha(HudFade.Mode.FADE, t0 + 6000, t0, six, false, Long.MIN_VALUE));
        assertEquals(0f, HudFade.alpha(HudFade.Mode.FADE, t0, Long.MIN_VALUE, six, false, Long.MIN_VALUE), "never triggered");
        // key: full while held, then a 1 s fade
        assertEquals(1f, HudFade.alpha(HudFade.Mode.FADE, t0 + 60_000, t0, six, true, Long.MIN_VALUE));
        long rel = t0 + 60_000;
        assertEquals(0.75f, HudFade.alpha(HudFade.Mode.FADE, rel + 250, t0, six, false, rel), 1e-4);
        assertEquals(0f, HudFade.alpha(HudFade.Mode.FADE, rel + 1000, t0, six, false, rel));
        // modes
        assertEquals(1f, HudFade.alpha(HudFade.Mode.ALWAYS, t0 + 99_999, t0, six, false, Long.MIN_VALUE));
        assertEquals(0f, HudFade.alpha(HudFade.Mode.NEVER, t0, t0, six, true, Long.MIN_VALUE));
        // a show time shorter than the fade fades from the start
        assertEquals(0.5f, HudFade.alpha(HudFade.Mode.FADE, t0 + 500, t0, 500, false, Long.MIN_VALUE), 1e-4);
    }

    @Test
    void chatBlocklistShipsTheJoinNotices() {
        ChatBlocklist list = ChatBlocklist.compile(ChatBlocklist.DEFAULT_PATTERNS);
        assertTrue(list.errors().isEmpty());
        // Create Train Utilities 3.1.0, the literal from net.adeptstack.Main [verified, javap]
        assertTrue(list.blocks("ATTENTION (Create Train Utilities): \nCreate Train Utilities will be renamed Create Train Doors in version 4."));
        assertTrue(list.blocks("Click here to order a Starbuncle Plush from Makeship!"));
        assertFalse(list.blocks("<Felix> the train utilities are great"));
        assertFalse(list.blocks("Caelum: A spark in the dark. I have seen you."));
        assertFalse(list.blocks(""));
    }

    @Test
    void chatBlocklistSkipsInvalidPatterns() {
        ChatBlocklist list = ChatBlocklist.compile(List.of("([unclosed", "", "Backup (started|completed)"));
        assertEquals(1, list.errors().size());
        assertTrue(list.blocks("Backup started..."));
        assertEquals(List.of("([unclosed", "", "Backup (started|completed)"), list.source());
    }

    @Test
    void nutritionOnlyAsBonus() {
        float base = 0.85f; // TFC 4.2.11 nutritionDefaultHealthModifier default
        assertEquals(0.85f, NutritionFloor.apply(0.2f, base, true), "a one-sided diet keeps the starting health");
        assertEquals(2.1f, NutritionFloor.apply(2.1f, base, true), "a varied diet still adds health");
        assertEquals(0.2f, NutritionFloor.apply(0.2f, base, false), "off: TFC as shipped");
        assertEquals(0.5f, NutritionFloor.apply(0.5f, 0f, true), "no usable baseline: unchanged");
    }
}
