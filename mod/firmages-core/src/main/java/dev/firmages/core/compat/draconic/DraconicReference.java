package dev.firmages.core.compat.draconic;

import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorComponent;
import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorCore;
import com.brandon3055.draconicevolution.blocks.tileentity.flowgate.TileFlowGate;
import com.brandon3055.draconicevolution.init.DEContent;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Draconic Evolution steps and probes of the energy reference runs ({@code balance/ReferenceRun}, harmony package A8).
 * {@code de_build} builds the reactor around a core (stabilizers and injector facing it, as the GameTest does);
 * {@code de_reactor} fuels a formed reactor and brings it to a running state the way charging would (Draconic
 * Evolution 3.1.4 [verified, javap {@code TileReactorCore.initializeStartup}]: saturation capacity = fuel x 96,450.6,
 * shield capacity = fuel x 9,645.06); from then on the reactor's own code runs it. {@code de_gate} sets a flux gate's
 * flow override (the extraction a player sets). The probe reads state, temperature, saturation, shield and the rates.
 */
public final class DraconicReference {
    private static final double SATURATION_PER_FUEL = 96.45061728395062 * 1000;
    private static final double SHIELD_PER_FUEL = 96.45061728395062 * 100;

    private DraconicReference() {}

    @Nullable
    public static String step(String op, JsonObject s, ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        switch (op) {
            case "de_build": {
                // the core at pos, four stabilizers 5 blocks out on the horizontal axes and an injector 5 above, all
                // facing the core (DE keeps the facing in its managed data, not in the block state)
                int d = s.has("distance") ? s.get("distance").getAsInt() : 5;
                level.setBlockAndUpdate(pos, DEContent.REACTOR_CORE.get().defaultBlockState());
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos p = pos.relative(dir, d);
                    level.setBlockAndUpdate(p, DEContent.REACTOR_STABILIZER.get().defaultBlockState());
                    if (level.getBlockEntity(p) instanceof TileReactorComponent c) c.facing.set(dir.getOpposite());
                }
                BlockPos inj = pos.above(d);
                level.setBlockAndUpdate(inj, DEContent.REACTOR_INJECTOR.get().defaultBlockState());
                if (level.getBlockEntity(inj) instanceof TileReactorComponent c) c.facing.set(Direction.DOWN);
                if (!(level.getBlockEntity(pos) instanceof TileReactorCore core)) return "no core placed";
                core.attemptInitialization();
                return "formed " + core.structureValid.get() + (core.structureValid.get() ? "" : ": " + core.structureError.get());
            }
            case "de_reactor": {
                if (!(be instanceof TileReactorCore core)) return "no reactor core";
                if (!core.structureValid.get()) core.attemptInitialization();
                if (!core.structureValid.get()) return "structure invalid: " + core.structureError.get();
                double fuel = s.has("fuel") ? s.get("fuel").getAsDouble() : core.reactableFuel.get();
                core.reactableFuel.set(fuel);
                core.convertedFuel.set(s.has("converted") ? s.get("converted").getAsDouble() : 0);
                double total = core.reactableFuel.get() + core.convertedFuel.get();
                core.maxSaturation.set((long) (total * SATURATION_PER_FUEL));
                core.maxShieldCharge.set(total * SHIELD_PER_FUEL);
                core.saturation.set((long) (core.maxSaturation.get() * (s.has("saturation") ? s.get("saturation").getAsDouble() : 0.5)));
                core.shieldCharge.set(core.maxShieldCharge.get() * (s.has("shield") ? s.get("shield").getAsDouble() : 0.5));
                core.temperature.set(s.has("temperature") ? s.get("temperature").getAsDouble() : 2000);
                core.startupInitialized.set(true);
                if (s.has("start") && s.get("start").getAsBoolean()) core.reactorState.set(TileReactorCore.ReactorState.RUNNING);
                return "state " + core.reactorState.get() + ", fuel " + total + ", max saturation " + core.maxSaturation.get();
            }
            case "de_feed": {
                // the field injector's energy, straight through the core as the injector hands it over (DE 3.1.4
                // [javap TileReactorCore.injectEnergy]: while RUNNING the shield gains energy x (1 - charge), so a
                // field held at half charge needs twice its drain); the run subtracts every FE fed (ReferenceRun.fed)
                if (!(be instanceof TileReactorCore core)) return "no reactor core";
                long want = (long) Math.ceil(core.fieldDrain.get() * (s.has("factor") ? s.get("factor").getAsDouble() : 2.0));
                core.injectEnergy(want);
                dev.firmages.core.balance.ReferenceRun.fed(want);
                return "fed " + want;
            }
            case "de_gate": {
                if (!(be instanceof TileFlowGate gate)) return "no flux gate";
                gate.flowOverride.set(s.get("flow").getAsLong());
                gate.flowOverridden.set(true);
                return "flow " + gate.getFlow();
            }
            default:
                return null;
        }
    }

    @Nullable
    public static String probe(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof TileReactorCore c)) return null;
        double sat = c.maxSaturation.get() == 0 ? 0 : c.saturation.get() / (double) c.maxSaturation.get();
        double shield = c.maxShieldCharge.get() == 0 ? 0 : c.shieldCharge.get() / c.maxShieldCharge.get();
        return String.format(Locale.ROOT, "DE reactor: %s, temperature %.1f, saturation %.2f %% of %d, shield %.2f %%, generation %.0f RF/t, "
                + "field drain %d, fuel use %.4f, fuel %.1f, converted %.1f",
            c.reactorState.get(), c.temperature.get(), sat * 100, c.maxSaturation.get(), shield * 100, c.generationRate.get(), c.fieldDrain.get(),
            c.fuelUseRate.get(), c.reactableFuel.get(), c.convertedFuel.get());
    }
}
