package dev.firmages.core.isles;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * One isle of a cluster (SPEC §30.3). Chunk-safe: {@link #postProcess} writes only the columns of the chunk being
 * generated (the shape is a pure function of the stored {@link IsleSpec}), places a tree from the chunk that holds its
 * trunk, and on the shrine's isle places the Wind Shrine's ruin clipped to the chunk ({@code WayshrinePlacement
 * .placeIsleShrine}, S6B).
 */
public final class IslePiece extends StructurePiece {
    /** Room above the top for trees and the Wind Shrine's ruin. */
    public static final int HEADROOM = 24;

    private final IsleSpec spec;

    public IslePiece(IsleSpec spec) {
        super(IsleRegistry.ISLE_PIECE.get(), 0, box(spec));
        this.spec = spec;
    }

    public IslePiece(CompoundTag tag) {
        super(IsleRegistry.ISLE_PIECE.get(), tag);
        this.spec = read(tag);
    }

    public IsleSpec spec() {
        return spec;
    }

    public static BoundingBox box(IsleSpec s) {
        int r = s.radius() + 1;
        return new BoundingBox(s.x() - r, s.tip() - 2, s.z() - r, s.x() + r, Math.min(IsleSettings.CROWN_MAX + 19, s.top() + HEADROOM), s.z() + r);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        write(spec, tag);
    }

    public static void write(IsleSpec s, CompoundTag tag) {
        tag.putInt("X", s.x());
        tag.putInt("Z", s.z());
        tag.putInt("R", s.radius());
        tag.putString("Size", s.size().id());
        tag.putInt("Top", s.top());
        tag.putInt("Depth", s.depth());
        tag.putLong("Seed", s.seed());
        tag.putBoolean("Lode", s.lode());
        tag.putBoolean("Shrine", s.shrine());
        tag.putInt("Trees", s.trees());
        tag.putInt("Crystals", s.crystals());
        tag.putBoolean("Ruin", s.ruin());
    }

    public static IsleSpec read(CompoundTag tag) {
        return new IsleSpec(tag.getInt("X"), tag.getInt("Z"), tag.getInt("R"), IsleSize.byId(tag.getString("Size")).orElse(IsleSize.SMALL),
            tag.getInt("Top"), tag.getInt("Depth"), tag.getLong("Seed"), tag.getBoolean("Lode"), tag.getBoolean("Shrine"), tag.getInt("Trees"),
            tag.getInt("Crystals"), tag.getBoolean("Ruin"));
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox box,
                            ChunkPos chunkPos, BlockPos pivot) {
        IsleShape shape = IsleShape.of(spec);
        IsleWriter.body(level, shape, box);
        for (IsleShape.Tree t : shape.trees()) {
            if (box.isInside(t.x(), box.minY(), t.z())) IsleWriter.tree(level, generator, shape, t);
        }
        if (spec.shrine()) IsleWriter.shrine(level, spec, box);
        if (spec.ruin()) IsleRuin.place(level, spec, box);
    }
}
