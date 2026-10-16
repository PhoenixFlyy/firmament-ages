package dev.firmages.core.isles;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Level U (SPEC §30.3, §30.4): the plan is a pure function of seed and chunk; the tops lie in the band, the tips above
 * the clouds and the clearance above the terrain; a main isle with no room ends the start; satellites are small and keep
 * their gap; the Wind Shrine roll hits about one cluster in four; the shape holds the lode at the tip of medium and large
 * isles, every crystal cluster hangs on a solid block placed before it, and nothing lies outside the radius.
 */
class IsleLayoutTest {
    private static final IsleSettings S = IsleSettings.DEFAULT;
    private static final IsleLayout.HeightSampler FLAT = (x, z) -> 64;

    private static List<IsleSpec> cluster(long world, int cx, int cz, IsleLayout.HeightSampler terrain) {
        boolean shrine = IsleLayout.shrineRoll(world, cx, cz, S.windShrineChance());
        return IsleLayout.resolve(IsleLayout.plan(IsleLayout.clusterSeed(world, cx, cz), cx * 16 + 8, cz * 16 + 8, S, shrine), S, terrain);
    }

    @Test
    void deterministic() {
        for (int i = 0; i < 50; i++) assertEquals(cluster(42L, i, -i * 3, FLAT), cluster(42L, i, -i * 3, FLAT));
        assertFalse(cluster(1L, 5, 5, FLAT).equals(cluster(2L, 5, 5, FLAT)), "seeds differ");
    }

    @Test
    void band() {
        int clusters = 0;
        for (long seed = 0; seed < 400; seed++) {
            List<IsleSpec> c = cluster(seed * 7919L, (int) seed, (int) (seed * 3), FLAT);
            assertFalse(c.isEmpty(), "flat land always has room");
            assertTrue(c.size() >= S.cluster().min() && c.size() <= S.cluster().max(), "cluster size " + c.size());
            clusters++;
            for (int i = 0; i < c.size(); i++) {
                IsleSpec s = c.get(i);
                assertTrue(s.top() >= S.topY().min() && s.top() <= S.topY().max(), "top " + s.top());
                assertTrue(s.tip() >= IsleSettings.TIP_MIN, "tip " + s.tip());
                assertTrue(s.tip() - 2 >= 64 + S.minClearance(), "clearance");
                assertTrue(s.top() + IslePiece.HEADROOM <= IsleSettings.CROWN_MAX, "headroom ends at " + (s.top() + IslePiece.HEADROOM));
                if (i > 0) {
                    assertEquals(IsleSize.SMALL, s.size(), "satellites are small");
                    assertFalse(s.shrine(), "only the main isle carries the shrine");
                    for (int j = 0; j < i; j++) {
                        IsleSpec o = c.get(j);
                        assertTrue(Math.hypot(o.x() - s.x(), o.z() - s.z()) >= o.radius() + s.radius() + IsleLayout.GAP_MIN, "gap");
                    }
                }
                assertEquals(s.size().ordinal() >= S.lodeFrom().ordinal(), s.lode(), "lode from medium");
                if (s.shrine()) assertTrue(s.radius() >= IsleShape.SHRINE_MIN_RADIUS, "the shrine's isle is wide enough");
            }
        }
        assertTrue(clusters == 400);
    }

    @Test
    void highTerrainSkips() {
        assertTrue(cluster(5L, 3, 3, (x, z) -> 250).isEmpty(), "no room above a 250 high mountain");
        // a satellite over a peak is left out, the main isle stays
        List<IsleSpec> all = cluster(11L, 0, 0, FLAT);
        if (all.size() > 1) {
            IsleSpec sat = all.get(1);
            List<IsleSpec> some = cluster(11L, 0, 0, (x, z) -> Math.hypot(x - sat.x(), z - sat.z()) < sat.radius() + 2 ? 260 : 64);
            assertEquals(all.size() - 1, some.size());
            assertEquals(all.get(0), some.get(0));
        }
    }

