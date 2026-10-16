package dev.firmages.core.shrine;

import com.google.gson.JsonObject;

import java.util.Optional;

/**
 * The proof of work a shrine offering waits for (Felix H5A, SPEC §35): {@code count} items of {@code item} (an item id)
 * or {@code tag} (an item tag, without {@code #}) must have passed the fellowship's Tally Chutes since {@code age}
 * dawned, the same numbers as the {@code firmages:tally} criterion (SPEC §22.2). One entry per offering Age in
 * {@code data/<ns>/firmages_shrine/tallies.json}; pure Java (Gson only), unit-tested.
 *
 * @param age the first Age whose tally counts; by default the Age of the offering itself
 */
public record TallyRequirement(Optional<String> item, Optional<String> tag, long count, String age) {

    /** {@code {"tag": "c:ingots/steel", "count": 512}} or {@code {"item": "...", "count": n, "age": "age_4"}}. */
    static TallyRequirement parse(String key, JsonObject o) {
        Optional<String> item = o.has("item") ? Optional.of(ShrineData.resourceId(o.get("item").getAsString())) : Optional.empty();
        Optional<String> tag = o.has("tag") ? Optional.of(ShrineData.resourceId(strip(o.get("tag").getAsString()))) : Optional.empty();
        if (item.isPresent() == tag.isPresent()) throw new IllegalArgumentException("tally '" + key + "' needs exactly one of 'item' and 'tag'");
        if (!o.has("count")) throw new IllegalArgumentException("tally '" + key + "' needs 'count'");
        long count = o.get("count").getAsLong();
        if (count < 1) throw new IllegalArgumentException("tally '" + key + "': 'count' must be positive");
        String age = o.has("age") ? o.get("age").getAsString() : key;
        if (!age.matches("dawn|age_[0-9]")) throw new IllegalArgumentException("tally '" + key + "': 'age' must be dawn or age_0..age_9");
        return new TallyRequirement(item, tag, count, age);
    }

    private static String strip(String s) {
        return s.startsWith("#") ? s.substring(1) : s;
    }

    /** {@code item minecraft:iron_ingot} or {@code tag #c:ingots/steel}, for logs and the status command. */
    public String describe() {
        return item.map(i -> "item " + i).orElseGet(() -> "tag #" + tag.orElseThrow()) + " x" + count + " since " + age;
    }
}
