package dev.firmages.core.compat.recipes;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The recipe caches an Age swap flushes (SPEC §3.3). A datapack load refreshes them through its reload listeners and
 * its {@code TagsUpdatedEvent}; the swap cannot post that event, because listeners treat it as the end of a load
 * (NeoForge's {@code DataMapLoader.apply} consumes the load's data maps and throws on a second call [verified, dev
 * GameTest and source]). So every server-side listener in the pack that caches recipes is called here directly
 * [verified, javap against the pack's jars; the pack's TagsUpdatedEvent listeners were scanned, the others cache tags,
 * which the swap does not change apart from the three Age ore tags]:
 * <ul>
 *   <li>Mekanism 10.7.19: {@code MekanismRecipeType.clearCache()} (every machine's input cache) and
 *       {@code TagCache.resetTagCaches()} (its tag lookups, the miner blacklist included).</li>
 *   <li>Create 6.0.10: {@code RecipeFinder.LISTENER} and {@code RecipeTrieFinder.LISTENER}, the reload listeners of its
 *       cached recipe searches.</li>
 *   <li>TerraFirmaCraft 4.2.11: {@code Helpers.updateReloadableData} (what its {@code onTagsUpdated} runs:
 *       {@code IndirectHashCollection.reloadAllCaches}, fluid heat, non-decaying recipe outputs).</li>
 *   <li>Immersive Engineering 12.4.2: {@code CachedRecipeList.onTagsUpdated} (bumps the reload count of its recipe lists).</li>
 *   <li>Mystical Agriculture 8.0.28: {@code RecipeIngredientCache.INSTANCE.onTagsUpdated} (the valid inputs of its
 *       machines; no compile dependency, so by reflection).</li>
 * </ul>
 * Each mod is only touched inside its {@code isLoaded} branch, through a nested class, so the absent mod's classes are
 * never linked. A failure is logged and skips that mod only.
 */
public final class RecipeCaches {
    private RecipeCaches() {}

    /** Flushes the caches of the loaded mods; returns the names of the ones flushed. */
    public static List<String> flush(RegistryAccess access, RecipeManager recipes, ResourceManager resources) {
        List<String> done = new ArrayList<>();
        ModList mods = ModList.get();
        // An event object handed to two listeners directly; it is never posted.
        TagsUpdatedEvent tagsEvent = new TagsUpdatedEvent(access, false, false);
        run(done, mods, "mekanism", () -> Mekanism.flush());
        run(done, mods, "create", () -> Create.flush(resources));
        run(done, mods, "tfc", () -> Tfc.flush(access, recipes));
        run(done, mods, "immersiveengineering", () -> Ie.flush(tagsEvent));
        run(done, mods, "mysticalagriculture", () -> MysticalAgriculture.flush(tagsEvent));
        return done;
    }

    private interface Flush {
        void run() throws ReflectiveOperationException;
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

    private static final class Mekanism {
        static void flush() {
            mekanism.common.recipe.MekanismRecipeType.clearCache();
            mekanism.common.base.TagCache.resetTagCaches();
        }
    }

    private static final class Create {
        static void flush(ResourceManager resources) {
            com.simibubi.create.foundation.recipe.RecipeFinder.LISTENER.onResourceManagerReload(resources);
            com.simibubi.create.foundation.recipe.trie.RecipeTrieFinder.LISTENER.onResourceManagerReload(resources);
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
}