    @Test
    void shrineRollIsAQuarter() {
        int hits = 0;
        int n = 20000;
        for (int i = 0; i < n; i++) if (IsleLayout.shrineRoll(1234L, i % 200, i / 200, 0.25F)) hits++;
        double share = hits / (double) n;
        assertTrue(share > 0.23 && share < 0.27, "share " + share);
        assertFalse(IsleLayout.shrineRoll(1234L, 1, 1, 0F));
    }

    private record Block(int x, int y, int z, IsleShape.Kind kind) {}

    @Test
    void shapeRules() {
        for (long seed = 0; seed < 60; seed++) {
            for (IsleSize size : IsleSize.values()) {
                IsleSettings.Range r = S.radius(size);
                int rad = r.min() + (int) (seed % (r.max() - r.min() + 1));
                boolean shrine = seed % 3 == 0 && rad >= IsleShape.SHRINE_MIN_RADIUS;
                IsleSpec spec = new IsleSpec(100, -50, rad, size, 250, IsleLayout.depth(rad), seed * 31 + size.ordinal(), size != IsleSize.SMALL,
                    shrine, (int) (seed % 3), IsleSpec.crystalsFor(rad, 3F));
                IsleShape shape = IsleShape.of(spec);
                List<Block> order = new ArrayList<>();
                shape.emitAll((x, y, z, k) -> order.add(new Block(x, y, z, k)));
                Map<Long, Integer> index = new HashMap<>();
                Map<Long, IsleShape.Kind> last = new HashMap<>();
                int lodes = 0;
                int clusters = 0;
                for (int i = 0; i < order.size(); i++) {
                    Block b = order.get(i);
                    long key = (((long) b.x) << 40) ^ (((long) b.z) << 16) ^ b.y;
                    index.put(key, i);
                    last.put(key, b.kind);
                    assertTrue(Math.hypot(b.x - spec.x(), b.z - spec.z()) <= rad + 1.5, "inside the radius");
                    assertTrue(b.y >= shape.minY() && b.y <= shape.maxY(), "y " + b.y);
                    if (b.kind == IsleShape.Kind.LODE) {
                        lodes++;
                        assertEquals(spec.x(), b.x);
                        assertEquals(spec.z(), b.z);
                        assertEquals(shape.bottomAt(0, 0), b.y, "the lode is the underside tip");
                    }
                }
                for (int i = 0; i < order.size(); i++) {
                    Block b = order.get(i);
                    if (!b.kind.cluster()) continue;
                    clusters++;
                    int[] s = b.kind.support();
                    long key = (((long) (b.x + s[0])) << 40) ^ (((long) (b.z + s[2])) << 16) ^ (b.y + s[1]);
                    Integer at = index.get(key);
                    assertTrue(at != null && at < i, "a cluster hangs on a block placed before it");
                    IsleShape.Kind carrier = last.get(key);
                    assertTrue(carrier == IsleShape.Kind.SKYSTONE || carrier == IsleShape.Kind.LODE, "carrier " + carrier);
                }
                assertEquals(spec.lode() ? 1 : 0, lodes, "one lode on medium and large isles");
                assertTrue(clusters >= 2, "at least two crystal clusters, got " + clusters);
                assertTrue(clusters <= spec.crystals(), "no more clusters than planned");
                if (shrine) {
                    for (int dx = -IsleShape.SHRINE_FLAT; dx <= IsleShape.SHRINE_FLAT; dx++) {
                        for (int dz = -IsleShape.SHRINE_FLAT; dz <= IsleShape.SHRINE_FLAT; dz++) {
                            if (dx * dx + dz * dz <= IsleShape.SHRINE_FLAT * IsleShape.SHRINE_FLAT) {
                                assertEquals(spec.top(), shape.topAt(dx, dz), "flat top for the shrine");
                            }
                        }
                    }
                }
            }
        }
    }
}
