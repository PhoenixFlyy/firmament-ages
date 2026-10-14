package dev.firmages.core.compat.ftbteams;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.firmages.core.FirmagesCore;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;

/** Reads the FTB team ids and keeps every player in the one party that holds the Ages. Only loaded when {@code ftbteams} is present. */
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

    /**
     * One fellowship per server (SPEC §2.3, {@code team.autoJoin}): a player outside a party joins the party that
     * holds the most Ages. A server without a party gets one only while no team holds an Age beyond Dawn: the player
     * founds it and every online player outside a party joins. ProgressiveStages 3.0.6 does not move a team's stages
     * into a party its owner founds (pack server, 0.9.0 review: 22 stages before, 0 after), so a player team that
     * already holds Ages is never turned into a party; the other players are told who holds the Ages instead.
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
        if (ages.applyAsInt(holder.getId()) > 0) {
            if (holder != own) {
                player.sendSystemMessage(Component.translatable("firmages.team.solo", holder.getName()).withStyle(ChatFormatting.GRAY));
                FirmagesCore.LOGGER.warn("One fellowship: {} joined, but the Ages are held by the player team {} outside a party; "
                    + "no party is founded automatically for a team with Ages", player.getGameProfile().getName(), holder.getName().getString());
            }
            return;
        }
        Team created = create(player);
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
