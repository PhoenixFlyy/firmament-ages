package dev.firmages.core.crown;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §27.13 U: the shipped chord library and the rejection of bad files. */
class ChordLibraryTest {
    static final Path CHORDS = Path.of("src/main/resources/data/firmages/firmages_crown/chords.json");

    static ChordLibrary shipped() throws IOException {
        return ChordLibrary.parse(Map.of("firmages:chords", JsonParser.parseString(Files.readString(CHORDS))));
    }

    private static ChordLibrary of(String json) {
        return ChordLibrary.parse(Map.of("test:chords", JsonParser.parseString(json)));
    }

    @Test
    void shippedLibraryParses() throws IOException {
        ChordLibrary lib = shipped();
        assertTrue(lib.errors().isEmpty(), lib.errors().toString());
        assertEquals(14, lib.chords().size());
        Set<Integer> signals = new HashSet<>();
        Set<Integer> masks = new HashSet<>();
        for (Chord c : lib.chords().values()) {
            assertTrue(c.signal() >= 1 && c.signal() <= 15, c.id() + " signal");
            assertTrue(signals.add(c.signal()), c.id() + " shares its signal");
            assertTrue(c.mask() != 0 && (c.mask() & ~Chord.ALL) == 0, c.id() + " lenses in 0..8, not empty");
            assertTrue(masks.add(c.mask()), c.id() + " repeats a lens set");
        }
        assertTrue(!signals.contains(15), "signal 15 stays free");
        assertEquals(java.util.List.of(6), lib.get("firmages:anchor_chord").orElseThrow().lenses());
        assertEquals(Chord.ALL, lib.get("firmages:full_crown").orElseThrow().mask());
        assertEquals("dawn_triad", lib.get("firmages:dawn_triad").orElseThrow().name());
    }

    @Test
    void badFilesAreRejected() {
        assertTrue(of("{\"a\": {\"lenses\": [9], \"signal\": 1}}").chords().isEmpty(), "lens 9");
        assertTrue(of("{\"a\": {\"lenses\": [], \"signal\": 1}}").chords().isEmpty(), "empty chord");
        assertTrue(of("{\"a\": {\"lenses\": [1], \"signal\": 16}}").chords().isEmpty(), "signal 16");
        assertTrue(of("{\"a\": {\"lenses\": [1], \"signal\": 0}}").chords().isEmpty(), "signal 0");
        assertTrue(of("{\"A b\": {\"lenses\": [1], \"signal\": 1}}").chords().isEmpty(), "bad name");
        assertTrue(of("{\"a\": {\"lenses\": [1, 1], \"signal\": 1}}").chords().isEmpty(), "a lens twice");
        assertTrue(of("{\"a\": {\"lenses\": [1], \"signal\": 2}, \"b\": {\"lenses\": [2], \"signal\": 2}}").chords().isEmpty(), "shared signal");
        assertTrue(of("{\"a\": {\"lenses\": [1, 2], \"signal\": 1}, \"b\": {\"lenses\": [2, 1], \"signal\": 2}}").chords().isEmpty(), "same lenses");
        StringBuilder many = new StringBuilder("{");
        for (int i = 0; i < 16; i++) {
            many.append(i == 0 ? "" : ",").append("\"c").append(i).append("\": {\"lenses\": [").append(i % 9)
                .append(i >= 9 ? ", " + ((i + 1) % 9) : "").append("], \"signal\": ").append(i % 15 + 1).append("}");
        }
        ChordLibrary tooMany = of(many.append("}").toString());
        assertTrue(tooMany.chords().isEmpty() && !tooMany.errors().isEmpty(), "more than 15 chords");
        assertTrue(of("[1, 2]").chords().isEmpty(), "not an object");
    }

    @Test
    void aConflictingSecondFileIsRejectedAlone() throws IOException {
        Map<String, JsonElement> files = new TreeMap<>();
        files.put("firmages:chords", JsonParser.parseString(Files.readString(CHORDS)));
        files.put("zzz:chords", JsonParser.parseString("{\"echo\": {\"lenses\": [8], \"signal\": 1}}"));
        ChordLibrary lib = ChordLibrary.parse(files);
        assertEquals(14, lib.chords().size());
        assertEquals(1, lib.errors().size());
        files.put("zzz:chords", JsonParser.parseString("{\"echo\": {\"lenses\": [8], \"signal\": 15}}"));
        assertEquals(15, ChordLibrary.parse(files).chords().size(), "a fifteenth chord on signal 15 fits");
    }
}
