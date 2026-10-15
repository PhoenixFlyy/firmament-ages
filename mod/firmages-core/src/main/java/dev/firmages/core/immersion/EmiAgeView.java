package dev.firmages.core.immersion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import dev.firmages.core.age.AgeId;

/**
 * What the recipe viewer and Ponder may show at the player's Ages (SPEC §21). One immutable snapshot per EMI reload
 * (and per change of the Ages or tags for Ponder): the unlocked Ages, the namespaces of locked mods and the locked
 * items and fluids. Pure Java: items and fluids are opaque keys (registry objects on the client, strings in tests),
 * so every decision is unit-tested.
 *
 * <p>Rules:
 * <ul>
 *   <li>Unlocked Ages: the stage paths the client holds; Dawn is always unlocked, and an empty or unknown set counts
 *       as Dawn only (fail closed).</li>
 *   <li>A namespace is locked when {@code age_namespaces.json} (generated from {@code dev/age_map.toml [mods]}) gives
 *       it an Age that is not unlocked. Unknown namespaces ({@code c}, {@code emi}, {@code jei}) are never locked.</li>
 *   <li>An item or fluid is locked when its Age tag ({@code firmages:age_items|age_fluids/<age>}) is a locked Age or
 *       {@code disabled}; without an Age tag, its namespace decides.</li>
 *   <li>A recipe category is locked with its namespace. An EMI Loot recipe ({@code emi_loot:/<category>/<ns>/<path>})
 *       is locked with the namespace of its loot table, so a later mod's mob or chest shows nothing.</li>
 * </ul>
 */
public final class EmiAgeView {
    /** Pseudo-Age of never-unlocked items and fluids. */
    public static final String DISABLED = "disabled";
    /** EMI Loot's recipe namespace; its recipe ids carry the loot table's namespace in the path. */
    public static final String EMI_LOOT = "emi_loot";

    private final Set<AgeId> unlocked;
    private final Set<String> lockedNamespaces;
    private final Set<Object> lockedItems;
    private final Set<Object> lockedFluids;

    private EmiAgeView(Set<AgeId> unlocked, Set<String> lockedNamespaces, Set<Object> lockedItems, Set<Object> lockedFluids) {
        this.unlocked = unlocked;
        this.lockedNamespaces = lockedNamespaces;
        this.lockedItems = lockedItems;
        this.lockedFluids = lockedFluids;
    }

    /** Nothing locked (EMI or the tags are not ready); used before the first build. */
    public static final EmiAgeView OPEN = new EmiAgeView(Set.copyOf(AgeId.all()), Set.of(), Set.of(), Set.of());

    /**
     * @param stagePaths the player's stage paths ({@code age_1}, also with a namespace; other stages are ignored)
     * @param namespaces namespace to Age ({@link #parseNamespaces})
     * @param items      every item; {@code itemAge} gives its Age tag ({@code dawn}..{@code age_9}, {@code disabled}) or null
     * @param itemId     the item's id ({@code ns:path}), for the namespace fallback
     */
    public static <I, F> EmiAgeView build(Collection<String> stagePaths, Map<String, AgeId> namespaces,
                                          Iterable<I> items, Function<I, String> itemId, Function<I, String> itemAge,
                                          Iterable<F> fluids, Function<F, String> fluidId, Function<F, String> fluidAge) {
        Set<AgeId> open = unlocked(stagePaths);
        Set<String> lockedNs = new HashSet<>();
        namespaces.forEach((ns, age) -> {
            if (!open.contains(age)) lockedNs.add(ns);
        });
        return new EmiAgeView(Collections.unmodifiableSet(open), Set.copyOf(lockedNs),
            lockedSet(items, itemId, itemAge, open, lockedNs), lockedSet(fluids, fluidId, fluidAge, open, lockedNs));
    }

    /** The unlocked Ages of a stage set: always Dawn, plus every known Age stage. */
    public static Set<AgeId> unlocked(Collection<String> stagePaths) {
        Set<AgeId> out = EnumSet.of(AgeId.DAWN);
        if (stagePaths != null) for (String s : stagePaths) AgeId.byId(s).ifPresent(out::add);
        return out;
    }

