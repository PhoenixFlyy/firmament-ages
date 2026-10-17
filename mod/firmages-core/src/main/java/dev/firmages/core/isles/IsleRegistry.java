package dev.firmages.core.isles;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * The Drifting Isles' blocks, item, structure type, structure piece, loot function and sound (SPEC §30.4, §30.5,
 * §30.8). The crystal cluster and the lode drop crystal (the lode re-forms); their items exist only for the creative
 * pick-block (round G2: no survival source, no creative tab, hidden from the recipe viewers). Skystone is a Dawn
 * building block that drops itself. Nothing here is in {@code #c:ores}, {@code #c:raw_materials} or
 * {@code #c:stones}, and no block gives or takes FE.
 */
public final class IsleRegistry {
    private IsleRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FirmagesCore.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FirmagesCore.MOD_ID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, FirmagesCore.MOD_ID);
    public static final DeferredRegister<StructurePieceType> PIECE_TYPES = DeferredRegister.create(Registries.STRUCTURE_PIECE, FirmagesCore.MOD_ID);
    public static final DeferredRegister<LootItemFunctionType<?>> LOOT_FUNCTIONS = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, FirmagesCore.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, FirmagesCore.MOD_ID);

    /** Pale isle stone: lets sky light through and refuses mob spawns (SPEC §30.4). */
    public static final DeferredBlock<SkystoneBlock> SKYSTONE = BLOCKS.register("skystone", () -> new SkystoneBlock(BlockBehaviour.Properties.of()
        .mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.BASEDRUM).strength(1.5F, 6.0F).requiresCorrectToolForDrops()
        .sound(SoundType.CALCITE).isValidSpawn((state, level, pos, type) -> false)));
    /** A one-time crystal deposit: light 10, drops one firmament crystal (loot table). */
    public static final DeferredBlock<AmethystClusterBlock> CRYSTAL_CLUSTER = BLOCKS.register("firmament_crystal_cluster",
        () -> new AmethystClusterBlock(7.0F, 3.0F, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).forceSolidOn().noOcclusion()
            .strength(1.5F).sound(SoundType.AMETHYST_CLUSTER).lightLevel(s -> 10).pushReaction(PushReaction.DESTROY)));
    /** The re-forming lode (SPEC §30.5): hardness 50, immune to explosions, immovable. */
    public static final DeferredBlock<FirmamentLodeBlock> LODE = BLOCKS.register("firmament_lode", () -> new FirmamentLodeBlock(BlockBehaviour.Properties.of()
        .mapColor(MapColor.COLOR_LIGHT_BLUE).instrument(NoteBlockInstrument.BASEDRUM).strength(50.0F, 1200.0F).sound(SoundType.AMETHYST)
        .lightLevel(s -> 10).pushReaction(PushReaction.BLOCK)));

    public static final DeferredItem<BlockItem> SKYSTONE_ITEM = ITEMS.registerSimpleBlockItem("skystone", SKYSTONE);
    /** Pick-block only (round G2, SPEC §30.4). */
    public static final DeferredItem<BlockItem> CRYSTAL_CLUSTER_ITEM = ITEMS.registerSimpleBlockItem("firmament_crystal_cluster", CRYSTAL_CLUSTER);
    /** Pick-block only (round G2, SPEC §30.4). */
    public static final DeferredItem<BlockItem> LODE_ITEM = ITEMS.registerSimpleBlockItem("firmament_lode", LODE);
    public static final DeferredItem<Item> FIRMAMENT_CRYSTAL = ITEMS.registerSimpleItem("firmament_crystal", new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredHolder<StructureType<?>, StructureType<DriftingIsleStructure>> DRIFTING_ISLE = STRUCTURE_TYPES.register("drifting_isle",
        () -> () -> DriftingIsleStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> ISLE_PIECE = PIECE_TYPES.register("drifting_isle",
        () -> (StructurePieceType.ContextlessType) IslePiece::new);

    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<LodeYieldFunction>> LODE_YIELD = LOOT_FUNCTIONS.register("lode_yield",
        () -> new LootItemFunctionType<>(LodeYieldFunction.CODEC));

    /** The lode stands again (sounds.json maps it onto a vanilla chime). */
    public static final DeferredHolder<SoundEvent, SoundEvent> LODE_REFORM = SOUNDS.register("lode.reform",
        () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "lode.reform")));

    /** Every isle block, for the self-test (none may be an ore, a stone or a collapse block). */
    public static List<DeferredBlock<? extends Block>> blocks() {
        return List.of(SKYSTONE, CRYSTAL_CLUSTER, LODE);
    }

    public static List<DeferredItem<? extends Item>> items() {
        return List.of(SKYSTONE_ITEM, CRYSTAL_CLUSTER_ITEM, LODE_ITEM, FIRMAMENT_CRYSTAL);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        PIECE_TYPES.register(modBus);
        LOOT_FUNCTIONS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(IsleRegistry::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) event.accept(SKYSTONE_ITEM.get());
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) event.accept(FIRMAMENT_CRYSTAL.get());
    }
}
