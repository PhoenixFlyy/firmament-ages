package dev.firmages.core.compat.create;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlockEntity;
import dev.firmages.core.balance.FuelShare;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Felix H1A: which rotation of a Create kinetic network is fuelled (SPEC §34). Create 6.0.10 [verified, javap]:
 * {@code KineticNetwork#sources} maps every generating block entity to its capacity, {@code getActualCapacityOf} scales
 * it by speed. Fuelled count:
 * <ul>
 *   <li>a steam engine's powered shaft whose boiler has active heat ({@code BoilerData#activeHeat > 0}: a fuelled blaze
 *       burner; passive heat does not count);</li>
 *   <li>the engines of Create: Diesel Generators (normal, modular, huge), which turn only while they burn fuel.</li>
 * </ul>
 * Everything else is not fuelled: water wheel, windmill, hand crank, the Crafts and Additions electric motor (FE to SU
 * to FE), TFC rotation through the torque link, creative motor. The share is cached per network once per tick.
 */
public final class AlternatorFuel {
    private static final String DIESEL_PACKAGE = "com.jesz.createdieselgenerators.";
    private static final Map<Long, long[]> CACHE = new HashMap<>(); // network id -> {game time, share bits}

    private AlternatorFuel() {}

    /** The fuelled share of the network {@code be} turns in, 0 to 1 (0 without a network). Server side only. */
    public static float share(KineticBlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide() || !be.hasNetwork()) return 0;
        long now = level.getGameTime();
        Long id = be.network;
        long[] hit = CACHE.get(id);
        if (hit != null && hit[0] == now) return Float.intBitsToFloat((int) hit[1]);
        KineticNetwork net = be.getOrCreateNetwork();
        float share = net == null ? 0 : share(net);
        if (CACHE.size() > 4096) CACHE.clear();
        CACHE.put(id, new long[] {now, Float.floatToIntBits(share)});
        return share;
    }

    private static float share(KineticNetwork net) {
        List<KineticBlockEntity> sources = new ArrayList<>(net.sources.keySet());
        float[] capacity = new float[sources.size()];
        boolean[] fuelled = new boolean[sources.size()];
        for (int i = 0; i < sources.size(); i++) {
            KineticBlockEntity s = sources.get(i);
            capacity[i] = net.sources.containsKey(s) ? Math.abs(net.getActualCapacityOf(s)) : 0;
            fuelled[i] = fuelled(s);
        }
        return FuelShare.share(capacity, fuelled);
    }

    /** {@code /firmages balance alternator <pos>}: the network's sources and the fuelled share at {@code pos}, or null. */
    public static String describe(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof KineticBlockEntity be)) return null;
        StringBuilder out = new StringBuilder();
        out.append(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock())).append(" at ").append(pos.toShortString())
            .append(", speed ").append(be.getSpeed());
        if (!be.hasNetwork()) return out.append(", no network").toString();
        KineticNetwork network = be.getOrCreateNetwork();
        for (KineticBlockEntity s : new ArrayList<>(network.sources.keySet())) {
            out.append("; source ").append(BuiltInRegistries.BLOCK.getKey(s.getBlockState().getBlock()))
                .append(" capacity ").append(network.sources.containsKey(s) ? Math.abs(network.getActualCapacityOf(s)) : 0)
                .append(fuelled(s) ? " fuelled" : " not fuelled");
        }
        return out.append("; fuelled share ").append(share(network)).toString();
    }

    /** True for a source that turns only while it burns fuel. */
    public static boolean fuelled(KineticBlockEntity source) {
        if (source.getClass().getName().startsWith(DIESEL_PACKAGE)) return true;
        if (source instanceof PoweredShaftBlockEntity shaft && shaft.enginePos != null && source.getLevel() != null) {
            // Create 6.0.10 keeps the engine as an offset (shaft minus engine, PoweredShaftBlockEntity.update [javap]);
            // read as an absolute position it never found the engine, so no boiler ever counted as fuelled (round H3)
            BlockEntity engine = source.getLevel().getBlockEntity(shaft.getBlockPos().subtract(shaft.enginePos));
            if (engine instanceof SteamEngineBlockEntity steam) {
                FluidTankBlockEntity tank = steam.getTank();
                if (tank != null) {
                    FluidTankBlockEntity controller = tank.getControllerBE();
                    FluidTankBlockEntity boilerTank = controller != null ? controller : tank;
                    return boilerTank.boiler != null && boilerTank.boiler.activeHeat > 0;
                }
            }
        }
        return false;
    }
}
