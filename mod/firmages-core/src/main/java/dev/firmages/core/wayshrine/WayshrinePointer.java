package dev.firmages.core.wayshrine;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.immersion.AgeStatus;
import dev.firmages.core.isles.IsleService;
import dev.firmages.core.text.Numbers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The pointer to the nearest sleeping Wayshrine (SPEC-himmel §28.8): from the home heart (from the Bronze Age, with
 * Caelum's name) and from a lit Wayshrine (without it). It looks at the cache in {@code firmages_wayshrines} first (found
 * structures, the tutorial Wayshrine, known cores that are not lit), then searches the structure tag
 * {@code #firmages:wayshrine} within {@code wayshrine.pointerRadiusChunks}, skipping structures already referenced (a lit
 * Wayshrine adds its reference). From the Industrial Age it also names the Wind Shrines of the Drifting Isles (S6B): the
 * isle starts whose Wind Shrine roll is true and the clusters placed by code ({@link IsleService#nearestShrine}). One
 * search per player per ten seconds; in between the last answer repeats.
 */
public final class WayshrinePointer {
    static final int SEARCH_COOLDOWN = 200;

    /** A sleeping Wayshrine the pointer names. */
    public record Target(BlockPos pos, String type) {}

    private record Answer(long at, Optional<Target> target) {}

    private static final Map<UUID, Answer> LAST = new HashMap<>();

    private WayshrinePointer() {}

    /** The pointer line for {@code player}: "A Wayshrine sleeps to the north-east, about 1,200 blocks." */
    public static Component line(MinecraftServer s, ServerPlayer player, boolean caelum) {
        if (player.level().dimension() != Level.OVERWORLD) return none(caelum);
        Optional<Target> t = find(s, player);
        if (t.isEmpty()) return none(caelum);
        BlockPos p = t.get().pos();
        String bearing = WayshrineRules.bearing(player.getX(), player.getZ(), p.getX() + 0.5, p.getZ() + 0.5);
        double d = Math.sqrt(Math.pow(p.getX() + 0.5 - player.getX(), 2) + Math.pow(p.getZ() + 0.5 - player.getZ(), 2));
        return Component.translatable(caelum ? "firmages.wayshrine.pointer.caelum" : "firmages.wayshrine.pointer",
            Component.translatable("firmages.bearing." + bearing), Numbers.words(WayshrineRules.roundedDistance(d)));
    }

    private static Component none(boolean caelum) {
        return Component.translatable(caelum ? "firmages.wayshrine.pointer.none.caelum" : "firmages.wayshrine.pointer.none");
    }

    /** The nearest sleeping Wayshrine for {@code player} (rate-limited per player). */
    public static Optional<Target> find(MinecraftServer s, ServerPlayer player) {
        long now = s.overworld().getGameTime();
        Answer last = LAST.get(player.getUUID());
        if (last != null && now - last.at() < SEARCH_COOLDOWN) return last.target();
        Optional<Target> t = nearest(s.overworld(), player.blockPosition(), true);
        LAST.put(player.getUUID(), new Answer(now, t));
        return t;
    }

    /**
     * The nearest sleeping Wayshrine from {@code from} in the overworld: the cache first, then the structure search.
     *
     * @param skipKnown skip structures with a reference (lit Wayshrines); {@code locate} passes false
     */
    public static Optional<Target> nearest(ServerLevel overworld, BlockPos from, boolean skipKnown) {
        int radius = ServerConfig.wayshrinePointerRadiusChunks() * 16;
        WayshrineSavedData sd = WayshrineSavedData.get(overworld.getServer());
        boolean isles = isleShrinesShown(overworld.getServer());
        Target best = null;
        double bestD = Double.MAX_VALUE;
        for (WayshrineSavedData.Found f : sd.found()) {
            if (lit(sd, f.pos()) || (!isles && WayshrinePlacement.ISLE_TYPE.equals(f.type()))) continue;
            double d = horizontal(from, f.pos());
            if (d <= radius && d < bestD) {
                best = new Target(f.pos(), f.type());
                bestD = d;
            }
        }
        for (WayshrineSavedData.Record r : sd.records()) {
            if (r.lit() || r.where().dimension() != Level.OVERWORLD || (!isles && WayshrinePlacement.ISLE_TYPE.equals(r.type()))) continue;
            double d = horizontal(from, r.where().pos());
            if (d <= radius && d < bestD) {
                best = new Target(r.where().pos(), r.type());
                bestD = d;
            }
        }
        if (best != null) return Optional.of(best);
        Optional<Target> ground = search(overworld, from, null, skipKnown);
        if (isles) {
            // the Wind Shrine stands on an isle (S6B): the nearest isle start whose cluster carries it, from the Industrial Age
            int reach = ground.map(t -> (int) Math.ceil(horizontal(from, t.pos()))).orElse(radius);
            Optional<Target> isle = isleShrine(overworld, from, reach, skipKnown);
            if (isle.isPresent()) ground = isle;
        }
        return ground.map(t -> {
            sd.addFound(new WayshrineSavedData.Found(t.pos(), t.type()));
            return t;
        });
    }

    /** From the Industrial Age the pointer also names the Wind Shrines of the Drifting Isles (SPEC-himmel §28.4a). */
    static boolean isleShrinesShown(MinecraftServer s) {
        return AgeStatus.current(AgeService.state(s).snapshot()).ordinal() >= AgeId.AGE_4.ordinal();
    }

    /** The nearest Wind Shrine of an isle within {@code radius} blocks (lit ones skipped when {@code skipLit}). */
    static Optional<Target> isleShrine(ServerLevel overworld, BlockPos from, int radius, boolean skipLit) {
        WayshrineSavedData sd = WayshrineSavedData.get(overworld.getServer());
        return IsleService.nearestShrine(overworld, from, radius, p -> skipLit && lit(sd, p))
            .map(p -> new Target(p, WayshrinePlacement.ISLE_TYPE));
    }

    /**
     * Structure search over {@code #firmages:wayshrine} (or one type) within {@code wayshrine.pointerRadiusChunks}. Empty
     * when the tag is empty (no TFC) or nothing is in reach.
     *
     * <p>Not {@code ChunkGenerator.findNearestMapStructure}: its radius counts placement cells, so with the first spacing of 128
     * chunks a radius of 100 would search 100 rings of cells and generate structure starts up to 200,000 blocks
     * away (a two-minute tick on the 0.10.0 copy boot). This search lists the one candidate chunk of every cell whose
     * candidate lies within the radius in blocks, sorts them by distance, drops those the placement itself refuses
     * ({@code isStructureChunk}: TFC's climate check, frequency, exclusion zones) and only then asks the structure
     * manager, loading a chunk to {@code STRUCTURE_STARTS} where the start is not known yet. The first valid start wins.
     */
    public static Optional<Target> search(ServerLevel overworld, BlockPos from, @Nullable String type, boolean skipKnown) {
        if (WayshrinePlacement.ISLE_TYPE.equals(type)) return isleShrine(overworld, from, ServerConfig.wayshrinePointerRadiusChunks() * 16, skipKnown);
        var registry = overworld.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<Holder<Structure>> holders = new ArrayList<>();
        if (type == null) {
            Optional<HolderSet.Named<Structure>> tag = registry.getTag(WayshrineService.STRUCTURES);
            if (tag.isEmpty() || tag.get().size() == 0) return Optional.empty();
            tag.get().forEach(holders::add);
        } else {
            Optional<Holder.Reference<Structure>> h = registry.getHolder(ResourceKey.create(Registries.STRUCTURE,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "wayshrine/" + type)));
            if (h.isEmpty()) return Optional.empty();
            holders.add(h.get());
        }
        long t0 = System.nanoTime();
        ChunkGeneratorStructureState state = overworld.getChunkSource().getGeneratorState();
        long seed = state.getLevelSeed();
        int radius = ServerConfig.wayshrinePointerRadiusChunks() * 16;
        int fromCx = SectionPos.blockToSectionCoord(from.getX());
        int fromCz = SectionPos.blockToSectionCoord(from.getZ());
        List<Candidate> candidates = new ArrayList<>();
        for (Holder<Structure> holder : holders) {
            for (StructurePlacement placement : state.getPlacementsForStructure(holder)) {
                if (!(placement instanceof RandomSpreadStructurePlacement spread)) continue;
                int spacing = spread.spacing();
                int cells = Math.ceilDiv(ServerConfig.wayshrinePointerRadiusChunks(), spacing) + 1;
                int cellX = Math.floorDiv(fromCx, spacing);
                int cellZ = Math.floorDiv(fromCz, spacing);
                for (int dx = -cells; dx <= cells; dx++) {
                    for (int dz = -cells; dz <= cells; dz++) {
                        ChunkPos c = spread.getPotentialStructureChunk(seed, (cellX + dx) * spacing, (cellZ + dz) * spacing);
                        BlockPos at = placement.getLocatePos(c);
                        double d = horizontal(from, at);
                        if (d <= radius) candidates.add(new Candidate(holder, placement, c, d));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distance));
        StructureManager structures = overworld.structureManager();
        Target found = null;
        int checked = 0;
        for (Candidate cand : candidates) {
            if (!cand.placement().isStructureChunk(state, cand.chunk().x, cand.chunk().z)) continue;
            checked++;
            StructureCheckResult r = structures.checkStructurePresence(cand.chunk(), cand.holder().value(), cand.placement(), skipKnown);
            if (r == StructureCheckResult.START_NOT_PRESENT) continue;
            if (!skipKnown && r == StructureCheckResult.START_PRESENT) {
                found = target(cand.holder(), cand.placement().getLocatePos(cand.chunk()));
                break;
            }
            ChunkAccess chunk = overworld.getChunk(cand.chunk().x, cand.chunk().z, ChunkStatus.STRUCTURE_STARTS);
            StructureStart start = structures.getStartForStructure(SectionPos.bottomOf(chunk), cand.holder().value(), chunk);
            if (start == null || !start.isValid()) continue;
            if (skipKnown) {
                if (!start.canBeReferenced()) continue;
                structures.addReference(start);
            }
            found = target(cand.holder(), cand.placement().getLocatePos(start.getChunkPos()));
            break;
        }
        FirmagesCore.LOGGER.debug("Wayshrine search from {} took {} ms ({} candidates, {} checked): {}", from,
            (System.nanoTime() - t0) / 1_000_000, candidates.size(), checked, found == null ? "none" : found.pos());
        return Optional.ofNullable(found);
    }

    private record Candidate(Holder<Structure> holder, StructurePlacement placement, ChunkPos chunk, double distance) {}

    private static Target target(Holder<Structure> holder, BlockPos pos) {
        String tid = holder.unwrapKey().map(k -> k.location().getPath()).orElse("wayshrine/unknown");
        return new Target(pos, tid.startsWith("wayshrine/") ? tid.substring("wayshrine/".length()) : tid);
    }

    private static boolean lit(WayshrineSavedData sd, BlockPos structurePos) {
        for (WayshrineSavedData.Record r : sd.records()) {
            if (!r.lit() || r.where().dimension() != Level.OVERWORLD) continue;
            BlockPos c = r.where().pos();
            if (Math.abs(c.getX() - structurePos.getX()) <= 32 && Math.abs(c.getZ() - structurePos.getZ()) <= 32) return true;
        }
        return false;
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Server stop: forget the per-player answers. */
    static void clear() {
        LAST.clear();
    }
}
