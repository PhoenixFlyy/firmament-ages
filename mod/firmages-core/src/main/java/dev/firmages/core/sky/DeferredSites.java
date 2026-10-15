package dev.firmages.core.sky;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Work that waits for its chunks (SPEC §24.4): a chunk-to-site index. {@code ChunkEvent.Load} only looks the chunk up
 * and enqueues the sites it belongs to (O(1), no level access: NeoForge 21.1.252 warns the event may fire before the
 * chunk is FULL, and level access there can deadlock [verified]). The server tick step then checks each queued site:
 * once every chunk of its box is loaded at FULL ({@code getChunkNow}), it calls the site's handler. Nothing is
 * force-loaded or generated. A queued site whose chunks are not ready stays queued for {@link #RETRY_TICKS} ticks
 * (a chunk that fires the event early is FULL a few ticks later), then waits for the next load.
 */
public final class DeferredSites {
    /** Sites the tick step hands to their handlers per tick at most. */
    public static final int READY_PER_TICK = 4;
    public static final int RETRY_TICKS = 200;

    /** Runs on the server thread once every chunk of the site is loaded at FULL. */
    @FunctionalInterface
    public interface Handler {
        void ready(ServerLevel level, long id);
    }

    public record Key(ResourceKey<Level> dimension, String kind, long id) {}

    private static final Map<String, Handler> HANDLERS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long2ObjectMap<Set<Key>>> INDEX = new HashMap<>();
    private static final Map<Key, long[]> CHUNKS = new HashMap<>();
    /** Queued site -> game tick until which it is retried. */
    private static final Map<Key, Long> QUEUE = new LinkedHashMap<>();
    private static long tick;

    private DeferredSites() {}

    public static synchronized void handler(String kind, Handler handler) {
        HANDLERS.put(kind, handler);
    }

    /** Registers (or re-registers) a site with the chunks of its box and queues it once (its chunks may be loaded already). */
    public static synchronized void register(ResourceKey<Level> dimension, String kind, long id, long[] chunks) {
        Key key = new Key(dimension, kind, id);
        unregister(key);
        CHUNKS.put(key, chunks.clone());
        Long2ObjectMap<Set<Key>> idx = INDEX.computeIfAbsent(dimension, d -> new Long2ObjectOpenHashMap<>());
        for (long c : chunks) idx.computeIfAbsent(c, k -> new HashSet<>()).add(key);
        QUEUE.put(key, tick + RETRY_TICKS);
    }

    public static synchronized void unregister(ResourceKey<Level> dimension, String kind, long id) {
        unregister(new Key(dimension, kind, id));
    }

    private static void unregister(Key key) {
        long[] chunks = CHUNKS.remove(key);
        QUEUE.remove(key);
        if (chunks == null) return;
        Long2ObjectMap<Set<Key>> idx = INDEX.get(key.dimension());
        if (idx == null) return;
        for (long c : chunks) {
            Set<Key> s = idx.get(c);
            if (s != null && s.remove(key) && s.isEmpty()) idx.remove(c);
        }
    }

    public static synchronized boolean registered(ResourceKey<Level> dimension, String kind, long id) {
        return CHUNKS.containsKey(new Key(dimension, kind, id));
    }

    public static synchronized boolean queued(ResourceKey<Level> dimension, String kind, long id) {
        return QUEUE.containsKey(new Key(dimension, kind, id));
    }

    /** Queues a site again (an operator ticket, a resumed carve). */
    public static synchronized void requeue(ResourceKey<Level> dimension, String kind, long id) {
        Key key = new Key(dimension, kind, id);
        if (CHUNKS.containsKey(key)) QUEUE.put(key, tick + RETRY_TICKS);
    }

    /** {@code ChunkEvent.Load}: one index lookup, no level access. */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long pos = event.getChunk().getPos().toLong();
        synchronized (DeferredSites.class) {
            Long2ObjectMap<Set<Key>> idx = INDEX.get(level.dimension());
            if (idx == null) return;
            Set<Key> keys = idx.get(pos);
            if (keys == null) return;
            for (Key k : keys) QUEUE.put(k, tick + RETRY_TICKS);
        }
    }

    /** True when every chunk is loaded at FULL (no load, no generation). */
    public static boolean allLoaded(ServerLevel level, long[] chunks) {
        for (long c : chunks) {
            if (level.getChunkSource().getChunkNow(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c)) == null) return false;
        }
        return true;
    }

    /** The tick step (server tick, Post): hands ready sites to their handlers, at most {@link #READY_PER_TICK}. */
    public static void tick(MinecraftServer server) {
        List<Map.Entry<Key, long[]>> ready = new ArrayList<>();
        synchronized (DeferredSites.class) {
            tick++;
            if (QUEUE.isEmpty()) return;
            var it = QUEUE.entrySet().iterator();
            while (it.hasNext() && ready.size() < READY_PER_TICK) {
                Map.Entry<Key, Long> e = it.next();
                Key key = e.getKey();
                long[] chunks = CHUNKS.get(key);
                ServerLevel level = server.getLevel(key.dimension());
                if (chunks == null || level == null) {
                    it.remove();
                    continue;
                }
                if (allLoaded(level, chunks)) {
                    it.remove();
                    ready.add(Map.entry(key, chunks));
                } else if (e.getValue() < tick) {
                    it.remove();
                }
            }
        }
        for (Map.Entry<Key, long[]> r : ready) {
            Handler h;
            synchronized (DeferredSites.class) {
                h = HANDLERS.get(r.getKey().kind());
            }
            ServerLevel level = server.getLevel(r.getKey().dimension());
            if (h != null && level != null) h.ready(level, r.getKey().id());
        }
    }

    /** Server stop: forget every site (they are registered again from their saved data at the next start). */
    public static synchronized void clear() {
        INDEX.clear();
        CHUNKS.clear();
        QUEUE.clear();
    }

    public static synchronized int registeredCount() {
        return CHUNKS.size();
    }
}
