package dev.firmages.core.crown;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
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
 * The Lens Crown (SPEC §27.4): Star Anvil, Lens Mount, lens blank, the nine lenses, the seven celestial alloys, the
 * {@code firmages:starforge} recipe type and the chord sound. The sky materials the recipes take (meteoric iron,
 * starsteel, storm glass, aurora light, firmament crystal, voidglass, deep-space sample) belong to the other sky
 * features and are named by id only.
 */
public final class CrownRegistry {
    private CrownRegistry() {}

    /** Lens N belongs to shrine tier N (SPEC §27.4). */
    public static final List<String> LENS_NAMES = List.of("stone", "bronze", "iron", "arcane", "industrial", "electric", "information",
        "space", "quantum");
    /** The celestial alloys, age_3 .. age_9 (SPEC §27.8). */
    public static final List<String> ALLOY_NAMES = List.of("starlit_iron", "cloudsteel", "aurora_steel", "halo_steel", "void_steel",
        "quasar_alloy", "firmament_alloy");

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FirmagesCore.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FirmagesCore.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FirmagesCore.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, FirmagesCore.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, FirmagesCore.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, FirmagesCore.MOD_ID);

    public static final DeferredBlock<StarAnvilBlock> STAR_ANVIL = BLOCKS.register("star_anvil", () -> new StarAnvilBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
            .sound(SoundType.ANVIL).lightLevel(s -> s.getValue(StarAnvilBlock.ACTIVE) ? 11 : s.getValue(StarAnvilBlock.FORMED) ? 4 : 0)
            .pushReaction(PushReaction.BLOCK).noOcclusion()));
    public static final DeferredBlock<LensMountBlock> LENS_MOUNT = BLOCKS.register("lens_mount", () -> new LensMountBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 1200.0F).requiresCorrectToolForDrops()
            .sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion()));

    public static final DeferredItem<BlockItem> STAR_ANVIL_ITEM = ITEMS.register("star_anvil",
        () -> new BlockItem(STAR_ANVIL.get(), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<BlockItem> LENS_MOUNT_ITEM = ITEMS.registerSimpleBlockItem("lens_mount", LENS_MOUNT);
    public static final DeferredItem<Item> LENS_BLANK = ITEMS.registerSimpleItem("lens_blank");
    public static final List<DeferredItem<LensItem>> LENSES = registerLenses();
    public static final List<DeferredItem<Item>> ALLOYS = ALLOY_NAMES.stream()
        .map(n -> ITEMS.register(n + "_ingot", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)))).toList();

    @SuppressWarnings("DataFlowIssue") // the data fixer type is null by design for mod block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StarAnvilBlockEntity>> STAR_ANVIL_BE = BLOCK_ENTITIES.register("star_anvil",
        () -> BlockEntityType.Builder.of(StarAnvilBlockEntity::new, STAR_ANVIL.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LensMountBlockEntity>> LENS_MOUNT_BE = BLOCK_ENTITIES.register("lens_mount",
        () -> BlockEntityType.Builder.of(LensMountBlockEntity::new, LENS_MOUNT.get()).build(null));

    public static final DeferredHolder<RecipeType<?>, RecipeType<StarforgeRecipe>> STARFORGE_TYPE = RECIPE_TYPES.register("starforge",
        () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "starforge")));
    public static final DeferredHolder<RecipeSerializer<?>, StarforgeRecipe.Serializer> STARFORGE_SERIALIZER =
        RECIPE_SERIALIZERS.register("starforge", StarforgeRecipe.Serializer::new);

    /** The chord played once when a forging completes (note-block bell, lens N at pitch 0.5 + N * 0.1875). */
    public static final DeferredHolder<SoundEvent, SoundEvent> CHORD_SOUND = SOUNDS.register("crown.chord",
        () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "crown.chord")));

    private static List<DeferredItem<LensItem>> registerLenses() {
        java.util.ArrayList<DeferredItem<LensItem>> out = new java.util.ArrayList<>();
        for (int n = 0; n < LENS_NAMES.size(); n++) {
            int index = n;
            out.add(ITEMS.register("lens/" + LENS_NAMES.get(n), () -> new LensItem(index, new Item.Properties().stacksTo(1))));
        }
        return List.copyOf(out);
    }

    /** The lens item of index {@code n} (0..8). */
    public static LensItem lens(int n) {
        return LENSES.get(n).get();
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(CrownRegistry::capabilities);
        modBus.addListener(CrownRegistry::creativeTab);
    }

    /**
     * SPEC §27.10: the top and the four sides insert into the four input slots and the tank; the bottom extracts the
     * result only. Mounts have no handler: lenses stay put and only swing.
     */
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, STAR_ANVIL_BE.get(),
            (be, side) -> side == Direction.DOWN ? be.outputHandler() : be.inputHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, STAR_ANVIL_BE.get(),
            (be, side) -> side == Direction.DOWN ? null : be.fluidInput());
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(STAR_ANVIL_ITEM.get());
            event.accept(LENS_MOUNT_ITEM.get());
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(LENS_BLANK.get());
            LENSES.forEach(l -> event.accept(l.get()));
            ALLOYS.forEach(a -> event.accept(a.get()));
        }
    }
}
