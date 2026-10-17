package dev.firmages.core.hardship;

import dev.firmages.core.age.AgeId;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The inventory rows by Age (SPEC §19.4, Felix 2026-10-17, round G2): in Dawn only the nine hotbar slots hold items,
 * the Stone Age opens two of the three main rows, the Bronze Age all of them. {@code firmages-server.toml
 * [hardship.inventoryRows]} gives the open main rows (0 to 3) per Age of the world. The rows open from the hotbar
 * upward: Minecraft's {@code Inventory} keeps the hotbar at indices 0..8 and the main rows top to bottom at 9..17,
 * 18..26 and 27..35 (the bottom row sits right above the hotbar), so with {@code n} open rows the indices
 * {@code 9 .. 9 + 9 * (3 - n) - 1} are locked: one open row is 27..35, two are 18..35. Armour (36..39) and the offhand
 * (40) are never locked. {@link InventoryRowsService} applies it. Pure Java (unit-tested).
 */
public final class InventoryRows {
    /** The first main-row index of {@code Inventory.items} and the slots per row. */
    public static final int MAIN_START = 9, ROW = 9;
    /** All three main rows: nothing locked. */
    public static final int ALL = 3;
    private static final Map<AgeId, Integer> DEFAULTS = defaults();

    private InventoryRows() {}

    private static Map<AgeId, Integer> defaults() {
        Map<AgeId, Integer> m = new EnumMap<>(AgeId.class);
        for (AgeId a : AgeId.all()) m.put(a, ALL);
        m.put(AgeId.DAWN, 0); // Felix: Dawn carries only the hotbar
        m.put(AgeId.AGE_0, 2); // the Stone Age adds two rows, the Bronze Age the last one
        return m;
    }

    /** The default open rows of an Age: Dawn 0, Stone Age 2, every later Age 3. */
    public static int defaultRows(AgeId age) {
        return DEFAULTS.get(age);
    }

    /** The open rows of Age {@code current} by the config ({@code rowsOf}: the value of the Age's key), kept in 0..3. */
    public static int rows(AgeId current, Function<AgeId, Integer> rowsOf) {
        Integer n = rowsOf.apply(current);
        return n == null ? ALL : Math.max(0, Math.min(ALL, n));
    }

    /** The open rows for one player: all of them for creative, spectator and machine players. */
    public static int rowsFor(int worldRows, boolean exempt) {
        return exempt ? ALL : worldRows;
    }

    /** True if index {@code index} of {@code Inventory.items} is locked while {@code openRows} main rows are open. */
    public static boolean locked(int index, int openRows) {
        return index >= MAIN_START && index < firstOpen(openRows);
    }

    /** The first open main-row index ({@code 36} when every row is locked): everything from 9 below it is locked. */
    public static int firstOpen(int openRows) {
        return MAIN_START + ROW * (ALL - Math.max(0, Math.min(ALL, openRows)));
    }
}
