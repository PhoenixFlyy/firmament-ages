package dev.firmages.core.isles;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Finds Drifting Isle starts near a point (SPEC §30.3, §28.4a), the way {@code WayshrinePointer.search} does for the
 * ground Wayshrines: it lists the one candidate chunk of every placement cell within the radius, sorts them by
 * distance, drops those a cheap pre-filter refuses (the pointer keeps only clusters whose Wind Shrine roll is true), and
 * only then asks the structure manager, loading a chunk to {@code STRUCTURE_STARTS} where the start is not known yet.
 * Nothing is generated beyond structure starts.
 */
public final class IsleSearch {
    public static final ResourceKey<Structure> STRUCTURE = ResourceKey.create(Registries.STRUCTURE,
        ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "drifting_isle"));

    private IsleSearch() {}

    /** A cluster that starts in {@code chunk}, with its isles (the main isle first). */
    public record Found(ChunkPos chunk, List<IsleSpec> isles, double distance) {
        public IsleSpec main() {
            return isles.get(0);
        }

        public Optional<IsleSpec> shrineIsle() {
            return isles.stream().filter(IsleSpec::shrine).findFirst();
        }
    }

    public static Optional<Holder.Reference<Structure>> holder(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(STRUCTURE);
    }

    /** The isle settings of the registered structure, or the shipped defaults when it is missing. */
    public static IsleSettings settings(ServerLevel level) {
        return holder(level).map(h -> h.value() instanceof DriftingIsleStructure d ? d.isles() : IsleSettings.DEFAULT).orElse(IsleSettings.DEFAULT);
    }

    /** The random-spread placement of the isle set, if the set is loaded. */
    public static Optional<RandomSpreadStructurePlacement> placement(ServerLevel level) {
        Optional<Holder.Reference<Structure>> h = holder(level);
        if (h.isEmpty()) return Optional.empty();
        for (StructurePlacement p : level.getChunkSource().getGeneratorState().getPlacementsForStructure(h.get())) {
            if (p instanceof RandomSpreadStructurePlacement spread) return Optional.of(spread);
        }
        return Optional.empty();
    }

    /** The candidate start chunks (one per cell) whose cluster centre lies within {@code radius} blocks, nearest first. */
    public static List<ChunkPos> candidates(ServerLevel level, BlockPos from, int radius) {
        Optional<RandomSpreadStructurePlacement> p = placement(level);
        if (p.isEmpty()) return List.of();
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        long seed = state.getLevelSeed();
        RandomSpreadStructurePlacement spread = p.get();
        int spacing = spread.spacing();
        int cells = Math.ceilDiv(Math.ceilDiv(radius, 16), spacing) + 1;
        int cellX = Math.floorDiv(SectionPos.blockToSectionCoord(from.getX()), spacing);
        int cellZ = Math.floorDiv(SectionPos.blockToSectionCoord(from.getZ()), spacing);
        List<ChunkPos> out = new ArrayList<>();
        for (int dx = -cells; dx <= cells; dx++) {
            for (int dz = -cells; dz <= cells; dz++) {
                ChunkPos c = spread.getPotentialStructureChunk(seed, (cellX + dx) * spacing, (cellZ + dz) * spacing);
                if (distance(from, c) <= radius) out.add(c);
            }
        }
        out.sort(Comparator.comparingDouble(c -> distance(from, c)));
        return out;
    }

    static double distance(BlockPos from, ChunkPos c) {
        return Math.hypot(c.getMiddleBlockX() - from.getX(), c.getMiddleBlockZ() - from.getZ());
    }

    /**
     * Up to {@code limit} valid isle starts within {@code radius} blocks of {@code from}, nearest first.
     *
     * @param prefilter a pure test on the start chunk before any chunk is touched
     */
    public static List<Found> nearest(ServerLevel level, BlockPos from, int radius, Predicate<ChunkPos> prefilter, int limit) {
        List<Found> out = new ArrayList<>();
        Optional<Holder.Reference<Structure>> holder = holder(level);
        Optional<RandomSpreadStructurePlacement> placement = placement(level);
        if (holder.isEmpty() || placement.isEmpty()) return out;
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        StructureManager structures = level.structureManager();
        long t0 = System.nanoTime();
        int checked = 0;
        for (ChunkPos c : candidates(level, from, radius)) {
            if (out.size() >= limit) break;
            if (!prefilter.test(c) || !placement.get().isStructureChunk(state, c.x, c.z)) continue;
            checked++;
            StructureCheckResult r = structures.checkStructurePresence(c, holder.get().value(), placement.get(), false);
            if (r == StructureCheckResult.START_NOT_PRESENT) continue;
            ChunkAccess chunk = level.getChunk(c.x, c.z, ChunkStatus.STRUCTURE_STARTS);
            StructureStart start = structures.getStartForStructure(SectionPos.bottomOf(chunk), holder.get().value(), chunk);
            if (start == null || !start.isValid()) continue;
            List<IsleSpec> isles = new ArrayList<>();
            for (StructurePiece piece : start.getPieces()) if (piece instanceof IslePiece ip) isles.add(ip.spec());
            if (isles.isEmpty()) continue;
            out.add(new Found(c, isles, distance(from, c)));
        }
        FirmagesCore.LOGGER.debug("Isle search from {} within {} took {} ms ({} checked, {} found)", from.toShortString(), radius,
            (System.nanoTime() - t0) / 1_000_000, checked, out.size());
        return out;
    }

    /** True when the column lies over the planned footprint of a cluster start near it (pure, no chunk access). */
    public static boolean plannedOver(ServerLevel level, int x, int z) {
        Optional<RandomSpreadStructurePlacement> placement = placement(level);
        if (placement.isEmpty()) return false;
        IsleSettings s = settings(level);
        long seed = level.getChunkSource().getGeneratorState().getLevelSeed();
        for (ChunkPos c : candidates(level, new BlockPos(x, 0, z), s.maxExtent() + 16 * 2)) {
            if (!IsleLayout.withinReach(c.getMiddleBlockX(), c.getMiddleBlockZ(), x, z, s)) continue;
            boolean shrine = IsleLayout.shrineRoll(seed, c.x, c.z, s.windShrineChance());
            for (IsleLayout.Plan p : IsleLayout.plan(IsleLayout.clusterSeed(seed, c.x, c.z), c.getMiddleBlockX(), c.getMiddleBlockZ(), s, shrine)) {
                long dx = x - p.x();
                long dz = z - p.z();
                long r = p.radius() + 3;
                if (dx * dx + dz * dz <= r * r) return true;
            }
        }
        return false;
    }
}
