package dev.firmages.core.proof;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Criterion {@code firmages:silo_launch} (SPEC §22.4): the player rode a rocket that launched from a silo, i.e. a pad at
 * least {@link SiloLaunch#DEPTH} blocks below the surrounding terrain whose shaft a moving Create hatch of
 * {@link SiloLaunch#HATCH_BLOCKS} or more blocks covered shortly before. No conditions besides the optional
 * {@code player} predicate: <pre>{"trigger": "firmages:silo_launch"}</pre>
 */
public final class SiloLaunchTrigger extends SimpleCriterionTrigger<SiloLaunchTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player) {
        trigger(player, inst -> true);
    }

    public record Instance(Optional<ContextAwarePredicate> player) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
            EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player)).apply(i, Instance::new));
    }
}
