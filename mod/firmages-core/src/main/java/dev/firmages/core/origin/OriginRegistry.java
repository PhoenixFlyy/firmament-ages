package dev.firmages.core.origin;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Origin (SPEC §16): its dimension key and the altar block of the Gathering. */
public final class OriginRegistry {
    private OriginRegistry() {}

    public static final ResourceLocation ORIGIN_ID = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "origin");
    public static final ResourceKey<Level> ORIGIN = ResourceKey.create(Registries.DIMENSION, ORIGIN_ID);

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FirmagesCore.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FirmagesCore.MOD_ID);

    /** Unbreakable in survival like bedrock; it is part of the arena, never crafted. */
    public static final DeferredBlock<Block> ORIGIN_ALTAR = BLOCKS.register("origin_altar", () -> new Block(
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3600000.0F).noLootTable()
            .sound(SoundType.LODESTONE).lightLevel(s -> 12).pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<BlockItem> ORIGIN_ALTAR_ITEM = ITEMS.register("origin_altar",
        () -> new BlockItem(ORIGIN_ALTAR.get(), new Item.Properties().rarity(Rarity.EPIC)));

    /** The four fight altars of the grand arena (round G3, SPEC §39.1); no item, unbreakable, lit while active. */
    public static final DeferredBlock<ArenaAltarBlock> ARENA_ALTAR = BLOCKS.register("arena_altar", () -> new ArenaAltarBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0F, 3600000.0F).noLootTable()
            .sound(SoundType.LODESTONE).lightLevel(s -> s.getValue(ArenaAltarBlock.ACTIVE) ? 15 : 6).pushReaction(PushReaction.BLOCK)));

    /**
     * The grand arena's stone (Felix: stone, dark stone, moss and purple tones): unbreakable copies of vanilla blocks,
     * drawn with the vanilla textures, no item, no loot. {@link OriginArena#grandState} maps the plan's consecrated keys
     * onto them.
     */
    public static final java.util.Map<String, DeferredBlock<Block>> ARENA_STONE = new java.util.LinkedHashMap<>();

    static {
        arenaStone("polished_deepslate", MapColor.DEEPSLATE, SoundType.POLISHED_DEEPSLATE, 0, false);
        arenaStone("deepslate_tiles", MapColor.DEEPSLATE, SoundType.DEEPSLATE_TILES, 0, false);
        arenaStone("deepslate_bricks", MapColor.DEEPSLATE, SoundType.DEEPSLATE_BRICKS, 0, false);
        arenaStone("chiseled_deepslate", MapColor.DEEPSLATE, SoundType.DEEPSLATE_BRICKS, 0, false);
        arenaStone("polished_blackstone_bricks", MapColor.COLOR_BLACK, SoundType.STONE, 0, false);
        arenaStone("stone_bricks", MapColor.STONE, SoundType.STONE, 0, false);
        arenaStone("mossy_stone_bricks", MapColor.STONE, SoundType.STONE, 0, false);
        arenaStone("mossy_cobblestone", MapColor.STONE, SoundType.STONE, 0, false);
        arenaStone("moss_block", MapColor.COLOR_GREEN, SoundType.MOSS, 0, false);
        arenaStone("purpur_block", MapColor.COLOR_MAGENTA, SoundType.STONE, 0, false);
        arenaStone("purpur_pillar", MapColor.COLOR_MAGENTA, SoundType.STONE, 0, false);
        arenaStone("amethyst_block", MapColor.COLOR_PURPLE, SoundType.AMETHYST, 0, false);
        arenaStone("pearlescent_froglight", MapColor.COLOR_PINK, SoundType.FROGLIGHT, 15, false);
        arenaStone("tinted_glass", MapColor.COLOR_GRAY, SoundType.GLASS, 0, true);
    }

    private static void arenaStone(String name, MapColor color, SoundType sound, int light, boolean glass) {
        ARENA_STONE.put(name, BLOCKS.register("arena_" + name, () -> {
            BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(color).strength(-1.0F, 3600000.0F).noLootTable()
                .sound(sound).lightLevel(s -> light).pushReaction(PushReaction.BLOCK);
            if (glass) p = p.noOcclusion().isValidSpawn((s, l, pos, t) -> false).isRedstoneConductor((s, l, pos) -> false)
                .isSuffocating((s, l, pos) -> false).isViewBlocking((s, l, pos) -> false);
            // pick block gives the vanilla original (the arena stone has no item of its own)
            java.util.function.Supplier<net.minecraft.world.item.ItemStack> pick = () -> new net.minecraft.world.item.ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(name)));
            return glass
                ? new net.minecraft.world.level.block.TintedGlassBlock(p) {
                    @Override
                    public net.minecraft.world.item.ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader l, net.minecraft.core.BlockPos pos,
                                                                                net.minecraft.world.level.block.state.BlockState s) {
                        return pick.get();
                    }
                }
                : new Block(p) {
                    @Override
                    public net.minecraft.world.item.ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader l, net.minecraft.core.BlockPos pos,
                                                                                net.minecraft.world.level.block.state.BlockState s) {
                        return pick.get();
                    }
                };
        }));
    }

    /** The arena stone {@code name} (a key of {@link #ARENA_STONE}). */
    public static net.minecraft.world.level.block.state.BlockState arenaStone(String name) {
        DeferredBlock<Block> b = ARENA_STONE.get(name);
        if (b == null) throw new IllegalArgumentException("unknown arena stone " + name);
        return b.get().defaultBlockState();
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
