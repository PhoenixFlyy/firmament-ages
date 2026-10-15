package dev.firmages.core.sky;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Starfall's blocks, items, block entity, sounds and loot condition (SPEC §25.4, §25.5, §25.9). The metals are plain
 * items: meteoric iron is forged on the TFC anvil (heat data and anvil recipes are pack data), starsteel is worked in
 * the IE arc furnace. No item or block here is in {@code #c:ores}, {@code #c:raw_materials} or {@code #c:stones}.
 */
public final class SkyRegistry {
    private SkyRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FirmagesCore.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FirmagesCore.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FirmagesCore.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, FirmagesCore.MOD_ID);
    public static final DeferredRegister<LootItemConditionType> LOOT_CONDITIONS = DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, FirmagesCore.MOD_ID);

    /** Dark fused stone of the crater shell; a Dawn building block (pickaxe). */
    public static final DeferredBlock<Block> SKY_ROCK = BLOCKS.register("sky_rock", () -> new Block(BlockBehaviour.Properties.of()
        .mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.BASEDRUM).strength(2.5F, 9.0F).requiresCorrectToolForDrops()
        .sound(SoundType.BASALT)));
    /** Meteoric iron mass: wrought iron tools and up (vanilla {@code needs_diamond_tool}). */
    public static final DeferredBlock<Block> METEORITE = BLOCKS.register("meteorite", () -> new Block(BlockBehaviour.Properties.of()
        .mapColor(MapColor.COLOR_BROWN).instrument(NoteBlockInstrument.BASEDRUM).strength(5.0F, 12.0F).requiresCorrectToolForDrops()
        .sound(SoundType.ANCIENT_DEBRIS)));
    /** Starsteel mass of the Electric Age falls: steel tools and up ({@code incorrect_for_diamond_tool}). */
    public static final DeferredBlock<Block> STARSTEEL_METEORITE = BLOCKS.register("starsteel_meteorite", () -> new Block(BlockBehaviour.Properties.of()
        .mapColor(MapColor.COLOR_LIGHT_BLUE).instrument(NoteBlockInstrument.BASEDRUM).strength(6.0F, 12.0F).requiresCorrectToolForDrops()
        .sound(SoundType.ANCIENT_DEBRIS)));
    public static final DeferredBlock<StarfallCoreBlock> STARFALL_CORE = BLOCKS.register("starfall_core", () -> new StarfallCoreBlock(BlockBehaviour.Properties.of()
        .mapColor(MapColor.FIRE).instrument(NoteBlockInstrument.BASEDRUM).strength(6.0F, 12.0F).requiresCorrectToolForDrops()
        .sound(SoundType.ANCIENT_DEBRIS).lightLevel(s -> s.getValue(StarfallCoreBlock.HOT) ? 15 : 0)));
    public static final DeferredBlock<Block> METEORIC_IRON_BLOCK = BLOCKS.register("meteoric_iron_block", () -> new Block(BlockBehaviour.Properties.of()
        .mapColor(MapColor.METAL).instrument(NoteBlockInstrument.IRON_XYLOPHONE).strength(5.0F, 6.0F).requiresCorrectToolForDrops()
        .sound(SoundType.METAL)));
    public static final DeferredBlock<Block> STARSTEEL_BLOCK = BLOCKS.register("starsteel_block", () -> new Block(BlockBehaviour.Properties.of()
        .mapColor(MapColor.COLOR_LIGHT_BLUE).instrument(NoteBlockInstrument.IRON_XYLOPHONE).strength(5.0F, 6.0F).requiresCorrectToolForDrops()
        .sound(SoundType.NETHERITE_BLOCK)));

    public static final DeferredItem<BlockItem> SKY_ROCK_ITEM = ITEMS.registerSimpleBlockItem("sky_rock", SKY_ROCK);
    public static final DeferredItem<BlockItem> METEORITE_ITEM = ITEMS.registerSimpleBlockItem("meteorite", METEORITE);
    public static final DeferredItem<BlockItem> STARSTEEL_METEORITE_ITEM = ITEMS.registerSimpleBlockItem("starsteel_meteorite", STARSTEEL_METEORITE);
    public static final DeferredItem<BlockItem> STARFALL_CORE_ITEM = ITEMS.register("starfall_core",
        () -> new BlockItem(STARFALL_CORE.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<BlockItem> METEORIC_IRON_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("meteoric_iron_block", METEORIC_IRON_BLOCK);
    public static final DeferredItem<BlockItem> STARSTEEL_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("starsteel_block", STARSTEEL_BLOCK);

    public static final DeferredItem<Item> METEORIC_IRON_CHUNK = ITEMS.registerSimpleItem("meteoric_iron_chunk");
    public static final DeferredItem<Item> METEORIC_IRON_INGOT = ITEMS.registerSimpleItem("meteoric_iron_ingot");
    public static final DeferredItem<Item> METEORIC_IRON_DOUBLE_INGOT = ITEMS.registerSimpleItem("meteoric_iron_double_ingot");
    public static final DeferredItem<Item> METEORIC_IRON_SHEET = ITEMS.registerSimpleItem("meteoric_iron_sheet");
    public static final DeferredItem<Item> METEORIC_IRON_ROD = ITEMS.registerSimpleItem("meteoric_iron_rod");
    public static final DeferredItem<Item> STARSTEEL_CHUNK = ITEMS.registerSimpleItem("starsteel_chunk", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> STARSTEEL_INGOT = ITEMS.registerSimpleItem("starsteel_ingot", new Item.Properties().rarity(Rarity.UNCOMMON));

    @SuppressWarnings("DataFlowIssue") // the data fixer type is null by design for mod block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StarfallCoreBlockEntity>> STARFALL_CORE_BE = BLOCK_ENTITIES.register("starfall_core",
        () -> BlockEntityType.Builder.of(StarfallCoreBlockEntity::new, STARFALL_CORE.get()).build(null));

    // sounds.json maps them onto vanilla sound events (as SPEC §8.1)
    public static final DeferredHolder<SoundEvent, SoundEvent> STARFALL_IMPACT = sound("starfall.impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> STARFALL_DISTANT = sound("starfall.distant");

    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> STARFALL_HOT = LOOT_CONDITIONS.register("starfall_hot",
        () -> new LootItemConditionType(StarfallHotCondition.CODEC));

    /** Every Starfall item, for the sky self-test (none may be an ore or raw material). */
    public static List<DeferredItem<? extends Item>> items() {
        return List.of(SKY_ROCK_ITEM, METEORITE_ITEM, STARSTEEL_METEORITE_ITEM, STARFALL_CORE_ITEM, METEORIC_IRON_BLOCK_ITEM, STARSTEEL_BLOCK_ITEM,
            METEORIC_IRON_CHUNK, METEORIC_IRON_INGOT, METEORIC_IRON_DOUBLE_INGOT, METEORIC_IRON_SHEET, METEORIC_IRON_ROD, STARSTEEL_CHUNK, STARSTEEL_INGOT);
    }

    /** Every Starfall block (crater blocks and storage blocks). */
    public static List<DeferredBlock<? extends Block>> blocks() {
        return List.of(SKY_ROCK, METEORITE, STARSTEEL_METEORITE, STARFALL_CORE, METEORIC_IRON_BLOCK, STARSTEEL_BLOCK);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, name)));
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        LOOT_CONDITIONS.register(modBus);
        modBus.addListener(SkyRegistry::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(SKY_ROCK_ITEM.get());
            event.accept(METEORIC_IRON_BLOCK_ITEM.get());
            event.accept(STARSTEEL_BLOCK_ITEM.get());
        }
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(METEORITE_ITEM.get());
            event.accept(STARSTEEL_METEORITE_ITEM.get());
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            for (var i : List.of(METEORIC_IRON_CHUNK, METEORIC_IRON_INGOT, METEORIC_IRON_DOUBLE_INGOT, METEORIC_IRON_SHEET, METEORIC_IRON_ROD,
                STARSTEEL_CHUNK, STARSTEEL_INGOT)) event.accept(i.get());
        }
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS && event.hasPermissions()) event.accept(STARFALL_CORE_ITEM.get());
    }
}
