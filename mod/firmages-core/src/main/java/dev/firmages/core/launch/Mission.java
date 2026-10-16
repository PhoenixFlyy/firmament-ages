package dev.firmages.core.launch;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A probe away from its cradle (SPEC §31.5): from ignition until the returns are unloaded. Saved in the launch control
 * and, when the control is broken mid-flight, carried by its item as the data component {@code firmages:mission}; the
 * control placed again resumes it.
 *
 * @param phase     {@code ASCENT}, {@code FLIGHT} or {@code DESCENT}
 * @param phaseTime ticks spent in the phase (relative, so a resumed mission keeps its moment)
 * @param progress  flight work done
 * @param work      the module's {@code flight_work}
 * @param module    the module item id the flight burns for (the criterion counts it)
 * @param probe     the probe hull, never lost
 * @param returns   the fixed returns, taken from the module file at ignition
 */
public record Mission(LaunchRules.Phase phase, int phaseTime, long progress, int work, String module, ItemStack probe, List<ItemStack> returns) {
    private static final Codec<LaunchRules.Phase> PHASE = Codec.STRING.xmap(
        s -> {
            try {
                return LaunchRules.Phase.valueOf(s);
            } catch (IllegalArgumentException e) {
                return LaunchRules.Phase.FLIGHT;
            }
        }, LaunchRules.Phase::name);

    public static final Codec<Mission> CODEC = RecordCodecBuilder.create(i -> i.group(
            PHASE.fieldOf("phase").forGetter(Mission::phase),
            Codec.INT.fieldOf("phase_time").forGetter(Mission::phaseTime),
            Codec.LONG.fieldOf("progress").forGetter(Mission::progress),
            Codec.INT.fieldOf("work").forGetter(Mission::work),
            Codec.STRING.fieldOf("module").forGetter(Mission::module),
            ItemStack.OPTIONAL_CODEC.fieldOf("probe").forGetter(Mission::probe),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("returns").forGetter(Mission::returns))
        .apply(i, Mission::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Mission> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public Mission {
        probe = probe.copy();
        returns = returns.stream().map(ItemStack::copy).toList();
    }

    public Mission with(LaunchRules.Phase phase, int phaseTime, long progress) {
        return new Mission(phase, phaseTime, progress, work, module, probe, returns);
    }

    /** The probe has landed: the descent is over and only the unloading is left. */
    public boolean landed() {
        return phase == LaunchRules.Phase.DESCENT && phaseTime >= LaunchRules.DESCENT_TICKS;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Mission m)) return false;
        if (phase != m.phase || phaseTime != m.phaseTime || progress != m.progress || work != m.work || !module.equals(m.module)) return false;
        if (!ItemStack.matches(probe, m.probe) || returns.size() != m.returns.size()) return false;
        for (int k = 0; k < returns.size(); k++) if (!ItemStack.matches(returns.get(k), m.returns.get(k))) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(phase, phaseTime, progress, work, module);
    }
}
