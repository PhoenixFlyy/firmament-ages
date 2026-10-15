package dev.firmages.core.crown;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.firmages.core.FirmagesCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads {@code data/<ns>/firmages_crown/chords.json} (the top pack of each namespace wins) on every datapack load.
 * The library is published as "pending" at the end of the prepare phase, so the {@code firmages:starforge} recipe
 * codec of the same load resolves its chords (SPEC §27.7: an unknown chord rejects the recipe file).
 */
public final class ChordLibraryLoader extends SimplePreparableReloadListener<ChordLibrary> {
    public static final String FOLDER = "firmages_crown";
    private static final String FILE = "chords.json";

    @Override
    protected ChordLibrary prepare(ResourceManager rm, ProfilerFiller profiler) {
        Map<String, JsonElement> files = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : rm.listResources(FOLDER, p -> p.getPath().equals(FOLDER + "/" + FILE)).entrySet()) {
            try (Reader r = e.getValue().openAsReader()) {
                files.put(e.getKey().getNamespace() + ":chords", JsonParser.parseReader(r));
            } catch (Exception ex) {
                FirmagesCore.LOGGER.error("Chord library: cannot read {}: {}", e.getKey(), ex.toString());
            }
        }
        ChordLibrary lib = ChordLibrary.parse(files);
        ChordLibrary.setPending(lib);
        return lib;
    }

    @Override
    protected void apply(ChordLibrary lib, ResourceManager rm, ProfilerFiller profiler) {
        lib.errors().forEach(e -> FirmagesCore.LOGGER.error("Chord library rejected {}", e));
        ChordLibrary.setCurrent(lib);
        CrownService.onDataReloaded();
        FirmagesCore.LOGGER.info("Chord library: {} chords", lib.chords().size());
    }
}
