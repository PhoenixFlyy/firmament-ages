package dev.firmages.core.proof;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.firmages.core.age.AgeId;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Criterion {@code firmages:tally} (SPEC §22.2): the player's team pushed at least {@code count} items of {@code item}
 * (an item id) or {@code tag} (an item tag, without {@code #}) through its Tally Chutes, counting only what passed in
 * {@code age} or a later Age when {@code age} is given. Exactly one of {@code item} and {@code tag}.
 * <pre>{"trigger": "firmages:tally", "conditions": {"tag": "c:dusts", "count": 128, "age": "age_4"}}</pre>
 * Fired by {@link TallyService} for every online member of a team whose tally changed (at most once a second) and
 * at login.
 */
public final class TallyTrigger extends SimpleCriterionTrigger<TallyTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, TallyCounts counts) {
        trigger(player, inst -> inst.matches(counts));
    }

    public record Instance(Optional<ContextAwarePredicate> player, Optional<ResourceLocation> item, Optional<ResourceLocation> tag, int count,
                           Optional<AgeId> age) implements SimpleCriterionTrigger.SimpleInstance {
        private static final Codec<AgeId> AGE = Codec.STRING.comapFlatMap(
            s -> AgeId.byId(s).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "unknown Age " + s)), AgeId::id);
        public static final Codec<Instance> CODEC = RecordCodecBuilder.<Instance>create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                ResourceLocation.CODEC.optionalFieldOf("item").forGetter(Instance::item),
                ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(Instance::tag),
                ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(Instance::count),
                AGE.optionalFieldOf("age").forGetter(Instance::age))
            .apply(i, Instance::new))
            .validate(inst -> inst.item.isPresent() == inst.tag.isPresent()
                ? DataResult.error(() -> "firmages:tally needs exactly one of item and tag")
                : DataResult.success(inst));

        public boolean matches(TallyCounts counts) {
            return counts.sum(itemFilter(), age.orElse(null)) >= count;
        }

        /** Item ids this criterion counts. */
        public Predicate<String> itemFilter() {
            if (item.isPresent()) {
                String id = item.get().toString();
                return id::equals;
            }
            TagKey<Item> key = TagKey.create(Registries.ITEM, tag.orElseThrow());
            return id -> {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                return rl != null && BuiltInRegistries.ITEM.getOptional(rl).map(it -> it.builtInRegistryHolder().is(key)).orElse(false);
            };
        }
    }
}
