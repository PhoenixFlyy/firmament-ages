package dev.firmages.core.launch;

import dev.firmages.core.FirmagesCore;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * The Launch Complex (SPEC §31.3, §31.4): the launch control, the probe cradle, the launch port, the probe and its two
 * payload modules, the two sky materials only probes bring (voidglass, deep-space samples), the {@code firmages:mission}
 * item component (with {@code firmages:launch_fuel} for the tank) and the {@code firmages:probe} criterion.
 */
public final class LaunchRegistry {
    private LaunchRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FirmagesCore.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FirmagesCore.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FirmagesCore.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FirmagesCore.MOD_ID);
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, FirmagesCore.MOD_ID);

    /** Drops itself to any hand and to any explosion: a mission in flight rides in the item (SPEC §31.5). */
    public static final DeferredBlock<LaunchControlBlock> LAUNCH_CONTROL = BLOCKS.register("launch_control", () -> new LaunchControlBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK)
            .pushReaction(PushReaction.BLOCK)));
    public static final DeferredBlock<ProbeCradleBlock> PROBE_CRADLE = BLOCKS.register("probe_cradle", () -> new ProbeCradleBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)
            .pushReaction(PushReaction.BLOCK).noOcclusion()));
    public static final DeferredBlock<LaunchPortBlock> LAUNCH_PORT = BLOCKS.register("launch_port", () -> new LaunchPortBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)
            .pushReaction(PushReaction.BLOCK)));

    public static final DeferredItem<BlockItem> LAUNCH_CONTROL_ITEM = ITEMS.registerSimpleBlockItem("launch_control", LAUNCH_CONTROL);
    public static final DeferredItem<BlockItem> PROBE_CRADLE_ITEM = ITEMS.registerSimpleBlockItem("probe_cradle", PROBE_CRADLE);
    public static final DeferredItem<BlockItem> LAUNCH_PORT_ITEM = ITEMS.registerSimpleBlockItem("launch_port", LAUNCH_PORT);
    /** The reusable hull: it always comes back. */
    public static final DeferredItem<Item> PROBE = ITEMS.register("probe", () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> SAMPLER_MODULE = ITEMS.register("sampler_module", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> DEEP_SPACE_MODULE = ITEMS.register("deep_space_module",
        () -> new Item(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> VOIDGLASS = ITEMS.register("voidglass", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> DEEP_SPACE_SAMPLE = ITEMS.register("deep_space_sample", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    @SuppressWarnings("DataFlowIssue") // the data fixer type is null by design for mod block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchControlBlockEntity>> LAUNCH_CONTROL_BE = BLOCK_ENTITIES.register(
        "launch_control", () -> BlockEntityType.Builder.of(LaunchControlBlockEntity::new, LAUNCH_CONTROL.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProbeCradleBlockEntity>> PROBE_CRADLE_BE = BLOCK_ENTITIES.register(
        "probe_cradle", () -> BlockEntityType.Builder.of(ProbeCradleBlockEntity::new, PROBE_CRADLE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchPortBlockEntity>> LAUNCH_PORT_BE = BLOCK_ENTITIES.register(
        "launch_port", () -> BlockEntityType.Builder.of(LaunchPortBlockEntity::new, LAUNCH_PORT.get()).build(null));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Mission>> MISSION = COMPONENTS.registerComponentType("mission",
        b -> b.persistent(Mission.CODEC).networkSynchronized(Mission.STREAM_CODEC));

    /** The tank of a broken control rides in its item as well, so no fuel is lost. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.neoforged.neoforge.fluids.SimpleFluidContent>> LAUNCH_FUEL =
        COMPONENTS.registerComponentType("launch_fuel", b -> b.persistent(net.neoforged.neoforge.fluids.SimpleFluidContent.CODEC)
            .networkSynchronized(net.neoforged.neoforge.fluids.SimpleFluidContent.STREAM_CODEC));

    public static final DeferredHolder<CriterionTrigger<?>, ProbeTrigger> PROBE_TRIGGER = TRIGGERS.register("probe", ProbeTrigger::new);

    /** Every item of the complex (selftest: none is an ore or a raw material). */
    public static List<DeferredItem<?>> items() {
        return List.of(LAUNCH_CONTROL_ITEM, PROBE_CRADLE_ITEM, LAUNCH_PORT_ITEM, PROBE, SAMPLER_MODULE, DEEP_SPACE_MODULE, VOIDGLASS, DEEP_SPACE_SAMPLE);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        TRIGGERS.register(modBus);
        modBus.addListener(LaunchRegistry::capabilities);
        modBus.addListener(LaunchRegistry::creativeTab);
    }

    /**
     * SPEC §31.6: the control and every linked port take probes, modules and fuel from any side and give the returns
     * (and the probe once it is out of the relaunch slot). An unlinked port offers nothing.
     */
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, LAUNCH_CONTROL_BE.get(), (be, side) -> be.automationItems());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, LAUNCH_CONTROL_BE.get(), (be, side) -> be.automationFluid());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, LAUNCH_PORT_BE.get(), (be, side) -> be.control().map(LaunchControlBlockEntity::automationItems).orElse(null));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, LAUNCH_PORT_BE.get(), (be, side) -> be.control().map(LaunchControlBlockEntity::automationFluid).orElse(null));
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(LAUNCH_CONTROL_ITEM.get());
            event.accept(PROBE_CRADLE_ITEM.get());
            event.accept(LAUNCH_PORT_ITEM.get());
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(PROBE.get());
            event.accept(SAMPLER_MODULE.get());
            event.accept(DEEP_SPACE_MODULE.get());
            event.accept(VOIDGLASS.get());
            event.accept(DEEP_SPACE_SAMPLE.get());
        }
    }
}
