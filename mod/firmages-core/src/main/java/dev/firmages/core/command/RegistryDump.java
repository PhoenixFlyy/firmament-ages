package dev.firmages.core.command;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * {@code /firmages dump registry} (SPEC §10): item, block and fluid ids grouped by mod, for
 * {@code dev/gen_stage_locks.py} to expand {@code mod:} entries. Output {@code logs/firmages-registry.json}:
 * <pre>{ "generated": "...", "items": { "create": ["create:andesite_alloy", ...] }, "blocks": {...}, "fluids": {...} }</pre>
 */
public final class RegistryDump {
    public static final String FILE = "firmages-registry.json";

    private RegistryDump() {}

    public record Result(Path file, int items, int blocks, int fluids, int mods) {}

    public static Result write() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("generated", Instant.now().toString());
        Map<String, TreeSet<String>> items = byMod(BuiltInRegistries.ITEM);
        Map<String, TreeSet<String>> blocks = byMod(BuiltInRegistries.BLOCK);
        Map<String, TreeSet<String>> fluids = byMod(BuiltInRegistries.FLUID);
        root.add("items", toJson(items));
        root.add("blocks", toJson(blocks));
        root.add("fluids", toJson(fluids));
        Path file = FMLPaths.GAMEDIR.get().resolve("logs").resolve(FILE);
        Files.createDirectories(file.getParent());
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root), StandardCharsets.UTF_8);
        TreeSet<String> mods = new TreeSet<>(items.keySet());
        mods.addAll(blocks.keySet());
        mods.addAll(fluids.keySet());
        return new Result(file, count(items), count(blocks), count(fluids), mods.size());
    }

    private static Map<String, TreeSet<String>> byMod(Registry<?> registry) {
        Map<String, TreeSet<String>> out = new TreeMap<>();
        for (ResourceLocation id : registry.keySet()) {
            out.computeIfAbsent(id.getNamespace(), k -> new TreeSet<>()).add(id.toString());
        }
        return out;
    }

    private static JsonObject toJson(Map<String, TreeSet<String>> byMod) {
        JsonObject o = new JsonObject();
        byMod.forEach((mod, ids) -> {
            JsonArray arr = new JsonArray();
            ids.forEach(arr::add);
            o.add(mod, arr);
        });
        return o;
    }

    private static int count(Map<String, ? extends java.util.Collection<String>> m) {
        return m.values().stream().mapToInt(java.util.Collection::size).sum();
    }
}
