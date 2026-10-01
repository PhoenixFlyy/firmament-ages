package dev.firmages.core.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.config.ServerConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.registration.NetworkChannel;
import net.neoforged.neoforge.network.registration.NetworkPayloadSetup;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * {@code /firmages debug ...} (needs {@code debug.allowSimulate}): headless test players for the dedicated test
 * server, so the shrine runs its real code paths (block use, prayer, ProgressiveStages and FTB Teams/Quests through
 * the player list) without a client. A test player is a real {@link ServerPlayer} on an in-memory connection, placed
 * with {@code PlayerList.placeNewPlayer} like vanilla's GameTest mock player; it accepts every mod payload and logs
 * the firmages payloads, particles, titles and chat lines it receives as {@code [debug-player <name>]}. Its UUID is the offline
 * UUID of the name, so it keeps its player data across restarts. {@code debug run <name> <command>} runs a command as
 * that player (for player-only commands such as {@code ftbteams party create}). {@code debug punch} sends one left
 * click (start of mining, optionally sneaking) and aborts it; {@code debug mine} mines a block the survival way: start,
 * then stop once the destroy progress allows it, through {@code ServerPlayerGameMode.handleBlockBreakAction}.
 */
public final class DebugPlayerCommands {
    private static final SimpleCommandExceptionType DISABLED = new SimpleCommandExceptionType(
        Component.literal("Debug players are disabled; set debug.allowSimulate = true in firmages-server.toml"));
    private static final SimpleCommandExceptionType NO_PLAYER = new SimpleCommandExceptionType(Component.literal("No such debug player online"));
    /** name -> remaining prayer ticks and the heart position */
    private static final Map<String, Prayer> PRAYING = new HashMap<>();

    private record Prayer(BlockPos pos, int[] ticksLeft) {}

    /** name -> block being mined and the ticks until the stop action */
    private static final Map<String, Prayer> MINING = new HashMap<>();

