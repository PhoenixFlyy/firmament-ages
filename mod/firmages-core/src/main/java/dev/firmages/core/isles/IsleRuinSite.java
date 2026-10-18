package dev.firmages.core.isles;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The pure part of {@link IsleRuin} (no Minecraft classes, so the layout stays unit-testable): where the ruin stands. */
public final class IsleRuinSite {
    /** Half the template's footprint (7 by 7). */
    public static final int HALF = 3;
    /** The template layer that lies in the isle's top surface (the lid layer). */
    public static final int CORE_Y = 3;
    /** Blocks of body that must lie below the top at every footprint column (chamber and floor). */
    static final int MIN_BODY = CORE_Y + 1;

    private IsleRuinSite() {}

    /** The ruin's centre column relative to the isle centre, or empty when the isle has no room for it. */
    public static Optional<int[]> site(IsleSpec s) {
        IsleShape shape = IsleShape.of(s);
        for (int d = 0; d <= s.radius(); d++) {
            int steps = d == 0 ? 1 : 16;
            for (int k = 0; k < steps; k++) {
                double a = k * Math.PI * 2 / steps;
                int dx = (int) Math.round(Math.cos(a) * d);
                int dz = (int) Math.round(Math.sin(a) * d);
                if (fits(shape, dx, dz)) return Optional.of(new int[] {dx, dz});
            }
        }
        return Optional.empty();
    }

    static boolean fits(IsleShape shape, int dx, int dz) {
        IsleSpec s = shape.spec();
        int margin = HALF + 3;
        if (s.shrine() && Math.hypot(dx, dz) < IsleShape.SHRINE_FLAT + margin) return false;
        int hr = shape.hollowRadius();
        if (hr > 0) {
            int[] h = shape.hollowCentre();
            if (Math.hypot(dx - h[0], dz - h[1]) < hr + margin) return false;
        }
        for (IsleShape.Tree t : shape.trees()) {
            if (Math.hypot(t.x() - s.x() - dx, t.z() - s.z() - dz) < margin) return false;
        }
        for (int x = dx - HALF; x <= dx + HALF; x++) {
            for (int z = dz - HALF; z <= dz + HALF; z++) {
                if (shape.t(x, z) > 0.85) return false; // the whole footprint on the flat top, inside the rim
                boolean inner = Math.abs(x - dx) < HALF && Math.abs(z - dz) < HALF;
                if (inner && s.top() - shape.bottomAt(x, z) < MIN_BODY) return false; // the chamber sits in the body
            }
        }
        return true;
    }

    /** Marks the ruin's isle in a resolved cluster: the first isle with a site. */
    public static List<IsleSpec> mark(List<IsleSpec> isles) {
        for (int i = 0; i < isles.size(); i++) {
            IsleSpec s = isles.get(i);
            if (site(s).isEmpty()) continue;
            List<IsleSpec> out = new ArrayList<>(isles);
            out.set(i, s.withRuin(true));
            return out;
        }
        return isles;
    }

}
