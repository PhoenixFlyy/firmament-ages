package dev.firmages.core.isles;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.firmages.core.config.ServerConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;

/** Loot function {@code firmages:lode_yield}: sets the stack to {@code isles.lodeYield} (SPEC §30.5, default two). */
public final class LodeYieldFunction extends LootItemConditionalFunction {
    public static final MapCodec<LodeYieldFunction> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i).apply(i, LodeYieldFunction::new));

    LodeYieldFunction(List<LootItemCondition> conditions) {
        super(conditions);
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        stack.setCount(ServerConfig.islesLodeYield());
        return stack;
    }

    @Override
    public LootItemFunctionType<LodeYieldFunction> getType() {
        return IsleRegistry.LODE_YIELD.get();
    }
}
