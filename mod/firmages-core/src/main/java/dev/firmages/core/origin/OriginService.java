package dev.firmages.core.origin;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.ceremony.CeremonyService;
import dev.firmages.core.config.ServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    /** Namespace of Gateways to Eternity's gate entities; the mod is not a compile dependency, its save data is read instead. */
    private static final String GATEWAYS = "gateways";

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
        ServerLevel level = level(server);
        if (level != null && !level.players().isEmpty()) OriginEchoes.tick(level);
    }

    /**
     * Players the Gathering waits for: everyone online who is not a spectator and has acted within
     * {@code origin.gatherIdleMinutes}, so one idle player cannot hold back the end of the pack.
     */
    public static List<ServerPlayer> team(MinecraftServer server) {
        long idleMillis = ServerConfig.gatherIdleMinutes() * 60_000L;
        long now = Util.getMillis();
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) continue;
            if (idleMillis > 0 && now - p.getLastActionTime() >= idleMillis) continue;
            out.add(p);
        }
        return out;
    }

    /** While some of the team stand at the altar and others do not, the ones at the altar see who is missing. */
    private static void showWaiting(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level == null || !level.isLoaded(OriginArena.ALTAR) || !level.getBlockState(OriginArena.ALTAR).is(OriginRegistry.ORIGIN_ALTAR.get())) return;
        double r = gatherRadius();
        Vec3 c = Vec3.atBottomCenterOf(OriginArena.ALTAR);
        List<ServerPlayer> at = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (ServerPlayer p : team(server)) {
            if (p.level() == level && p.isAlive() && p.position().distanceToSqr(c) <= r * r) at.add(p);
            else missing.add(p.getGameProfile().getName());
        }
        if (at.isEmpty() || missing.isEmpty() || fightActive(level)) return;
        String names = String.join(", ", missing.subList(0, Math.min(5, missing.size()))) + (missing.size() > 5 ? ", ..." : "");
        Component msg = Component.translatable("firmages.origin.waiting", missing.size(), names).withStyle(ChatFormatting.LIGHT_PURPLE);
        for (ServerPlayer p : at) p.displayClientMessage(msg, true);
    }

    /** True when every player of {@link #team} is alive in The Origin within {@code origin.gatherRadius} of the altar. */
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

    /**
     * One Gathering check (every second; GameTests call it directly). The Gathering starts when the whole team
     * arrives at the altar (the rising edge), at most once per {@code origin.gatherCooldownSeconds}, and never while
     * its fight runs ({@link #fightActive}): players walk away from the altar and back during the waves and the
     * boss, and each return used to start (and announce) the Gathering again once the cooldown had passed. After a
     * wipe the gate and the boss are gone, so the next arrival after the cooldown starts it again.
     * @return true if the start fired now
     */
    public static boolean checkGathering(MinecraftServer server) {
        OriginSavedData sd = OriginSavedData.get(server);
        if (sd.won()) {
            wasGathered = false;
            return false;
        }
        boolean now = gathered(server);
        if (!now) showWaiting(server);
        boolean arrived = now && !wasGathered;
        wasGathered = now;
        if (!arrived) return false;
        ServerLevel level = level(server);
        if (level != null && fightActive(level)) {
            FirmagesCore.LOGGER.debug("The Origin: team at the altar while the fight runs, no new Gathering");
            return false;
        }
        if (lastStartTick >= 0 && server.getTickCount() - lastStartTick < cooldownTicks()) {
            FirmagesCore.LOGGER.debug("The Origin: Gathering within the cooldown, not started again");
            return false;
        }
        startGathering(server, sd);
        return true;
    }

    /**
     * The fight of a Gathering runs: the finale gate ({@code origin.finaleGateway}) or an entity with
     * {@link #FINAL_BOSS_TAG} is alive in The Origin (loaded entities; players stand next to both while it matters).
     */
    public static boolean fightActive(ServerLevel level) {
        String gate = ServerConfig.finaleGateway();
        for (Entity e : level.getAllEntities()) {
            if (e.isAlive() && (e.getTags().contains(FINAL_BOSS_TAG) || gate.equals(gatewayId(e)))) return true;
        }
        return false;
    }

    /**
     * The gateway id of a Gateways to Eternity gate entity, from its save data ({@code gate}, verified with javap in
     * GatewaysToEternity 5.1.0 {@code GatewayEntity#addAdditionalSaveData}); null for every other entity.
     */
    @Nullable
    static String gatewayId(Entity e) {
        if (!isGate(e)) return null;
        return e.saveWithoutId(new CompoundTag()).getString("gate");
    }

    private static boolean isGate(Entity e) {
        return GATEWAYS.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace());
    }

    /**
     * {@code /firmages origin kill} (operators): kills every hostile in The Origin, i.e. the enemies of the current gate
     * wave (also those a gate tracks that are not monsters, from its {@code wave_entities}) and the boss's helpers,
     * but never the final boss. A kill (not a discard) counts for the gate, so the wave ends as if fought.
     * @return the number of entities killed
     */
    public static int killHostiles(ServerLevel level) {
        Set<UUID> waves = new HashSet<>();
        List<Entity> all = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            all.add(e);
            if (isGate(e)) {
                long[] ids = e.saveWithoutId(new CompoundTag()).getLongArray("wave_entities");
                for (int i = 0; i + 1 < ids.length; i += 2) waves.add(new UUID(ids[i], ids[i + 1]));
            }
        }
        int killed = 0;
        for (Entity e : all) {
            if (!(e instanceof LivingEntity le) || !le.isAlive() || le instanceof ServerPlayer || le.getTags().contains(FINAL_BOSS_TAG)) continue;
            if (!(le instanceof Enemy) && !waves.contains(le.getUUID())) continue;
            if (kill(le)) killed++;
        }
        FirmagesCore.LOGGER.info("The Origin: {} hostile(s) killed by an operator", killed);
        return killed;
    }

    /**
     * {@code /firmages origin kill boss} (operators): kills every entity with {@link #FINAL_BOSS_TAG} in The Origin;
     * its death grants {@code finale_won} the normal way ({@link #onLivingDeath}). @return the number killed
     */
    public static int killBoss(ServerLevel level) {
        List<LivingEntity> bosses = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof LivingEntity le && le.isAlive() && le.getTags().contains(FINAL_BOSS_TAG)) bosses.add(le);
        }
        int killed = 0;
        for (LivingEntity boss : bosses) if (kill(boss)) killed++;
        FirmagesCore.LOGGER.info("The Origin: {} final boss(es) killed by an operator", killed);
        return killed;
    }

    /**
     * {@code /kill}'s damage (generic_kill bypasses invulnerability, so Cataclysm's per-hit damage cap and its
     * invulnerable attack animations do not apply); a death cancelled by a listener stays cancelled.
     */
    private static boolean kill(LivingEntity e) {
        e.kill();
        return e.isDeadOrDying() || e.isRemoved();
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

    /**
     * {@code /firmages origin gathering} (operators): the Gathering starts now for everyone online, wherever they
     * stand, regardless of {@code origin.gatherRadius} and the cooldown. @return false once the finale is won or
     * without The Origin
     */
    public static boolean forceGathering(MinecraftServer server) {
        OriginSavedData sd = OriginSavedData.get(server);
        if (sd.won() || level(server) == null) return false;
        FirmagesCore.LOGGER.info("The Origin: Gathering forced by an operator");
        startGathering(server, sd);
        return true;
    }

    /**
     * {@code /firmages origin fight} (operators): asks the boss script to summon its final boss now, without the
     * gateway waves ({@link OriginEvent.SummonBoss}). @return the answered event, or null without The Origin
     */
    @Nullable
    public static OriginEvent.SummonBoss summonBoss(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level == null) return null;
        OriginEvent.SummonBoss e = NeoForge.EVENT_BUS.post(new OriginEvent.SummonBoss(level, OriginArena.ALTAR));
        FirmagesCore.LOGGER.info("The Origin: boss summon forced by an operator: {}", e.isSummoned() ? "summoned"
            : e.getRefusal().isEmpty() ? "no script answered" : "refused (" + e.getRefusal() + ")");
        return e;
    }

    // ---- the final boss --------------------------------------------------------------------------------------

    /**
     * A tagged boss counts only when it dies in The Origin: a tagged entity that a player lures through a portal,
     * a copy summoned elsewhere, or a tag added by hand in another dimension wins nothing.
     */
    private static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !e.getTags().contains(FINAL_BOSS_TAG)) return;
        if (e.level().dimension() != OriginRegistry.ORIGIN) {
            FirmagesCore.LOGGER.warn("The Origin: a {} died in {} at {}, not in The Origin; it does not count", FINAL_BOSS_TAG,
                e.level().dimension().location(), e.blockPosition());
            return;
        }
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
        dev.firmages.core.shrine.ShrineConsecration.livingChanged(server); // the shrine's idle beam turns gold
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
