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

    /** The consecrated family (SPEC §17), one block per role; block items for operators only (no survival source). */
    public static final java.util.Map<Consecration.Role, DeferredBlock<ConsecratedBlock>> CONSECRATED = registerConsecrated();
    public static final java.util.Map<Consecration.Role, DeferredItem<BlockItem>> CONSECRATED_ITEMS = registerConsecratedItems();

    private static java.util.Map<Consecration.Role, DeferredBlock<ConsecratedBlock>> registerConsecrated() {
        java.util.Map<Consecration.Role, DeferredBlock<ConsecratedBlock>> out = new java.util.EnumMap<>(Consecration.Role.class);
        for (Consecration.Role role : Consecration.Role.values()) {
            out.put(role, BLOCKS.register("consecrated_" + role.id(), () -> {
                BlockBehaviour.Properties p = BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3_600_000.0F).noLootTable().pushReaction(PushReaction.BLOCK).forceSolidOn()
                    .isValidSpawn((s, l, pos, e) -> false)
                    .mapColor(switch (role) {
                        case METAL, SCAFFOLD -> MapColor.METAL;
                        case GLASS -> MapColor.NONE;
                        case LAMP -> MapColor.GOLD;
                        default -> MapColor.QUARTZ;
                    })
                    .sound(switch (role) {
                        case METAL -> SoundType.METAL;
                        case SCAFFOLD -> SoundType.NETHERITE_BLOCK;
                        case GLASS, LAMP -> SoundType.GLASS;
                        default -> SoundType.STONE;
                    });
                if (role.transparent()) p = p.noOcclusion().isViewBlocking((s, l, pos) -> false).isSuffocating((s, l, pos) -> false)
                    .isRedstoneConductor((s, l, pos) -> false);
                if (role == Consecration.Role.LAMP) return new ConsecratedLampBlock(p.lightLevel(s -> s.getValue(ConsecratedLampBlock.LIT) ? 15 : 0));
                return new ConsecratedBlock(role, p);
            }));
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    private static java.util.Map<Consecration.Role, DeferredItem<BlockItem>> registerConsecratedItems() {
        java.util.Map<Consecration.Role, DeferredItem<BlockItem>> out = new java.util.EnumMap<>(Consecration.Role.class);
        for (Consecration.Role role : Consecration.Role.values()) {
            out.put(role, ITEMS.register("consecrated_" + role.id(),
                () -> new BlockItem(CONSECRATED.get(role).get(), new Item.Properties().rarity(Rarity.EPIC))));
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    /** The consecrated state of {@code role} with accent {@code accent} (lamps lit). */
    public static net.minecraft.world.level.block.state.BlockState consecrated(Consecration.Role role, int accent) {
        net.minecraft.world.level.block.state.BlockState s = CONSECRATED.get(role).get().defaultBlockState()
            .setValue(ConsecratedBlock.ACCENT, Math.max(0, Math.min(Consecration.MAX_ACCENT, accent)));
        return s.hasProperty(ConsecratedLampBlock.LIT) ? s.setValue(ConsecratedLampBlock.LIT, true) : s;
    }

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
    public static final DeferredHolder<SoundEvent, SoundEvent> CONSECRATE = sound("shrine.consecrate");
    public static final DeferredHolder<SoundEvent, SoundEvent> MAINTENANCE = sound("shrine.maintenance");

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
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS && event.hasPermissions()) {
            CONSECRATED_ITEMS.values().forEach(i -> event.accept(i.get()));
        }
    }
}
