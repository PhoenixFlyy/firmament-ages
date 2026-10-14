package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.perf.TfcRegistryScan;
import net.dries007.tfc.common.blocks.rock.Ore;
import net.dries007.tfc.common.blocks.rock.Rock;
import net.dries007.tfc.common.blocks.soil.SandBlockType;
import net.dries007.tfc.common.blocks.soil.SoilBlockType;
import net.dries007.tfc.common.blocks.wood.Wood;
import net.dries007.tfc.util.Metal;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import tfcregistryapi.util.MetalRegistryHelper;
import tfcregistryapi.util.OreRegistryHelper;
import tfcregistryapi.util.RockRegistryHelper;
import tfcregistryapi.util.SandRegistryHelper;
import tfcregistryapi.util.SoilRegistryHelper;
import tfcregistryapi.util.SoilVariantRegistryHelper;
import tfcregistryapi.util.WoodRegistryHelper;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** TFCRegistryAPI checks of {@link PerfGameTests}; only loaded when TFCRegistryAPI (and so TFC) is in the run. */
final class TfcRegistryApiProbe {
    private TfcRegistryApiProbe() {}

    static void check(GameTestHelper h) {
        if (!TfcRegistryScan.enabled()) h.fail("perf.limitTfcRegistryApiScan is on by default");
        List<IModInfo> all = ModList.get().getMods();
        Set<String> kept = TfcRegistryScan.limit(all).stream().map(IModInfo::getModId).collect(Collectors.toSet());
        for (String id : List.of("tfc", "tfcregistryapi")) {
            if (!kept.contains(id)) h.fail("the scan keeps " + id + ", kept " + kept);
        }
        for (String id : List.of("minecraft", "neoforge", "immersiveengineering", "mekanism", "progressivestages")) {
            if (ModList.get().isLoaded(id) && kept.contains(id)) h.fail("the scan leaves out " + id + ", kept " + kept);
        }
        FirmagesCore.LOGGER.info("GameTest tfcRegistryApiScanScope: scan keeps {} of {} mods: {}", kept.size(), all.size(), kept);

        registered(h, "metal", MetalRegistryHelper.getAllMetalValues(), Metal.values());
        registered(h, "ore", OreRegistryHelper.getAllOreValues(), Ore.values());
        registered(h, "rock", RockRegistryHelper.getAllRockValues(), Rock.values());
        registered(h, "sand", SandRegistryHelper.getAllSandValues(), SandBlockType.values());
        registered(h, "soil", SoilRegistryHelper.getAllSoilValues(), SoilBlockType.values());
        registered(h, "soil variant", SoilVariantRegistryHelper.getAllSoilVariantValues(), SoilBlockType.Variant.values());
        registered(h, "wood", WoodRegistryHelper.getAllWoodValues(), Wood.values());
        String woodMod = WoodRegistryHelper.getWoodEnumMap().get(Wood.class).getModId();
        if (!"tfc".equals(woodMod)) h.fail("TFC's woods are registered under mod tfc, not " + woodMod);
    }

    private static void registered(GameTestHelper h, String what, Collection<?> values, Object[] expected) {
        if (expected.length == 0 || !values.containsAll(Arrays.asList(expected))) {
            h.fail("TFCRegistryAPI " + what + " values " + values + " lack some of TFC's " + Arrays.toString(expected));
        }
    }
}
