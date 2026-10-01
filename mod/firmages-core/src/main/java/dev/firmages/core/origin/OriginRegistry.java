package dev.firmages.core.origin;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Origin (SPEC §16): its dimension key and the altar block of the Gathering. */
public final class OriginRegistry {
    private OriginRegistry() {}

    public static final ResourceLocation ORIGIN_ID = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "origin");
    public static final ResourceKey<Level> ORIGIN = ResourceKey.create(Registries.DIMENSION, ORIGIN_ID);

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FirmagesCore.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FirmagesCore.MOD_ID);

    /** Unbreakable in survival like bedrock; it is part of the arena, never crafted. */
    public static final DeferredBlock<Block> ORIGIN_ALTAR = BLOCKS.register("origin_altar", () -> new Block(
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3600000.0F).noLootTable()
            .sound(SoundType.LODESTONE).lightLevel(s -> 12).pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<BlockItem> ORIGIN_ALTAR_ITEM = ITEMS.register("origin_altar",
        () -> new BlockItem(ORIGIN_ALTAR.get(), new Item.Properties().rarity(Rarity.EPIC)));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
