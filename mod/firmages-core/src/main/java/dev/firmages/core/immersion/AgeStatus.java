package dev.firmages.core.immersion;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeSnapshot;

import java.util.Optional;
import java.util.function.IntFunction;

/**
 * What a client needs to know about the world's Age (SPEC §18): the current Age, its goal item and whether the
 * recipe viewer (EMI) and the block-info overlay (Jade) are open yet. Computed on the server from the global Age
 * state, the shrine offering map and {@code firmages-server.toml [ui]}; sent as {@code firmages:age_status}.
 * Pure Java (unit-tested).
 *
 * @param age          the highest unlocked Age ({@code dawn} .. {@code age_9})
 * @param goalItem     item id of the current Age's offering (the next Age's key), empty for Dawn and age_9
 * @param recipeViewer EMI may be shown
 * @param blockInfo    the Jade overlay may be shown
 */
public record AgeStatus(AgeId age, String goalItem, boolean recipeViewer, boolean blockInfo) {

    /** Before the first status arrives the client treats the world as Dawn: nothing of the machinery shows. */
    public static final AgeStatus UNKNOWN = new AgeStatus(AgeId.DAWN, "", false, false);

    /**
     * @param offeringFor tier N (worked in age_N, grants age_(N+1)) to its offering item id
     */
    public static AgeStatus of(AgeSnapshot snapshot, IntFunction<Optional<String>> offeringFor, AgeId viewerFrom, AgeId overlayFrom) {
        AgeId current = current(snapshot);
        String goal = "";
        int ix = current.ordinal() - AgeId.AGE_0.ordinal();
        if (ix >= 0 && current != AgeId.AGE_9) goal = offeringFor.apply(ix).orElse("");
        return new AgeStatus(current, goal, reached(current, viewerFrom), reached(current, overlayFrom));
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
