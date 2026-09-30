package dev.firmages.core.age;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;

/**
 * The mirror file {@code <world>/firmages/ages.json}: a copy of {@link AgeState} that can be read during
 * the initial datapack load, before SavedData exists. Fail-strict: anything that is not a well-formed mirror
 * is reported as {@link Status#CORRUPT} and the caller falls back to the strict boot stages.
 * Pure Java (unit-testable).
 */
public final class AgeMirror {
    public static final String RELATIVE_PATH = "firmages/ages.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public enum Status { OK, MISSING, CORRUPT }

    /** @param snapshot present only for {@link Status#OK}; @param detail human-readable reason for the status. */
    public record ReadResult(Status status, Optional<AgeSnapshot> snapshot, String detail) {
        static ReadResult ok(AgeSnapshot s) { return new ReadResult(Status.OK, Optional.of(s), "ok"); }
        static ReadResult missing() { return new ReadResult(Status.MISSING, Optional.empty(), "file missing"); }
        static ReadResult corrupt(String why) { return new ReadResult(Status.CORRUPT, Optional.empty(), why); }
    }

    private AgeMirror() {}

    /** Fail-strict rule of SPEC §3.2: a valid mirror is used as is, anything else yields the fallback. */
    public static AgeSnapshot snapshotOrFallback(ReadResult read, AgeSnapshot fallback) {
        return read.status() == Status.OK ? read.snapshot().orElse(fallback) : fallback;
    }

    public static String toJson(AgeSnapshot snapshot, Instant timestamp) {
        JsonObject o = new JsonObject();
        JsonArray arr = new JsonArray();
        snapshot.unlockedIds().forEach(arr::add);
        o.add("unlocked", arr);
        o.addProperty("version", snapshot.version());
        o.addProperty("lastReloadGameTime", snapshot.lastReloadGameTime());
        o.addProperty("timestamp", timestamp.toString());
        return GSON.toJson(o);
    }

    /** Parses mirror JSON. Unknown stage ids, wrong types or a missing {@code unlocked} array are CORRUPT. */
    public static ReadResult parse(String json) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            return ReadResult.corrupt("not valid JSON: " + e.getMessage());
        }
        if (root == null || !root.isJsonObject()) return ReadResult.corrupt("root is not a JSON object");
        JsonObject o = root.getAsJsonObject();
        JsonElement unlockedEl = o.get("unlocked");
        if (unlockedEl == null || !unlockedEl.isJsonArray()) return ReadResult.corrupt("'unlocked' is missing or not an array");
        EnumSet<AgeId> unlocked = EnumSet.noneOf(AgeId.class);
        for (JsonElement e : unlockedEl.getAsJsonArray()) {
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) return ReadResult.corrupt("'unlocked' contains a non-string entry");
            String id = e.getAsString();
            Optional<AgeId> age = AgeId.byId(id);
            if (age.isEmpty()) return ReadResult.corrupt("unknown stage id '" + id + "'");
            unlocked.add(age.get());
        }
        long version;
        long lastReload;
        try {
            version = optLong(o, "version");
            lastReload = optLong(o, "lastReloadGameTime");
        } catch (IllegalArgumentException ex) {
            return ReadResult.corrupt(ex.getMessage());
        }
        return ReadResult.ok(new AgeSnapshot(unlocked, version, lastReload));
    }

    private static long optLong(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null) return 0;
        if (e instanceof JsonPrimitive p && p.isNumber()) return p.getAsLong();
        throw new IllegalArgumentException("'" + key + "' is not a number");
    }

    public static ReadResult read(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return ReadResult.missing();
        } catch (IOException | RuntimeException e) {
            return ReadResult.corrupt("unreadable: " + e);
        }
        return parse(text);
    }

    /** Writes the mirror atomically: temp file in the same directory, then an atomic rename. */
    public static void write(Path file, AgeSnapshot snapshot, Instant timestamp) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, "ages", ".json.tmp");
        try {
            Files.writeString(tmp, toJson(snapshot, timestamp), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
