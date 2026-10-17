package dev.firmages.core.wayshrine;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.component.DataComponentType;
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

/**
 * Wayshrine blocks, the Wayshrine Stone, the core's block entity, the {@code firmages:wayshrine} item component and the
 * lighting sound (SPEC-himmel §28.5). The core's item exists only for the creative pick-block (round G2): no survival
 * source, in no creative tab, hidden from the recipe viewers ({@code dev/age_map.toml [hidden]}).
 */
public final class WayshrineRegistry {
    private WayshrineRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FirmagesCore.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FirmagesCore.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FirmagesCore.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, FirmagesCore.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(FirmagesCore.MOD_ID);

    public static final DeferredBlock<WayshrineCoreBlock> CORE = BLOCKS.register("wayshrine_core", () -> new WayshrineCoreBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0F, 1200.0F).noLootTable().sound(SoundType.DECORATED_POT)
            .pushReaction(PushReaction.BLOCK).noOcclusion()
            .lightLevel(s -> switch (s.getValue(WayshrineCoreBlock.PHASE)) {
                case LIT -> s.getValue(WayshrineCoreBlock.INTACT) ? 12 : 4;
                case RESTORED -> 4;
                case RUIN -> 1;
            })));
    /** Pick-block only (round G2, SPEC §28.5): no recipe, no loot, no creative tab. */
    public static final DeferredItem<BlockItem> CORE_ITEM = ITEMS.register("wayshrine_core",
        () -> new BlockItem(CORE.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredBlock<WayshrineStoneBlock> STONE = BLOCKS.register("wayshrine_stone", () -> new WayshrineStoneBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(1.5F, 1200.0F).sound(SoundType.AMETHYST).lightLevel(s -> 6)));
    public static final DeferredItem<WayshrineStoneItem> STONE_ITEM = ITEMS.register("wayshrine_stone",
        () -> new WayshrineStoneItem(STONE.get(), new Item.Properties().rarity(Rarity.RARE).fireResistant()));

    @SuppressWarnings("DataFlowIssue") // the data fixer type is null by design for mod block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WayshrineCoreBlockEntity>> CORE_BE = BLOCK_ENTITIES.register("wayshrine_core",
        () -> BlockEntityType.Builder.of(WayshrineCoreBlockEntity::new, CORE.get()).build(null));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WayshrineOrigin>> ORIGIN = COMPONENTS.registerComponentType("wayshrine",
        b -> b.persistent(WayshrineOrigin.CODEC).networkSynchronized(WayshrineOrigin.STREAM_CODEC));

    /** Played when a Wayshrine lights (sounds.json maps it onto a vanilla event). */
    public static final DeferredHolder<SoundEvent, SoundEvent> LIT = SOUNDS.register("wayshrine.lit",
        () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "wayshrine.lit")));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(WayshrineRegistry::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) event.accept(STONE_ITEM.get());
    }
}
