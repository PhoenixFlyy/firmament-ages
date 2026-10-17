package dev.firmages.core.immersion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §18.11: the F3 veil (Felix 2026-10-17, round G2). */
class F3VeilTest {
    @Test
    void lineKeepsItsLabel() {
        assertEquals("Biome: ???", F3Veil.veilLine("Biome: tfc:plains"));
        assertEquals("XYZ: ???", F3Veil.veilLine("XYZ: 12.300 / 64.00000 / -3.700"));
        assertEquals("Local Difficulty: ???", F3Veil.veilLine("Local Difficulty: 2.25 // 0.00 (Day 5)"));
        assertEquals("§nTargeted Block: ???", F3Veil.veilLine("§nTargeted Block: 12, 64, -3"));
        assertEquals("???", F3Veil.veilLine("minecraft:overworld FC: 0"), "an id before the colon is no label");
        assertEquals("Chunks[C] W: ???", F3Veil.veilLine("Chunks[C] W: 961 E: 76,48,35,6,0,0,0"));
        assertEquals("???", F3Veil.veilLine("#minecraft:logs"));
        assertEquals("", F3Veil.veilLine(""));
    }

    @Test
    void runsOfUnlabelledLinesCollapse() {
        assertEquals(List.of("§nTargeted Block: ???", "???", "axis: ???", "???", "", "Biome: ???"),
            F3Veil.veil(List.of("§nTargeted Block: 1, 2, 3", "minecraft:oak_log", "axis: y", "#minecraft:logs", "#minecraft:logs_that_burn",
                "#c:logs", "", "Biome: tfc:plains")));
    }

    @Test
    void vanillaLeftKeepsVersionFpsServerAndHelp() {
        List<String> left = List.of("Minecraft 1.21.1 (1.21.1/neoforge)", "117 fps T: 120 vsync fancy fancy-clouds B: 2 GPU: 34%",
            "Integrated server @ 4.1/50.0 ms, 12 tx, 340 rx", "C: 523/15000 (s) D: 12, pC: 000, pU: 00, aB: 12", "E: 12/80, SD: 12",
            "P: 0. T: 80", "Chunks[C] W: 961 E: 76,48,35,6,0,0,0", "minecraft:overworld FC: 0", "", "XYZ: 1.000 / 64.00000 / 2.000",
            "Block: 1 64 2 [1 0 2]", "Biome: tfc:plains", "Avg. Temp: 12.3", "", "Debug charts: [F3+1] Profiler hidden", "For help: press F3 + Q");
        assertEquals(List.of("Minecraft 1.21.1 (1.21.1/neoforge)", "117 fps T: 120 vsync fancy fancy-clouds B: 2 GPU: 34%",
            "Integrated server @ 4.1/50.0 ms, 12 tx, 340 rx", "C: ???", "E: ???", "P: ???", "Chunks[C] W: ???", "???", "", "XYZ: ???", "Block: ???", "Biome: ???",
            "Avg. Temp: ???", "", "Debug charts: [F3+1] Profiler hidden", "For help: press F3 + Q"), F3Veil.veilLeft(left));
        assertTrue(F3Veil.keptLeft("\"neoforge\" server, 12 tx, 340 rx"), "a remote server's line");
        assertTrue(F3Veil.keptLeft("\"neoforge\" server (frozen), 12 tx, 340 rx"));
        assertFalse(F3Veil.keptLeft("Server Light: (12 sky, 0 block)"));
    }

    @Test
    void vanillaRightVeilsOnlyTheTargetSections() {
        List<String> right = List.of("Java: 21.0.5 64bit", "Mem: 40% 1600/4096MB", "Allocation rate: 120MB /s", "Allocated: 60% 2400MB", "",
            "CPU: 16x AMD Ryzen 7", "", "Display: 2560x1440 (NVIDIA)", "NVIDIA GeForce RTX 3070", "4.6.0 NVIDIA 560.94", "",
            "§nTargeted Block: 1, 64, 2", "minecraft:stone", "#minecraft:base_stone_overworld", "", "§nTargeted Entity", "minecraft:cow", "",
            "Sodium Renderer (0.8.13)");
        assertEquals(List.of("Java: 21.0.5 64bit", "Mem: 40% 1600/4096MB", "Allocation rate: 120MB /s", "Allocated: 60% 2400MB", "",
            "CPU: 16x AMD Ryzen 7", "", "Display: 2560x1440 (NVIDIA)", "NVIDIA GeForce RTX 3070", "4.6.0 NVIDIA 560.94", "",
            "§nTargeted Block: ???", "???", "", "???", "", "Sodium Renderer (0.8.13)"), F3Veil.veilRight(right));
    }

    @Test
    void betterF3ModulesBySystemData() {
        for (String kept : List.of("MinecraftModule", "FpsModule", "SystemModule", "GraphicsModule", "ServerModule", "HelpModule",
            "MiscRightModule", "EmptyModule")) assertTrue(F3Veil.keepsModule(kept), kept);
        for (String veiled : List.of("CoordsModule", "LocationModule", "TargetModule", "EntityModule", "ChunksModule", "SoundModule",
            "MiscLeftModule", "SomeNewModule")) assertFalse(F3Veil.keepsModule(veiled), veiled);
    }
}
