package dev.firmages.core.balance;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Global loot modifier {@code firmages:replace_item} (SPEC §34.4, One Iron): every stack of {@code from} that a loot
 * table yields becomes the same count of {@code to}, components dropped. The pack's
 * {@code kubejs/data/firmages/loot_modifiers/one_iron_loot.json} turns the hidden vanilla iron ingot of other mods'
 * chests and mob drops into TFC wrought iron; without conditions it applies to every loot table.
 */
public final class ReplaceItemModifier extends LootModifier {
    public static final MapCodec<ReplaceItemModifier> CODEC = RecordCodecBuilder.mapCodec(i -> codecStart(i).and(i.group(
        BuiltInRegistries.ITEM.byNameCodec().fieldOf("from").forGetter(m -> m.from),
        BuiltInRegistries.ITEM.byNameCodec().fieldOf("to").forGetter(m -> m.to)
    )).apply(i, ReplaceItemModifier::new));

    private final Item from;
    private final Item to;

    public ReplaceItemModifier(LootItemCondition[] conditions, Item from, Item to) {
        super(conditions);
        this.from = from;
        this.to = to;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        for (int i = 0; i < generatedLoot.size(); i++) {
            ItemStack s = generatedLoot.get(i);
            if (s.is(from)) generatedLoot.set(i, new ItemStack(to, s.getCount()));
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
