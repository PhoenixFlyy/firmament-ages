package dev.firmages.core.anchor;

import com.mojang.serialization.Codec;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.shrine.BlessingSources;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
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
 * The Sky Anchor, the blessing shard and the shard's {@code firmages:tier} component (SPEC-himmel §29.3). The shard
 * recipes are the nine {@code anchor_chord} starforge recipes of the Lens Crown (§29.4); the anchor's recipe is pack
 * data ({@code kubejs/server_scripts/recipes/age_7/space_age.js}).
 */
public final class AnchorRegistry {
    private AnchorRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FirmagesCore.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FirmagesCore.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FirmagesCore.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(FirmagesCore.MOD_ID);

    /** {@code firmages:tier}: the shrine tier 0..8 of a blessing shard. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TIER = COMPONENTS.registerComponentType("tier",
        b -> b.persistent(Codec.intRange(0, BlessingSources.TIERS - 1)).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredBlock<SkyAnchorBlock> SKY_ANCHOR = BLOCKS.register("sky_anchor", () -> new SkyAnchorBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
            .sound(SoundType.NETHERITE_BLOCK).pushReaction(PushReaction.BLOCK).noOcclusion()
            .lightLevel(s -> s.getValue(SkyAnchorBlock.ACTIVE) ? 12 : 3)));
    public static final DeferredItem<BlockItem> SKY_ANCHOR_ITEM = ITEMS.register("sky_anchor",
        () -> new BlockItem(SKY_ANCHOR.get(), new Item.Properties().rarity(Rarity.RARE).fireResistant()));
    /** One item for all nine tiers; the tier is the {@link #TIER} component (a stack without it counts as tier 0). */
    public static final DeferredItem<BlessingShardItem> BLESSING_SHARD = ITEMS.register("blessing_shard",
        () -> new BlessingShardItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON).fireResistant()));

    @SuppressWarnings("DataFlowIssue") // the data fixer type is null by design for mod block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SkyAnchorBlockEntity>> SKY_ANCHOR_BE = BLOCK_ENTITIES.register("sky_anchor",
        () -> BlockEntityType.Builder.of(SkyAnchorBlockEntity::new, SKY_ANCHOR.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(AnchorRegistry::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(SKY_ANCHOR_ITEM.get());
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            for (int t = 0; t < BlessingSources.TIERS; t++) event.accept(BlessingShardItem.of(t));
        }
    }
}
