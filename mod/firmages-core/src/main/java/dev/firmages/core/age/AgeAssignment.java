package dev.firmages.core.age;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Element id → Age from the tags {@code firmages:<prefix>/<age>} of one tag directory. An id found in two Age
 * tags takes the earliest Age and is reported as a duplicate. Pure Java (unit-testable).
 *
 * @param byId       element id → earliest Age
 * @param perAge     number of ids assigned to each Age (after the earliest-wins rule)
 * @param missingTags Age tags that no pack defines
 * @param duplicates one line per id that appears in more than one Age tag
 */
public record AgeAssignment(Map<String, AgeId> byId, Map<AgeId, Integer> perAge, List<AgeId> missingTags, List<String> duplicates) {

    public static final String NAMESPACE = "firmages";

    public static String tagId(String prefix, AgeId age) {
        return NAMESPACE + ":" + prefix + "/" + age.id();
    }

    public static AgeAssignment assign(AgeTagResolver resolver, String prefix) {
        Map<String, AgeId> byId = new LinkedHashMap<>();
        Map<AgeId, Integer> perAge = new EnumMap<>(AgeId.class);
        List<AgeId> missing = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        for (AgeId age : AgeId.all()) {
            String tag = tagId(prefix, age);
            if (!resolver.exists(tag)) {
                missing.add(age);
                continue;
            }
            for (String id : resolver.resolve(tag)) {
                AgeId prev = byId.putIfAbsent(id, age);
                if (prev != null && prev != age) duplicates.add(id + " in " + prev.id() + " and " + age.id() + " (kept " + prev.id() + ")");
            }
        }
        byId.values().forEach(a -> perAge.merge(a, 1, Integer::sum));
        return new AgeAssignment(Collections.unmodifiableMap(byId), Collections.unmodifiableMap(perAge),
            List.copyOf(missing), List.copyOf(duplicates));
    }
}
