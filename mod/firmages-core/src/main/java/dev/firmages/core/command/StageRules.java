package dev.firmages.core.command;

import dev.firmages.core.age.AgeId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The order rules of {@code /firmages stage} (SPEC §10.2). Ages stay contiguous: a grant of an Age also grants every
 * earlier Age the holder lacks (Dawn first), a revoke also revokes every later Age the holder has (latest first). No
 * Minecraft types, so JUnit tests them ({@code StageRulesTest}).
 */
public final class StageRules {
    private StageRules() {}

    /** The Ages a grant of {@code target} hands out, in progression order: every Age up to it that {@code held} lacks. */
    public static List<AgeId> agesToGrant(Set<AgeId> held, AgeId target) {
        List<AgeId> out = new ArrayList<>();
        for (AgeId a : AgeId.all()) {
            if (a.ordinal() > target.ordinal()) break;
            if (!held.contains(a)) out.add(a);
        }
        return out;
    }

    /** The Ages a revoke of {@code target} takes away, latest first: {@code target} and every later Age in {@code held}. */
    public static List<AgeId> agesToRevoke(Set<AgeId> held, AgeId target) {
        List<AgeId> out = new ArrayList<>();
        for (AgeId a : AgeId.all().reversed()) {
            if (a.ordinal() < target.ordinal()) break;
            if (held.contains(a)) out.add(a);
        }
        return out;
    }

    /** {@code clear}: the held stages in revoke order, the Ages latest first, then the others by id. */
    public static List<String> clearOrder(Collection<String> held) {
        List<String> ages = new ArrayList<>();
        for (AgeId a : AgeId.all().reversed()) if (held.contains(a.id())) ages.add(a.id());
        List<String> rest = held.stream().filter(s -> AgeId.byId(s).isEmpty()).sorted().toList();
        ages.addAll(rest);
        return ages;
    }

    /** {@code list} and the tab completion: the Ages in progression order, then the other stages by id. */
    public static List<String> listOrder(Collection<String> ids) {
        Comparator<String> c = Comparator.comparingInt((String s) -> AgeId.byId(s).map(Enum::ordinal).orElse(Integer.MAX_VALUE))
            .thenComparing(Comparator.naturalOrder());
        return ids.stream().distinct().sorted(c).toList();
    }
}
