package dev.firmages.core.compat.progressivestages;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.origin.OriginSavedData;
import dev.firmages.core.origin.OriginService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Gives a team the world's Ages (SPEC §2.3, "Team carry-over"). ProgressiveStages 3.0.6 keeps stages per team and
 * starts a new FTB party with its starting stages only: a player who founds or joins a party loses the view of the Ages
 * their old team held (pack server, 0.9.0 review: 22 stages before founding, 0 after). The world's Ages are AgeState
 * (the mirror), the union of every team's Ages; {@link #apply} grants each one the team lacks through ProgressiveStages,
 * the same path as a shrine grant, so ProgressiveStages sends its lock and stage sync (coalesced to one per player at
 * the end of the tick, §3.5) and the pack's scripts derive the mob ladder and the helper stages. AgeState does not
 * change, so no ceremony, no Age swap and no Firmament Night follows (the night script counts Ages per world).
 */
public final class TeamCarryOver {
    private TeamCarryOver() {}

    /** The stages a team must hold: every unlocked Age in Age order, then {@code finale_won} once the final boss fell. */
    public static List<String> stagesFor(Set<AgeId> unlocked, boolean finaleWon) {
        List<String> out = new ArrayList<>();
        for (AgeId a : AgeId.all()) if (unlocked.contains(a)) out.add(a.id());
        if (finaleWon) out.add(OriginService.FINALE_STAGE);
        return out;
    }

    /**
     * Grants {@code member}'s team (in {@code ftb_teams} mode the party) every stage of {@link #stagesFor} it lacks.
     * Stages ProgressiveStages does not define are skipped. @return the stages granted
     */
    public static List<String> apply(ServerPlayer member, String why) {
        MinecraftServer s = member.server;
        List<String> wanted = stagesFor(AgeService.state(s).snapshot().unlocked(), OriginSavedData.get(s).won());
        List<String> granted = new ArrayList<>();
        for (String stage : wanted) {
            StageId id = StageId.of(stage);
            try {
                if (!ProgressiveStagesAPI.stageExists(id) || ProgressiveStagesAPI.hasStage(member, id)) continue;
                if (!ProgressiveStagesAPI.grantStage(member, id, StageCause.API) && !ProgressiveStagesAPI.hasStage(member, id)) {
                    ProgressiveStagesAPI.grantStageBypass(member, id, StageCause.API);
                }
                if (ProgressiveStagesAPI.hasStage(member, id)) granted.add(stage);
                else FirmagesCore.LOGGER.warn("Team carry-over ({}): ProgressiveStages refused {} for {}", why, stage, member.getGameProfile().getName());
            } catch (RuntimeException | LinkageError e) {
                FirmagesCore.LOGGER.error("Team carry-over ({}): cannot grant {} to {}", why, stage, member.getGameProfile().getName(), e);
            }
        }
        if (!granted.isEmpty()) {
            FirmagesCore.LOGGER.info("Team carry-over ({}): granted {} to the team of {}", why, granted, member.getGameProfile().getName());
        }
        return granted;
    }
}
