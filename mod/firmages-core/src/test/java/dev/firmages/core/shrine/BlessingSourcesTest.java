package dev.firmages.core.shrine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Level U (SPEC-himmel §29.8): the union of masks, the radius edge at 24.0 and 24.01, another dimension ignored,
 * sanctuary per kind, and no source while the shrine is not intact.
 */
class BlessingSourcesTest {
    private static final String OW = "minecraft:overworld";
    private static final String MOON = "ad_astra:moon";

    private static BlessingSources.Source src(BlessingSources.Kind kind, String dim, int x, int radius, int mask, boolean sanctuary) {
        return new BlessingSources.Source(kind, dim, x, 64, 0, radius, mask, sanctuary);
    }

    @Test
    void masksUnite() {
        List<BlessingSources.Source> s = List.of(
            src(BlessingSources.Kind.HEART, OW, 0, 20, 0b0111, true),
            src(BlessingSources.Kind.ANCHOR, OW, 30, 24, 0b1000_0001, true),
            src(BlessingSources.Kind.WAYSHRINE, OW, 500, 24, 0b1111, true));
        // between the heart and the anchor: both reach
        assertEquals(0b1000_0111, BlessingSources.mask(s, OW, 15.5, 64.5, 0.5));
        // only the heart
        assertEquals(0b0111, BlessingSources.mask(s, OW, 0.5, 64.5, 0.5));
        // nobody
        assertEquals(0, BlessingSources.mask(s, OW, 250.5, 64.5, 0.5));
        assertEquals(0b1111, BlessingSources.mask(s, OW, 510.5, 64.5, 0.5));
    }

    @Test
    void radiusEdge() {
        List<BlessingSources.Source> s = List.of(src(BlessingSources.Kind.WAYSHRINE, OW, 0, 24, 0b1, false));
        assertEquals(0b1, BlessingSources.mask(s, OW, 0.5 + 24.0, 64.5, 0.5), "24.0 from the centre is inside");
        assertEquals(0, BlessingSources.mask(s, OW, 0.5 + 24.01, 64.5, 0.5), "24.01 is outside");
        assertEquals(0b1, BlessingSources.mask(s, OW, 0.5, 64.5 + 24.0, 0.5), "the radius is a sphere");
    }

    @Test
    void otherDimensionIgnored() {
        List<BlessingSources.Source> s = List.of(src(BlessingSources.Kind.ANCHOR, MOON, 0, 24, 0b101, true));
        assertEquals(0, BlessingSources.mask(s, OW, 0.5, 64.5, 0.5));
        assertFalse(BlessingSources.sanctuaryAt(s, OW, 0.5, 64.5, 0.5));
        assertEquals(0b101, BlessingSources.mask(s, MOON, 0.5, 64.5, 0.5));
        assertTrue(BlessingSources.sanctuaryAt(s, MOON, 0.5, 64.5, 0.5));
    }

    @Test
    void sanctuaryPerSource() {
        List<BlessingSources.Source> s = List.of(
            src(BlessingSources.Kind.HEART, OW, 0, 12, 0b1, true),
            src(BlessingSources.Kind.ANCHOR, OW, 100, 24, 0b100, false),
            src(BlessingSources.Kind.WAYSHRINE, OW, 200, 24, 0b11, true));
        assertTrue(BlessingSources.sanctuaryAt(s, OW, 5.5, 64.5, 0.5), "the heart wards");
        assertFalse(BlessingSources.sanctuaryAt(s, OW, 100.5, 64.5, 0.5), "an anchor without the tier-0 shard does not");
        assertTrue(BlessingSources.sanctuaryAt(s, OW, 210.5, 64.5, 0.5), "a lit Wayshrine wards");
        assertFalse(BlessingSources.sanctuaryAt(s, OW, 20.5, 64.5, 0.5), "outside every radius");
    }

    @Test
    void inForceFollowsTheShrine() {
        List<BlessingSources.Source> c = List.of(
            src(BlessingSources.Kind.HEART, OW, 0, 20, BlessingSources.allTiers(3), true),
            src(BlessingSources.Kind.ANCHOR, OW, 50, 24, 0b1_0000_0101, false));
        assertEquals(List.of(), BlessingSources.inForce(false, 3, c), "nothing while the shrine is broken");
        assertEquals(List.of(), BlessingSources.inForce(true, 0, c), "nothing before the first awakening");
        List<BlessingSources.Source> in = BlessingSources.inForce(true, 3, c);
        assertEquals(2, in.size());
        assertEquals(0b0111, in.get(0).tierMask());
        assertEquals(0b0101, in.get(1).tierMask(), "a shard of a sleeping tier does nothing");
        assertEquals(List.of(), BlessingSources.inForce(true, 1, List.of(src(BlessingSources.Kind.ANCHOR, OW, 0, 24, 0b10, false))),
            "an anchor holding only sleeping tiers is no source");
    }

    @Test
    void allTiers() {
        assertEquals(0, BlessingSources.allTiers(0));
        assertEquals(0b1, BlessingSources.allTiers(1));
        assertEquals(0b1_1111_1111, BlessingSources.allTiers(9));
        assertEquals(0b1_1111_1111, BlessingSources.allTiers(12), "nine tiers at most");
    }
}
