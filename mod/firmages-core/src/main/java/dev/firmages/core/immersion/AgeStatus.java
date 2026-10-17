package dev.firmages.core.immersion;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeSnapshot;
import dev.firmages.core.hardship.CaveDwellerGate;
import dev.firmages.core.hardship.GridLock;
import dev.firmages.core.hardship.ToolRule;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * What a client needs to know about the world's Age (SPEC §18): the current Age, its goal item, whether the
 * recipe viewer (EMI), the block-info overlay (Jade), each of TerraFirmaCraft's inventory tabs, the Curios and Apothic
 * Attributes inventory buttons and the map (JourneyMap) are open yet, and whether the Cave Dweller still hunts, the
 * hand-breaking rule holds and the crafting grid is locked (§19). Computed on the server from the global Age state, the
 * shrine offering map and {@code firmages-server.toml [ui]} and {@code [hardship]}; sent as {@code firmages:age_status}.
 * Creative mode is the client's own exemption ({@link ViewerGate#open}). Pure Java (unit-tested).
 *
 * @param age          the highest unlocked Age ({@code dawn} .. {@code age_9})
 * @param goalItem     item id of the current Age's offering (the next Age's key), empty for Dawn and age_9
 * @param recipeViewer EMI may be shown
 * @param blockInfo    the Jade overlay may be shown
 * @param tabs         the TFC inventory tabs that may be shown and opened (§18.6)
 * @param caveDweller  the Cave Dweller still hunts, so its sounds play (§19)
 * @param toolRule     the hand-breaking rule holds, so the client predicts break speed 0 for its blocks (§19.2)
 * @param reloadPending an Age swap (or reload) is pending or running: the client lets the recipe viewer reload once,
 *                      after the new recipes arrive, instead of on each stage change (§3.3)
 * @param gathered     the shrine's Gathering is done (§7.5): the HUD goal of age_9 turns to "Beyond the Firmament"
 * @param gridLock     the inventory crafting grid is locked (§19.3): the client hides it and predicts no result
 * @param inventoryButtons the Curios and Apothic Attributes buttons may be shown ({@code ui.inventoryButtonsFromAge}, §18.2)
 * @param map          JourneyMap may be shown ({@code ui.mapFromAge}, §18.2)
 */
public record AgeStatus(AgeId age, String goalItem, boolean recipeViewer, boolean blockInfo, Set<InventoryTab> tabs, boolean caveDweller,
                        boolean toolRule, boolean reloadPending, boolean gathered, boolean gridLock, boolean inventoryButtons, boolean map) {

    /** Before the first status arrives the client treats the world as Dawn: nothing of the machinery shows, no
     *  inventory buttons, no map. The hand-breaking rule and the grid lock are left to the server until then (the
     *  client predicts no block and shows the grid). */
    public static final AgeStatus UNKNOWN = new AgeStatus(AgeId.DAWN, "", false, false, Set.of(), true, false, false, false, false, false, false);

    /**
     * @param offeringFor      tier N (worked in age_N, grants age_(N+1)) to its offering item id
     * @param tabFrom          each TFC tab's first Age, empty for never
     * @param caveDwellerUntil the first Age without the Cave Dweller, empty for never
     * @param toolRuleUntil    the first Age without the hand-breaking rule, empty when it is off
     */
    public static AgeStatus of(AgeSnapshot snapshot, IntFunction<Optional<String>> offeringFor, AgeId viewerFrom, AgeId overlayFrom,
                               Function<InventoryTab, Optional<AgeId>> tabFrom, Optional<AgeId> caveDwellerUntil,
                               Optional<AgeId> toolRuleUntil) {
        AgeId current = current(snapshot);
        String goal = "";
        int ix = current.ordinal() - AgeId.AGE_0.ordinal();
        if (ix >= 0 && current != AgeId.AGE_9) goal = offeringFor.apply(ix).orElse("");
        return new AgeStatus(current, goal, reached(current, viewerFrom), reached(current, overlayFrom), InventoryTab.open(current, tabFrom),
            CaveDwellerGate.hunts(current, caveDwellerUntil), ToolRule.active(current, toolRuleUntil), false, false, false,
            reached(current, InventoryButtons.DEFAULT_FROM), reached(current, MapGate.DEFAULT_FROM));
    }

    /** This status with the grid lock decided for its Age by {@code gridLockUntil} (the first Age with an open grid, empty for never). */
    public AgeStatus withGridLock(Optional<AgeId> gridLockUntil) {
        return new AgeStatus(age, goalItem, recipeViewer, blockInfo, tabs, caveDweller, toolRule, reloadPending, gathered,
            GridLock.active(age, gridLockUntil), inventoryButtons, map);
    }

    /**
     * This status with the inventory buttons and the map decided for its Age ({@link #of} uses the defaults).
     *
     * @param inventoryButtonsFrom {@code ui.inventoryButtonsFromAge}
     * @param mapFrom              {@code ui.mapFromAge}
     */
    public AgeStatus withUiGates(AgeId inventoryButtonsFrom, AgeId mapFrom) {
        return new AgeStatus(age, goalItem, recipeViewer, blockInfo, tabs, caveDweller, toolRule, reloadPending, gathered, gridLock,
            reached(age, inventoryButtonsFrom), reached(age, mapFrom));
    }

    /** This status with {@link #reloadPending} set. */
    public AgeStatus withReloadPending(boolean pending) {
        return new AgeStatus(age, goalItem, recipeViewer, blockInfo, tabs, caveDweller, toolRule, pending, gathered, gridLock, inventoryButtons, map);
    }

    /** This status with the shrine's Gathering done or not. */
    public AgeStatus withGathered(boolean done) {
        return new AgeStatus(age, goalItem, recipeViewer, blockInfo, tabs, caveDweller, toolRule, reloadPending, done, gridLock, inventoryButtons, map);
    }

    /** Lang key of the HUD goal when the Age has no goal item: {@code firmages.hud.goal.<age>}, after the Gathering {@code ..._gathered}. */
    public String goalKey() {
        return "firmages.hud.goal." + age.id() + (gathered ? "_gathered" : "");
    }

    /** The highest unlocked Age. Ages are monotonic in play, so this is the Age the world is in. */
    public static AgeId current(AgeSnapshot snapshot) {
        AgeId best = AgeId.DAWN;
        for (AgeId a : snapshot.unlocked()) if (a.ordinal() > best.ordinal()) best = a;
        return best;
    }

    /** True once {@code current} is {@code from} or later. */
    public static boolean reached(AgeId current, AgeId from) {
        return current.ordinal() >= from.ordinal();
    }

    /** Parses a config value; unknown ids fall back to {@code fallback}. */
    public static AgeId parse(String id, AgeId fallback) {
        return AgeId.byId(id).orElse(fallback);
    }
}
