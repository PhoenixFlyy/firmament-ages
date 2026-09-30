package dev.firmages.core.age;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The eleven Ages in progression order. The id is the ProgressiveStages stage path.
 * Pure Java, no Minecraft types, so it is usable in unit tests.
 */
public enum AgeId {
    DAWN("dawn"),
    AGE_0("age_0"),
    AGE_1("age_1"),
    AGE_2("age_2"),
    AGE_3("age_3"),
    AGE_4("age_4"),
    AGE_5("age_5"),
    AGE_6("age_6"),
    AGE_7("age_7"),
    AGE_8("age_8"),
    AGE_9("age_9");

    private static final List<AgeId> ALL = List.of(values());

    private final String id;

    AgeId(String id) {
        this.id = id;
    }

    /** Stage id as used by ProgressiveStages and in the tag paths, e.g. {@code age_3}. */
    public String id() {
        return id;
    }

    public static List<AgeId> all() {
        return ALL;
    }

    /** Parses a stage id; accepts an optional namespace ({@code progressivestages:age_3}). */
    public static Optional<AgeId> byId(String stageId) {
        if (stageId == null) return Optional.empty();
        String path = stageId.trim().toLowerCase(Locale.ROOT);
        int colon = path.indexOf(':');
        if (colon >= 0) path = path.substring(colon + 1);
        String p = path;
        return Arrays.stream(values()).filter(a -> a.id.equals(p)).findFirst();
    }

    @Override
    public String toString() {
        return id;
    }
}
