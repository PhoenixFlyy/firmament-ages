package dev.firmages.core.reactor;

/**
 * The few things the controller and the fuel port need from a Draconic reactor core (SPEC §6). Pure Java, so the
 * state machine runs in unit tests; {@code compat/draconic/DraconicReactors} adapts DE's {@code TileReactorCore},
 * GameTests may plug in a stub through {@link ReactorLookup#addProvider}.
 */
public interface ReactorView {
    /** DE 3.1.4 {@code TileReactorCore.ReactorState} by name, plus {@link #UNKNOWN} for a state this mod does not know. */
    enum Phase {
        INVALID, COLD, WARMING_UP, RUNNING, STOPPING, COOLING, BEYOND_HOPE, UNKNOWN;

        public boolean online() {
            return this == WARMING_UP || this == RUNNING;
        }

        public static Phase byName(String name) {
            for (Phase p : values()) {
                if (p.name().equals(name)) return p;
            }
            return UNKNOWN;
        }
    }

    Phase phase();

    /** {@code reactableFuel}: unconverted awakened draconium, in DE units (nugget 16, ingot 144, block 1296). */
    double fuel();

    /** {@code convertedFuel}: chaos, same units. */
    double chaos();

    /** Asks the reactor to shut down; DE only accepts it while WARMING_UP or RUNNING. */
    void shutdown();

    /** Adds unconverted fuel. Callers check {@link ReactorFuel#room} and the COLD phase first. */
    void addFuel(int amount);

    /** Removes chaos. Callers check the COLD phase and the amount first. */
    void removeChaos(int amount);
}
