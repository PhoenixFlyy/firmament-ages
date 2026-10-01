package dev.firmages.core.reactor;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The reactor controller block and the item capabilities of m4 (SPEC §6). */
public final class ReactorRegistry {
    private ReactorRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FirmagesCore.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FirmagesCore.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FirmagesCore.MOD_ID);

    public static final DeferredBlock<ReactorControllerBlock> REACTOR_CONTROLLER = BLOCKS.register("reactor_controller", () -> new ReactorControllerBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
            .sound(SoundType.NETHERITE_BLOCK).lightLevel(s -> s.getValue(ReactorControllerBlock.READY) ? 9 : 2)));
    public static final DeferredItem<BlockItem> REACTOR_CONTROLLER_ITEM = ITEMS.registerSimpleBlockItem("reactor_controller", REACTOR_CONTROLLER);

    @SuppressWarnings("DataFlowIssue") // the data fixer type is null by design for mod block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReactorControllerBlockEntity>> REACTOR_CONTROLLER_BE =
        BLOCK_ENTITIES.register("reactor_controller", () -> BlockEntityType.Builder.of(ReactorControllerBlockEntity::new, REACTOR_CONTROLLER.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(ReactorRegistry::capabilities);
        modBus.addListener(ReactorRegistry::commonSetup);
        modBus.addListener(ReactorRegistry::creativeTab);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, REACTOR_CONTROLLER_BE.get(), (be, side) -> be.external());
        if (!ModList.get().isLoaded("draconicevolution")) return;
        try {
            dev.firmages.core.compat.draconic.DraconicReactors.registerCapabilities(event);
        } catch (RuntimeException | LinkageError e) {
            FirmagesCore.LOGGER.error("Reactor fuel port not registered: Draconic Evolution's reactor API differs from 3.1.4 ({})", e.toString());
        }
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("draconicevolution")) event.enqueueWork(dev.firmages.core.compat.draconic.DraconicReactors::init);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(REACTOR_CONTROLLER_ITEM.get());
    }
}
