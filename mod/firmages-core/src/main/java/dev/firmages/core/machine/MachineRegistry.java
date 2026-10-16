package dev.firmages.core.machine;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The pack's own machines of the harmony plan's end game (SPEC §40, §41): the Ore Processor of the Singularity Age
 * with its recipe type {@code firmages:ore_processing}, and the Arcane Catalyst of the Information Age with the block
 * tag {@code firmages:catalysable} of the machines it can quicken.
 */
public final class MachineRegistry {
    private MachineRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FirmagesCore.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FirmagesCore.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FirmagesCore.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, FirmagesCore.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, FirmagesCore.MOD_ID);

    /** Machines the Arcane Catalyst quickens (the pack lists Immersive Engineering and Mekanism machines, never a generator). */
    public static final TagKey<Block> CATALYSABLE = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "catalysable"));

    public static final DeferredBlock<OreProcessorBlock> ORE_PROCESSOR = BLOCKS.register("ore_processor", () -> new OreProcessorBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(6.0F, 1200.0F).requiresCorrectToolForDrops()
            .sound(SoundType.NETHERITE_BLOCK).lightLevel(s -> s.getValue(OreProcessorBlock.ACTIVE) ? 9 : 2)));
    public static final DeferredBlock<ArcaneCatalystBlock> ARCANE_CATALYST = BLOCKS.register("arcane_catalyst", () -> new ArcaneCatalystBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(4.0F, 600.0F).requiresCorrectToolForDrops()
            .sound(SoundType.AMETHYST).lightLevel(s -> s.getValue(ArcaneCatalystBlock.LIT) ? 10 : 3)));

    public static final DeferredItem<BlockItem> ORE_PROCESSOR_ITEM = ITEMS.register("ore_processor",
        () -> new BlockItem(ORE_PROCESSOR.get(), new Item.Properties().rarity(Rarity.EPIC)));
    public static final DeferredItem<BlockItem> ARCANE_CATALYST_ITEM = ITEMS.register("arcane_catalyst",
        () -> new BlockItem(ARCANE_CATALYST.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));

    @SuppressWarnings("DataFlowIssue") // the data fixer type is null by design for mod block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OreProcessorBlockEntity>> ORE_PROCESSOR_BE = BLOCK_ENTITIES.register("ore_processor",
        () -> BlockEntityType.Builder.of(OreProcessorBlockEntity::new, ORE_PROCESSOR.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcaneCatalystBlockEntity>> ARCANE_CATALYST_BE = BLOCK_ENTITIES.register("arcane_catalyst",
        () -> BlockEntityType.Builder.of(ArcaneCatalystBlockEntity::new, ARCANE_CATALYST.get()).build(null));

    public static final DeferredHolder<RecipeType<?>, RecipeType<OreProcessingRecipe>> ORE_PROCESSING_TYPE = RECIPE_TYPES.register("ore_processing",
        () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "ore_processing")));
    public static final DeferredHolder<RecipeSerializer<?>, OreProcessingRecipe.Serializer> ORE_PROCESSING_SERIALIZER =
        RECIPE_SERIALIZERS.register("ore_processing", OreProcessingRecipe.Serializer::new);

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        modBus.addListener(MachineRegistry::capabilities);
        modBus.addListener(MachineRegistry::creativeTab);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ORE_PROCESSOR_BE.get(), (be, side) -> be.itemHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ORE_PROCESSOR_BE.get(), (be, side) -> be.fluidHandler());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ORE_PROCESSOR_BE.get(), (be, side) -> be.energyHandler());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ARCANE_CATALYST_BE.get(),
            (be, side) -> side == be.facing() ? null : be.energyHandler());
        if (ModList.get().isLoaded("mekanism")) dev.firmages.core.compat.mekanism.ProcessorChemicals.register(event);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ORE_PROCESSOR_ITEM.get());
            event.accept(ARCANE_CATALYST_ITEM.get());
        }
    }
}
