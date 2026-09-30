package dev.firmages.core;

import com.mojang.logging.LogUtils;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.command.FirmagesCommands;
import dev.firmages.core.config.ClientConfig;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.miner.OreGuard;
import net.neoforged.bus.api.EventPriority;
import dev.firmages.core.net.FirmagesNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(FirmagesCore.MOD_ID)
public final class FirmagesCore {
    public static final String MOD_ID = "firmages";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FirmagesCore(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modEventBus.addListener(FirmagesNetwork::register);

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(AgeService::onAddReloadListeners);
        bus.addListener(AgeService::onServerAboutToStart);
        bus.addListener(AgeService::onServerStarted);
        bus.addListener(AgeService::onServerStopped);
        bus.addListener(AgeService::onServerTick);
        bus.addListener(AgeService::onStageChange);
        bus.addListener(AgeService::onStagesBulkChanged);
        bus.addListener(AgeService::onPlayerLoggedIn);
        bus.addListener(FirmagesCommands::register);
        bus.addListener(EventPriority.HIGH, OreGuard::onBreak);
        bus.addListener(EventPriority.LOW, OreGuard::onDrops);
        LOGGER.info("firmages-core loaded");
    }
}
