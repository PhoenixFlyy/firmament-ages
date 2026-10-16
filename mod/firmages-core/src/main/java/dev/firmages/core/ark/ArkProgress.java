package dev.firmages.core.ark;


import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What one island of the Archipelago has received (SPEC §31.8), kept for good in {@code OriginSavedData} under
 * {@code ark.<age>}: the groups done, the flora items given (distinct ids count), the crown items given per entry, the
 * stage grown so far, whether the island is complete, where its plinth stands, and every block its stages placed (so a
 * later stage may replace them, and only them, besides air). Pure Java (unit-tested); {@link ArkState} reads and writes
 * the NBT.
 */
public final class ArkProgress {
    final Set<String> groups = new TreeSet<>();
    final Map<String, Integer> flora = new TreeMap<>();
    final Map<Integer, Integer> crown = new TreeMap<>();
    /** The entity type that was given as the fauna sample (status only). */
    String faunaEntity = "";
    int stage;
    boolean complete;
    /** {@code BlockPos#asLong} of the plinth, or {@link Long#MIN_VALUE} while none stands. */
    long plinth = Long.MIN_VALUE;
    /** Blocks the island's own stages placed: position ({@code BlockPos#asLong}) -> block id. Insertion order is kept. */
    final Map<Long, String> grown = new LinkedHashMap<>();

    public Set<String> groups() {
        return groups;
    }

    public Map<String, Integer> flora() {
        return flora;
    }

    public Map<Integer, Integer> crown() {
        return crown;
    }

    public String faunaEntity() {
        return faunaEntity;
    }

    public int stage() {
        return stage;
    }

    public boolean complete() {
        return complete;
    }

    public long plinth() {
        return plinth;
    }

    public Map<Long, String> grown() {
        return grown;
    }

    public boolean has(String group) {
        return groups.contains(group);
    }

    /** Forgets everything but the plinth position. */
    void clear() {
        groups.clear();
        flora.clear();
        crown.clear();
        faunaEntity = "";
        stage = 0;
        complete = false;
        grown.clear();
    }
}
