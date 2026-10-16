package dev.firmages.core.balance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Felix H1A (SPEC §34.1): the alternator gives FE only for the fuelled share of its network. */
class FuelShareTest {
    /** Crafts and Additions at 256 RPM: 480 FE/t base times the pack's generator_efficiency 0.35. */
    private static final int RATE_256 = 168;

    @Test
    void windmillAloneGivesNothing() {
        float share = FuelShare.share(new float[] {4096}, new boolean[] {false});
        assertEquals(0f, share);
        assertEquals(0, FuelShare.scaledRate(RATE_256, share), "a windmill or water wheel turns the alternator for no FE");
    }

    @Test
    void coalBoilerGivesTheFullRate() {
        float share = FuelShare.share(new float[] {16384}, new boolean[] {true});
        assertEquals(1f, share);
        assertEquals(168, FuelShare.scaledRate(RATE_256, share), "a steam engine on a fuelled blaze burner: 168 FE/t");
    }

    @Test
    void halfWindHalfSteamGivesHalf() {
        float share = FuelShare.share(new float[] {8192, 8192}, new boolean[] {false, true});
        assertEquals(0.5f, share);
        assertEquals(84, FuelShare.scaledRate(RATE_256, share), "half the capacity from wind, half from steam: 84 FE/t");
    }

    @Test
    void edges() {
        assertEquals(0f, FuelShare.share(new float[0], new boolean[0]), "no sources, no share");
        assertEquals(0f, FuelShare.share(new float[] {0, 0}, new boolean[] {true, true}), "no capacity, no share");
        assertEquals(0, FuelShare.scaledRate(0, 1f));
        assertEquals(33, FuelShare.scaledRate(100, 1f / 3f), "rounded down");
    }
}
