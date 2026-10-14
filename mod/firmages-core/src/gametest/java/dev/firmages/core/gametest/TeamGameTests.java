package dev.firmages.core.gametest;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.stage.StageManager;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.ReloadScheduler;
import dev.firmages.core.command.DebugPlayerCommands;
import dev.firmages.core.compat.ftbteams.FtbTeamsCompat;
import dev.firmages.core.config.ServerConfig;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Level G tests of the team carry-over (SPEC §2.3) with FTB Teams in the run: ProgressiveStages starts a founded party
 * without the founder's stages, firmages-core gives the party the world's Ages. Each test sits in its own batch and
 * leaves no party behind. The other batches run with {@code team.autoJoin} off, so their debug players keep their own
 * player teams; these tests call the login path ({@link FtbTeamsCompat#ensureFellowship}) themselves.
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class TeamGameTests {
    private static final StageId AGE_9 = StageId.of("age_9");

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(TeamGameTests.class);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (event.getServer() instanceof GameTestServer) ServerConfig.TEAM_AUTO_JOIN.set(false);
    }

    private static boolean teams() {
        return ModList.get().isLoaded(FtbTeamsCompat.MOD_ID) && ModList.get().isLoaded("progressivestages") && FTBTeamsAPI.api().isManagerLoaded();
    }

    private static boolean reloadIdle(MinecraftServer s) {
        return AgeService.status(s).phase() == ReloadScheduler.Phase.IDLE;
    }

    private static Team teamOf(ServerPlayer p) {
        return FTBTeamsAPI.api().getManager().getTeamForPlayer(p).orElseThrow();
    }

    private static boolean partyHolds(UUID party, StageId stage) {
        return StageManager.getInstance().getStages(party).contains(stage);
    }

    private static ServerPlayer spawn(GameTestHelper h, String name) {
        ServerPlayer p = DebugPlayerCommands.spawn(h.getLevel().getServer(), h.getLevel(), name, Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 1))));
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    private static void grantAge9(ServerPlayer p) {
        if (!ProgressiveStagesAPI.grantStage(p, AGE_9, StageCause.API)) ProgressiveStagesAPI.grantStageBypass(p, AGE_9, StageCause.API);
    }

    /** Disbands the party of {@code p} (its members return to their player teams), revokes age_9 and despawns the players. */
    private static void cleanUp(MinecraftServer server, ServerPlayer... players) {
        for (ServerPlayer p : players) {
            if (p == null) continue;
            Team t = FTBTeamsAPI.api().getManager().getTeamForPlayer(p).orElse(null);
            if (t instanceof PartyTeam pt) {
                try {
                    pt.forceDisband(server.createCommandSourceStack());
                } catch (Exception e) {
                    FirmagesCore.LOGGER.warn("GameTest teams: cannot disband {}: {}", pt.getShortName(), e.toString());
                }
            }
        }
        for (ServerPlayer p : players) {
            if (p == null) continue;
            if (ProgressiveStagesAPI.hasStage(p, AGE_9)) ProgressiveStagesAPI.revokeStage(p, AGE_9, StageCause.API);
        }
        for (ServerPlayer p : players) if (p != null) DebugPlayerCommands.despawn(p);
    }

    /**
     * A player whose own team holds age_9 founds the party (the login path of team.autoJoin): ProgressiveStages starts
     * the party without age_9, one tick later the carry-over grants it, and the founder keeps the Age. AgeState never
     * loses age_9, and the founder's old player team no longer counts as a second team.
     */
    @GameTest(template = "empty", batch = "firmages_9_team_found", timeoutTicks = 600)
    public static void foundedPartyKeepsTheAges(GameTestHelper h) {
        if (!teams()) {
            h.succeed();
            return;
        }
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer[] p = new ServerPlayer[1];
        UUID[] ids = new UUID[2]; // own player team, party
        h.startSequence()
            .thenExecute(() -> {
                p[0] = spawn(h, "gt_founder");
                h.assertTrue(FTBTeamsAPI.api().getManager().getTeams().stream().noneMatch(Team::isPartyTeam), "a party is left from another test");
                grantAge9(p[0]);
            })
            .thenWaitUntil(() -> h.assertTrue(AgeService.state(server).snapshot().isUnlocked(AgeId.AGE_9) && reloadIdle(server), "age_9 grant and its swap"))
            .thenExecute(() -> {
                Team own = teamOf(p[0]);
                ids[0] = own.getId();
                h.assertTrue(own.isPlayerTeam() && partyHolds(own.getId(), AGE_9), "the founder's own team holds age_9");
                FtbTeamsCompat.ensureFellowship(p[0], AgeService::ageCount);
                Team party = teamOf(p[0]);
                ids[1] = party.getId();
                h.assertTrue(party.isPartyTeam() && FtbTeamsCompat.PARTY_NAME.equals(party.getName().getString()), "the founder is in the party Firmament, not " + party.getName().getString());
                FirmagesCore.LOGGER.info("GameTest foundedPartyKeepsTheAges: party stages right after founding {}", StageManager.getInstance().getStages(party.getId()));
            })
            .thenWaitUntil(() -> h.assertTrue(partyHolds(ids[1], AGE_9), "the party holds age_9 (carry-over)"))
            .thenExecute(() -> {
                h.assertTrue(ProgressiveStagesAPI.hasStage(p[0], AGE_9), "the founder keeps age_9");
                h.assertTrue(AgeService.state(server).snapshot().isUnlocked(AgeId.AGE_9), "AgeState keeps age_9");
                h.assertTrue(FtbTeamsCompat.dormant(ids[0]), "the founder's player team is dormant");
                h.assertTrue(!AgeService.teamsWithAges().contains(ids[0]), "the dormant player team is not counted as a team with Ages");
                FirmagesCore.LOGGER.info("GameTest foundedPartyKeepsTheAges: party {} stages {}", ids[1], StageManager.getInstance().getStages(ids[1]));
                cleanUp(server, p[0]);
            })
            .thenWaitUntil(() -> h.assertTrue(!AgeService.state(server).snapshot().isUnlocked(AgeId.AGE_9) && reloadIdle(server), "age_9 revoke and its swap"))
            .thenSucceed();
    }

    /**
     * A new player joins the party that holds the Ages (the login path of team.autoJoin) and holds them at once:
     * ProgressiveStages shares the party's stages, the carry-over on entering adds any Age the party lacked.
     */
    @GameTest(template = "empty", batch = "firmages_9_team_join", timeoutTicks = 600)
    public static void newcomerSeesTheAges(GameTestHelper h) {
        if (!teams()) {
            h.succeed();
            return;
        }
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer[] p = new ServerPlayer[2]; // holder, newcomer
        UUID[] party = new UUID[1];
        h.startSequence()
            .thenExecute(() -> {
                p[0] = spawn(h, "gt_keeper");
                grantAge9(p[0]);
            })
            .thenWaitUntil(() -> h.assertTrue(AgeService.state(server).snapshot().isUnlocked(AgeId.AGE_9) && reloadIdle(server), "age_9 grant and its swap"))
            .thenExecute(() -> FtbTeamsCompat.ensureFellowship(p[0], AgeService::ageCount))
            .thenWaitUntil(() -> {
                Team t = teamOf(p[0]);
                h.assertTrue(t.isPartyTeam() && partyHolds(t.getId(), AGE_9), "the keeper's party holds age_9");
            })
            .thenExecute(() -> {
                party[0] = teamOf(p[0]).getId();
                p[1] = spawn(h, "gt_newcomer");
                h.assertTrue(!ProgressiveStagesAPI.hasStage(p[1], AGE_9), "the newcomer starts without age_9");
                FtbTeamsCompat.ensureFellowship(p[1], AgeService::ageCount);
                h.assertTrue(teamOf(p[1]).getId().equals(party[0]), "the newcomer joined the party");
            })
            .thenWaitUntil(() -> h.assertTrue(ProgressiveStagesAPI.hasStage(p[1], AGE_9), "the newcomer holds age_9"))
            .thenExecute(() -> {
                h.assertTrue(ProgressiveStagesAPI.hasStage(p[0], AGE_9), "the keeper keeps age_9");
                cleanUp(server, p[0], p[1]);
            })
            .thenWaitUntil(() -> h.assertTrue(!AgeService.state(server).snapshot().isUnlocked(AgeId.AGE_9) && reloadIdle(server), "age_9 revoke and its swap"))
            .thenSucceed();
    }

    /**
     * The login repair path: a party founded before the world reached age_9 lacks it (no carry-over runs when AgeState
     * grows through another team, only when a player enters a party or logs in). The next login of a party member
     * ({@link FtbTeamsCompat#onLogin}) runs the carry-over again and the party gets age_9 from AgeState.
     */
    @GameTest(template = "empty", batch = "firmages_9_team_repair", timeoutTicks = 600)
    public static void loginRepairsTheParty(GameTestHelper h) {
        if (!teams()) {
            h.succeed();
            return;
        }
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer[] p = new ServerPlayer[2]; // party member, holder outside the party
        UUID[] party = new UUID[1];
        h.startSequence()
            .thenExecute(() -> {
                p[0] = spawn(h, "gt_member");
                h.assertTrue(FTBTeamsAPI.api().getManager().getTeams().stream().noneMatch(Team::isPartyTeam), "a party is left from another test");
                FtbTeamsCompat.ensureFellowship(p[0], AgeService::ageCount);
                Team t = teamOf(p[0]);
                h.assertTrue(t.isPartyTeam(), "the member founded a party");
                party[0] = t.getId();
                p[1] = spawn(h, "gt_holder");
            })
            // the real login hook of both spawns runs one tick later (AgeState has no age_9 yet, so it grants nothing)
            .thenIdle(3)
            .thenExecute(() -> {
                h.assertTrue(teamOf(p[1]).isPlayerTeam(), "the holder stays outside the party");
                grantAge9(p[1]);
            })
            .thenWaitUntil(() -> h.assertTrue(AgeService.state(server).snapshot().isUnlocked(AgeId.AGE_9) && reloadIdle(server), "age_9 grant and its swap"))
            .thenExecute(() -> {
                h.assertTrue(!partyHolds(party[0], AGE_9), "the party lacks age_9 before the login repair");
                FtbTeamsCompat.onLogin(p[0], false, AgeService::ageCount);
            })
            .thenWaitUntil(() -> h.assertTrue(partyHolds(party[0], AGE_9) && ProgressiveStagesAPI.hasStage(p[0], AGE_9), "the login repair gave the party age_9"))
            .thenExecute(() -> cleanUp(server, p[0], p[1]))
            .thenWaitUntil(() -> h.assertTrue(!AgeService.state(server).snapshot().isUnlocked(AgeId.AGE_9) && reloadIdle(server), "age_9 revoke and its swap"))
            .thenSucceed();
    }

    /**
     * Only the global mirror counts: the carry-over grants what AgeState holds, never what a dormant player team still
     * stores. A founder's party loses age_9 (admin revoke, AgeState drops it); the founder's old player team keeps
     * age_9 ({@code persist_stages_on_leave}), and a login repair does not bring it back.
     */
    @GameTest(template = "empty", batch = "firmages_9_team_mirror", timeoutTicks = 600)
    public static void carryOverTakesOnlyTheMirror(GameTestHelper h) {
        if (!teams()) {
            h.succeed();
            return;
        }
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer[] p = new ServerPlayer[1];
        UUID[] ids = new UUID[2]; // own player team, party
        h.startSequence()
            .thenExecute(() -> {
                p[0] = spawn(h, "gt_revoked");
                h.assertTrue(FTBTeamsAPI.api().getManager().getTeams().stream().noneMatch(Team::isPartyTeam), "a party is left from another test");
                grantAge9(p[0]);
            })
            .thenWaitUntil(() -> h.assertTrue(AgeService.state(server).snapshot().isUnlocked(AgeId.AGE_9) && reloadIdle(server), "age_9 grant and its swap"))
            .thenExecute(() -> {
                ids[0] = teamOf(p[0]).getId();
                FtbTeamsCompat.ensureFellowship(p[0], AgeService::ageCount);
                ids[1] = teamOf(p[0]).getId();
                h.assertTrue(!ids[1].equals(ids[0]), "the founder is in a party");
            })
            .thenWaitUntil(() -> h.assertTrue(partyHolds(ids[1], AGE_9), "the party holds age_9 (carry-over)"))
            .thenExecute(() -> ProgressiveStagesAPI.revokeStage(p[0], AGE_9, StageCause.API))
            .thenWaitUntil(() -> h.assertTrue(!AgeService.state(server).snapshot().isUnlocked(AgeId.AGE_9) && reloadIdle(server), "age_9 revoke and its swap"))
            .thenExecute(() -> {
                h.assertTrue(partyHolds(ids[0], AGE_9) && FtbTeamsCompat.dormant(ids[0]), "the dormant player team still stores age_9");
                FtbTeamsCompat.onLogin(p[0], false, AgeService::ageCount);
            })
            .thenIdle(20)
            .thenExecute(() -> {
                h.assertTrue(!partyHolds(ids[1], AGE_9), "the login repair did not take age_9 from the dormant team");
                h.assertTrue(!AgeService.state(server).snapshot().isUnlocked(AgeId.AGE_9), "AgeState stays without age_9");
                StageManager.getInstance().revokeStageFromTeam(ids[0], AGE_9);
                cleanUp(server, p[0]);
            })
            .thenWaitUntil(() -> h.assertTrue(!partyHolds(ids[0], AGE_9) && !AgeService.state(server).snapshot().isUnlocked(AgeId.AGE_9)
                && reloadIdle(server), "the old player team released age_9"))
            .thenSucceed();
    }
}
