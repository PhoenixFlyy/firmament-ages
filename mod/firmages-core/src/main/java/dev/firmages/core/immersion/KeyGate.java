package dev.firmages.core.immersion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.firmages.core.age.AgeId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Keys and screens by mod Age (SPEC §18.10, Felix 2026-10-17, round G2: "C opens the Psi menu although Psi is locked;
 * every mod shortcut must be gated"). Before a mod's Age a key mapping it registers does nothing and a screen of its
 * packages does not open; players in creative mode have them in every Age, operators in survival do not
 * ({@link ViewerGate#open}). The world's Age is the one of {@code firmages:age_status} (Dawn before it arrives).
 * Pure Java (unit-tested); the client half is {@code client/immersion/KeyGateClient}.
 *
 * <p>The table is the pack file {@code assets/firmages/key_gates.json} (shipped through {@code kubejs/assets}, so it
 * changes without a core build), written by {@code dev/gen_key_gates.py} from the client jars:
 * {@code {"mods": {"psi": "age_6"}, "keys": {"psimisc.keybind": "psi"}, "packages": {"vazkii.psi": "psi"}}}. It lists only
 * mods that can be locked (an Age after Dawn, not exempt); the exempt mods (own gates such as Jade, EMI, JourneyMap and
 * Curios, the Chronicle, client tools) and their reasons are in the script.
 */
public final class KeyGate {
    /** The parsed pack file. A key or package whose mod has no valid Age is dropped. */
    public record Table(Map<String, AgeId> mods, Map<String, String> keys, Map<String, String> packages) {
        public static final Table EMPTY = new Table(Map.of(), Map.of(), Map.of());

        public Table {
            mods = Map.copyOf(mods);
            keys = Map.copyOf(keys);
            packages = Map.copyOf(packages);
        }

        /** The mod that registers a key mapping (by its name), if it can be locked. */
        public Optional<String> modOfKey(String keyName) {
            return keyName == null ? Optional.empty() : Optional.ofNullable(keys.get(keyName));
        }

        /** The mod a class belongs to: the longest listed package its name starts with (whole package parts only). */
        public Optional<String> modOfClass(String className) {
            if (className == null) return Optional.empty();
            int end = className.lastIndexOf('.');
            while (end > 0) {
                String mod = packages.get(className.substring(0, end));
                if (mod != null) return Optional.of(mod);
                end = className.lastIndexOf('.', end - 1);
            }
            return Optional.empty();
        }

        public Optional<AgeId> ageOf(String mod) {
            return mod == null ? Optional.empty() : Optional.ofNullable(mods.get(mod));
        }
    }

    private KeyGate() {}

    /**
     * @param modAge   the mod's Age (empty: never locked)
     * @param world    the world's Age
     * @param creative the player is in creative mode
     * @return the mod's keys and screens are locked for this player
     */
    public static boolean locked(Optional<AgeId> modAge, AgeId world, boolean creative) {
        return modAge.isPresent() && !ViewerGate.open(AgeStatus.reached(world, modAge.get()), creative);
    }

    public static boolean keyLocked(Table table, String keyName, AgeId world, boolean creative) {
        return locked(table.modOfKey(keyName).flatMap(table::ageOf), world, creative);
    }

    public static boolean screenLocked(Table table, String screenClass, AgeId world, boolean creative) {
        return locked(table.modOfClass(screenClass).flatMap(table::ageOf), world, creative);
    }

    /** Parses {@code key_gates.json}; unknown Ages, keys and packages of unlisted mods are skipped. */
    public static Table parse(JsonElement json) {
        if (json == null || !json.isJsonObject()) return Table.EMPTY;
        JsonObject root = json.getAsJsonObject();
        Map<String, AgeId> mods = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : strings(root, "mods").entrySet()) {
            AgeId.byId(e.getValue()).ifPresent(a -> mods.put(e.getKey(), a));
        }
        Map<String, String> keys = new LinkedHashMap<>();
        strings(root, "keys").forEach((k, mod) -> { if (mods.containsKey(mod)) keys.put(k, mod); });
        Map<String, String> packages = new LinkedHashMap<>();
        strings(root, "packages").forEach((p, mod) -> { if (mods.containsKey(mod) && !p.isBlank()) packages.put(p, mod); });
        return new Table(mods, keys, packages);
    }

    private static Map<String, String> strings(JsonObject root, String member) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonElement e = root.get(member);
        if (e == null || !e.isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> m : e.getAsJsonObject().entrySet()) {
            if (m.getValue().isJsonPrimitive()) out.put(m.getKey(), m.getValue().getAsString());
        }
        return out;
    }
}