    private static <T> Set<Object> lockedSet(Iterable<T> all, Function<T, String> id, Function<T, String> tagAge, Set<AgeId> open,
                                             Set<String> lockedNs) {
        Set<Object> out = Collections.newSetFromMap(new IdentityHashMap<>());
        for (T t : all) {
            if (locked(tagAge.apply(t), namespace(id.apply(t)), open, lockedNs)) out.add(t);
        }
        return Collections.unmodifiableSet(out);
    }

    /** One item or fluid: its Age tag decides, else its namespace. */
    static boolean locked(String tagAge, String namespace, Set<AgeId> open, Set<String> lockedNs) {
        if (tagAge != null) {
            if (DISABLED.equals(tagAge)) return true;
            Optional<AgeId> age = AgeId.byId(tagAge);
            if (age.isPresent()) return !open.contains(age.get());
        }
        return namespace != null && lockedNs.contains(namespace);
    }

    /** Parses {@code assets/firmages/age_namespaces.json}: {@code {"namespaces": {"create": "age_1", ...}}}. Unknown
     *  Ages are skipped. */
    public static Map<String, AgeId> parseNamespaces(JsonElement json) {
        Map<String, AgeId> out = new LinkedHashMap<>();
        if (json == null || !json.isJsonObject()) return out;
        JsonElement ns = json.getAsJsonObject().get("namespaces");
        if (ns == null || !ns.isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> e : ((JsonObject) ns).entrySet()) {
            if (!e.getValue().isJsonPrimitive()) continue;
            AgeId.byId(e.getValue().getAsString()).ifPresent(a -> out.put(e.getKey(), a));
        }
        return out;
    }

    public Set<AgeId> unlockedAges() {
        return unlocked;
    }

    /** The highest unlocked Age. */
    public AgeId age() {
        AgeId best = AgeId.DAWN;
        for (AgeId a : unlocked) if (a.ordinal() > best.ordinal()) best = a;
        return best;
    }

    public boolean namespaceLocked(String namespace) {
        return namespace != null && lockedNamespaces.contains(namespace);
    }

    /** The namespace of an id ({@code ns:path}) is locked. */
    public boolean idLocked(String id) {
        return namespaceLocked(namespace(id));
    }

    public boolean itemLocked(Object item) {
        return lockedItems.contains(item);
    }

    public boolean fluidLocked(Object fluid) {
        return lockedFluids.contains(fluid);
    }

    /** A recipe category ({@code ns:path}) of a locked mod. */
    public boolean categoryLocked(String categoryId) {
        return idLocked(categoryId);
    }

    /** A recipe is removed with its category, and an EMI Loot recipe with its loot table's namespace. */
    public boolean recipeLocked(String categoryId, String recipeId) {
        if (categoryLocked(categoryId)) return true;
        return EMI_LOOT.equals(namespace(categoryId)) && namespaceLocked(lootNamespace(recipeId));
    }

    /** {@code emi_loot:/mob_drops/draconicevolution/entities/draconic_guardian} gives {@code draconicevolution}; null for other ids. */
    static String lootNamespace(String recipeId) {
        if (recipeId == null || !recipeId.startsWith(EMI_LOOT + ":/")) return null;
        String[] parts = recipeId.substring(EMI_LOOT.length() + 2).split("/");
        return parts.length >= 2 ? parts[1] : null;
    }

    static String namespace(String id) {
        if (id == null) return null;
        int colon = id.indexOf(':');
        return colon < 0 ? "minecraft" : id.substring(0, colon);
    }

    public int lockedItemCount() {
        return lockedItems.size();
    }

    public int lockedFluidCount() {
        return lockedFluids.size();
    }

    public Set<String> lockedNamespaces() {
        return lockedNamespaces;
    }

    /** One log line: Ages and counts. */
    public String summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("age", age().id());
        m.put("locked items", lockedItems.size());
        m.put("locked fluids", lockedFluids.size());
        m.put("locked mods", lockedNamespaces.size());
        return m.toString();
    }

    /** Groups ids by namespace and counts them (audit output). */
    public static Map<String, Integer> countByNamespace(Iterable<String> ids) {
        Map<String, Integer> out = new HashMap<>();
        for (String id : ids) out.merge(namespace(id), 1, Integer::sum);
        return out;
    }
}
