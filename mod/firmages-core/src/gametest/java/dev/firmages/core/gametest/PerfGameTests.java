package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Level G tests of the start-up performance changes (SPEC §20). */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class PerfGameTests {
    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(PerfGameTests.class);
    }

    /**
     * SPEC §20: with the scan limited, TFCRegistryAPI still registers every TFC enum of its seven interfaces under mod
     * {@code tfc}, and the scope leaves out the mods that have nothing to do with TFC. TFCRegistryAPI is only in the
     * opt-in run {@code gradlew runGameTestServer -PgametestTfc}; without it this test passes empty.
     */
    @GameTest(template = "empty", batch = "firmages_7_perf")
    public static void tfcRegistryApiScanScope(GameTestHelper h) {
        if (!ModList.get().isLoaded("tfcregistryapi")) {
            FirmagesCore.LOGGER.info("GameTest tfcRegistryApiScanScope: TFCRegistryAPI not in this run (-PgametestTfc), skipped");
            h.succeed();
            return;
        }
        TfcRegistryApiProbe.check(h);
        h.succeed();
    }
}
