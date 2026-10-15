package dev.firmages.core.sky;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Level U (SPEC §25.11): the same seed gives the same plan; block counts lie within the size; the core comes last; the
 * lining covers every bowl column and the lip ring; the rim is only sky rock; nothing lies outside radius + 3.
 */
class CraterPlanTest {
    private static final List<StarfallData.Size> SIZES = List.of(
        new StarfallData.Size("old", 4, 2, 6, 10), new StarfallData.Size("small", 5, 3, 10, 14),
        new StarfallData.Size("medium", 7, 4, 16, 22), new StarfallData.Size("large", 9, 5, 24, 32));

    private static Map<String, Double> starsteel() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("firmages:meteorite", 0.6);
        m.put("firmages:starsteel_meteorite", 0.4);
        return m;
    }

    @Test
    void deterministic() {
        for (StarfallData.Size s : SIZES) {
            assertEquals(CraterPlan.of(99L, s, starsteel()).ops(), CraterPlan.of(99L, s, starsteel()).ops(), s.id());
        }
        assertTrue(!CraterPlan.of(1L, SIZES.get(2), starsteel()).ops().equals(CraterPlan.of(2L, SIZES.get(2), starsteel()).ops()), "seeds differ");
    }

    @Test
    void shapeRules() {
        for (long seed = 0; seed < 40; seed++) {
            for (StarfallData.Size s : SIZES) {
                CraterPlan p = CraterPlan.of(seed, s, starsteel());
                List<CraterPlan.Op> ops = p.ops();
                long met = p.count(CraterPlan.Kind.METEORITE);
                assertTrue(met >= s.minMeteorites() && met <= s.maxMeteorites(), s.id() + " meteorites " + met);
                assertEquals(1, p.count(CraterPlan.Kind.CORE));
                CraterPlan.Op last = ops.get(ops.size() - 1);
                assertEquals(CraterPlan.Kind.CORE, last.kind(), "the core comes last");
                assertEquals(-s.depth(), last.dy(), "the core sits at the bottom of the bowl");
                int r = s.radius();
                Set<Long> floorCols = new HashSet<>();
                Set<Long> lipCols = new HashSet<>();
                Set<Long> positions = new HashSet<>();
                for (CraterPlan.Op o : ops) {
                    double d = Math.hypot(o.dx(), o.dz());
                    assertTrue(d <= r + CraterPlan.MARGIN, "op outside radius + 3: " + o);
                    if (o.kind() != CraterPlan.Kind.AIR) assertTrue(positions.add(key(o.dx(), o.dy(), o.dz(), o.surface())), "two solid ops at " + o);
                    if (o.surface() && o.dy() == 1) assertEquals(CraterPlan.Kind.SKY_ROCK, o.kind(), "the rim is sky rock");
                    if (o.surface() && o.dy() == 1) assertTrue(d >= r - 1e-9 && d <= r + 2 + 1e-9, "rim between radius and radius + 2: " + o);
                    if (o.surface() && o.dy() == 0) lipCols.add(key(o.dx(), 0, o.dz(), true));
                    if (!o.surface() && o.kind() != CraterPlan.Kind.AIR && o.dy() == CraterPlan.floor(seed, r, s.depth(), o.dx(), o.dz())) {
                        floorCols.add(key(o.dx(), 0, o.dz(), false));
                    }
                    if (o.kind() == CraterPlan.Kind.METEORITE) {
                        assertTrue(o.block().equals("firmages:meteorite") || o.block().equals("firmages:starsteel_meteorite"), o.block());
                        assertTrue(o.dy() <= CraterPlan.floor(seed, r, s.depth(), o.dx(), o.dz()), "meteorites sit in the floor");
                    }
                }
                for (int dx = -r - 1; dx <= r + 1; dx++) {
                    for (int dz = -r - 1; dz <= r + 1; dz++) {
                        int d2 = dx * dx + dz * dz;
                        if (d2 <= r * r) assertTrue(floorCols.contains(key(dx, 0, dz, false)), "bowl column " + dx + "," + dz + " not lined");
                        else if (d2 <= (r + 1) * (r + 1)) assertTrue(lipCols.contains(key(dx, 0, dz, true)), "lip column " + dx + "," + dz + " not lined");
                    }
                }
                // air reaches twelve above the centre and the order is air first
                assertTrue(ops.stream().anyMatch(o -> o.kind() == CraterPlan.Kind.AIR && o.dy() == CraterPlan.AIR_ABOVE));
                int firstSolid = 0;
                while (ops.get(firstSolid).kind() == CraterPlan.Kind.AIR) firstSolid++;
                for (int i = firstSolid; i < ops.size(); i++) assertTrue(ops.get(i).kind() != CraterPlan.Kind.AIR, "air after solid at " + i);
            }
        }
    }

    @Test
    void largeCraterSize() {
        CraterPlan p = CraterPlan.of(5L, SIZES.get(3), starsteel());
        long solid = p.ops().stream().filter(o -> o.kind() != CraterPlan.Kind.AIR).count();
        assertTrue(solid > 250 && solid < 600, "solid ops of a large crater " + solid);
        assertEquals(12, p.reach());
    }

    private static long key(int x, int y, int z, boolean surface) {
        return ((long) (x & 0xFFFF) << 33) | ((long) (y & 0xFFFF) << 17) | ((long) (z & 0xFFFF) << 1) | (surface ? 1 : 0);
    }
}
