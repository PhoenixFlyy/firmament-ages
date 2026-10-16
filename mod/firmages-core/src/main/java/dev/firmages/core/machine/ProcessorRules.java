package dev.firmages.core.machine;

/**
 * The Ore Processor's operation as pure logic (SPEC §40.4), so it runs in unit tests without a world. An operation
 * starts when the input, the chemical, the working fluid and the room for the result are there; it takes the lubricant
 * at its start if the tank holds enough, and runs {@code lubricantSpeed} times faster with it. Every tick of work draws
 * {@code fePerTick x speed} FE, so an operation costs {@code fePerTick x time} FE either way: the lubricant buys speed,
 * never metal or power. Without that much FE in the buffer the operation waits and keeps its progress.
 */
public final class ProcessorRules {
    private ProcessorRules() {}

    /** Why the processor works or waits (Jade line, right-click line). */
    public enum Reason {
        IDLE("idle"), NO_RECIPE("no_recipe"), NEEDS_CHEMICAL("needs_chemical"), NEEDS_FLUID("needs_fluid"), OUTPUT_FULL("output_full"),
        NO_POWER("no_power"), WORKING("working");

        private final String key;

        Reason(String key) {
            this.key = key;
        }

        /** Lang key suffix: {@code firmages.processor.reason.<key>}. */
        public String key() {
            return key;
        }

        public static Reason byOrdinal(int o) {
            Reason[] all = values();
            return o >= 0 && o < all.length ? all[o] : IDLE;
        }
    }

    /** The operation in progress: progress in ticks of base speed, whether it started, whether it runs lubricated. */
    public record State(double progress, boolean started, boolean lubricated) {
        public static final State IDLE = new State(0, false, false);
    }

    /**
     * What the processor holds this tick.
     *
     * @param inputEmpty     the input slot is empty
     * @param hasRecipe      a recipe takes the input and the slot holds at least its count
     * @param chemicalReady  the chemical tank holds the recipe's chemical and amount
     * @param fluidReady     the fluid tank holds the recipe's fluid and amount
     * @param outputFits     the result fits into the output slots
     * @param lubricantReady the recipe has a lubricant and the lubricant tank holds its amount
     */
    public record View(boolean inputEmpty, boolean hasRecipe, boolean chemicalReady, boolean fluidReady, boolean outputFits,
                       boolean lubricantReady, long energy, long fePerTick, double lubricantSpeed, int time) {}

    /**
     * @param takeLubricant take the recipe's lubricant now (the operation starts lubricated)
     * @param done          the operation completes: take the input, chemical and fluid, put the result out
     */
    public record Step(State next, Reason reason, long energyUsed, boolean takeLubricant, boolean done) {}

    public static Step tick(State s, View v) {
        if (v.inputEmpty()) return new Step(State.IDLE, Reason.IDLE, 0, false, false);
        if (!v.hasRecipe()) return new Step(State.IDLE, Reason.NO_RECIPE, 0, false, false);
        if (!v.chemicalReady()) return new Step(s, Reason.NEEDS_CHEMICAL, 0, false, false);
        if (!v.fluidReady()) return new Step(s, Reason.NEEDS_FLUID, 0, false, false);
        if (!v.outputFits()) return new Step(s, Reason.OUTPUT_FULL, 0, false, false);
        boolean take = false;
        State cur = s;
        if (!s.started()) {
            take = v.lubricantReady();
            cur = new State(s.progress(), true, take);
        }
        double speed = speed(cur.lubricated(), v.lubricantSpeed());
        long fe = energyPerTick(v.fePerTick(), speed);
        if (v.energy() < fe) return new Step(cur, Reason.NO_POWER, 0, take, false);
        double p = cur.progress() + speed;
        if (p >= v.time() - 1e-9) return new Step(State.IDLE, Reason.WORKING, fe, take, true);
        return new Step(new State(p, true, cur.lubricated()), Reason.WORKING, fe, take, false);
    }

    public static double speed(boolean lubricated, double lubricantSpeed) {
        return lubricated ? Math.max(1.0, lubricantSpeed) : 1.0;
    }

    public static long energyPerTick(long fePerTick, double speed) {
        return Math.round(fePerTick * speed);
    }

    /** Percent of the operation done (0..100). */
    public static int percent(double progress, int time) {
        return time <= 0 ? 0 : (int) Math.max(0, Math.min(100, Math.floor(progress * 100.0 / time)));
    }
}
