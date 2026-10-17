package dev.firmages.core.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Global loot modifier {@code firmages:age_filter} (SPEC §19.5): removes from every generated loot (chests, barrels,
 * chest minecarts, archaeology, fishing, mob and block drops) each stack whose item belongs to an Age the roll's
 * player (or, without one, the server) does not hold, and every item of {@code age_items/disabled}. Listed by the
 * jar's {@code data/neoforge/loot_modifiers/global_loot_modifiers.json}; {@code mixin/LootModifierManagerMixin} runs it
 * after every other modifier (NeoForge 21.1 keeps the modifiers in hash order), so the One Iron swap and the items
 * other modifiers add are judged too. Idempotent.
 */
public final class AgeLootModifier extends LootModifier {
    public static final MapCodec<AgeLootModifier> CODEC = RecordCodecBuilder.mapCodec(i -> codecStart(i).apply(i, AgeLootModifier::new));

    public AgeLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        AgeLoot.filter(generatedLoot, context);
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
