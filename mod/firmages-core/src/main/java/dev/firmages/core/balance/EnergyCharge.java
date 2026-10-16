package dev.firmages.core.balance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /firmages energy charge <pos> <FE per tick> <seconds>} (SPEC §38, debug.allowSimulate): a test power line.
 * Every server tick it offers the block the given FE through its energy capability (null side, then each side), as a
 * cable would, so a copy boot can run a machine without a generator. What a block takes this way counts as "in" in an
 * energy audit.
 */
public final class EnergyCharge {
    private record Line(ResourceKey<Level> dimension, BlockPos pos, int fePerTick, long until) {}

    private static final List<Line> LINES = new ArrayList<>();

    private EnergyCharge() {}

    public static void register(IEventBus bus) {
        bus.addListener(EnergyCharge::onTick);
    }

    public static void start(ServerLevel level, BlockPos pos, int fePerTick, int seconds) {
        synchronized (LINES) {
            LINES.removeIf(l -> l.dimension() == level.dimension() && l.pos().equals(pos));
            LINES.add(new Line(level.dimension(), pos.immutable(), fePerTick, level.getServer().getTickCount() + seconds * 20L));
        }
    }

    private static void onTick(ServerTickEvent.Post event) {
        synchronized (LINES) {
            if (LINES.isEmpty()) return;
            long now = event.getServer().getTickCount();
            LINES.removeIf(l -> now > l.until());
            for (Line l : LINES) {
                ServerLevel level = event.getServer().getLevel(l.dimension());
                if (level == null || !level.isLoaded(l.pos())) continue;
                IEnergyStorage e = level.getCapability(Capabilities.EnergyStorage.BLOCK, l.pos(), null);
                for (Direction d : Direction.values()) {
                    if (e != null && e.canReceive()) break;
                    e = level.getCapability(Capabilities.EnergyStorage.BLOCK, l.pos(), d);
                }
                if (e != null) e.receiveEnergy(l.fePerTick(), false);
            }
        }
    }
}
