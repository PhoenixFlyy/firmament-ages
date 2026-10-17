package dev.firmages.core.loot;

import dev.firmages.core.age.AgeId;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The loot Age rule (SPEC §19.5), pure Java: a generated stack stays when its item is untagged or its Age is unlocked
 * for whoever the loot is rolled for; an item of {@code age_items/disabled} never stays. The same rule as the recipe
 * gate (§4.3) and ProgressiveStages' item locks: an Age counts when it is in the set, not when a later Age is.
 */
public final class LootAgeRule {
    private LootAgeRule() {}

    /** True when a stack of an item with this Age (null = untagged) and disabled flag stays in loot rolled for {@code unlocked}. */
    public static boolean keep(AgeId age, boolean disabled, Set<AgeId> unlocked) {
        if (disabled) return false;
        return age == null || age == AgeId.DAWN || unlocked.contains(age);
    }

    /** The Ages of a set of stage ids (with or without namespace; other stages are ignored); Dawn is always in it. */
    public static EnumSet<AgeId> ages(Collection<String> stageIds) {
        EnumSet<AgeId> out = EnumSet.of(AgeId.DAWN);
        for (String id : stageIds) AgeId.byId(id).ifPresent(out::add);
        return out;
    }

    /**
     * Removes in place every entry whose item {@link #keep} rejects; {@code age} and {@code disabled} read the entry's
     * item (null and false for an empty entry, which always stays). Returns the number of removed entries.
     */
    public static <T> int filter(List<T> loot, Function<T, AgeId> age, Predicate<T> disabled, Set<AgeId> unlocked) {
        int before = loot.size();
        loot.removeIf(t -> !keep(age.apply(t), disabled.test(t), unlocked));
        return before - loot.size();
    }
}
