package dev.firmages.core.origin;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.ceremony.CeremonyService;
import dev.firmages.core.config.ServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The Origin at runtime (SPEC §16): builds the arena once, watches the Gathering at the altar and turns the death
 * of the tagged final boss into {@code finale_won} plus the FINALE ceremony. The fight itself (Gateways to Eternity
 * waves, the Cataclysm boss, its phases) is KubeJS; this class only provides the trigger and the result.
 */
public final class OriginService {
    /**
     * Scoreboard tag ({@code Entity#addTag}) that marks the final boss; its death wins the game. A dot, not a colon:
     * selectors ({@code @e[tag=...]}) and {@code /tag ... add} read an unquoted word, which cannot hold a colon.
     */
    public static final String FINAL_BOSS_TAG = "firmages.final_boss";
    public static final String FINALE_STAGE = "finale_won";
    public static final ResourceLocation START_FUNCTIONS = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "origin/start");
    public static final ResourceLocation WON_FUNCTIONS = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "origin/won");
    private static final int CHECK_PERIOD = 20;

    private static boolean wasGathered;
    /** Server tick of the last start, -1 for none since boot (the cooldown is not persisted). */
    private static long lastStartTick = -1;

    private OriginService() {}

    public static void register(IEventBus bus) {
        bus.addListener(OriginService::onServerStarted);
        bus.addListener(OriginService::onServerStopped);
        bus.addListener(OriginService::onServerTick);
        // LOWEST: a script or totem that cancels the death runs first, and a cancelled death wins nothing.
        bus.addListener(EventPriority.LOWEST, OriginService::onLivingDeath);
        bus.addListener(OriginService::onLogin);
    }

    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(OriginRegistry.ORIGIN);
    }

    // ---- arena -----------------------------------------------------------------------------------------------

    private static void onServerStarted(ServerStartedEvent event) {
        ensureBuilt(event.getServer());
    }

    /** Builds what is missing: the arena (once per {@link OriginArena#VERSION}) and, with Stargate Journey, the gate. */
    public static void ensureBuilt(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level == null) {
            FirmagesCore.LOGGER.warn("The Origin dimension {} is not loaded; arena not built", OriginRegistry.ORIGIN_ID);
            return;
        }
        OriginSavedData sd = OriginSavedData.get(server);
        if (sd.arenaVersion() < OriginArena.VERSION) {
            OriginArena.buildArena(level);
            sd.setArenaVersion(OriginArena.VERSION);
        }
        if (!sd.gateBuilt() && ModList.get().isLoaded("sgjourney")) {
            if (OriginArena.buildGate(level)) sd.setGateBuilt(true);
            else FirmagesCore.LOGGER.error("The Origin: Stargate Journey is loaded but its template {} is missing; no return gate", OriginArena.GATE_TEMPLATE);
        }
    }

    // ---- Gathering -------------------------------------------------------------------------------------------

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_PERIOD != 0) return;
        checkGathering(server);
    }

    /** Players the Gathering waits for: everyone online who is not a spectator. */
    public static List<ServerPlayer> team(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.isSpectator()) out.add(p);
        }
        return out;
    }

    /** True when every online non-spectator is alive in The Origin within {@code origin.gatherRadius} of the altar. */
    public static boolean gathered(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level == null || !level.isLoaded(OriginArena.ALTAR) || !level.getBlockState(OriginArena.ALTAR).is(OriginRegistry.ORIGIN_ALTAR.get())) return false;
        List<ServerPlayer> team = team(server);
        if (team.isEmpty()) return false;
        double r = gatherRadius();
        Vec3 c = Vec3.atBottomCenterOf(OriginArena.ALTAR);
        for (ServerPlayer p : team) {
            if (p.level() != level || !p.isAlive() || p.position().distanceToSqr(c) > r * r) return false;
        }
        return true;
    }

    /** One Gathering check (every second; GameTests call it directly). @return true if the start fired now */
    public static boolean checkGathering(MinecraftServer server) {
        OriginSavedData sd = OriginSavedData.get(server);
        if (sd.won()) {
            wasGathered = false;
            return false;
        }
        boolean now = gathered(server);
        boolean fire = now && !wasGathered && (lastStartTick < 0 || server.getTickCount() - lastStartTick >= cooldownTicks());
        if (now && !wasGathered && !fire) {
            FirmagesCore.LOGGER.debug("The Origin: Gathering within the cooldown, not started again");
        }
        wasGathered = now;
        if (fire) startGathering(server, sd);
        return fire;
    }

    private static void startGathering(MinecraftServer server, OriginSavedData sd) {
        ServerLevel level = level(server);
        if (level == null) return;
        sd.gathered();
        lastStartTick = server.getTickCount();
        List<ServerPlayer> team = team(server);
        FirmagesCore.LOGGER.info("The Origin: Gathering #{} of {} player(s) at {}", sd.gatherings(), team.size(), OriginArena.ALTAR);
        for (ServerPlayer p : team) {
            p.displayClientMessage(Component.translatable("firmages.origin.gathering").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
        NeoForge.EVENT_BUS.post(new OriginEvent.Gathering(level, OriginArena.ALTAR, team, sd.gatherings()));
        runFunctions(server, level, OriginArena.ALTAR, START_FUNCTIONS);
    }

    // ---- the final boss --------------------------------------------------------------------------------------

    private static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !e.getTags().contains(FINAL_BOSS_TAG)) return;
        if (e.getServer() != null) bossDefeated(e.getServer(), e);
    }

    /** The tagged boss died. @return false if the finale was already won (a second tagged boss changes nothing) */
    public static boolean bossDefeated(MinecraftServer server, LivingEntity boss) {
        OriginSavedData sd = OriginSavedData.get(server);
        if (sd.won()) {
            FirmagesCore.LOGGER.info("The Origin: another {} died after the finale; nothing to do", FINAL_BOSS_TAG);
            return false;
        }
        sd.setWon(true);
        FirmagesCore.LOGGER.info("The Origin: final boss {} defeated, granting {}", boss.getType().getDescriptionId(), FINALE_STAGE);
        grantFinale(server);
        ServerLevel origin = level(server);
        ServerLevel where = origin != null ? origin : (ServerLevel) boss.level();
        BlockPos at = origin != null ? OriginArena.ALTAR : boss.blockPosition();
        CeremonyService.startFinale(server, GlobalPos.of(where.dimension(), at));
        NeoForge.EVENT_BUS.post(new OriginEvent.Victory((ServerLevel) boss.level(), at, boss));
        runFunctions(server, where, at, WON_FUNCTIONS);
        return true;
    }

    /** Grants {@code finale_won} to every online player (team mode: the first grant covers the team). */
    static void grantFinale(MinecraftServer server) {
        StageId id = StageId.of(FINALE_STAGE);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) grantTo(p, id);
        if (server.getPlayerList().getPlayers().isEmpty()) {
            FirmagesCore.LOGGER.warn("The Origin: {} won with nobody online; the next player to log in receives it", FINALE_STAGE);
        }
    }

    private static void grantTo(ServerPlayer p, StageId id) {
        try {
            if (ProgressiveStagesAPI.hasStage(p, id)) return;
            if (!ProgressiveStagesAPI.grantStage(p, id, StageCause.API) && !ProgressiveStagesAPI.hasStage(p, id)) {
                FirmagesCore.LOGGER.warn("ProgressiveStages refused {} for {}; granting without the dependency check", id, p.getGameProfile().getName());
                ProgressiveStagesAPI.grantStageBypass(p, id, StageCause.API);
            }
        } catch (RuntimeException | LinkageError e) {
            FirmagesCore.LOGGER.error("Cannot grant {} to {} through ProgressiveStages", id, p.getGameProfile().getName(), e);
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && OriginSavedData.get(p.server).won()) grantTo(p, StageId.of(FINALE_STAGE));
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    /** Runs every function of the tag {@code #id} as the server at {@code pos} in {@code level} (permission 2). */
    public static int runFunctions(MinecraftServer server, ServerLevel level, BlockPos pos, ResourceLocation id) {
        Collection<CommandFunction<CommandSourceStack>> fns = server.getFunctions().getTag(id);
        if (fns.isEmpty()) return 0;
        CommandSourceStack src = server.createCommandSourceStack().withLevel(level).withPosition(Vec3.atBottomCenterOf(pos))
            .withPermission(2).withSuppressedOutput();
        for (CommandFunction<CommandSourceStack> fn : fns) server.getFunctions().execute(fn, src);
        FirmagesCore.LOGGER.info("The Origin: ran {} function(s) of #{}", fns.size(), id);
        return fns.size();
    }

    public static double gatherRadius() {
        return ServerConfig.loaded() ? ServerConfig.ORIGIN_GATHER_RADIUS.get() : 6;
    }

    public static int cooldownTicks() {
        return 20 * (ServerConfig.loaded() ? ServerConfig.ORIGIN_GATHER_COOLDOWN.get() : 30);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        wasGathered = false;
        lastStartTick = -1;
    }

    /** {@code /firmages origin reset}: the Gathering can fire again and the boss can be won again. */
    public static void reset(MinecraftServer server) {
        OriginSavedData.get(server).resetFight();
        wasGathered = false;
        lastStartTick = -1;
    }
}
