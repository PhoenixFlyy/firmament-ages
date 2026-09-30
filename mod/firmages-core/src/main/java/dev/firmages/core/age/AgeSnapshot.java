package dev.firmages.core.age;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable view of the unlocked Ages. {@code dawn} is always contained.
 * Pure Java (unit-testable).
 */
public record AgeSnapshot(Set<AgeId> unlocked, long version, long lastReloadGameTime) {

    public static final AgeSnapshot DAWN_ONLY = of(List.of(AgeId.DAWN), 0, 0);

    public AgeSnapshot {
        EnumSet<AgeId> copy = unlocked.isEmpty() ? EnumSet.noneOf(AgeId.class) : EnumSet.copyOf(unlocked);
        copy.add(AgeId.DAWN);
        unlocked = Collections.unmodifiableSet(copy);
    }

    public static AgeSnapshot of(Collection<AgeId> unlocked, long version, long lastReloadGameTime) {
        return new AgeSnapshot(unlocked.isEmpty() ? EnumSet.noneOf(AgeId.class) : EnumSet.copyOf(unlocked), version, lastReloadGameTime);
    }

    public boolean isUnlocked(AgeId age) {
        return unlocked.contains(age);
    }

    /** Locked Ages in progression order. */
    public List<AgeId> locked() {
        return AgeId.all().stream().filter(a -> !unlocked.contains(a)).toList();
    }

    /** Unlocked Ages in progression order. */
    public List<AgeId> unlockedOrdered() {
        return AgeId.all().stream().filter(unlocked::contains).toList();
    }

    public List<String> unlockedIds() {
        return unlockedOrdered().stream().map(AgeId::id).toList();
    }

    /** True when both snapshots unlock the same Ages (version and times are ignored). */
    public boolean sameAges(AgeSnapshot other) {
        return other != null && unlocked.equals(other.unlocked);
    }
}
