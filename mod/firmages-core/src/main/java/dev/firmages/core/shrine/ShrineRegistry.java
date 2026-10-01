package dev.firmages.core.shrine;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Shrine blocks, items, block entities and sounds (SPEC §7.1). */
public final class ShrineRegistry {
    private ShrineRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FirmagesCore.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FirmagesCore.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FirmagesCore.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, FirmagesCore.MOD_ID);

    public static final DeferredBlock<ShrineHeartBlock> SHRINE_HEART = BLOCKS.register("shrine_heart", () -> new ShrineHeartBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(3.0F, 1200.0F).requiresCorrectToolForDrops()
            .sound(SoundType.DECORATED_POT).lightLevel(s -> s.getValue(ShrineHeartBlock.LIT) ? 12 : 3).pushReaction(PushReaction.BLOCK).noOcclusion()));
    public static final DeferredBlock<OfferingPlinthBlock> OFFERING_PLINTH = BLOCKS.register("offering_plinth", () -> new OfferingPlinthBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0F, 1200.0F).requiresCorrectToolForDrops()
            .sound(SoundType.STONE).lightLevel(s -> s.getValue(OfferingPlinthBlock.AWAKENED) ? 7 : 0).pushReaction(PushReaction.BLOCK).noOcclusion()));

    public static final DeferredItem<BlockItem> SHRINE_HEART_ITEM = ITEMS.register("shrine_heart",
        () -> new BlockItem(SHRINE_HEART.get(), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<BlockItem> OFFERING_PLINTH_ITEM = ITEMS.registerSimpleBlockItem("offering_plinth", OFFERING_PLINTH);

    @SuppressWarnings("DataFlowIssue") // the data fixer type is null by design for mod block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShrineHeartBlockEntity>> SHRINE_HEART_BE = BLOCK_ENTITIES.register("shrine_heart",
        () -> BlockEntityType.Builder.of(ShrineHeartBlockEntity::new, SHRINE_HEART.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OfferingPlinthBlockEntity>> OFFERING_PLINTH_BE = BLOCK_ENTITIES.register("offering_plinth",
        () -> BlockEntityType.Builder.of(OfferingPlinthBlockEntity::new, OFFERING_PLINTH.get()).build(null));

    // Sounds: sounds.json maps them onto vanilla sound events (no own audio files in the MVP).
    public static final DeferredHolder<SoundEvent, SoundEvent> STING_STONE = sound("shrine.sting.stone");
    public static final DeferredHolder<SoundEvent, SoundEvent> STING_BRONZE = sound("shrine.sting.bronze");
    public static final DeferredHolder<SoundEvent, SoundEvent> STING_IRON = sound("shrine.sting.iron");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHOIR = sound("shrine.choir");
    public static final DeferredHolder<SoundEvent, SoundEvent> PRAYER = sound("shrine.prayer");
    public static final DeferredHolder<SoundEvent, SoundEvent> REFUSED = sound("shrine.refused");
    public static final DeferredHolder<SoundEvent, SoundEvent> ACCEPTED = sound("shrine.accepted");
    public static final DeferredHolder<SoundEvent, SoundEvent> KINDLED = sound("shrine.kindled");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, name)));
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(ShrineRegistry::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(SHRINE_HEART_ITEM.get());
            event.accept(OFFERING_PLINTH_ITEM.get());
        }
    }
}
