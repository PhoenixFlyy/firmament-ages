package dev.firmages.core.gate;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.firmages.core.age.AgeTagResolver;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagManager;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Item and fluid tag members of the datapack load in progress, for tag outputs. The tags are not bound yet when
 * {@code RecipeManager#apply} runs (on a reload the bound tags are still the previous load's), so they come from
 * the load's own {@link TagManager} result, which is complete (KubeJS tag edits included) because the tag manager
 * is the first reload listener and its apply phase runs before the recipe manager's [verified,
 * SimpleReloadInstance]. Without a linked tag manager the tag JSON is resolved directly (no KubeJS edits).
 */
final class TagView {
    private final Map<String, Collection<String>> items;
    private final Map<String, Collection<String>> fluids;
    private final Resolver itemJson;
    private final Resolver fluidJson;
    private final String source;

    private interface Resolver {
        Set<String> resolve(String tagId);
    }

    private TagView(Map<String, Collection<String>> items, Map<String, Collection<String>> fluids, Resolver itemJson, Resolver fluidJson, String source) {
        this.items = items;
        this.fluids = fluids;
        this.itemJson = itemJson;
        this.fluidJson = fluidJson;
        this.source = source;
    }

    String source() {
        return source;
    }

    static TagView of(TagManager tags, ResourceManager rm) {
        if (tags != null && !tags.getResult().isEmpty()) {
            return new TagView(members(tags, Registries.ITEM), members(tags, Registries.FLUID), null, null, "tag manager");
        }
        return new TagView(null, null, json(rm, Registries.ITEM), json(rm, Registries.FLUID), "tag JSON (no tag manager linked)");
    }

    Collection<String> itemTag(String id) {
        return lookup(items, itemJson, id);
    }

    Collection<String> fluidTag(String id) {
        return lookup(fluids, fluidJson, id);
    }

    private static Collection<String> lookup(Map<String, Collection<String>> loaded, Resolver json, String id) {
        if (loaded != null) {
            Collection<String> c = loaded.get(id);
            return c == null ? List.of() : c;
        }
        return json.resolve(id);
    }

    private static Map<String, Collection<String>> members(TagManager tags, ResourceKey<? extends Registry<?>> registry) {
        Map<String, Collection<String>> out = new HashMap<>();
        for (TagManager.LoadResult<?> r : tags.getResult()) {
            if (!r.key().equals(registry)) continue;
            r.tags().forEach((id, holders) -> {
                List<String> ids = new ArrayList<>(holders.size());
                for (Holder<?> h : holders) h.unwrapKey().ifPresent(k -> ids.add(k.location().toString()));
                out.put(id.toString(), ids);
            });
        }
        return out;
    }

    private static Resolver json(ResourceManager rm, ResourceKey<? extends Registry<?>> registry) {
        String dir = Registries.tagsDirPath(registry);
        AgeTagResolver resolver = new AgeTagResolver(tagId -> {
            ResourceLocation tag = ResourceLocation.tryParse(tagId);
            if (tag == null) return List.of();
            ResourceLocation file = ResourceLocation.fromNamespaceAndPath(tag.getNamespace(), dir + "/" + tag.getPath() + ".json");
            List<JsonElement> out = new ArrayList<>();
            for (Resource res : rm.getResourceStack(file)) {
                try (Reader reader = res.openAsReader()) {
                    out.add(JsonParser.parseReader(reader));
                } catch (Exception ignored) {
                    // unreadable layer: the tag resolves without it
                }
            }
            return out;
        });
        return resolver::resolve;
    }
}
