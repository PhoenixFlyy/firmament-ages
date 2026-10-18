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
        dev.firmages.core.ark.ArkRegistry.register(modEventBus); // the Archipelago's plinth (SPEC §31.8)
        dev.firmages.core.proof.ProofRegistry.register(modEventBus); // Tally Chute, firmages:tally, firmages:silo_launch (SPEC §22)
        BossRegistry.register(modEventBus); // The Primordial, the end boss (docs/primordial.md)
        dev.firmages.core.balance.EnergyMeter.register(modEventBus, NeoForge.EVENT_BUS); // balance.feCap and the energy audit (SPEC 37, 38)
        dev.firmages.core.balance.OneIron.register(modEventBus, NeoForge.EVENT_BUS); // vanilla iron in loot and trades (SPEC 34.4)
        dev.firmages.core.loot.AgeLoot.register(modEventBus); // loot of later Ages never generates (SPEC 19.5)
        dev.firmages.core.balance.EnergyCharge.register(NeoForge.EVENT_BUS); // /firmages energy charge, a test power line (SPEC 38)
        dev.firmages.core.sky.SkyRegistry.register(modEventBus); // Starfall blocks, metals, core, sounds, loot condition (SPEC §25)
        dev.firmages.core.crown.CrownRegistry.register(modEventBus); // Lens Crown, firmages:starforge (SPEC §27)
        dev.firmages.core.anchor.AnchorRegistry.register(modEventBus); // Sky Anchor, blessing shards, firmages:tier (SPEC §29)
        dev.firmages.core.launch.LaunchRegistry.register(modEventBus); // Launch Complex, probes, firmages:probe (SPEC §31)
        dev.firmages.core.sky.HarvestRegistry.register(modEventBus); // Sky Harvest: Storm Rod, storm glass, Aurora Net, aurora light (SPEC §26)
        dev.firmages.core.isles.IsleRegistry.register(modEventBus); // Drifting Isles: skystone, crystal, lode, structure (SPEC §30)
        dev.firmages.core.machine.MachineRegistry.register(modEventBus); // Ore Processor, Arcane Catalyst (SPEC §40, §41)

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
        dev.firmages.core.hardship.GridLockService.register(bus); // Dawn grid lock and ground crafting (SPEC §19.3)
        dev.firmages.core.hardship.InventoryRowsService.register(bus); // inventory rows by Age (SPEC §19.4)
        dev.firmages.core.hardship.NightsService.register(bus); // Creeping Darkness: nights by Age (SPEC §19.7)
        bus.addListener(FirmagesCommands::register);
        bus.addListener(dev.firmages.core.command.ServerCommands::register); // /stop confirm and /restart (SPEC §10.1)
        bus.addListener(dev.firmages.core.command.ServerCommands::onServerTick);
        bus.addListener(dev.firmages.core.command.ServerCommands::onServerStopped);
        bus.addListener(dev.firmages.core.immersion.PonderCommandGate::onCommand); // /ponder for operators only (SPEC §21.6)
        bus.addListener(DebugPlayerCommands::onServerTick);
        bus.addListener(StageSyncCoalescer::onServerTick);
        bus.addListener(StageSyncCoalescer::onServerStopped);
        bus.addListener(EventPriority.HIGH, OreGuard::onBreak);
        bus.addListener(EventPriority.LOW, OreGuard::onDrops);
        ShrineEvents.register(bus);
        dev.firmages.core.wayshrine.WayshrineEvents.register(bus);
        OriginService.register(bus);
        dev.firmages.core.ark.ArkService.register(bus); // the Origin Archipelago, post-game (SPEC §31.8)
        if (ModList.get().isLoaded("create")) dev.firmages.core.ark.ArkWork.kineticSpeed = dev.firmages.core.compat.create.CreateKinetics::speed;
        // the Arcane Catalyst's links (SPEC §41.3)
        if (ModList.get().isLoaded("create")) dev.firmages.core.machine.CatalystHooks.rotation = dev.firmages.core.compat.create.CreateKinetics::speed;
        if (ModList.get().isLoaded("ars_nouveau")) dev.firmages.core.machine.CatalystHooks.source = dev.firmages.core.compat.ars.ArsSource::take;
        if (ModList.get().isLoaded("immersiveengineering")) dev.firmages.core.machine.CatalystHooks.multiblock = dev.firmages.core.compat.ie.IeMultiblockTicks::tick;
        OriginAccess.register(bus);
        OriginSpawns.register(bus);
        dev.firmages.core.proof.TallyService.register(bus);
        dev.firmages.core.proof.SiloLaunch.register(bus);
        BossEvents.register(bus);
        dev.firmages.core.sky.StarfallService.register(bus); // sky plumbing and Starfall (SPEC §24.4, §25)
        dev.firmages.core.isles.IsleService.register(bus); // lode re-forming, fallback cluster, Starfall probe (SPEC §30)
        dev.firmages.core.crown.CrownService.register(bus);
        dev.firmages.core.anchor.AnchorEvents.register(bus); // Sky Anchors as blessing sources (SPEC §29.5)
        dev.firmages.core.launch.LaunchService.register(bus);
        dev.firmages.core.sky.HarvestRegistry.registerGame(bus); // sky_harvest.json
        // the unlinked wireless crafting terminal opens a portable crafting grid (AE2 package, review 0.11.0)
        if (ModList.get().isLoaded(dev.firmages.core.compat.ae2.PortableCrafting.MOD_ID)) dev.firmages.core.compat.ae2.PortableCrafting.register(bus);
        LOGGER.info("firmages-core loaded");
    }
}
