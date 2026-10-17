package dev.firmages.core.gametest;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageId;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.command.DebugPlayerCommands;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Level G tests of {@code /firmages stage} and of {@code /firmages} without arguments (SPEC §10, §10.2, round G2), run
 * through the server's own dispatcher with a permission-2 source, on a headless player and the GameTest-only stages
 * {@code gt_cmd_1} and {@code gt_cmd_2} (which depends on {@code gt_cmd_1}). The Age path is not run here: an Age grant
 * swaps the GameTest server's Age state under the other tests ({@code StageRulesTest} covers its order rules).
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class StageCommandGameTests {
    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(StageCommandGameTests.class);
    }

    /** grant (dependency refusal, repeat bypass, already held), list, revoke, clear and an unknown stage, as /stage. */
    @GameTest(template = "empty", batch = "firmages_1_stage_commands", timeoutTicks = 100)
    public static void stageCommandsFollowProgressiveStages(GameTestHelper h) {
        if (!ModList.get().isLoaded("progressivestages")) {
            h.succeed();
            return;
        }
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer p = DebugPlayerCommands.spawn(server, h.getLevel(), "gt_stagecmd", Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 1))));
        List<String> said = new ArrayList<>();
        CommandSourceStack op = source(server, said);
        StageId one = StageId.of("gt_cmd_1");
        StageId two = StageId.of("gt_cmd_2");
        try {
            h.assertTrue(ProgressiveStagesAPI.stageExists(one) && ProgressiveStagesAPI.stageExists(two), "the GameTest stages gt_cmd_1 and gt_cmd_2 are loaded");
            int first = run(server, "firmages stage grant gt_stagecmd gt_cmd_2", op);
            h.assertTrue(first == 0 && !ProgressiveStagesAPI.hasStage(p, two), "a missing dependency refuses the first grant");
            h.assertTrue(said.stream().anyMatch(s -> s.contains("missing gt_cmd_1") && s.contains("again within 10 seconds")), "refusal names the dependency, was " + said);
            int second = run(server, "firmages stage grant gt_stagecmd gt_cmd_2", op);
            h.assertTrue(second == 1 && ProgressiveStagesAPI.hasStage(p, two) && !ProgressiveStagesAPI.hasStage(p, one),
                "the repeat grants gt_cmd_2 without its dependency");
            h.assertTrue(run(server, "firmages stage grant gt_stagecmd gt_cmd_1", op) == 1 && ProgressiveStagesAPI.hasStage(p, one), "gt_cmd_1 granted");
            said.clear();
            h.assertTrue(run(server, "firmages stage grant gt_stagecmd gt_cmd_1", op) == 0, "a held stage is refused");
            h.assertTrue(said.stream().anyMatch(s -> s.contains("already has stage gt_cmd_1")), "already-held text, was " + said);
            said.clear();
            int listed = run(server, "firmages stage list gt_stagecmd", op);
            h.assertTrue(listed == ProgressiveStagesAPI.getStages(p).size(), "list returns the held count");
            h.assertTrue(said.stream().anyMatch(s -> s.contains("✔ gt_cmd_1")) && said.stream().anyMatch(s -> s.contains("✔ gt_cmd_2")),
                "list ticks both stages, was " + said);
            h.assertTrue(run(server, "firmages stage revoke gt_stagecmd gt_cmd_2", op) == 1 && !ProgressiveStagesAPI.hasStage(p, two), "gt_cmd_2 revoked");
            h.assertTrue(run(server, "firmages stage revoke gt_stagecmd gt_cmd_2", op) == 0, "revoking a stage not held is refused");
            run(server, "firmages stage clear gt_stagecmd", op);
            h.assertTrue(ProgressiveStagesAPI.getStages(p).isEmpty(), "clear leaves no stage, held " + ProgressiveStagesAPI.getStages(p));
            said.clear();
            h.assertTrue(run(server, "firmages stage grant gt_stagecmd gt_no_such_stage", op) == 0, "an unknown stage is refused");
            h.assertTrue(said.stream().anyMatch(s -> s.contains("Unknown stage 'gt_no_such_stage'")), "unknown-stage text, was " + said);
        } catch (CommandSyntaxException e) {
            h.fail("command failed: " + e.getMessage());
        } finally {
            DebugPlayerCommands.despawn(p);
        }
        h.succeed();
    }

    /** /firmages alone lists every subcommand, the new stage and locate trees among them. */
    @GameTest(template = "empty", batch = "firmages_1_stage_commands")
    public static void firmagesAloneListsSubcommands(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        List<String> said = new ArrayList<>();
        try {
            CommandSourceStack src = source(server, said);
            int n = run(server, "firmages", src);
            long children = server.getCommands().getDispatcher().getRoot().getChild("firmages").getChildren().stream().filter(c -> c.canUse(src)).count();
            h.assertTrue(n == children, n + " lines for " + children + " subcommands");
            for (String sub : List.of("/firmages stage", "/firmages locate", "/firmages ages", "/firmages selftest")) {
                h.assertTrue(said.stream().anyMatch(s -> s.startsWith(sub)), sub + " listed, was " + said);
            }
        } catch (CommandSyntaxException e) {
            h.fail("command failed: " + e.getMessage());
        }
        h.succeed();
    }

    private static int run(MinecraftServer server, String command, CommandSourceStack src) throws CommandSyntaxException {
        return server.getCommands().getDispatcher().execute(command, src);
    }

    private static CommandSourceStack source(MinecraftServer server, List<String> said) {
        CommandSource sink = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                said.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        return new CommandSourceStack(sink, Vec3.ZERO, Vec2.ZERO, server.overworld(), 2, "firmages-gametest-stage", Component.literal("stage"), server, null);
    }
}
