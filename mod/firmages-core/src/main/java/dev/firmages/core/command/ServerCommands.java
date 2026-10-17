package dev.firmages.core.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.config.ServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;

/**
 * {@code /stop} with a confirmation and {@code /restart} (SPEC §10.1), permission level 4 like vanilla {@code /stop},
 * on dedicated servers only (vanilla registers {@code /stop} there only). The start scripts start the server again when
 * it left the {@code RESTART} marker ({@link ServerControl}) and after a crash; a confirmed stop ends their loop.
 */
public final class ServerCommands {
    public static final String ASK = "Really stop? Run /stop confirm within 30 seconds. The start script will not restart the server; use /restart for that.";
    private static final ServerControl.Confirmations CONFIRMATIONS = new ServerControl.Confirmations(ServerControl.CONFIRM_WINDOW_MS);
    private static ServerControl.Countdown countdown; // server thread only

    private ServerCommands() {}

    public static void register(RegisterCommandsEvent event) {
        if (event.getCommandSelection() != Commands.CommandSelection.INTEGRATED) registerOn(event.getDispatcher());
    }

    /** Public for the GameTest, which merges into a dispatcher of its own (see ServerCommandGameTests). */
    public static void registerOn(CommandDispatcher<CommandSourceStack> d) {
        // RegisterCommandsEvent fires after the vanilla commands are registered. Brigadier merges a literal of the same
        // name into the existing node (CommandNode.addChild): this executor replaces vanilla's, "confirm" is added below
        // it, and the vanilla requirement (permission 4) stays; requires() here only counts without vanilla's node.
        d.register(Commands.literal("stop").requires(s -> s.hasPermission(4))
            .executes(c -> stop(c.getSource()))
            .then(Commands.literal("confirm").executes(c -> stopConfirm(c.getSource()))));
        d.register(Commands.literal("restart").requires(s -> s.hasPermission(4))
            .executes(c -> restart(c.getSource())));
    }

    private static int stop(CommandSourceStack s) {
        if (!ServerConfig.confirmStop()) return halt(s);
        CONFIRMATIONS.ask(s.getTextName(), Util.getMillis());
        s.sendSuccess(() -> Component.literal(ASK).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int stopConfirm(CommandSourceStack s) {
        if (ServerConfig.confirmStop() && !CONFIRMATIONS.confirm(s.getTextName(), Util.getMillis())) {
            s.sendFailure(Component.literal("Nothing to confirm: run /stop, then /stop confirm within 30 seconds."));
            return 0;
        }
        return halt(s);
    }

    /** Vanilla's /stop, after removing the marker of a pending /restart: a confirmed stop stays stopped. */
    private static int halt(CommandSourceStack s) {
        MinecraftServer server = s.getServer();
        countdown = null;
        try {
            ServerControl.deleteMarker(server.getServerDirectory());
        } catch (IOException e) {
            FirmagesCore.LOGGER.warn("/stop: could not delete {}: {}", ServerControl.markerPath(server.getServerDirectory()).toAbsolutePath(), e.toString());
        }
        s.sendSuccess(() -> Component.translatable("commands.stop.stopping"), true);
        server.halt(false);
        return 1;
    }

    private static int restart(CommandSourceStack s) {
        MinecraftServer server = s.getServer();
        if (countdown != null) {
            s.sendFailure(Component.literal("A restart is already counting down."));
            return 0;
        }
        try {
            ServerControl.writeMarker(server.getServerDirectory());
        } catch (IOException e) {
            s.sendFailure(Component.literal("Could not write the RESTART file: " + e));
            return 0;
        }
        countdown = new ServerControl.Countdown(Util.getMillis(), ServerControl.RESTART_SECONDS);
        s.sendSuccess(() -> Component.literal("RESTART file written; the server restarts in " + ServerControl.RESTART_SECONDS + " seconds."), true);
        tick(server); // the first announcement at once
        return 1;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (countdown != null) tick(event.getServer());
    }

    private static void tick(MinecraftServer server) {
        long now = Util.getMillis();
        countdown.announce(now).ifPresent(left -> server.getPlayerList().broadcastSystemMessage(
            Component.literal("Server restarts in " + left + (left == 1 ? " second" : " seconds")).withStyle(ChatFormatting.GOLD), false));
        if (countdown.finished(now)) {
            countdown = null;
            server.halt(false);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        countdown = null;
        CONFIRMATIONS.clear();
    }
}
