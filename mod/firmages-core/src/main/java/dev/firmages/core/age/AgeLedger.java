package dev.firmages.core.age;

import java.util.Collection;
import java.util.EnumSet;

/**
 * Mutable Age set behind {@link AgeState}. Every change bumps the version. The current value is
 * published as an immutable {@link AgeSnapshot} in a volatile field, so reload threads can read it.
 * Mutations happen on the server thread only. Pure Java (unit-testable).
 */
public final class AgeLedger {
    private volatile AgeSnapshot snapshot;

    public AgeLedger() {
        this(AgeSnapshot.DAWN_ONLY);
    }

    public AgeLedger(AgeSnapshot initial) {
        this.snapshot = initial;
    }

    public AgeSnapshot snapshot() {
        return snapshot;
    }

    /** @return true if the Age was not unlocked before. */
    public synchronized boolean grant(AgeId age) {
        AgeSnapshot s = snapshot;
        if (s.isUnlocked(age)) return false;
        EnumSet<AgeId> next = EnumSet.copyOf(s.unlocked());
        next.add(age);
        snapshot = new AgeSnapshot(next, s.version() + 1, s.lastReloadGameTime());
        return true;
    }

    /** @return true if the Age was unlocked before. {@code dawn} can never be revoked. */
    public synchronized boolean revoke(AgeId age) {
        AgeSnapshot s = snapshot;
        if (age == AgeId.DAWN || !s.isUnlocked(age)) return false;
        EnumSet<AgeId> next = EnumSet.copyOf(s.unlocked());
        next.remove(age);
        snapshot = new AgeSnapshot(next, s.version() + 1, s.lastReloadGameTime());
        return true;
    }

    /** Adds every given Age. @return true if at least one was new. */
    public synchronized boolean grantAll(Collection<AgeId> ages) {
        boolean changed = false;
        for (AgeId a : ages) changed |= grant(a);
        return changed;
    }

    public synchronized void setLastReloadGameTime(long gameTime) {
        AgeSnapshot s = snapshot;
        snapshot = new AgeSnapshot(s.unlocked(), s.version(), gameTime);
    }
}
