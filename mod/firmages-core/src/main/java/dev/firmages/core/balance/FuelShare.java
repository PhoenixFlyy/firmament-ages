package dev.firmages.core.balance;

/**
 * Felix H1A (harmony package A7): the alternator gives FE only in proportion to the rotation that comes from fuelled
 * sources. Pure rule, unit-tested: the share of fuelled stress capacity in a kinetic network, and the alternator's FE
 * per tick scaled by it. Create's own capacity numbers come in through {@code compat/create/AlternatorFuel}.
 */
public final class FuelShare {
    private FuelShare() {}

    /**
     * The fuelled part of a network's capacity, 0 to 1. {@code capacities[i]} is the actual stress capacity source
     * {@code i} adds (Create's {@code KineticNetwork#getActualCapacityOf}), {@code fuelled[i]} whether it burns fuel.
     * A network without capacity has no fuelled share.
     */
    public static float share(float[] capacities, boolean[] fuelled) {
        double total = 0;
        double burning = 0;
        for (int i = 0; i < capacities.length; i++) {
            float c = Math.max(0, capacities[i]);
            total += c;
            if (fuelled[i]) burning += c;
        }
        if (total <= 0) return 0;
        return (float) Math.min(1.0, burning / total);
    }

    /** The FE per tick of an alternator whose network has {@code share} fuelled capacity, rounded down. */
    public static int scaledRate(int rate, float share) {
        if (rate <= 0 || share <= 0) return 0;
        if (share >= 1) return rate;
        return (int) Math.floor(rate * (double) share);
    }
}