    private DebugPlayerCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> debug() {
        return Commands.literal("debug")
            .then(Commands.literal("player")
                .then(Commands.literal("join").then(Commands.argument("name", StringArgumentType.word()).executes(DebugPlayerCommands::join)))
                .then(Commands.literal("leave").then(Commands.argument("name", StringArgumentType.word()).executes(DebugPlayerCommands::leave))))
            .then(Commands.literal("use").then(Commands.argument("name", StringArgumentType.word())
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(c -> use(c, false))
                    .then(Commands.literal("sneak").executes(c -> use(c, true))))))
            .then(Commands.literal("punch").then(Commands.argument("name", StringArgumentType.word())
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(c -> punch(c, false))
                    .then(Commands.literal("sneak").executes(c -> punch(c, true))))))
            .then(Commands.literal("mine").then(Commands.argument("name", StringArgumentType.word())
                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(DebugPlayerCommands::mine))))
            .then(Commands.literal("break").then(Commands.argument("name", StringArgumentType.word())
                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(DebugPlayerCommands::breakBlock))))
            .then(Commands.literal("run").then(Commands.argument("name", StringArgumentType.word())
                .then(Commands.argument("command", StringArgumentType.greedyString()).executes(DebugPlayerCommands::run))))
            .then(Commands.literal("pray").then(Commands.argument("name", StringArgumentType.word())
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 120)).executes(DebugPlayerCommands::pray)))));
    }

    private static void check() throws CommandSyntaxException {
        if (!ServerConfig.allowSimulate()) throw DISABLED.create();
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(c, "name"));
        if (p == null) throw NO_PLAYER.create();
        return p;
    }

    private static int join(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        check();
        String name = StringArgumentType.getString(c, "name");
        MinecraftServer server = c.getSource().getServer();
        if (server.getPlayerList().getPlayerByName(name) != null) {
            c.getSource().sendSuccess(() -> Component.literal(name + " is already online"), false);
            return 0;
        }
        spawn(server, c.getSource().getLevel(), name, c.getSource().getPosition());
        c.getSource().sendSuccess(() -> Component.literal("Debug player " + name + " joined"), true);
        return 1;
    }

    /**
     * A headless test player: a real {@link ServerPlayer} on an in-memory connection, in the player list, with the
     * offline UUID of {@code name}. Also used by the GameTests (vanilla's mock player always counts as creative).
     */
    public static ServerPlayer spawn(MinecraftServer server, ServerLevel level, String name, Vec3 at) {
        GameProfile profile = new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, level, profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(new Capture(name), connection);
        ChannelAttributes.setConnectionType(connection, ConnectionType.NEOFORGE);
        ChannelAttributes.setPayloadSetup(connection, new NetworkPayloadSetup(new AllProtocols()));
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.teleportTo(level, at.x, at.y, at.z, player.getYRot(), player.getXRot());
        FirmagesCore.LOGGER.info("[debug-player {}] joined as {} at {} (channel open {})", name, profile.getId(), BlockPos.containing(at).toShortString(), channel.isOpen());
        return player;
    }

    /** Disconnects a player made by {@link #spawn}. */
    public static void despawn(ServerPlayer p) {
        PRAYING.remove(p.getGameProfile().getName());
        MINING.remove(p.getGameProfile().getName());
        p.connection.onDisconnect(new DisconnectionDetails(Component.literal("debug player left")));
    }

    private static int leave(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        check();
        ServerPlayer p = player(c);
        despawn(p);
        c.getSource().sendSuccess(() -> Component.literal("Debug player " + p.getGameProfile().getName() + " left"), true);
        return 1;
    }

    private static int use(CommandContext<CommandSourceStack> c, boolean sneak) throws CommandSyntaxException {
        check();
        ServerPlayer p = player(c);
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        InteractionResult r = useOn(p, pos, sneak);
        FirmagesCore.LOGGER.info("[debug-player {}] use{} {} on {} with {}: {}", p.getGameProfile().getName(), sneak ? " (sneak)" : "",
            p.serverLevel().getBlockState(pos).getBlock(), pos.toShortString(), p.getMainHandItem(), r);
        c.getSource().sendSuccess(() -> Component.literal("use -> " + r), false);
        return 1;
    }

    private static InteractionResult useOn(ServerPlayer p, BlockPos pos, boolean sneak) {
        p.setShiftKeyDown(sneak);
        try {
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
            return p.gameMode.useItemOn(p, p.serverLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        } finally {
            p.setShiftKeyDown(false);
        }
    }

    private static void action(ServerPlayer p, BlockPos pos, ServerboundPlayerActionPacket.Action action) {
        p.gameMode.handleBlockBreakAction(pos, action, Direction.UP, p.serverLevel().getMaxBuildHeight(), 0);
    }

    /** One left click on {@code pos} (START_DESTROY_BLOCK), then ABORT so no mining stays in progress. */
    private static int punch(CommandContext<CommandSourceStack> c, boolean sneak) throws CommandSyntaxException {
        check();
        ServerPlayer p = player(c);
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        BlockState before = p.serverLevel().getBlockState(pos);
        p.setShiftKeyDown(sneak);
        try {
            action(p, pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
            action(p, pos, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK);
        } finally {
            p.setShiftKeyDown(false);
        }
        BlockState after = p.serverLevel().getBlockState(pos);
        FirmagesCore.LOGGER.info("[debug-player {}] punch{} {} with {}: {} -> {}", p.getGameProfile().getName(), sneak ? " (sneak)" : "",
            pos.toShortString(), p.getMainHandItem(), before, after);
        c.getSource().sendSuccess(() -> Component.literal("punch -> " + after), false);
        return 1;
    }

    /**
     * Survival mining of {@code pos}: START_DESTROY_BLOCK now; with a destroy progress above 0 the STOP action follows
     * once the progress reaches vanilla's 0.7 threshold (at most 30 s). Progress 0 (unbreakable) aborts at once.
     */
    private static int mine(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        check();
        ServerPlayer p = player(c);
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        String name = p.getGameProfile().getName();
        BlockState state = p.serverLevel().getBlockState(pos);
        float progress = state.getDestroyProgress(p, p.serverLevel(), pos);
        action(p, pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
        BlockState now = p.serverLevel().getBlockState(pos);
        String result;
        if (now != state) {
            result = "broken at once (progress " + progress + ")";
        } else if (progress <= 0) {
            action(p, pos, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK);
            result = "destroy progress 0, mining aborted";
        } else {
            int ticks = Math.min(600, (int) Math.ceil(0.7f / progress) + 1);
            MINING.put(name, new Prayer(pos.immutable(), new int[] {ticks}));
            result = "mining, progress " + progress + " per tick, stop in " + ticks + " ticks";
        }
        FirmagesCore.LOGGER.info("[debug-player {}] mine {} {} with {}: {}", name, pos.toShortString(), state, p.getMainHandItem(), result);
        c.getSource().sendSuccess(() -> Component.literal("mine -> " + result), false);
        return 1;
    }

    /**
     * The server side of a finished mining (what a client claiming the break triggers): {@code ServerPlayerGameMode.destroyBlock},
     * with the {@code BreakEvent} and the block's own drop logic, without the destroy-progress check.
     */
    private static int breakBlock(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        check();
        ServerPlayer p = player(c);
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        BlockState before = p.serverLevel().getBlockState(pos);
        boolean broken = p.gameMode.destroyBlock(pos);
        BlockState after = p.serverLevel().getBlockState(pos);
        FirmagesCore.LOGGER.info("[debug-player {}] break {} {} with {}: {} -> {}", p.getGameProfile().getName(), pos.toShortString(), before,
            p.getMainHandItem(), broken ? "broken" : "refused", after);
        c.getSource().sendSuccess(() -> Component.literal("break -> " + (broken ? "broken" : "refused") + ", now " + after), false);
        return 1;
    }

    private static void tickMining(MinecraftServer server) {
        Iterator<Map.Entry<String, Prayer>> it = MINING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Prayer> e = it.next();
            if (--e.getValue().ticksLeft()[0] > 0) continue;
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayerByName(e.getKey());
            if (p == null) continue;
            BlockPos pos = e.getValue().pos();
            BlockState before = p.serverLevel().getBlockState(pos);
            action(p, pos, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK);
            FirmagesCore.LOGGER.info("[debug-player {}] mine {} done: {} -> {}", e.getKey(), pos.toShortString(), before, p.serverLevel().getBlockState(pos));
        }
    }

    /** Runs a command as the player itself (player-only commands such as {@code ftbteams party ...}). */
    private static int run(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        check();
        ServerPlayer p = player(c);
        String command = StringArgumentType.getString(c, "command");
        FirmagesCore.LOGGER.info("[debug-player {}] runs /{}", p.getGameProfile().getName(), command);
        c.getSource().getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(), command);
        return 1;
    }

    private static int pray(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        check();
        ServerPlayer p = player(c);
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        int seconds = IntegerArgumentType.getInteger(c, "seconds");
        PRAYING.put(p.getGameProfile().getName(), new Prayer(pos.immutable(), new int[] {seconds * 20}));
        FirmagesCore.LOGGER.info("[debug-player {}] prays at {} for up to {} s", p.getGameProfile().getName(), pos.toShortString(), seconds);
        c.getSource().sendSuccess(() -> Component.literal(p.getGameProfile().getName() + " prays for up to " + seconds + " s"), false);
        return 1;
    }

    /** Holds sneak plus use on the heart: one use every 5 ticks, as a held use button does. */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!MINING.isEmpty()) tickMining(server);
        if (PRAYING.isEmpty()) return;
        Iterator<Map.Entry<String, Prayer>> it = PRAYING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Prayer> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayerByName(e.getKey());
            int left = --e.getValue().ticksLeft()[0];
            if (p == null || left <= 0) {
                FirmagesCore.LOGGER.info("[debug-player {}] stops praying", e.getKey());
                it.remove();
                continue;
            }
            if (left % 5 == 0) useOn(p, e.getValue().pos(), true);
        }
    }

    /** Every protocol answers "negotiated" for every channel id: the test player accepts all mod payloads. */
    private static final class AllProtocols extends AbstractMap<net.minecraft.network.ConnectionProtocol, Map<ResourceLocation, NetworkChannel>> {
        private static final Map<ResourceLocation, NetworkChannel> ALL = new AbstractMap<>() {
            @Override
            public boolean containsKey(Object key) {
                return key instanceof ResourceLocation;
            }

            @Override
            public NetworkChannel get(Object key) {
                return key instanceof ResourceLocation id ? new NetworkChannel(id, "debug") : null;
            }

            @Override
            public Set<Entry<ResourceLocation, NetworkChannel>> entrySet() {
                return Collections.emptySet();
            }
        };

        @Override
        public Map<ResourceLocation, NetworkChannel> get(Object key) {
            return ALL;
        }

        @Override
        public Map<ResourceLocation, NetworkChannel> getOrDefault(Object key, Map<ResourceLocation, NetworkChannel> def) {
            return ALL;
        }

        @Override
        public boolean containsKey(Object key) {
            return true;
        }

        @Override
        public Set<Entry<net.minecraft.network.ConnectionProtocol, Map<ResourceLocation, NetworkChannel>>> entrySet() {
            return Set.of(Map.entry(net.minecraft.network.ConnectionProtocol.PLAY, ALL), Map.entry(net.minecraft.network.ConnectionProtocol.CONFIGURATION, ALL));
        }
    }

    /** Drops every outbound packet; logs the firmages payloads, particles, titles and chat lines. */
    private static final class Capture extends ChannelOutboundHandlerAdapter {
        private final String name;
        private long lastOverlay;

        Capture(String name) {
            this.name = name;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            try {
                log(msg);
            } catch (RuntimeException e) {
                FirmagesCore.LOGGER.warn("[debug-player {}] cannot log {}: {}", name, msg.getClass().getSimpleName(), e.toString());
            } finally {
                ReferenceCountUtil.release(msg);
                promise.setSuccess();
            }
        }

        private void log(Object msg) {
            if (msg instanceof ClientboundBundlePacket bundle) {
                for (Packet<?> p : bundle.subPackets()) log(p);
            } else if (msg instanceof ClientboundCustomPayloadPacket cp && cp.payload().type().id().getNamespace().equals(FirmagesCore.MOD_ID)) {
                FirmagesCore.LOGGER.info("[debug-player {}] payload {}: {}", name, cp.payload().type().id(), cp.payload());
            } else if (msg instanceof ClientboundLevelParticlesPacket pp) {
                FirmagesCore.LOGGER.info("[debug-player {}] particles {} x{} at {} {} {}", name, BuiltInRegistries.PARTICLE_TYPE.getKey(pp.getParticle().getType()),
                    pp.getCount(), Math.floor(pp.getX()), Math.floor(pp.getY()), Math.floor(pp.getZ()));
            } else if (msg instanceof ClientboundSetTitleTextPacket t) {
                FirmagesCore.LOGGER.info("[debug-player {}] title: {}", name, t.text().getString());
            } else if (msg instanceof ClientboundSetSubtitleTextPacket t) {
                FirmagesCore.LOGGER.info("[debug-player {}] subtitle: {}", name, t.text().getString());
            } else if (msg instanceof ClientboundSystemChatPacket chat) {
                if (!chat.overlay()) {
                    FirmagesCore.LOGGER.info("[debug-player {}] chat: {}", name, chat.content().getString());
                } else if (System.currentTimeMillis() - lastOverlay >= 1000) {
                    lastOverlay = System.currentTimeMillis();
                    FirmagesCore.LOGGER.info("[debug-player {}] actionbar: {}", name, chat.content().getString());
                }
            }
        }
    }
}
