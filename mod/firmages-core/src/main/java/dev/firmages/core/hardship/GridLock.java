package dev.firmages.core.hardship;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.immersion.AgeStatus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The Dawn crafting-grid lock (SPEC §19.3, Felix 2026-10-15, HW2A): while the world is below
 * {@code firmages-server.toml [hardship] gridLockUntilAge} (default {@code age_0}, so only in Dawn, the same boundary
 * as the hand-breaking rule of {@link ToolRule}) the 2x2 crafting grid of the player inventory (and of the Curios
 * screen) is closed: its slots are inactive and take nothing, it makes nothing, and the client paints it over. Dawn
 * crafting happens on the ground instead: items laid with TerraFirmaCraft's place key on one block form a pile, and a
 * sneaking use with an empty hand crafts the pile by the ordinary crafting recipes ({@link GroundCraft}).
 * {@link GridLockService} applies it. Pure Java (unit-tested).
 */
public final class GridLock {
    /** Felix 2026-10-15 (HW2A): the grid opens with the Stone Age ("The First Spark"). */
    public static final AgeId DEFAULT_UNTIL = AgeId.AGE_0;
    public static final String NEVER = "never";
    /**
     * Slots of a TerraFirmaCraft placed-item pile. TFC 4.2.11 {@code PlacedItemBlockEntity.getSlotSelected} [verified,
     * javap]: +1 for the west half, +2 for the north half, so 0 south-east, 1 south-west, 2 north-east, 3 north-west.
     */
    public static final int PILE_SLOTS = 4;

    private GridLock() {}

    /** The first Age with an open grid for a config value: empty for {@code never} (the lock is off); an unknown value counts as the default. */
    public static Optional<AgeId> until(String value) {
        if (isNever(value)) return Optional.empty();
        return Optional.of(AgeId.byId(value).orElse(DEFAULT_UNTIL));
    }

    /** The config validator: an Age id or {@code never}. */
    public static boolean isValid(Object o) {
        return o instanceof String s && (isNever(s) || AgeId.byId(s).isPresent());
    }

    /** True while the lock holds in Age {@code current}: below {@code until}; never with {@code never}. */
    public static boolean active(AgeId current, Optional<AgeId> until) {
        return until.map(u -> !AgeStatus.reached(current, u)).orElse(false);
    }

    /** The lock for one player: active and the player is a real survival player (not creative, spectator or a machine). */
    public static boolean locks(boolean active, boolean exempt) {
        return active && !exempt;
    }

    /**
     * The pile slots in 2x2 grid order (top-left, top-right, bottom-left, bottom-right) as the player sees the pile
     * when looking in horizontal direction {@code facing} (0 south, 1 west, 2 north, 3 east, as Minecraft's
     * {@code Direction.get2DDataValue}): the far row is the top row.
     */
    public static int[] viewOrder(int facing) {
        return switch (Math.floorMod(facing, 4)) {
            case 0 -> new int[] {0, 1, 2, 3}; // south: far row south (SE, SW), near row north (NE, NW)
            case 1 -> new int[] {1, 3, 0, 2}; // west: far column west (SW, NW), near column east (SE, NE)
            case 3 -> new int[] {2, 0, 3, 1}; // east: far column east (NE, SE), near column west (NW, SW)
            default -> new int[] {3, 2, 1, 0}; // north: far row north (NW, NE), near row south (SW, SE)
        };
    }

    /**
     * Every order in which a pile is tried against the recipes: first as the player sees it, then all other
     * arrangements of the four slots (24 in total), so the shape of a recipe never depends on where on the block the
     * parts were laid.
     */
    public static List<int[]> orders(int facing) {
        Set<List<Integer>> seen = new LinkedHashSet<>();
        int[] view = viewOrder(facing);
        seen.add(List.of(view[0], view[1], view[2], view[3]));
        permute(new int[] {0, 1, 2, 3}, 0, seen);
        List<int[]> out = new ArrayList<>(seen.size());
        for (List<Integer> o : seen) out.add(new int[] {o.get(0), o.get(1), o.get(2), o.get(3)});
        return out;
    }

    private static void permute(int[] a, int k, Set<List<Integer>> out) {
        if (k == a.length) {
            out.add(List.of(a[0], a[1], a[2], a[3]));
            return;
        }
        for (int i = k; i < a.length; i++) {
            swap(a, k, i);
            permute(a, k + 1, out);
            swap(a, k, i);
        }
    }

    private static void swap(int[] a, int i, int j) {
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    private static boolean isNever(String value) {
        return value != null && value.trim().toLowerCase(Locale.ROOT).equals(NEVER);
    }
}
