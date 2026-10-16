package dev.firmages.core.isles;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.wayshrine.WayshrinePlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Turns an {@link IsleShape} into blocks, for the structure piece (one chunk at a time) and the fallback placer (a
 * list placed at most {@code 64} blocks per tick). The tree pockets are TerraFirmaCraft podzol dirt under podzol grass
 * (TFC 4.2 has no loam soil), and the trees are TFC species of the high, cold air (spruce, pine, white cedar, Douglas
 * fir, aspen, birch: an isle top at Y 228 to 272 counts as polar in TFC's climate); without TFC (the GameTest run) the
 * pocket is vanilla dirt and grass and no tree grows.
 */
public final class IsleWriter {
    /** The tree species of the isles (TFC configured features {@code tfc:tree/<species>}). */
    public static final List<String> SPECIES = List.of("spruce", "pine", "white_cedar", "douglas_fir", "aspen", "birch");

    private IsleWriter() {}

    private static BlockState block(String id, Block fallback) {
        Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        return (b == Blocks.AIR ? fallback : b).defaultBlockState();
    }

    public static BlockState state(IsleShape.Kind kind) {
        return switch (kind) {
            case SKYSTONE -> IsleRegistry.SKYSTONE.get().defaultBlockState();
            case DIRT -> block("tfc:dirt/podzol", Blocks.DIRT);
            case GRASS -> block("tfc:grass/podzol", Blocks.GRASS_BLOCK);
            case LODE -> IsleRegistry.LODE.get().defaultBlockState();
            case AIR -> Blocks.AIR.defaultBlockState();
            case CLUSTER_UP -> cluster(Direction.UP);
            case CLUSTER_DOWN -> cluster(Direction.DOWN);
            case CLUSTER_NORTH -> cluster(Direction.NORTH);
            case CLUSTER_SOUTH -> cluster(Direction.SOUTH);
            case CLUSTER_EAST -> cluster(Direction.EAST);
            case CLUSTER_WEST -> cluster(Direction.WEST);
        };
    }

    private static BlockState cluster(Direction facing) {
        return IsleRegistry.CRYSTAL_CLUSTER.get().defaultBlockState().setValue(AmethystClusterBlock.FACING, facing)
            .setValue(AmethystClusterBlock.WATERLOGGED, false);
    }

    /** Writes the isle's blocks whose columns lie inside {@code box} (no neighbour updates, as worldgen does). */
    public static void body(WorldGenLevel level, IsleShape shape, BoundingBox box) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        shape.emit((x, y, z, kind) -> {
            if (y < box.minY() || y > box.maxY()) return;
            level.setBlock(p.set(x, y, z), state(kind), Block.UPDATE_CLIENTS);
        }, box.minX(), box.minZ(), box.maxX(), box.maxZ());
    }

    /** The configured feature of a tree (by the isle's seed and the tree's place), if TFC registers it. */
    static Optional<Holder.Reference<ConfiguredFeature<?, ?>>> treeFeature(WorldGenLevel level, IsleShape shape, IsleShape.Tree t) {
        long h = IsleLayout.mix(shape.spec().seed(), ((long) t.x() << 32) ^ (t.z() & 0xFFFFFFFFL));
        String species = SPECIES.get((int) Math.floorMod(h, SPECIES.size()));
        return level.registryAccess().registry(Registries.CONFIGURED_FEATURE)
            .flatMap(r -> r.getHolder(ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath("tfc", "tree/" + species))));
    }

    /** Grows one tree on its pocket; false without TFC or when the feature refuses the spot. */
    public static boolean tree(WorldGenLevel level, ChunkGenerator generator, IsleShape shape, IsleShape.Tree t) {
        Optional<Holder.Reference<ConfiguredFeature<?, ?>>> f = treeFeature(level, shape, t);
        if (f.isEmpty()) return false;
        RandomSource rand = RandomSource.create(IsleLayout.mix(shape.spec().seed(), t.x() * 31L + t.z()));
        try {
            return f.get().value().place(level, generator, rand, new BlockPos(t.x(), t.y(), t.z()));
        } catch (RuntimeException e) {
            FirmagesCore.LOGGER.warn("Drifting Isle tree {} at {} {} {} failed: {}", f.get().key().location(), t.x(), t.y(), t.z(), e.toString());
            return false;
        }
    }

    /** The core of the Wind Shrine on this isle: the floor centre (the core sits in the top surface). */
    public static BlockPos shrineCore(IsleSpec s) {
        return new BlockPos(s.x(), s.top(), s.z());
    }

    public static Rotation shrineRotation(IsleSpec s) {
        return Rotation.values()[(int) Math.floorMod(IsleLayout.mix(s.seed(), 0x5A), 4)];
    }

    /** Places the Wind Shrine's ruin on the isle (S6B), clipped to {@code clip} (a chunk during worldgen, or a slice). */
    public static Optional<BlockPos> shrine(ServerLevelAccessor level, IsleSpec s, @Nullable BoundingBox clip) {
        RandomSource rand = RandomSource.create(IsleLayout.mix(s.seed(), 0x5A1));
        return WayshrinePlacement.placeIsleShrine(level, shrineCore(s), shrineRotation(s), rand, clip);
    }
}
