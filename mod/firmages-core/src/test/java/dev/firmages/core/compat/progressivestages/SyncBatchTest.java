package dev.firmages.core.compat.progressivestages;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Level U: the per-tick batch of deferred ProgressiveStages stage syncs (SPEC §3.5, {@code sync.coalesce}). */
class SyncBatchTest {
    private static List<String> keys(SyncBatch.Drained<String, Set<String>> d) {
        return d.syncs().stream().map(Map.Entry::getKey).toList();
    }

    @Test
    void anAgeGrantBecomesOneSyncPerPlayer() {
        SyncBatch<String, Set<String>> b = new SyncBatch<>();
        // grants.js after age_4 with two team members: the Age, two mob ladder changes, two helper windows
        String[][] changes = {{"age_4"}, {"age_4", "mob_3"}, {"age_4", "mob_3", "mob_4"}, {"age_4", "mob_4"}, {"age_4", "mob_4", "tool_anvil"}};
        for (String[] stages : changes) {
            b.add("alice", Set.of(stages));
            b.add("bob", Set.of(stages));
        }
        assertEquals(10, b.requests());
        assertEquals(2, b.size());
        SyncBatch.Drained<String, Set<String>> d = b.drain();
        assertEquals(List.of("alice", "bob"), keys(d), "one sync per player, in first-request order");
        assertEquals(10, d.requests());
        for (Map.Entry<String, Set<String>> e : d.syncs()) {
            assertEquals(Set.of("age_4", "mob_4", "tool_anvil"), e.getValue(), "the stage set of the last call wins");
        }
        assertTrue(b.isEmpty());
        assertEquals(0, b.requests(), "drain starts a new batch");
        assertTrue(b.drain().syncs().isEmpty());
    }

    @Test
    void laterRequestKeepsTheFirstPosition() {
        SyncBatch<String, Set<String>> b = new SyncBatch<>();
        b.add("bob", Set.of("a"));
        b.add("alice", Set.of("a"));
        b.add("bob", Set.of("a", "b"));
        SyncBatch.Drained<String, Set<String>> d = b.drain();
        assertEquals(List.of("bob", "alice"), keys(d));
        assertEquals(Set.of("a", "b"), d.syncs().get(0).getValue());
    }

    @Test
    void aFullSyncSupersedesThePendingOne() {
        SyncBatch<String, Set<String>> b = new SyncBatch<>();
        b.add("alice", Set.of("a"));
        b.add("bob", Set.of("a"));
        assertTrue(b.supersede("alice"), "syncPlayer/onPlayerJoin sent alice's state directly");
        assertFalse(b.supersede("alice"));
        assertFalse(b.supersede("carol"));
        SyncBatch.Drained<String, Set<String>> d = b.drain();
        assertEquals(List.of("bob"), keys(d));
        assertEquals(2, d.requests(), "the superseded request still counts as a request");
    }

    @Test
    void aRequestAfterTheSupersedeIsSentAgain() {
        SyncBatch<String, Set<String>> b = new SyncBatch<>();
        b.add("alice", Set.of("a"));
        b.supersede("alice");
        b.add("alice", Set.of("a", "b"));
        SyncBatch.Drained<String, Set<String>> d = b.drain();
        assertEquals(List.of("alice"), keys(d));
        assertEquals(Set.of("a", "b"), d.syncs().get(0).getValue());
    }

    @Test
    void drainedEntriesSurviveTheNextBatch() {
        SyncBatch<String, Set<String>> b = new SyncBatch<>();
        b.add("alice", Set.of("a"));
        SyncBatch.Drained<String, Set<String>> d = b.drain();
        b.add("alice", Set.of("x"));
        assertEquals(Set.of("a"), d.syncs().get(0).getValue());
        assertEquals(1, b.size());
    }
}
