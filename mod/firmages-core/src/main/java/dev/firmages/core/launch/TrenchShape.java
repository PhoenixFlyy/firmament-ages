package dev.firmages.core.launch;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The flame trench under the pad (SPEC §31.3), checked in code after the pattern validates: a channel three wide and
 * three deep under the cradle that runs to the pad edge and opens to the side, with walls and a floor from
 * {@code #firmages:launch/trench}. Coordinates are relative to the cradle: {@code along} runs toward the opening,
 * {@code across} to its right, {@code depth} downward (1 = the layer under the pad). Pure Java.
 */
public final class TrenchShape {
    private TrenchShape() {}

    /** Half the pad: the channel runs from under the cradle (0) to under the pad edge (2), the opening is beyond. */
    public static final int REACH = 2;
    public static final int DEPTH = 3;

    public enum Kind {
        /** Open: no collision box, no fluid. */
        CHANNEL,
        /** The mouth beside the pad: open as the channel. */
        OPENING,
        /** Walls and floor: a trench block. */
        WALL
    }

    public record Cell(int along, int across, int depth, Kind kind) {}

    public static final List<Cell> CELLS = build();

    private static List<Cell> build() {
        List<Cell> out = new ArrayList<>();
        for (int d = 1; d <= DEPTH; d++) {
            for (int c = -1; c <= 1; c++) {
                for (int a = 0; a <= REACH; a++) out.add(new Cell(a, c, d, Kind.CHANNEL));
                out.add(new Cell(REACH + 1, c, d, Kind.OPENING));
                out.add(new Cell(-1, c, d, Kind.WALL)); // back wall
            }
            for (int a = 0; a <= REACH; a++) {
                out.add(new Cell(a, -2, d, Kind.WALL));
                out.add(new Cell(a, 2, d, Kind.WALL));
            }
        }
        for (int a = 0; a <= REACH; a++) {
            for (int c = -1; c <= 1; c++) out.add(new Cell(a, c, DEPTH + 1, Kind.WALL)); // floor
        }
        return List.copyOf(out);
    }

    /** Number of trench blocks (walls and floor). */
    public static long wallCount() {
        return CELLS.stream().filter(c -> c.kind() == Kind.WALL).count();
    }

    /** The first cell that does not fit, or null when the trench stands. */
    public static Cell firstFailure(Predicate<Cell> fits) {
        for (Cell c : CELLS) if (!fits.test(c)) return c;
        return null;
    }
}
