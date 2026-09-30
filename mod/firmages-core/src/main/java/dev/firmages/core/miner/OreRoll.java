package dev.firmages.core.miner;

import java.util.function.Predicate;
import java.util.function.Supplier;

/** The m3 miner roll rule, pure Java (unit-testable): keep an unlocked roll, else take a spoil. */
public final class OreRoll {
    private OreRoll() {}

    public static <T> T pick(T rolled, Predicate<T> locked, Supplier<T> spoil) {
        return rolled != null && locked.test(rolled) ? spoil.get() : rolled;
    }
}
