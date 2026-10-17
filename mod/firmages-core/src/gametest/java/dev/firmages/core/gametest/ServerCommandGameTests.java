package dev.firmages.core.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.command.ServerCommands;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.StopCommand;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Level G tests of {@code /stop confirm} and {@code /restart} (SPEC §10.1). The GameTest server builds its commands as
 * an integrated server after any datapack reload ({@code isDedicatedServer()} is false), so vanilla {@code /stop} is not
 * reliably there; the tests merge {@link ServerCommands} into a dispatcher of their own that holds vanilla's
 * {@link StopCommand}, the order of a dedicated server (RegisterCommandsEvent after the vanilla commands). They never
 * run {@code /stop confirm} after a {@code /stop}, nor {@code /restart}: both would halt the GameTest server.
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class ServerCommandGameTests {
    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(ServerCommandGameTests.class);
    }

    private static CommandDispatcher<CommandSourceStack> dedicatedStop() {
        CommandDispatcher<CommandSourceStack> d = new CommandDispatcher<>();
        StopCommand.register(d);
        ServerCommands.registerOn(d);
        return d;
    }

    /** The merged /stop node keeps permission 4 and gains "confirm"; /restart exists with permission 4. */
    @GameTest(template = "empty", batch = "firmages_1_server_commands")
    public static void stopAndRestartNeedLevelFour(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        CommandNode<CommandSourceStack> root = dedicatedStop().getRoot();
        CommandNode<CommandSourceStack> stop = root.getChild("stop");
        CommandNode<CommandSourceStack> restart = root.getChild("restart");
        h.assertTrue(root.getChildren().size() == 2, "one /stop node (merged), one /restart node");
        h.assertTrue(stop != null && stop.getChild("confirm") != null, "/stop confirm registered");
        h.assertTrue(restart != null && restart.getCommand() != null, "/restart registered");
        CommandSourceStack op2 = source(server, "firmages-gametest-op2", 2, new ArrayList<>());
        CommandSourceStack op4 = source(server, "firmages-gametest-op4", 4, new ArrayList<>());
        h.assertFalse(stop.canUse(op2) || restart.canUse(op2), "level 2 may use neither");
        h.assertTrue(stop.canUse(op4) && restart.canUse(op4), "level 4 may use both");
        h.succeed();
    }

    /**
     * /stop alone only asks (vanilla's executor would halt the server: it is replaced); /stop confirm without a /stop of
     * the same source is refused; the server keeps running.
     */
    @GameTest(template = "empty", batch = "firmages_1_server_commands")
    public static void stopAsksForConfirmation(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        CommandDispatcher<CommandSourceStack> d = dedicatedStop();
        List<String> said = new ArrayList<>();
        try {
            int unasked = d.execute("stop confirm", source(server, "firmages-gametest-unasked", 4, said));
            h.assertTrue(unasked == 0 && server.isRunning(), "stop confirm without /stop refused");
            h.assertTrue(said.size() == 1 && said.get(0).startsWith("Nothing to confirm"), "refusal text, was " + said);
            said.clear();
            int asked = d.execute("stop", source(server, "firmages-gametest-asker", 4, said));
            h.assertTrue(asked == 1 && server.isRunning(), "/stop only asks");
            h.assertTrue(said.equals(List.of(ServerCommands.ASK)), "question text, was " + said);
        } catch (Exception e) {
            h.fail("command failed: " + e);
        }
        h.succeed();
    }

    private static CommandSourceStack source(MinecraftServer server, String name, int level, List<String> said) {
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
        return new CommandSourceStack(sink, Vec3.ZERO, Vec2.ZERO, server.overworld(), level, name, Component.literal(name), server, null);
    }
}
