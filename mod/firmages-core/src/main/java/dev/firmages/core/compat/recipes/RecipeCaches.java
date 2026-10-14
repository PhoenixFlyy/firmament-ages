package dev.firmages.core.compat.recipes;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The recipe caches an Age swap flushes (SPEC §3.3). A datapack load refreshes them through its reload listeners, its
 * {@code TagsUpdatedEvent} and, for some mods, {@code ServerStartedEvent}; the swap cannot post the tags event,
 * because listeners treat it as the end of a load (NeoForge's {@code DataMapLoader.apply} consumes the load's data
 * maps and throws on a second call [verified, dev GameTest and source]). So every server-side cache of recipe state in
 * the pack is refreshed here directly. The list comes from a scan of all pack jars (0.9.0, 216 mods, jar-in-jar
 * included) for classes that reference {@code RecipeManager} together with a reload listener, {@code
 * AddReloadListenerEvent}, {@code TagsUpdatedEvent}, {@code ServerStartedEvent} or {@code OnDatapackSyncEvent}, and
 * for subclasses of {@code RecipeManager} [verified, javap against the pack's jars]:
 * <ul>
 *   <li>FastSuite 6.0.7: its {@code AuxRecipeManager} replaces the server's recipe manager and keeps one {@code
 *       CachedRecipeList} per recipe type with 100 or more recipes, built on first use and never cleared; the
 *       crafting table reads it. Its private {@code cachedRecipeListMap} is cleared (reflection).</li>
 *   <li>Mekanism 10.7.19: {@code MekanismRecipeType.clearCache()} (every machine's input cache) and
 *       {@code TagCache.resetTagCaches()} (its tag lookups, the miner blacklist included).</li>
 *   <li>Create 6.0.10: {@code RecipeFinder.LISTENER} and {@code RecipeTrieFinder.LISTENER}, the reload listeners of its
 *       cached recipe searches.</li>
 *   <li>TerraFirmaCraft 4.2.11: {@code Helpers.updateReloadableData} (what its {@code onTagsUpdated} runs:
 *       {@code IndirectHashCollection.reloadAllCaches}, fluid heat, non-decaying recipe outputs).</li>
 *   <li>Immersive Engineering 12.4.2: {@code CachedRecipeList.onTagsUpdated} (bumps the reload count of its recipe lists).</li>
 *   <li>Mystical Agriculture 8.0.28: {@code RecipeIngredientCache.INSTANCE.onTagsUpdated} (the valid inputs of its
 *       machines; reflection).</li>
 *   <li>Ars Nouveau 5.13.3: what its reload listener queues for the next tick ({@code EventHandler$1$1}):
 *       {@code GenericRecipeRegistry.reloadAll} (the Imbuement Chamber and every other registry, Ars Additions' too),
 *       {@code CasterTomeRegistry}, {@code BuddingConversionRegistry}, {@code AlakarkinosConversionRegistry},
 *       {@code ScryRitualRegistry} (reflection).</li>
 *   <li>Immersive Petroleum 4.5.0: {@code RecipeReloadListener.lists} (distillation tower, coker, high-pressure
 *       refinery, reservoirs; package-private, reflection).</li>
 *   <li>Applied Energistics 2 19.2.18: {@code TransformLogic.clearCache()} (the items that transform in water or
 *       explosions; private, reflection).</li>
 *   <li>FramedBlocks 10.6.3: {@code FramingSawRecipeCache.get(false).update} (the framing saw; reflection).</li>
 * </ul>
 * Not flushed, because the swap already refreshes them: megacells 4.11.0 ({@code CompressionService},
 * {@code LavaTransformLogic}), Immersive Engineering's arc recycling and Sophisticated Core listen to the
 * {@code OnDatapackSyncEvent} that {@link dev.firmages.core.age.AgeSwap} posts. Client-only listeners (JEI) and
 * listeners that only cache tags are out of scope.
 * <p>
 * Each mod is only touched inside its {@code isLoaded} branch, through a nested class, so the absent mod's classes are
 * never linked. A failure is logged and skips that mod only. {@link #stale} compares the caches with the live recipes
 * ({@code /firmages selftest server}, case {@code recipe_caches_fresh}).
 */
public final class RecipeCaches {
    private RecipeCaches() {}

    /** Flushes the caches of the loaded mods; returns the names of the ones flushed. */
    public static List<String> flush(MinecraftServer server, RecipeManager recipes) {
        List<String> done = new ArrayList<>();
        ModList mods = ModList.get();
        RegistryAccess access = server.registryAccess();
        // An event object handed to two listeners directly; it is never posted.
        TagsUpdatedEvent tagsEvent = new TagsUpdatedEvent(access, false, false);
        run(done, mods, "fastsuite", () -> FastSuite.flush(recipes));
        run(done, mods, "mekanism", () -> Mekanism.flush());
        run(done, mods, "create", () -> Create.flush(server));
        run(done, mods, "tfc", () -> Tfc.flush(access, recipes));
        run(done, mods, "immersiveengineering", () -> Ie.flush(tagsEvent));
        run(done, mods, "mysticalagriculture", () -> MysticalAgriculture.flush(tagsEvent));
        run(done, mods, "ars_nouveau", () -> ArsNouveau.flush(server, recipes));
        run(done, mods, "immersivepetroleum", () -> ImmersivePetroleum.flush(recipes));
        run(done, mods, "ae2", () -> Ae2.flush());
        run(done, mods, "framedblocks", () -> FramedBlocks.flush(recipes));
        return done;
    }

    /**
     * The caches that disagree with the live recipes, one line each (empty: all fresh). For the selftest. Each cache is
     * compared with what the mod's own rebuild makes from the live recipes, so the probe needs no copy of a mod's rules
     * (which recipes a registry keeps where, which ingredients count): FramedBlocks builds a second cache, Ars Nouveau,
     * Immersive Petroleum and AE2 are read, rebuilt by their own code and read again (a stale cache is then fresh),
     * FastSuite's lists hold every recipe of their type. A cache that was not built yet is built by the reading.
     */
    public static List<String> stale(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        RecipeManager rm = server.getRecipeManager();
        ModList mods = ModList.get();
        check(out, mods, "fastsuite", () -> FastSuite.stale(rm));
        check(out, mods, "ars_nouveau", () -> ArsNouveau.stale(rm));
        check(out, mods, "immersivepetroleum", () -> ImmersivePetroleum.stale(rm));
        check(out, mods, "ae2", () -> Ae2.stale(server.overworld()));
        check(out, mods, "framedblocks", () -> FramedBlocks.stale(rm));
        return out;
    }

    /** The mods {@link #stale} can check that are loaded. */
    public static List<String> checkable() {
        ModList mods = ModList.get();
        return List.of("fastsuite", "ars_nouveau", "immersivepetroleum", "ae2", "framedblocks").stream().filter(mods::isLoaded).toList();
    }

    private interface Flush {
        void run() throws ReflectiveOperationException;
    }

    private interface Check {
        List<String> run() throws ReflectiveOperationException;
    }

    private static void run(List<String> done, ModList mods, String modId, Flush flush) {
        if (!mods.isLoaded(modId)) return;
        try {
            flush.run();
            done.add(modId);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            FirmagesCore.LOGGER.error("Age swap: cannot flush the recipe caches of {}", modId, e);
        }
    }

    private static void check(List<String> out, ModList mods, String modId, Check check) {
        if (!mods.isLoaded(modId)) return;
        try {
            for (String s : check.run()) out.add(modId + ": " + s);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            out.add(modId + ": cannot read the cache (" + e + ")");
        }
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static Method method(Class<?> owner, String name, Class<?>... params) throws NoSuchMethodException {
        Method m = owner.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    private static Set<ResourceLocation> ids(Collection<?> holders) {
        Set<ResourceLocation> out = new HashSet<>();
        for (Object h : holders) out.add(((RecipeHolder<?>) h).id());
        return out;
    }

    private static String diff(String what, Set<ResourceLocation> cached, Set<ResourceLocation> live) {
        if (cached.equals(live)) return null;
        Set<ResourceLocation> extra = new HashSet<>(cached);
        extra.removeAll(live);
        Set<ResourceLocation> missing = new HashSet<>(live);
        missing.removeAll(cached);
        return what + ": cached " + cached.size() + ", live " + live.size() + " (" + extra.size() + " no longer live, e.g. "
            + extra.stream().limit(2).toList() + "; " + missing.size() + " missing, e.g. " + missing.stream().limit(2).toList() + ")";
    }

    private static final class FastSuite {
        static final String AUX = "dev.shadowsoffire.fastsuite.AuxRecipeManager";

        /** The manager's {@code cachedRecipeListMap}, or null when the live manager is not FastSuite's. */
        @SuppressWarnings("unchecked")
        static Map<RecipeType<?>, Object> map(RecipeManager rm) throws ReflectiveOperationException {
            for (Class<?> c = rm.getClass(); c != null && c != RecipeManager.class; c = c.getSuperclass()) {
                if (c.getName().equals(AUX)) return (Map<RecipeType<?>, Object>) field(c, "cachedRecipeListMap").get(rm);
            }
            return null;
        }

        static void flush(RecipeManager rm) throws ReflectiveOperationException {
            Map<RecipeType<?>, Object> map = map(rm);
            if (map == null) return;
            // FastSuite reads and fills the map under its own monitor (AuxRecipeManager.getCachedRecipeList).
            synchronized (map) {
                map.clear();
            }
        }

        static List<String> stale(RecipeManager rm) throws ReflectiveOperationException {
            Map<RecipeType<?>, Object> map = map(rm);
            if (map == null) return List.of("the live recipe manager is " + rm.getClass().getName() + ", not FastSuite's");
            Map<RecipeType<?>, Object> copy;
            synchronized (map) {
                copy = new LinkedHashMap<>(map);
            }
            List<String> out = new ArrayList<>();
            for (Map.Entry<RecipeType<?>, Object> e : copy.entrySet()) {
                Object list = e.getValue();
                Class<?> c = list.getClass();
                Set<ResourceLocation> cached = ids((Collection<?>) field(c, "serialRecipes").get(list));
                cached.addAll(ids((Collection<?>) field(c, "parallelRecipes").get(list)));
                @SuppressWarnings({"unchecked", "rawtypes"})
                Set<ResourceLocation> live = ids(rm.getAllRecipesFor((RecipeType) e.getKey()));
                String d = diff(String.valueOf(e.getKey()), cached, live);
                if (d != null) out.add(d);
            }
            return out;
        }
    }

    private static final class Mekanism {
        static void flush() {
            mekanism.common.recipe.MekanismRecipeType.clearCache();
            mekanism.common.base.TagCache.resetTagCaches();
        }
    }

    private static final class Create {
        static void flush(MinecraftServer server) {
            com.simibubi.create.foundation.recipe.RecipeFinder.LISTENER.onResourceManagerReload(server.getResourceManager());
            com.simibubi.create.foundation.recipe.trie.RecipeTrieFinder.LISTENER.onResourceManagerReload(server.getResourceManager());
        }
    }

    private static final class Tfc {
        static void flush(RegistryAccess access, RecipeManager recipes) {
            net.dries007.tfc.util.Helpers.updateReloadableData(access, recipes);
        }
    }

    private static final class Ie {
        static void flush(TagsUpdatedEvent event) {
            blusunrize.immersiveengineering.api.crafting.cache.CachedRecipeList.onTagsUpdated(event);
        }
    }

    private static final class MysticalAgriculture {
        static void flush(TagsUpdatedEvent event) throws ReflectiveOperationException {
            Class<?> cls = Class.forName("com.blakebr0.mysticalagriculture.util.RecipeIngredientCache");
            Object cache = cls.getField("INSTANCE").get(null);
            cls.getMethod("onTagsUpdated", TagsUpdatedEvent.class).invoke(cache, event);
        }
    }

    private static final class ArsNouveau {
        static final String PKG = "com.hollingsworth.arsnouveau.api.registry.";

        static void flush(MinecraftServer server, RecipeManager rm) throws ReflectiveOperationException {
            // EventHandler$1$1.tick, without its PacketInitDocs (the clients rebuild their docs from the recipe packet).
            Class.forName(PKG + "GenericRecipeRegistry").getMethod("reloadAll", RecipeManager.class).invoke(null, rm);
            Class.forName(PKG + "CasterTomeRegistry").getMethod("reloadTomeData", RecipeManager.class, RegistryAccess.class)
                .invoke(null, rm, server.registryAccess());
            Class.forName(PKG + "BuddingConversionRegistry").getMethod("reloadBuddingConversionRecipes", RecipeManager.class).invoke(null, rm);
            Class.forName(PKG + "AlakarkinosConversionRegistry").getMethod("reloadAlakarkinosRecipes", RecipeManager.class, MinecraftServer.class)
                .invoke(null, rm, server);
            Class.forName(PKG + "ScryRitualRegistry").getMethod("reloadScryRitualRecipes", RecipeManager.class).invoke(null, rm);
        }

        /**
         * Each registry before and after its own {@code reload}. A registry may keep recipes outside {@code RECIPES}
         * (Ars Additions' {@code SourceSpawnerRegistry} keeps the ones without an entity in {@code DEFAULTS}), so every
         * public list of recipe holders of the registry is read.
         */
        static List<String> stale(RecipeManager rm) throws ReflectiveOperationException {
            Class<?> cls = Class.forName(PKG + "GenericRecipeRegistry");
            List<String> out = new ArrayList<>();
            for (Object reg : (List<?>) cls.getField("REGISTRIES").get(null)) {
                Object type = cls.getMethod("getType").invoke(reg);
                Set<ResourceLocation> before = held(reg);
                cls.getMethod("reload", RecipeManager.class).invoke(reg, rm);
                String d = diff(String.valueOf(type), before, held(reg));
                if (d != null) out.add(d);
            }
            return out;
        }

        private static Set<ResourceLocation> held(Object reg) throws IllegalAccessException {
            Set<ResourceLocation> out = new HashSet<>();
            for (Field f : reg.getClass().getFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !Collection.class.isAssignableFrom(f.getType())) continue;
                for (Object o : (Collection<?>) f.get(reg)) if (o instanceof RecipeHolder<?> h) out.add(h.id());
            }
            return out;
        }
    }

    private static final class ImmersivePetroleum {
        static final String PKG = "flaxbeard.immersivepetroleum.";
        /** The static maps {@code RecipeReloadListener.lists} fills, by recipe class. */
        static final String[][] MAPS = {
            {"api.crafting.DistillationTowerRecipe", "recipes"},
            {"api.crafting.CokerUnitRecipe", "recipes"},
            {"api.crafting.HighPressureRefineryRecipe", "recipes"},
            {"api.reservoir.ReservoirType", "map"},
        };

        static void flush(RecipeManager rm) throws ReflectiveOperationException {
            method(Class.forName(PKG + "common.crafting.RecipeReloadListener"), "lists", RecipeManager.class).invoke(null, rm);
        }

        /** The keys of each map before and after {@code RecipeReloadListener.lists}, the mod's own rebuild. */
        static List<String> stale(RecipeManager rm) throws ReflectiveOperationException {
            List<Set<ResourceLocation>> before = keys();
            flush(rm);
            List<Set<ResourceLocation>> after = keys();
            List<String> out = new ArrayList<>();
            for (int i = 0; i < MAPS.length; i++) {
                String d = diff(MAPS[i][0].substring(MAPS[i][0].lastIndexOf('.') + 1), before.get(i), after.get(i));
                if (d != null) out.add(d);
            }
            return out;
        }

        private static List<Set<ResourceLocation>> keys() throws ReflectiveOperationException {
            List<Set<ResourceLocation>> out = new ArrayList<>();
            for (String[] m : MAPS) {
                Set<ResourceLocation> have = new HashSet<>();
                for (Object k : ((Map<?, ?>) field(Class.forName(PKG + m[0]), m[1]).get(null)).keySet()) have.add((ResourceLocation) k);
                out.add(have);
            }
            return out;
        }
    }

    private static final class Ae2 {
        static final String LOGIC = "appeng.recipes.transform.TransformLogic";

        static void flush() throws ReflectiveOperationException {
            method(Class.forName(LOGIC), "clearCache").invoke(null);
        }

        /** The three item sets AE2 transforms (in water, in any fluid, in explosions) before and after {@code clearCache}. */
        static List<String> stale(ServerLevel level) throws ReflectiveOperationException {
            List<Set<?>> before = sets(level);
            flush();
            List<Set<?>> after = sets(level);
            String[] what = {"items that transform in water", "items that transform in any fluid", "items that transform in explosions"};
            List<String> out = new ArrayList<>();
            for (int i = 0; i < what.length; i++) {
                if (!before.get(i).equals(after.get(i))) out.add(what[i] + ": cached " + before.get(i).size() + ", rebuilt " + after.get(i).size());
            }
            return out;
        }

        private static List<Set<?>> sets(ServerLevel level) throws ReflectiveOperationException {
            Class<?> logic = Class.forName(LOGIC);
            Class<?> lvl = net.minecraft.world.level.Level.class;
            return List.of(
                Set.copyOf((Set<?>) method(logic, "getTransformableItems", lvl, net.minecraft.world.level.material.Fluid.class).invoke(null, level, Fluids.WATER)),
                Set.copyOf((Set<?>) method(logic, "getTransformableItemsAnyFluid", lvl).invoke(null, level)),
                Set.copyOf((Set<?>) method(logic, "getTransformableItemsExplosion", lvl).invoke(null, level)));
        }
    }

    private static final class FramedBlocks {
        static final String CACHE = "xfacthd.framedblocks.common.crafting.FramingSawRecipeCache";

        static void flush(RecipeManager rm) throws ReflectiveOperationException {
            Class<?> cls = Class.forName(CACHE);
            Object server = cls.getMethod("get", boolean.class).invoke(null, false);
            cls.getMethod("update", RecipeManager.class).invoke(server, rm);
        }

        static List<String> stale(RecipeManager rm) throws ReflectiveOperationException {
            Class<?> cls = Class.forName(CACHE);
            Object server = cls.getMethod("get", boolean.class).invoke(null, false);
            Object fresh = cls.getConstructor().newInstance();
            cls.getMethod("update", RecipeManager.class).invoke(fresh, rm);
            Method recipes = cls.getMethod("getRecipes");
            String d = diff("framing saw", ids((Collection<?>) recipes.invoke(server)), ids((Collection<?>) recipes.invoke(fresh)));
            return d == null ? List.of() : List.of(d);
        }
    }
}
