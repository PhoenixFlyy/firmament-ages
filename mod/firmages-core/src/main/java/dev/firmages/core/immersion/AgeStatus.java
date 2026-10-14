package dev.firmages.core.immersion;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeSnapshot;
import dev.firmages.core.hardship.CaveDwellerGate;
import dev.firmages.core.hardship.ToolRule;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * What a client needs to know about the world's Age (SPEC §18): the current Age, its goal item, whether the
 * recipe viewer (EMI), the block-info overlay (Jade) and each of TerraFirmaCraft's inventory tabs are open yet, and
 * whether the Cave Dweller still hunts and the hand-breaking rule holds (§19). Computed on the server from the global Age state, the shrine offering
 * map and {@code firmages-server.toml [ui]} and {@code [hardship]}; sent as {@code firmages:age_status}. Pure Java
 * (unit-tested).
 *
 * @param age          the highest unlocked Age ({@code dawn} .. {@code age_9})
 * @param goalItem     item id of the current Age's offering (the next Age's key), empty for Dawn and age_9
 * @param recipeViewer EMI may be shown
 * @param blockInfo    the Jade overlay may be shown
 * @param tabs         the TFC inventory tabs that may be shown and opened (§18.6)
 * @param caveDweller  the Cave Dweller still hunts, so its sounds play (§19)
 * @param toolRule     the hand-breaking rule holds, so the client predicts break speed 0 for its blocks (§19.2)
 * @param reloadPending an Age reload (or Age swap) is scheduled or running: the client skips ProgressiveStages' own
 *                      EMI reload at the stage change, because the recipe and tag sync after the reload reloads EMI
 *                      anyway (§21.5)
 */
public record AgeStatus(AgeId age, String goalItem, boolean recipeViewer, boolean blockInfo, Set<InventoryTab> tabs, boolean caveDweller,
                        boolean toolRule, boolean reloadPending) {

    /** Before the first status arrives the client treats the world as Dawn: nothing of the machinery shows. The
     *  hand-breaking rule is left to the server until then (the client predicts no block). */
    public static final AgeStatus UNKNOWN = new AgeStatus(AgeId.DAWN, "", false, false, Set.of(), true, false, false);

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
            CaveDwellerGate.hunts(current, caveDwellerUntil), ToolRule.active(current, toolRuleUntil), false);
    }

    /** This status with {@link #reloadPending} set. */
    public AgeStatus withReloadPending(boolean pending) {
        return new AgeStatus(age, goalItem, recipeViewer, blockInfo, tabs, caveDweller, toolRule, pending);
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
