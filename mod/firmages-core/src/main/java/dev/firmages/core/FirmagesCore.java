package dev.firmages.core;

import com.mojang.logging.LogUtils;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.boss.BossEvents;
import dev.firmages.core.boss.BossRegistry;
import dev.firmages.core.command.DebugPlayerCommands;
import dev.firmages.core.command.FirmagesCommands;
import dev.firmages.core.compat.ftbteams.FtbTeamsCompat;
import dev.firmages.core.compat.progressivestages.StageSyncCoalescer;
import dev.firmages.core.config.ClientConfig;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.immersion.ImmersionService;
import dev.firmages.core.hardship.CaveDwellerService;
import dev.firmages.core.hardship.ToolRuleService;
import dev.firmages.core.miner.OreGuard;
import net.neoforged.bus.api.EventPriority;
import dev.firmages.core.net.FirmagesNetwork;
import dev.firmages.core.origin.OriginAccess;
import dev.firmages.core.origin.OriginRegistry;
import dev.firmages.core.origin.OriginService;
import dev.firmages.core.origin.OriginSpawns;
import dev.firmages.core.reactor.ReactorRegistry;
import dev.firmages.core.shrine.ShrineEvents;
import dev.firmages.core.shrine.ShrineRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
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
        ServerConfig.warnRemovedKeys(); // S4A: shrine.blessings.everywhere is gone (SPEC-himmel §29.7)
        modEventBus.addListener(FirmagesNetwork::register);
        ShrineRegistry.register(modEventBus);
        dev.firmages.core.wayshrine.WayshrineRegistry.register(modEventBus); // Wayshrines (SPEC-himmel §28)
        ReactorRegistry.register(modEventBus);
        OriginRegistry.register(modEventBus);
        dev.firmages.core.proof.ProofRegistry.register(modEventBus); // Tally Chute, firmages:tally, firmages:silo_launch (SPEC §22)
        BossRegistry.register(modEventBus); // The Primordial, the end boss (docs/primordial.md)
        dev.firmages.core.sky.SkyRegistry.register(modEventBus); // Starfall blocks, metals, core, sounds, loot condition (SPEC §25)
        dev.firmages.core.crown.CrownRegistry.register(modEventBus); // Lens Crown, firmages:starforge (SPEC §27)
        dev.firmages.core.anchor.AnchorRegistry.register(modEventBus); // Sky Anchor, blessing shards, firmages:tier (SPEC §29)

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(AgeService::onAddReloadListeners);
        bus.addListener(AgeService::onServerAboutToStart);
        bus.addListener(AgeService::onServerStarted);
        bus.addListener(AgeService::onServerStopped);
        bus.addListener(AgeService::onServerTick);
        bus.addListener(AgeService::onStageChange);
        bus.addListener(AgeService::onStagesBulkChanged);
        bus.addListener(AgeService::onPlayerLoggedIn);
        if (ModList.get().isLoaded(FtbTeamsCompat.MOD_ID)) FtbTeamsCompat.register(); // team carry-over (SPEC §2.3)
        ImmersionService.register(modEventBus, bus); // after AgeService: the login status sees the login's Ages
        dev.firmages.core.ceremony.CeremonyEcho.register(modEventBus, bus); // seen_age attachment, echo of missed ceremonies
        CaveDwellerService.register(bus);
        ToolRuleService.register(bus);
        bus.addListener(FirmagesCommands::register);
        bus.addListener(dev.firmages.core.immersion.PonderCommandGate::onCommand); // /ponder for operators only (SPEC §21.6)
        bus.addListener(DebugPlayerCommands::onServerTick);
        bus.addListener(StageSyncCoalescer::onServerTick);
        bus.addListener(StageSyncCoalescer::onServerStopped);
        bus.addListener(EventPriority.HIGH, OreGuard::onBreak);
        bus.addListener(EventPriority.LOW, OreGuard::onDrops);
        ShrineEvents.register(bus);
        dev.firmages.core.wayshrine.WayshrineEvents.register(bus);
        OriginService.register(bus);
        OriginAccess.register(bus);
        OriginSpawns.register(bus);
        dev.firmages.core.proof.TallyService.register(bus);
        dev.firmages.core.proof.SiloLaunch.register(bus);
        BossEvents.register(bus);
        dev.firmages.core.sky.StarfallService.register(bus); // sky plumbing and Starfall (SPEC §24.4, §25)
        dev.firmages.core.crown.CrownService.register(bus);
        dev.firmages.core.anchor.AnchorEvents.register(bus); // Sky Anchors as blessing sources (SPEC §29.5)
        // the unlinked wireless crafting terminal opens a portable crafting grid (AE2 package, review 0.11.0)
        if (ModList.get().isLoaded(dev.firmages.core.compat.ae2.PortableCrafting.MOD_ID)) dev.firmages.core.compat.ae2.PortableCrafting.register(bus);
        LOGGER.info("firmages-core loaded");
    }
}
