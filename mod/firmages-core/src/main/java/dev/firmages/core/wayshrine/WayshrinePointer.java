package dev.firmages.core.wayshrine;

import com.mojang.datafixers.util.Pair;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.text.Numbers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The pointer to the nearest sleeping Wayshrine (SPEC-himmel §28.8): from the home heart (from the Bronze Age, with
 * Caelum's name) and from a lit Wayshrine (without it). It looks at the cache in {@code firmages_wayshrines} first (found
 * structures, the tutorial Wayshrine, known cores that are not lit), then searches the structure tag
 * {@code #firmages:wayshrine} within {@code wayshrine.pointerRadiusChunks}, skipping structures already referenced (a lit
 * Wayshrine adds its reference). One search per player per ten seconds; in between the last answer repeats.
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
        Target best = null;
        double bestD = Double.MAX_VALUE;
        for (WayshrineSavedData.Found f : sd.found()) {
            if (lit(sd, f.pos())) continue;
            double d = horizontal(from, f.pos());
            if (d <= radius && d < bestD) {
                best = new Target(f.pos(), f.type());
                bestD = d;
            }
        }
        for (WayshrineSavedData.Record r : sd.records()) {
            if (r.lit() || r.where().dimension() != Level.OVERWORLD) continue;
            double d = horizontal(from, r.where().pos());
            if (d <= radius && d < bestD) {
                best = new Target(r.where().pos(), r.type());
                bestD = d;
            }
        }
        if (best != null) return Optional.of(best);
        return search(overworld, from, null, skipKnown).map(t -> {
            sd.addFound(new WayshrineSavedData.Found(t.pos(), t.type()));
            return t;
        });
    }

    /**
     * Structure search over {@code #firmages:wayshrine} (or one type) with {@code ChunkGenerator.findNearestMapStructure},
     * radius {@code wayshrine.pointerRadiusChunks}. Empty when the tag is empty (no TFC) or nothing is in reach.
     */
    public static Optional<Target> search(ServerLevel overworld, BlockPos from, @Nullable String type, boolean skipKnown) {
        var registry = overworld.registryAccess().registryOrThrow(Registries.STRUCTURE);
        HolderSet<Structure> set;
        if (type == null) {
            Optional<HolderSet.Named<Structure>> tag = registry.getTag(WayshrineService.STRUCTURES);
            if (tag.isEmpty() || tag.get().size() == 0) return Optional.empty();
            set = tag.get();
        } else {
            Optional<Holder.Reference<Structure>> h = registry.getHolder(ResourceKey.create(Registries.STRUCTURE,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "wayshrine/" + type)));
            if (h.isEmpty()) return Optional.empty();
            set = HolderSet.direct(h.get());
        }
        long t0 = System.nanoTime();
        Pair<BlockPos, Holder<Structure>> found = overworld.getChunkSource().getGenerator()
            .findNearestMapStructure(overworld, set, from, ServerConfig.wayshrinePointerRadiusChunks(), skipKnown);
        FirmagesCore.LOGGER.debug("Wayshrine search from {} took {} ms: {}", from, (System.nanoTime() - t0) / 1_000_000, found == null ? "none" : found.getFirst());
        if (found == null) return Optional.empty();
        String tid = found.getSecond().unwrapKey().map(k -> k.location().getPath()).orElse("wayshrine/unknown");
        return Optional.of(new Target(found.getFirst(), tid.startsWith("wayshrine/") ? tid.substring("wayshrine/".length()) : tid));
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
