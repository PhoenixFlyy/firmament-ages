package dev.firmages.core.ark;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Archipelago's rules without a world (SPEC §31.8), unit-tested: which given item counts for which group, when a
 * group, an island and the whole Archipelago are done, which stage an island should stand at, where the nine sockets
 * around the altar lie, and the order in which a stage grows from the plinth outward.
 */
public final class ArkRules {
    /** Radius of the socket ring around the altar. */
    public static final int SOCKET_RADIUS = 3;

    private ArkRules() {}

    /** Answers whether item {@code itemId} is one of {@code what} (by id, or by tag through the registry). */
    @FunctionalInterface
    public interface Matcher {
        boolean matches(ArkData.Selector what, String itemId);
    }

    /** What a given item counts for: {@code count} items towards {@code group} (crown: entry {@code entry}). */
    public record Take(String group, int entry, int count) {}

    /** True when one flora option is satisfied by the items given so far. */
    public static boolean floraDone(ArkData.Island isl, ArkProgress p, Matcher m) {
        for (ArkData.Flora f : isl.flora()) {
            if (given(f, p, m) >= f.needed()) return true;
        }
        return false;
    }

    /** Items given so far towards option {@code f}: distinct ids, or the total count. */
    public static int given(ArkData.Flora f, ArkProgress p, Matcher m) {
        int n = 0;
        for (Map.Entry<String, Integer> e : p.flora.entrySet()) {
            if (!m.matches(f.what(), e.getKey())) continue;
            n += f.distinct() > 0 ? 1 : e.getValue();
        }
        return n;
    }

    /** True when every crown entry has its count (an island without a crown has none to give). */
    public static boolean crownDone(ArkData.Island isl, ArkProgress p) {
        for (int i = 0; i < isl.crown().size(); i++) {
            if (p.crown.getOrDefault(i, 0) < isl.crown().get(i).count()) return false;
        }
        return true;
    }

    /**
     * How many of {@code available} items of {@code itemId} the island takes now, and for which group. Flora first
     * (one per new id for a {@code distinct} option, up to the rest for a {@code count} option), then the crown.
     */
    public static Optional<Take> accept(ArkData.Island isl, ArkProgress p, String itemId, int available, Matcher m) {
        if (available <= 0) return Optional.empty();
        if (!p.has("flora")) {
            for (ArkData.Flora f : isl.flora()) {
                if (!m.matches(f.what(), itemId)) continue;
                if (f.distinct() > 0) {
                    if (!p.flora.containsKey(itemId) && given(f, p, m) < f.distinct()) return Optional.of(new Take("flora", -1, 1));
                } else {
                    int rest = f.count() - given(f, p, m);
                    if (rest > 0) return Optional.of(new Take("flora", -1, Math.min(rest, available)));
                }
            }
        }
        if (!p.has(ArkData.CROWN)) {
            for (int i = 0; i < isl.crown().size(); i++) {
                ArkData.CrownEntry c = isl.crown().get(i);
                if (!m.matches(c.what(), itemId)) continue;
                int rest = c.count() - p.crown.getOrDefault(i, 0);
                if (rest > 0) return Optional.of(new Take(ArkData.CROWN, i, Math.min(rest, available)));
            }
        }
        return Optional.empty();
    }

    /** Records {@code t} for {@code itemId} and marks the groups that are now done. @return the groups that became done */
    public static List<String> apply(ArkData.Island isl, ArkProgress p, String itemId, Take t, Matcher m) {
        if (t.group().equals("flora")) p.flora.merge(itemId, t.count(), Integer::sum);
        else p.crown.merge(t.entry(), t.count(), Integer::sum);
        return settle(isl, p, m, false);
    }

    /**
     * Marks every group that the recorded items already satisfy, and (once the finale is won) the fauna group of an
     * island that takes no animal. @return the groups that became done now
     */
    public static List<String> settle(ArkData.Island isl, ArkProgress p, Matcher m, boolean won) {
        List<String> out = new ArrayList<>();
        if (!p.has("flora") && floraDone(isl, p, m)) out.add("flora");
        if (won && !isl.hasFauna() && !p.has("fauna")) out.add("fauna");
        if (!p.has(ArkData.CROWN) && isl.hasCrown() && crownDone(isl, p)) out.add(ArkData.CROWN);
        p.groups.addAll(out);
        return out;
    }

    /** The stage the island should stand at: one per group done (0..3); the crown grows no stage. */
    public static int targetStage(ArkProgress p) {
        int n = 0;
        for (String g : ArkData.GROUPS) if (p.has(g)) n++;
        return n;
    }

    /** Every group done and, for an island with a crown, the crown too. */
    public static boolean isComplete(ArkData.Island isl, ArkProgress p) {
        for (String g : ArkData.GROUPS) if (!p.has(g)) return false;
        return !isl.hasCrown() || p.has(ArkData.CROWN);
    }

    /** Bit {@code i} set for each complete island. */
    public static int completeMask(List<ArkProgress> islands) {
        int mask = 0;
        for (int i = 0; i < islands.size(); i++) if (islands.get(i).complete) mask |= 1 << i;
        return mask;
    }

    /** True when all nine islands are complete. */
    public static boolean allComplete(int mask) {
        return mask == (1 << ArkData.ISLANDS) - 1;
    }

    /**
     * Socket {@code i} around the altar: {x, z} offset on a circle of {@link #SOCKET_RADIUS}, in the direction of island
     * {@code i} (the islands lie at 12 + 40 i degrees).
     */
    public static int[] socket(int i) {
        double a = Math.toRadians(i * 40 + 12);
        return new int[] {(int) Math.round(Math.cos(a) * SOCKET_RADIUS), (int) Math.round(Math.sin(a) * SOCKET_RADIUS)};
    }

    /**
     * The tick (0..{@code ticks}) at which a stage block at horizontal offset ({@code dx}, {@code dz}) from the plinth
     * is placed: in proportion to its distance, the farthest block at {@code ticks}.
     */
    public static int growthTick(int dx, int dz, double maxDistance, int ticks) {
        if (ticks <= 0 || maxDistance <= 0) return 0;
        double d = Math.sqrt((double) dx * dx + (double) dz * dz);
        return (int) Math.min(ticks, Math.round(d / maxDistance * ticks));
    }
}
