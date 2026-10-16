package dev.firmages.core.sky;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;

/**
 * The Sky Harvest (SPEC §26): storm glass, the Storm Rod (Iron Age), the Aurora Net and aurora light (Industrial
 * Age). Storm glass and aurora light are the only sky materials made here, and only these two blocks make them. No
 * block of this class has an energy capability.
 */
public final class HarvestRegistry {
    private HarvestRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FirmagesCore.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FirmagesCore.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FirmagesCore.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, FirmagesCore.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, FirmagesCore.MOD_ID);
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, FirmagesCore.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, FirmagesCore.MOD_ID);

    /** A full glass block, blast resistance 1200, hardness 3; drops itself without silk touch (SPEC §26.5). */
    public static final DeferredBlock<TransparentBlock> STORM_GLASS = BLOCKS.register("storm_glass", () -> new TransparentBlock(BlockBehaviour.Properties.of()
        .mapColor(MapColor.COLOR_LIGHT_BLUE).instrument(NoteBlockInstrument.HAT).strength(3.0F, 1200.0F).sound(SoundType.GLASS).noOcclusion()
        .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false)
        .isViewBlocking((s, l, p) -> false)));
    public static final DeferredBlock<StormRodBlock> STORM_ROD = BLOCKS.register("storm_rod", () -> new StormRodBlock(BlockBehaviour.Properties.of()
        .mapColor(MapColor.COLOR_ORANGE).strength(4.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.COPPER).noOcclusion()
        .pushReaction(PushReaction.BLOCK)));
    public static final DeferredBlock<AuroraNetBlock> AURORA_NET = BLOCKS.register("aurora_net", () -> new AuroraNetBlock(BlockBehaviour.Properties.of()
        .mapColor(MapColor.COLOR_CYAN).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()
        .lightLevel(s -> s.getValue(AuroraNetBlock.CATCHING) ? 10 : 0).pushReaction(PushReaction.BLOCK)
        .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false)));

    public static final DeferredHolder<FluidType, FluidType> AURORA_LIGHT_TYPE = FLUID_TYPES.register("aurora_light", () -> new FluidType(
        FluidType.Properties.create().lightLevel(12).viscosity(400).temperature(250).canSwim(false).canDrown(false)
            .canPushEntity(false).canExtinguish(false).canConvertToSource(false).supportsBoating(false).canHydrate(false)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL).sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> AURORA_LIGHT = FLUIDS.register("aurora_light",
        () -> new BaseFlowingFluid.Source(auroraProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> AURORA_LIGHT_FLOWING = FLUIDS.register("flowing_aurora_light",
        () -> new BaseFlowingFluid.Flowing(auroraProperties()));

    public static final DeferredItem<BlockItem> STORM_GLASS_ITEM = ITEMS.registerSimpleBlockItem("storm_glass", STORM_GLASS);
    public static final DeferredItem<DoubleHighBlockItem> STORM_ROD_ITEM = ITEMS.register("storm_rod",
        () -> new DoubleHighBlockItem(STORM_ROD.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> AURORA_NET_ITEM = ITEMS.registerSimpleBlockItem("aurora_net", AURORA_NET);
    public static final DeferredItem<AuroraLightBucketItem> AURORA_LIGHT_BUCKET = ITEMS.register("aurora_light_bucket",
        () -> new AuroraLightBucketItem(AURORA_LIGHT.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    @SuppressWarnings("DataFlowIssue") // the data fixer type is null by design for mod block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StormRodBlockEntity>> STORM_ROD_BE = BLOCK_ENTITIES.register("storm_rod",
        () -> BlockEntityType.Builder.of(StormRodBlockEntity::new, STORM_ROD.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AuroraNetBlockEntity>> AURORA_NET_BE = BLOCK_ENTITIES.register("aurora_net",
        () -> BlockEntityType.Builder.of(AuroraNetBlockEntity::new, AURORA_NET.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<StormRodMenu>> STORM_ROD_MENU = MENUS.register("storm_rod",
        () -> IMenuTypeExtension.create(StormRodMenu::new));

    // sounds.json maps them onto vanilla sound events (as SPEC §8.1)
    public static final DeferredHolder<SoundEvent, SoundEvent> AURORA_NET_CATCH = sound("aurora_net.catch");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_ROD_STRIKE = sound("storm_rod.strike");

    private static BaseFlowingFluid.Properties auroraProperties() {
        // no block: aurora light never enters the world (SPEC §26.4); the bucket only fills and empties containers
        return new BaseFlowingFluid.Properties(AURORA_LIGHT_TYPE, AURORA_LIGHT, AURORA_LIGHT_FLOWING).bucket(AURORA_LIGHT_BUCKET);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, name)));
    }

    /** Every Sky Harvest item, for the sky self-test (none may be an ore or raw material). */
    public static List<DeferredItem<? extends Item>> items() {
        return List.of(STORM_GLASS_ITEM, STORM_ROD_ITEM, AURORA_NET_ITEM, AURORA_LIGHT_BUCKET);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        SOUNDS.register(modBus);
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
        modBus.addListener(HarvestRegistry::capabilities);
        modBus.addListener(HarvestRegistry::creativeTab);
    }

    /** Game-bus listeners: the reload listener of {@code sky_harvest.json}; the test storm ends with the server. */
    public static void registerGame(IEventBus bus) {
        bus.addListener((net.neoforged.neoforge.event.AddReloadListenerEvent e) -> e.addListener(new SkyHarvestDataLoader()));
        bus.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> StormWeather.reset());
    }

    /**
     * SPEC §26.4, §26.5: the net is drained from every side and never filled; the rod takes sand from the top and the
     * sides (either half) and gives storm glass below its basin. No energy capability anywhere.
     */
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AURORA_NET_BE.get(), (be, side) -> be.drainOnly());
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) -> {
            boolean upper = state.getValue(StormRodBlock.HALF) == DoubleBlockHalf.UPPER;
            StormRodBlockEntity rod = upper ? (level.getBlockEntity(pos.below()) instanceof StormRodBlockEntity r ? r : null)
                : be instanceof StormRodBlockEntity r ? r : null;
            if (rod == null) return null;
            return !upper && side == Direction.DOWN ? rod.outputHandler() : rod.inputHandler();
        }, STORM_ROD.get());
        event.registerItem(Capabilities.FluidHandler.ITEM, (stack, ctx) -> new FluidBucketWrapper(stack), AURORA_LIGHT_BUCKET.get());
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) event.accept(STORM_GLASS_ITEM.get());
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(STORM_ROD_ITEM.get());
            event.accept(AURORA_NET_ITEM.get());
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(AURORA_LIGHT_BUCKET.get());
    }
}
