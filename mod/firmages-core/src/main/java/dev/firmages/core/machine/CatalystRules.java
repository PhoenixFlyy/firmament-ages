package dev.firmages.core.machine;

/**
 * The Arcane Catalyst as pure logic (SPEC §41.4). While it is charged (Source drawn this second and a turning shaft at
 * its side) it gives the machine it faces {@code extraTicks} extra work ticks per game tick (0.5: one extra tick every
 * second tick, +50 % speed). Each extra tick costs the catalyst what the machine drew in it times
 * {@code feSurcharge} on top (1.0: the extra operations cost twice the FE), or {@code flatFe} when the machine's energy
 * cannot be read. The catalyst never makes output from nothing: the machine still uses its own inputs and FE.
 */
public final class CatalystRules {
    private CatalystRules() {}

    /** Accumulates {@code rate} extra ticks; returns {whole extra ticks now, the remainder x 1e6}. */
    public static long[] extraTicks(double accumulated, double rate) {
        double a = accumulated + Math.max(0, Math.min(1.0, rate));
        long whole = (long) Math.floor(a + 1e-9);
        return new long[] {whole, Math.round((a - whole) * 1_000_000L)};
    }

    /** FE the catalyst pays for one extra tick in which the machine drew {@code machineUse} FE (-1: not readable). */
    public static long surcharge(long machineUse, double factor, long flatFe) {
        if (machineUse < 0) return Math.max(0, flatFe);
        return (long) Math.ceil(machineUse * Math.max(0, factor));
    }

    /** The catalyst is charged: Source paid for this second (or none asked) and the shaft turns fast enough. */
    public static boolean charged(boolean sourcePaid, double rpm, int minRpm) {
        return sourcePaid && rpm >= minRpm;
    }
}
