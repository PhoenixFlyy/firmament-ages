package dev.firmages.core.sky;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Level U (SPEC §25.11, TFC jar data): no Starfall block is in {@code #c:ores} or {@code #c:stones}, and none is in
 * TFC's collapse and landslide tags ({@code tfc:can_collapse}, {@code can_landslide}, {@code can_trigger_collapse},
 * {@code can_start_collapse}), resolved through the TFC jar's tag JSON in {@code libs/} and this mod's own tags.
 */
class NoCollapseTagsTest {
    private static final Path MOD_DATA = Path.of("src/main/resources/data");
    private static final List<String> STARFALL = List.of("firmages:sky_rock", "firmages:meteorite", "firmages:starsteel_meteorite",
        "firmages:starfall_core", "firmages:meteoric_iron_block", "firmages:starsteel_block");

    private static Path tfcJar() throws IOException {
        try (Stream<Path> s = Files.list(Path.of("libs"))) {
            return s.filter(p -> p.getFileName().toString().startsWith("TerraFirmaCraft-")).findFirst()
                .orElseThrow(() -> new IllegalStateException("libs/TerraFirmaCraft-*.jar missing (gradlew fetchLibs)"));
        }
    }

    /** Every value of a block tag, from the TFC jar and this mod's data (tag references expanded). */
    private static Set<String> resolve(ZipFile tfc, String tag, Set<String> seen) throws IOException {
        Set<String> out = new HashSet<>();
        if (!seen.add(tag)) return out;
        String ns = tag.substring(0, tag.indexOf(':'));
        String path = tag.substring(tag.indexOf(':') + 1);
        List<JsonObject> files = new ArrayList<>();
        ZipEntry e = tfc.getEntry("data/" + ns + "/tags/block/" + path + ".json");
        if (e != null) {
            try (var r = new InputStreamReader(tfc.getInputStream(e), StandardCharsets.UTF_8)) {
                files.add(JsonParser.parseReader(r).getAsJsonObject());
            }
        }
        Path mod = MOD_DATA.resolve(ns).resolve("tags/block").resolve(path + ".json");
        if (Files.exists(mod)) files.add(JsonParser.parseString(Files.readString(mod)).getAsJsonObject());
        for (JsonObject f : files) {
            for (JsonElement v : f.getAsJsonArray("values")) {
                String id = v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString();
                if (id.startsWith("#")) out.addAll(resolve(tfc, id.substring(1), seen));
                else out.add(id);
            }
        }
        return out;
    }

    @Test
    void starfallBlocksStartNoCollapse() throws IOException {
        try (ZipFile tfc = new ZipFile(tfcJar().toFile())) {
            Set<String> collapse = resolve(tfc, "tfc:can_collapse", new HashSet<>());
            assertTrue(collapse.stream().anyMatch(b -> b.startsWith("tfc:rock/raw/")), "TFC raw rock collapses (the tags were read)");
            Set<String> landslide = resolve(tfc, "tfc:can_landslide", new HashSet<>());
            assertTrue(landslide.contains("tfc:rock/cobble/granite"), "TFC cobble slides [verified, TFC 4.2.11 tags]");
            List<String> bad = new ArrayList<>();
            for (String t : List.of("c:ores", "c:stones", "tfc:can_collapse", "tfc:can_landslide", "tfc:can_trigger_collapse", "tfc:can_start_collapse")) {
                Set<String> values = resolve(tfc, t, new HashSet<>());
                for (String b : STARFALL) if (values.contains(b)) bad.add(b + " in #" + t);
            }
            assertEquals(List.of(), bad);
        }
    }
}
