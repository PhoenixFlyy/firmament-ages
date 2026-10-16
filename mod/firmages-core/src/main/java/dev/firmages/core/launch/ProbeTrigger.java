package dev.firmages.core.launch;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;

import java.util.Map;
import java.util.Optional;

/**
 * Criterion {@code firmages:probe} (SPEC §31.6): the player's team brought at least {@code count} probes home, counting
 * only flights of {@code module} (a module item id) when it is given.
 * <pre>{"trigger": "firmages:probe", "conditions": {"module": "firmages:sampler_module", "count": 16}}</pre>
 * Fired by {@link LaunchService} for every online member of a team whose count changed (at most once a second) and at
 * login. A crewed rocket never fires it ({@code firmages:silo_launch} stays the crewed criterion).
 */
public final class ProbeTrigger extends SimpleCriterionTrigger<ProbeTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, Map<String, Long> counts) {
        trigger(player, inst -> inst.matches(counts));
    }

    public record Instance(Optional<ContextAwarePredicate> player, Optional<ResourceLocation> module, int count)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                ResourceLocation.CODEC.optionalFieldOf("module").forGetter(Instance::module),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(Instance::count))
            .apply(i, Instance::new));

        public boolean matches(Map<String, Long> counts) {
            return LaunchCounts.sum(counts, module.map(ResourceLocation::toString).orElse(null)) >= count;
        }
    }
}
