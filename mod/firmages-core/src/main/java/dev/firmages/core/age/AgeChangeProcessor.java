package dev.firmages.core.age;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Turns ProgressiveStages stage changes into AgeState changes (SPEC §3.1). Pure Java (unit-testable):
 * <ul>
 *   <li>Non-Age stages are ignored.</li>
 *   <li>The same (stage, change) within one tick is handled once, because team sync may fire once per member.</li>
 *   <li>Only real changes of the ledger reach the sink (persist, reload request, revoke warning).</li>
 *   <li>Bulk events only add Ages: a bulk set is one team's view, and AgeState is the union over all teams,
 *       so a bulk set without an Age proves nothing (for example a new player's own team on login).</li>
 * </ul>
 */
public final class AgeChangeProcessor {

    public interface Sink {
        /** The ledger changed: persist SavedData and the mirror. */
        void persist();

        /** @param revoke true when the change removed an Age (the sink applies {@code gate.reloadOnRevoke}). */
        void requestReload(String reason, boolean revoke);

        /** An Age was revoked (after persist and the reload request). */
        void revoked(AgeId age);
    }

    private final AgeLedger ledger;
    private final Sink sink;
    private long dedupTick = Long.MIN_VALUE;
    private final Set<String> seenThisTick = new HashSet<>();

    public AgeChangeProcessor(AgeLedger ledger, Sink sink) {
        this.ledger = ledger;
        this.sink = sink;
    }

    /** Single stage change. @return true if AgeState changed. */
    public boolean onStageChange(String stageId, boolean granted, long tick) {
        Optional<AgeId> age = AgeId.byId(stageId);
        if (age.isEmpty()) return false;
        if (tick != dedupTick) {
            dedupTick = tick;
            seenThisTick.clear();
        }
        if (!seenThisTick.add(age.get().id() + (granted ? "+" : "-"))) return false;
        boolean changed = granted ? ledger.grant(age.get()) : ledger.revoke(age.get());
        if (!changed) return false;
        sink.persist();
        sink.requestReload((granted ? "granted " : "revoked ") + age.get().id(), !granted);
        if (!granted) sink.revoked(age.get());
        return true;
    }

    /** Bulk change or reconcile: adds every Age of {@code currentStages} that AgeState lacks. @return the added Ages. */
    public List<AgeId> onStagesPresent(Collection<String> currentStages, String reason) {
        List<AgeId> added = new ArrayList<>();
        for (String s : currentStages) {
            AgeId.byId(s).ifPresent(a -> {
                if (ledger.grant(a)) added.add(a);
            });
        }
        if (!added.isEmpty()) {
            sink.persist();
            sink.requestReload(reason + ": granted " + added, false);
        }
        return added;
    }
}
