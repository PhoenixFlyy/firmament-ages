package dev.firmages.core.compat.draconic;

import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorComponent;
import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorCore;
import com.brandon3055.draconicevolution.init.DEContent;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.reactor.ReactorFuelPort;
import dev.firmages.core.reactor.ReactorLookup;
import dev.firmages.core.reactor.ReactorView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Draconic Evolution 3.1.4 coupling of m4 (SPEC §6) [verified, javap]: a stabilizer or injector
 * ({@code TileReactorComponent}) resolves its core with {@code tryGetCore()} (null while unbound); the core keeps
 * fuel and chaos in the public {@code ManagedDouble} fields {@code reactableFuel} and {@code convertedFuel}, its
 * state in {@code reactorState}, and {@code shutdownReactor()} only acts while WARMING_UP or RUNNING. Only loaded
 * when Draconic Evolution is present.
 */
public final class DraconicReactors {
    private static boolean enabled;

    private DraconicReactors() {}

    /** Common setup: checks the DE members once and registers the provider; any linkage problem disables m4. */
    public static void init() {
        try {
            probe();
            ReactorLookup.addProvider(DraconicReactors::at);
            enabled = true;
            FirmagesCore.LOGGER.info("Draconic reactor coupling enabled (controller and fuel port)");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            enabled = false;
            FirmagesCore.LOGGER.error("Draconic reactor coupling disabled: Draconic Evolution's reactor API differs from 3.1.4 ({})", e.toString());
        }
    }

    private static void probe() throws ReflectiveOperationException {
        Class<?> core = TileReactorCore.class;
        core.getField("reactableFuel");
        core.getField("convertedFuel");
        core.getField("reactorState");
        core.getMethod("shutdownReactor");
        TileReactorComponent.class.getMethod("tryGetCore");
    }

    public static boolean enabled() {
        return enabled;
    }

    @Nullable
    static ReactorView at(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof TileReactorComponent part)) return null;
        TileReactorCore core = part.tryGetCore();
        return core == null || core.isRemoved() ? null : new View(core);
    }

    /** m4 capability on stabilizers and injectors (mod bus, before common setup). */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, DEContent.TILE_REACTOR_STABILIZER.get(),
            (be, side) -> new ReactorFuelPort(() -> enabled ? viewOf(be) : null));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, DEContent.TILE_REACTOR_INJECTOR.get(),
            (be, side) -> new ReactorFuelPort(() -> enabled ? viewOf(be) : null));
    }

    @Nullable
    private static ReactorView viewOf(TileReactorComponent part) {
        TileReactorCore core = part.tryGetCore();
        return core == null || core.isRemoved() ? null : new View(core);
    }

    private record View(TileReactorCore core) implements ReactorView {
        @Override
        public Phase phase() {
            return Phase.byName(core.reactorState.get().name());
        }

        @Override
        public double fuel() {
            return core.reactableFuel.get();
        }

        @Override
        public double chaos() {
            return core.convertedFuel.get();
        }

        @Override
        public void shutdown() {
            core.shutdownReactor();
        }

        @Override
        public void addFuel(int amount) {
            core.reactableFuel.add(amount);
            core.setChanged();
        }

        @Override
        public void removeChaos(int amount) {
            core.convertedFuel.subtract(amount);
            core.setChanged();
        }
    }
}
