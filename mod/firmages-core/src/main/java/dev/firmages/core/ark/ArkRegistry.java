package dev.firmages.core.ark;

import dev.firmages.core.FirmagesCore;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Archipelago's one block (SPEC §31.8): the island plinth, part of The Origin's arena from version 3 on. Like the
 * altar it is unbreakable, drops nothing and is never crafted (Age map age_9). It takes items by hand or by pipe
 * ({@code Capabilities.ItemHandler.BLOCK} without a block entity: the state lives in {@code OriginSavedData}).
 */
public final class ArkRegistry {
    private ArkRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FirmagesCore.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FirmagesCore.MOD_ID);

    public static final DeferredBlock<ArkPlinthBlock> ARK_PLINTH = BLOCKS.register("ark_plinth", () -> new ArkPlinthBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3600000.0F).noLootTable()
            .sound(SoundType.LODESTONE).lightLevel(s -> 9).pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<BlockItem> ARK_PLINTH_ITEM = ITEMS.register("ark_plinth",
        () -> new BlockItem(ARK_PLINTH.get(), new Item.Properties().rarity(Rarity.EPIC)));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(ArkRegistry::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) -> ArkPlinthInput.at(level, pos), ARK_PLINTH.get());
    }
}
