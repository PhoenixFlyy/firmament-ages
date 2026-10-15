package dev.firmages.core.proof;

import dev.firmages.core.FirmagesCore;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Proof of work (SPEC §22): the Tally Chute and the criteria {@code firmages:tally} and {@code firmages:silo_launch}. */
public final class ProofRegistry {
    private ProofRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FirmagesCore.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FirmagesCore.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FirmagesCore.MOD_ID);
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, FirmagesCore.MOD_ID);

    public static final DeferredBlock<TallyChuteBlock> TALLY_CHUTE = BLOCKS.register("tally_chute", () -> new TallyChuteBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 6.0F).sound(SoundType.COPPER)));
    public static final DeferredItem<BlockItem> TALLY_CHUTE_ITEM = ITEMS.registerSimpleBlockItem("tally_chute", TALLY_CHUTE);

    @SuppressWarnings("DataFlowIssue") // the data fixer type is null by design for mod block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TallyChuteBlockEntity>> TALLY_CHUTE_BE = BLOCK_ENTITIES.register("tally_chute",
        () -> BlockEntityType.Builder.of(TallyChuteBlockEntity::new, TALLY_CHUTE.get()).build(null));

    public static final DeferredHolder<CriterionTrigger<?>, TallyTrigger> TALLY = TRIGGERS.register("tally", TallyTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SiloLaunchTrigger> SILO_LAUNCH = TRIGGERS.register("silo_launch", SiloLaunchTrigger::new);

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TRIGGERS.register(modBus);
        modBus.addListener(ProofRegistry::capabilities);
        modBus.addListener(ProofRegistry::creativeTab);
    }

    /** Items go in from the top and the four sides; the bottom is the outlet and offers nothing to pull. */
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TALLY_CHUTE_BE.get(), (be, side) -> side == Direction.DOWN ? null : be.inlet());
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(TALLY_CHUTE_ITEM.get());
    }
}
