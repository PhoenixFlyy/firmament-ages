package dev.firmages.core.perf;

import dev.firmages.core.perf.RegistryScanScope.ModEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §20: which mod files TFCRegistryAPI still scans. Mod ids and dependencies as in the pack's jars (0.8.0). */
class RegistryScanScopeTest {
    private static final ModEntry MINECRAFT = new ModEntry("minecraft.jar", "minecraft", List.of());
    private static final ModEntry NEOFORGE = new ModEntry("neoforge.jar", "neoforge", List.of("minecraft"));
    private static final ModEntry TFC = new ModEntry("TerraFirmaCraft.jar", "tfc", List.of("patchouli", "jei", "neoforge", "minecraft"));
    private static final ModEntry API = new ModEntry("TFCRegistryAPI.jar", "tfcregistryapi", List.of("tfc", "neoforge", "minecraft"));
    private static final ModEntry AFC = new ModEntry("ArborFirmaCraft.jar", "afc", List.of("tfc", "neoforge", "minecraft"));
    private static final ModEntry CARTS = new ModEntry("TFCAstikorCarts.jar", "tfcastikorcarts",
        List.of("tfcregistryapi", "astikorcartsredux", "tfc", "neoforge", "minecraft"));
    private static final ModEntry CREATE = new ModEntry("create.jar", "create", List.of("neoforge", "minecraft", "flywheel", "ponder", "sodium"));
    private static final ModEntry AZIMUTH = new ModEntry("azimuth.jar", "azimuth", List.of("neoforge", "minecraft"));

    private static List<ModEntry> select(List<ModEntry> mods) {
        return RegistryScanScope.select(mods, Function.identity(), RegistryScanScope.ROOTS);
    }

    @Test
    void rootsAreTfcAndTheApi() {
        assertEquals(Set.of("tfc", "tfcregistryapi"), RegistryScanScope.ROOTS);
    }

    @Test
    void keepsTheRootsAndTheirDependents() {
        List<ModEntry> all = List.of(MINECRAFT, NEOFORGE, CREATE, TFC, AZIMUTH, API, AFC, CARTS);
        assertEquals(List.of(TFC, API, AFC, CARTS), select(all));
    }

    @Test
    void anyDeclaredDependencyCounts() {
        // An addon with an enum of TFCRegistryAPI's own interfaces (RegistryOre, RegistrySand, RegistrySoil) may
        // declare only the API.
        ModEntry onlyApi = new ModEntry("ores.jar", "moreores", List.of("tfcregistryapi"));
        assertTrue(RegistryScanScope.touchesRoots(onlyApi, RegistryScanScope.ROOTS));
        assertTrue(RegistryScanScope.touchesRoots(TFC, RegistryScanScope.ROOTS));
        assertFalse(RegistryScanScope.touchesRoots(CREATE, RegistryScanScope.ROOTS));
        assertFalse(RegistryScanScope.touchesRoots(NEOFORGE, RegistryScanScope.ROOTS));
    }

    @Test
    void aFileIsKeptWhole() {
        // Two mods in one jar, only the second declares TFC: both stay, in list order, so the jar is scanned exactly
        // as before (once per mod entry, each with its own mod id).
        ModEntry core = new ModEntry("bundle.jar", "bundle_core", List.of("neoforge"));
        ModEntry tfcPart = new ModEntry("bundle.jar", "bundle_tfc", List.of("tfc"));
        List<ModEntry> all = List.of(core, CREATE, tfcPart, AFC);
        assertEquals(List.of(core, tfcPart, AFC), select(all));
        assertEquals(Set.of("bundle.jar", "ArborFirmaCraft.jar"), RegistryScanScope.selectFiles(all, RegistryScanScope.ROOTS));
    }

    @Test
    void orderAndDuplicatesArePreserved() {
        List<ModEntry> all = List.of(CARTS, CREATE, AFC, TFC, CARTS);
        assertEquals(List.of(CARTS, AFC, TFC, CARTS), select(all));
    }

    @Test
    void nothingToScan() {
        assertEquals(List.of(), select(List.of()));
        assertEquals(List.of(), select(List.of(MINECRAFT, NEOFORGE, CREATE, AZIMUTH)));
    }

    @Test
    void otherRoots() {
        assertEquals(List.of(CREATE), RegistryScanScope.select(List.of(TFC, CREATE, AZIMUTH), Function.identity(), Set.of("create")));
    }
}
