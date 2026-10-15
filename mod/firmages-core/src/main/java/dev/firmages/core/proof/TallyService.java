package dev.firmages.core.proof;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.compat.ftbteams.FtbTeamsCompat;
import dev.firmages.core.immersion.AgeStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Counting and the {@code firmages:tally} criterion (SPEC §22.1, §22.2). A Tally Chute reports each accepted insert
 * here; the team is the owner's FTB team (the owner's own id without FTB Teams), the Age is the world's current Age.
 * Once a second every team whose tally changed fires the criterion for its online members; a login fires it for the
 * player, so a proof completed while a member was away still reaches them.
 */
public final class TallyService {
    private static final int FIRE_PERIOD = 20;
    private static final Set<UUID> dirty = new HashSet<>();

    private TallyService() {}

    public static void register(IEventBus bus) {
        bus.addListener(TallyService::onServerTick);
        bus.addListener(TallyService::onLogin);
        bus.addListener(TallyService::onServerStopped);
    }

    /**
     * Counts {@code n} items of {@code item} for {@code owner}'s team in the world's current Age.
     * @return the team's total of {@code item} over all Ages
     */
    public static long count(MinecraftServer server, UUID owner, String item, long n) {
        UUID team = teamOf(owner);
        TallyData data = TallyData.get(server);
        data.add(team, currentAge(server), item, n);
        dirty.add(team);
        return data.of(team).total(item);
    }

    /** The team {@code player} counts for: the FTB team (a party or the player's own team), else the player's id. */
    public static UUID teamOf(UUID player) {
        if (ModList.get().isLoaded(FtbTeamsCompat.MOD_ID)) {
            UUID team = FtbTeamsCompat.teamOf(player);
            if (team != null) return team;
        }
        return player;
    }

    /** The tally of {@code player}'s team. */
    public static TallyCounts countsOf(MinecraftServer server, UUID player) {
        return TallyData.get(server).of(teamOf(player));
    }

    static AgeId currentAge(MinecraftServer server) {
        return AgeStatus.current(AgeService.state(server).snapshot());
    }

    /** Fires the criterion now for every online member of the teams counted since the last fire (GameTests call it). */
    public static void fireDirty(MinecraftServer server) {
        if (dirty.isEmpty()) return;
        List<UUID> teams = new ArrayList<>(dirty);
        dirty.clear();
        TallyData data = TallyData.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID team = teamOf(p.getUUID());
            if (teams.contains(team)) ProofRegistry.TALLY.get().trigger(p, data.of(team));
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % FIRE_PERIOD == 0) fireDirty(event.getServer());
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) ProofRegistry.TALLY.get().trigger(p, countsOf(p.server, p.getUUID()));
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        dirty.clear();
    }
}
