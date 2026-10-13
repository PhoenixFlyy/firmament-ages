package dev.firmages.core.compat.progressivestages;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The deferred ProgressiveStages stage syncs of one tick (pure Java, state for {@link StageSyncCoalescer}).
 *
 * <p>One entry per key (the player UUID): a later request for the same key replaces the value (the stage set the
 * last call would have sent) and keeps the key's place, so {@link #drain()} returns every key once, in the order of
 * its first request. {@link #supersede} drops a key whose full sync (lock sync and stage sync) went out directly
 * after the request, so the batch does not send it a second time.
 */
public final class SyncBatch<K, V> {
    private final LinkedHashMap<K, V> pending = new LinkedHashMap<>();
    private int requests;

    /** Records one deferred sync request. */
    public void add(K key, V value) {
        pending.put(key, value);
        requests++;
    }

    /** Drops the pending request of {@code key}; @return true if there was one. */
    public boolean supersede(K key) {
        return pending.remove(key) != null;
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }

    /** Pending keys. */
    public int size() {
        return pending.size();
    }

    /** Requests recorded since the last {@link #drain()}, superseded ones included. */
    public int requests() {
        return requests;
    }

    /** Takes the pending syncs (first-request order, last value per key) and starts an empty batch. */
    public Drained<K, V> drain() {
        List<Map.Entry<K, V>> syncs = new ArrayList<>(pending.size());
        for (Map.Entry<K, V> e : pending.entrySet()) syncs.add(new AbstractMap.SimpleImmutableEntry<>(e));
        Drained<K, V> d = new Drained<>(List.copyOf(syncs), requests);
        pending.clear();
        requests = 0;
        return d;
    }

    /** One drained batch: the syncs to send and how many requests they stand for. */
    public record Drained<K, V>(List<Map.Entry<K, V>> syncs, int requests) {}
}
