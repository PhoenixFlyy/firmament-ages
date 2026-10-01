package dev.firmages.core.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Finds the reactor behind a block: a provider maps a reactor part (DE stabilizer or injector) to the
 * {@link ReactorView} of its bound core. Draconic Evolution registers its provider at common setup when it is
 * loaded ({@code compat/draconic/DraconicReactors}); GameTests may add a stub.
 */
public final class ReactorLookup {
    @FunctionalInterface
    public interface Provider {
        /** The reactor whose part sits at {@code pos}, or null if the block is no bound reactor part. */
        @Nullable ReactorView at(Level level, BlockPos pos);
    }

    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    private ReactorLookup() {}

    public static void addProvider(Provider p) {
        PROVIDERS.add(p);
    }

    public static void removeProvider(Provider p) {
        PROVIDERS.remove(p);
    }

    @Nullable
    public static ReactorView at(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        for (Provider p : PROVIDERS) {
            ReactorView v = p.at(level, pos);
            if (v != null) return v;
        }
        return null;
    }

    /** The reactor of the first touching part (the controller's view), or null. */
    @Nullable
    public static ReactorView touching(Level level, BlockPos controller) {
        for (Direction d : Direction.values()) {
            ReactorView v = at(level, controller.relative(d));
            if (v != null) return v;
        }
        return null;
    }

    public static boolean available() {
        return !PROVIDERS.isEmpty();
    }
}
