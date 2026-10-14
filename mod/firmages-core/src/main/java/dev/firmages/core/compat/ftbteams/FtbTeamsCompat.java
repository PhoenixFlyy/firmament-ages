package dev.firmages.core.compat.ftbteams;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.firmages.core.FirmagesCore;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.ftb.mods.ftbteams.api.event.PlayerChangedTeamEvent;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.firmages.core.compat.progressivestages.TeamCarryOver;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * Reads the FTB team ids, keeps every player in the one party that holds the Ages and gives a party the world's Ages
 * when a player enters it. Only loaded when {@code ftbteams} is present.
 */
public final class FtbTeamsCompat {
    public static final String MOD_ID = "ftbteams";
    /** Name of the party created when a server has none yet. */
    public static final String PARTY_NAME = "Firmament";

    private FtbTeamsCompat() {}

    /** Ids of all FTB teams (player and party teams); empty while the manager is not loaded. */
    public static List<UUID> teamIds() {
        if (!FTBTeamsAPI.api().isManagerLoaded()) return List.of();
        return FTBTeamsAPI.api().getManager().getTeams().stream().map(Team::getId).toList();
    }

    /** Registers the party listener of the team carry-over (FTB Teams' Architectury event). */
    public static void register() {
        TeamEvent.PLAYER_CHANGED.register(FtbTeamsCompat::onPlayerChanged);
    }

    /**
     * A player entered a party (founded it, joined it, was invited): one tick later, when ProgressiveStages resolves the
     * player to the party, the party gets the world's Ages ({@link TeamCarryOver}).
     */
    private static void onPlayerChanged(PlayerChangedTeamEvent event) {
        Team team = event.getTeam();
        if (team == null || !team.isPartyTeam() || !FTBTeamsAPI.api().isManagerLoaded()) return;
        MinecraftServer server = FTBTeamsAPI.api().getManager().getServer();
        if (server == null) return;
        UUID party = team.getId();
        String who = event.getPlayer() != null ? event.getPlayer().getGameProfile().getName() : String.valueOf(event.getPlayerId());
        server.tell(new TickTask(server.getTickCount() + 1, () -> carryOver(party, who + " entered " + team.getShortName())));
    }

    /** Grants the party {@code partyId} the world's Ages through one of its online members. */
    public static void carryOver(UUID partyId, String why) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) return;
        Team party = FTBTeamsAPI.api().getManager().getTeamByID(partyId).orElse(null);
        if (party == null || !party.isPartyTeam()) return;
        party.getOnlineMembers().stream().findFirst().ifPresent(member -> TeamCarryOver.apply(member, why));
    }

    /**
     * True for a player team whose player now belongs to a party: its stages stay stored (ProgressiveStages
     * {@code persist_stages_on_leave}) but no longer count, the party holds the Ages.
     */
    public static boolean dormant(UUID teamId) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) return false;
        TeamManager mgr = FTBTeamsAPI.api().getManager();
        Team t = mgr.getTeamByID(teamId).orElse(null);
        if (t == null || !t.isPlayerTeam()) return false;
        return mgr.getTeamForPlayerID(teamId).map(Team::isPartyTeam).orElse(false);
    }

    /** True for a team FTB Teams no longer knows (a disbanded party). */
    public static boolean gone(UUID teamId) {
        return FTBTeamsAPI.api().isManagerLoaded() && FTBTeamsAPI.api().getManager().getTeamByID(teamId).isEmpty();
    }

    /**
     * One tick after a login: a player in a party gets the team carry-over again (repairs a party that lost Ages, for
     * example one founded without the carry-over); a player outside a party goes through {@link #ensureFellowship} with
     * {@code team.autoJoin}.
     */
    public static void onLogin(ServerPlayer player, boolean autoJoin, ToIntFunction<UUID> ages) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) return;
        Team own = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (own != null && own.isPartyTeam()) {
            TeamCarryOver.apply(player, "login of " + player.getGameProfile().getName());
        } else if (autoJoin) {
            ensureFellowship(player, ages);
        }
    }

    /**
     * One fellowship per server (SPEC §2.3, {@code team.autoJoin}): a player outside a party joins the party that
     * holds the most Ages. A server without a party gets one, "Firmament": its founder is the player whose own team
     * holds the most Ages (FTB Quests moves the founder's quest book into the party), or the joining player while
     * nobody has passed Dawn; every online player outside a party joins it. The team carry-over gives the party the
     * world's Ages. While the player who holds the Ages is offline, the others are told who it is and the fellowship
     * forms at that player's next login.
     *
     * @param ages number of Age stages beyond Dawn a team holds (ProgressiveStages team storage)
     */
    public static void ensureFellowship(ServerPlayer player, ToIntFunction<UUID> ages) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) return;
        TeamManager mgr = FTBTeamsAPI.api().getManager();
        Team own = mgr.getTeamForPlayer(player).orElse(null);
        if (own == null || !own.isPlayerTeam()) return; // already in a party or a server team
        Comparator<Team> byAges = Comparator.comparingInt((Team t) -> ages.applyAsInt(t.getId()));
        Team party = mgr.getTeams().stream().filter(Team::isPartyTeam).filter(Team::isValid).max(byAges).orElse(null);
        if (party != null) {
            join(party, player);
            return;
        }
        Team holder = mgr.getTeams().stream().filter(Team::isPlayerTeam).max(byAges).orElse(own);
        ServerPlayer founder = player;
        if (holder != own && ages.applyAsInt(holder.getId()) > ages.applyAsInt(own.getId())) {
            founder = mgr.getServer().getPlayerList().getPlayer(holder.getOwner());
            if (founder == null) {
                player.sendSystemMessage(Component.translatable("firmages.team.waiting", holder.getName()).withStyle(ChatFormatting.GRAY));
                FirmagesCore.LOGGER.info("One fellowship: {} joined; the Ages are held by {}, the party is founded at their next login",
                    player.getGameProfile().getName(), holder.getName().getString());
                return;
            }
        }
        Team created = create(founder);
        if (created != null) gather(mgr.getServer(), created);
    }

    private static Team create(ServerPlayer owner) {
        try {
            Team t = FTBTeamsAPI.api().getManager().createPartyTeam(owner, PARTY_NAME, null, null);
            FirmagesCore.LOGGER.info("One fellowship: {} founded the party {} ({})", owner.getGameProfile().getName(), PARTY_NAME, t.getId());
            return t;
        } catch (CommandSyntaxException | RuntimeException e) {
            FirmagesCore.LOGGER.warn("One fellowship: {} cannot found a party: {}", owner.getGameProfile().getName(), e.getMessage());
            return null;
        }
    }

    /** Every online player outside a party joins {@code party}. */
    private static void gather(MinecraftServer server, Team party) {
        TeamManager mgr = FTBTeamsAPI.api().getManager();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (mgr.getTeamForPlayer(p).map(Team::isPlayerTeam).orElse(false)) join(party, p);
        }
    }

    private static void join(Team party, ServerPlayer player) {
        if (!(party instanceof PartyTeam pt)) return;
        try {
            pt.join(player);
            FirmagesCore.LOGGER.info("One fellowship: {} joined the party {} ({})", player.getGameProfile().getName(), party.getShortName(), party.getId());
            player.sendSystemMessage(Component.translatable("firmages.team.joined", party.getName()).withStyle(ChatFormatting.GRAY));
        } catch (CommandSyntaxException | RuntimeException e) {
            FirmagesCore.LOGGER.warn("One fellowship: {} cannot join the party {}: {}", player.getGameProfile().getName(), party.getId(), e.getMessage());
        }
    }
}
