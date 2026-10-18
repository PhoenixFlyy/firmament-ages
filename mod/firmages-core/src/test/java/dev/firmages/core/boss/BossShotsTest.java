package dev.firmages.core.boss;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round G4 (SPEC §39.2): the pack's shot table parses, every Age has its row, and the built-in defaults say the same. */
class BossShotsTest {
    static final Path PACK_TABLE = Path.of("..", "..", "kubejs", "data", "firmages", "balance", "boss_shots.json");

    /** The pack's table (gradle runs the tests in the mod's directory). */
    static BossShots packTable() {
        try (Reader r = Files.newBufferedReader(PACK_TABLE, StandardCharsets.UTF_8)) {
            return BossShots.parse(r);
        } catch (IOException e) {
            throw new IllegalStateException("kubejs/data/firmages/balance/boss_shots.json unreadable", e);
        }
    }

    @Test
    void everyAgeHasARowWithASpeedAndDamage() {
        BossShots t = packTable();
        for (ShotAge a : ShotAge.values()) {
            BossShots.Shot s = t.shot(a);
            assertNotNull(s, a.key);
            assertTrue(s.speed() > 0 && s.speed() <= 2.0, a.key + " speed " + s.speed());
            assertTrue(s.damage() > 0, a.key + " damage");
            assertTrue(s.expectedDamage() >= s.damage(), a.key + " expected damage counts the burst at least");
        }
        assertEquals(10, ShotAge.COUNT, "ten segments, one per Age");
        assertEquals(0xF4F0FF, ShotAge.SINGULARITY.color(), "the tenth segment is the white-violet of Age 9");
    }

    @Test
    void thePaceAndTheMarksOfTheDesign() {
        BossShots t = packTable();
        assertEquals(30, t.intervalTicks(1), "1.5 s in phase 1");
        assertEquals(20, t.intervalTicks(2), "1.0 s in phase 2");
        assertEquals(14, t.intervalTicks(3), "0.7 s in phase 3");
        assertTrue(t.stormIntervalTicks(1) > t.stormIntervalTicks(2) && t.stormIntervalTicks(2) > t.stormIntervalTicks(3), "the storm thickens by phase");
        assertEquals(2, t.shot(ShotAge.IRON).pierce(), "the Iron lance runs through two");
        assertEquals(3, t.shot(ShotAge.INFORMATION).count(), "a fan of three data splinters");
        assertEquals(60, t.shot(ShotAge.ARCANE).ticks("levitation_seconds"), "3 s of levitation");
        assertEquals(80, t.shot(ShotAge.INDUSTRIAL).ticks("cloud_seconds"), "a 4 s steam cloud");
        assertEquals(2, t.shot(ShotAge.ELECTRIC).chainHops(), "the chain bolt jumps twice");
        assertEquals(60, t.shot(ShotAge.SPACE).wellTicks(), "a 3 s gravity well");
        assertEquals(40, t.shot(ShotAge.QUANTUM).ticks("freeze_seconds"), "2 s frozen");
        assertEquals(3, t.shot(ShotAge.SINGULARITY).phaseFrom(), "the black hole only in phase 3");
        assertEquals(3, t.shot(ShotAge.SINGULARITY).everyCycles(), "and only one ring cycle in three");
        assertTrue(t.shot(ShotAge.SINGULARITY).implosionDamage() >= 30, "the implosion hits hard");
        assertEquals(4.0, t.melee().range(), 1e-9, "the ring's edge reaches 4 blocks");
        assertEquals(40, t.melee().cooldownTicks(), "every 2 s");
    }

    @Test
    void theBuiltInDefaultsMatchThePack() {
        BossShots pack = packTable(), def = BossShots.defaults();
        for (ShotAge a : ShotAge.values()) {
            assertEquals(pack.shot(a).expectedDamage(), def.shot(a).expectedDamage(), 1e-9, a.key + ": the defaults mirror the pack's row");
            assertEquals(pack.shot(a).speed(), def.shot(a).speed(), 1e-9, a.key + " speed");
        }
        for (int p = 1; p <= 3; p++) assertEquals(pack.intervalTicks(p), def.intervalTicks(p), "interval of phase " + p);
        assertEquals(pack.shotDps(3), def.shotDps(3), 1e-9);
    }

    @Test
    void theDamageASecondGrowsWithThePhase() {
        BossShots t = packTable();
        double p1 = t.shotDps(1), p2 = t.shotDps(2), p3 = t.shotDps(3);
        assertTrue(p1 < p2 && p2 < p3, p1 + " / " + p2 + " / " + p3);
        assertTrue(p3 > 15 && p3 < 30, "phase 3 ring damage a second " + p3);
        assertEquals(5.0, t.meleeDps(), 1e-9, "10 every 2 s");
    }
}
