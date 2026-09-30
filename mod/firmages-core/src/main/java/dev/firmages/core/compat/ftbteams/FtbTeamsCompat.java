package dev.firmages.core.compat.ftbteams;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;

import java.util.List;
import java.util.UUID;

/** Reads the FTB team ids. Only loaded when {@code ftbteams} is present. */
public final class FtbTeamsCompat {
    public static final String MOD_ID = "ftbteams";

    private FtbTeamsCompat() {}

    /** Ids of all FTB teams (player and party teams); empty while the manager is not loaded. */
    public static List<UUID> teamIds() {
        if (!FTBTeamsAPI.api().isManagerLoaded()) return List.of();
        return FTBTeamsAPI.api().getManager().getTeams().stream().map(Team::getId).toList();
    }
}
