package dev.firmages.core.boss;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Primordial's fight model (SPEC §39): the defaults give 6 to 10 minutes for 1, 2 and 4 players in Chaotic gear. */
class FightModelTest {
    private static final FightModel.Settings S = FightModel.Settings.DEFAULTS;
    private static final double REF = FightModel.chaoticSword(FightModel.REFERENCE_DAMAGE_MODULES);

    @Test
    void draconicStatsFromTheJar() {
        assertEquals(17.5, FightModel.chaoticSword(0), 1e-9, "Chaotic 2.5 x sword 7.0");
        assertEquals(49.5, REF, 1e-9, "two Chaotic damage modules, +16 each");
        assertEquals(2.0, FightModel.hitsPerSecond(0), 1e-9, "3.2 swings a second, one counting hit per 10 ticks");
    }

    @Test
    void armourAndTheHitCap() {
        assertEquals(12.1625, FightModel.afterArmour(17.5, 12, 8), 1e-6, "a bare Chaotic sword after armour 12 / toughness 8");
        assertEquals(44.748, FightModel.afterArmour(REF, 12, 8), 1e-6);
        assertEquals(40, S.poolPerHit(REF), 1e-9, "the reference build is at the cap");
        assertEquals(40, S.poolPerHit(FightModel.chaoticSword(40)), 1e-9, "a grid full of damage modules counts no more");
        assertEquals(12.1625, S.poolPerHit(17.5), 1e-6, "below the cap a hit counts as it is");
    }

    @Test
    void poolPerPartySize() {
        assertEquals(13000, S.pool(1), 1e-9);
        assertEquals(22100, S.pool(2), 1e-9);
        assertEquals(40300, S.pool(4), 1e-9);
    }

    @Test
    void theFightLastsSixToTenMinutes() {
        double one = S.fightSeconds(1, REF), two = S.fightSeconds(2, REF), four = S.fightSeconds(4, REF);
        assertEquals(592.1, one, 0.5, "one player");
        assertEquals(464.4, two, 0.5, "two players");
        assertEquals(408.1, four, 0.5, "four players");
        for (double t : new double[] {one, two, four}) assertTrue(t >= 360 && t <= 600, "6 to 10 minutes: " + t + " s");
        assertTrue(one < FightModel.ENRAGE_MINUTES * 60, "the soft enrage only meets a slower fight than the model's");
        assertTrue(FightModel.STARFALL_ENRAGE_MINUTES > FightModel.ENRAGE_MINUTES);
    }

    @Test
    void eachPhaseTakesAboutAThird() {
        double[] t = S.phaseSeconds(2, REF);
        double sum = t[0] + t[1] + t[2];
        for (double p : t) assertTrue(Math.abs(p / sum - 1.0 / 3) < 0.01, "phase share " + p / sum);
    }

    @Test
    void withoutTheCapAFullGridWouldEndItInAMinute() {
        FightModel.Settings uncapped = new FightModel.Settings(S.effectiveHealth(), S.perPlayer(), S.veilFactor(), 0);
        assertTrue(uncapped.fightSecondsWithoutAltars(1, FightModel.chaoticSword(40)) < 60, "why the cap exists");
    }

    /** Round G3 (SPEC §39.1): the altar shield of phase 2 and 3 adds at most 20 % to the fight of 1, 2 and 4 players. */
    @Test
    void theAltarsAddAtMostAFifth() {
        assertEquals(43.08, FightModel.altarSeconds(1), 0.01, "one player lights four altars against a strike every 20 s");
        assertEquals(16.97, FightModel.altarSeconds(2), 0.01);
        assertEquals(7.67, FightModel.altarSeconds(4), 0.01);
        for (int n : new int[] {1, 2, 4}) {
            double before = S.fightSecondsWithoutAltars(n, REF), after = S.fightSeconds(n, REF);
            assertEquals(505.9, S.fightSecondsWithoutAltars(1, REF), 0.5, "the model before the altars");
            assertTrue(after / before - 1 <= FightModel.ALTAR_MAX_GROWTH, n + " players: +" + (after / before - 1));
        }
    }
}
