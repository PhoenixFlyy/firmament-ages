package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.ReloadScheduler;
import dev.firmages.core.ceremony.CeremonyEcho;
import dev.firmages.core.command.DebugPlayerCommands;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.net.AgeTransitionPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Level G test of the echo of the ceremony (SPEC §8.2) on a headless player whose data is saved and loaded between
 * joins like a real player's: the first join only records the current Age; an Age granted while the player is
 * offline plays once, as one SHORT payload plus one chat line, {@link CeremonyEcho#DELAY_TICKS} after the next join;
 * a further join plays nothing; the attachment holds the newest Age.
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class CeremonyGameTests {
    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(CeremonyGameTests.class);
    }

    private static void ok(GameTestHelper h, boolean cond, String what) {
        if (!cond) FirmagesCore.LOGGER.error("Ceremony GameTest assertion failed: {}", what);
        h.assertTrue(cond, what);
    }

    private static boolean reloadIdle(MinecraftServer s) {
        return AgeService.status(s).phase() == ReloadScheduler.Phase.IDLE;
    }

    private static List<AgeTransitionPayload> ceremonies(String name) {
        List<CustomPacketPayload> got = DebugPlayerCommands.recorded(name);
        return got.stream().filter(AgeTransitionPayload.class::isInstance).map(AgeTransitionPayload.class::cast).toList();
    }

    private static long missedLines(String name) {
        return DebugPlayerCommands.recordedChat(name).stream()
            .filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals("firmages.ceremony.missed")).count();
    }

    private static ServerPlayer join(GameTestHelper h, String name) {
        DebugPlayerCommands.record(name);
        return DebugPlayerCommands.spawn(h.getLevel().getServer(), h.getLevel(), name, Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 1))));
    }

    @GameTest(template = "empty", batch = "firmages_6_echo", timeoutTicks = 2400)
    public static void missedCeremonyOnce(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String name = "gt_echo";
        ServerPlayer[] p = new ServerPlayer[1];
        h.startSequence()
            .thenExecute(() -> {
                ServerConfig.DEBUG_ALLOW_SIMULATE.set(true);
                for (int i = AgeId.all().size() - 1; i >= 2; i--) AgeService.simulate(server, AgeId.all().get(i), false);
                AgeService.simulate(server, AgeId.AGE_0, true);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_0 setup still running"))
            .thenExecute(() -> {
                p[0] = join(h, name);
                ok(h, "age_0".equals(p[0].getData(CeremonyEcho.SEEN_AGE.get())) && CeremonyEcho.pendingCount() == 0,
                    "first join records the current Age and schedules nothing, seen " + p[0].getData(CeremonyEcho.SEEN_AGE.get()));
                DebugPlayerCommands.despawn(p[0]);
                // two Ages dawn while the player is away
                AgeService.simulate(server, AgeId.AGE_1, true);
                AgeService.simulate(server, AgeId.AGE_2, true);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the grants still running"))
            .thenExecute(() -> {
                p[0] = join(h, name);
                ok(h, "age_0".equals(p[0].getData(CeremonyEcho.SEEN_AGE.get())) && CeremonyEcho.pendingCount() == 1,
                    "rejoin: the saved attachment says age_0, one echo scheduled");
            })
            .thenIdle(CeremonyEcho.DELAY_TICKS / 2)
            .thenExecute(() -> ok(h, ceremonies(name).isEmpty(), "nothing before the delay"))
            .thenIdle(CeremonyEcho.DELAY_TICKS / 2 + 10)
            .thenExecute(() -> {
                List<AgeTransitionPayload> got = ceremonies(name);
                ok(h, got.size() == 1 && got.get(0).stage().equals("age_2") && !got.get(0).full(), "one SHORT ceremony of the newest Age, got " + got);
                ok(h, missedLines(name) == 1, "one chat line, got " + DebugPlayerCommands.recordedChat(name).stream().map(Component::getString).toList());
                Component line = DebugPlayerCommands.recordedChat(name).stream()
                    .filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals("firmages.ceremony.missed")).findFirst().orElseThrow();
                Object arg = ((TranslatableContents) line.getContents()).getArgs()[0];
                ok(h, arg instanceof Component c && c.getSiblings().size() == 3, "the line names age_1 and age_2: " + arg);
                ok(h, "age_2".equals(p[0].getData(CeremonyEcho.SEEN_AGE.get())), "seen age_2 now");
                DebugPlayerCommands.despawn(p[0]);
            })
            .thenExecute(() -> p[0] = join(h, name))
            .thenIdle(CeremonyEcho.DELAY_TICKS + 10)
            .thenExecute(() -> {
                ok(h, ceremonies(name).isEmpty() && missedLines(name) == 0, "a further join plays nothing");
                // a ceremony that reaches the player online counts as seen
                AgeService.simulate(server, AgeId.AGE_3, true);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_3 grant still running"))
            .thenExecute(() -> {
                ok(h, ceremonies(name).size() == 1 && "age_3".equals(p[0].getData(CeremonyEcho.SEEN_AGE.get())),
                    "online: the SHORT ceremony itself marks age_3 seen, got " + ceremonies(name));
                DebugPlayerCommands.despawn(p[0]);
                for (int i = AgeId.all().size() - 1; i >= 1; i--) AgeService.simulate(server, AgeId.all().get(i), false);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the clean-up still running"))
            .thenExecute(() -> ServerConfig.DEBUG_ALLOW_SIMULATE.set(false))
            .thenSucceed();
    }
}
