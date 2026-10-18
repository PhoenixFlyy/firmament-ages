package dev.firmages.core.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.firmages.core.origin.OriginAccess;
import dev.firmages.core.origin.OriginArena;
import dev.firmages.core.origin.OriginEvent;
import dev.firmages.core.origin.OriginRegistry;
import dev.firmages.core.origin.OriginSavedData;
import dev.firmages.core.origin.OriginService;
import dev.firmages.core.reactor.ReactorControllerBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * {@code /firmages origin tp [players [force]] | gathering | fight | kill [boss] | status | reset | rebuild | altars reset|complete} and
 * {@code /firmages reactor status [pos]} (SPEC §10, operators only like every {@code /firmages} command).
 * {@code origin tp} uses the normal teleport path, so the age_9 dimension locks apply: a player without
 * {@code age_9} (and outside creative) stays where they are; {@code force} gives the players a forced visit that
 * passes both locks until they leave The Origin ({@link OriginAccess}). {@code gathering} starts the Gathering at
 * once, wherever the players stand and regardless of the cooldown; {@code fight} asks for the final boss at once,
 * without the Gathering; {@code kill} kills every hostile but the final boss, {@code kill boss} the final boss
 * itself, whose death then wins the finale the normal way (test shortcuts).
 */
final class OriginCommands {
    private static final SimpleCommandExceptionType NO_ORIGIN = new SimpleCommandExceptionType(
        Component.literal("The Origin dimension (firmages:origin) is not loaded"));

    private OriginCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> origin() {
        return Commands.literal("origin")
            .then(Commands.literal("tp")
                .executes(c -> tp(c, List.of(c.getSource().getPlayerOrException()), false))
                .then(Commands.argument("players", EntityArgument.players()).executes(c -> tp(c, EntityArgument.getPlayers(c, "players"), false))
                    .then(Commands.literal("force").executes(c -> tp(c, EntityArgument.getPlayers(c, "players"), true)))))
            .then(Commands.literal("gathering").executes(OriginCommands::gathering))
            .then(Commands.literal("fight").executes(OriginCommands::fight))
            .then(Commands.literal("kill").executes(OriginCommands::killHostiles)
                .then(Commands.literal("boss").executes(OriginCommands::killBoss)))
            .then(Commands.literal("status").executes(OriginCommands::status))
            .then(Commands.literal("reset").executes(OriginCommands::reset))
            .then(Commands.literal("rebuild").executes(OriginCommands::rebuild))
            .then(Commands.literal("altars")
                .then(Commands.literal("reset").executes(c -> altars(c, false)))
                .then(Commands.literal("complete").executes(c -> altars(c, true))))
            .then(Commands.literal("gate").executes(OriginCommands::gate)
                .then(Commands.literal("trial").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(OriginCommands::gateTrial))));
    }

    /** SPEC §16.2a: the dial costs Stargate Journey reads and the charge of The Origin's return gate. */
    private static int gate(CommandContext<CommandSourceStack> c) {
        if (!net.neoforged.fml.ModList.get().isLoaded("sgjourney")) {
            c.getSource().sendFailure(Component.literal("Stargate Journey is not loaded"));
            return 0;
        }
        for (String l : dev.firmages.core.compat.sgjourney.SgjGates.statusLines(c.getSource().getServer(), OriginService.level(c.getSource().getServer()),
            OriginArena.gateBase())) {
            dev.firmages.core.FirmagesCore.LOGGER.info("origin gate: {}", l);
            c.getSource().sendSuccess(() -> Component.literal(l), false);
        }
        return 1;
    }

    /** A real dial of The Origin from a pedestal gate placed at {@code pos}, below and at the cost (debug.allowSimulate). */
    private static int gateTrial(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        if (!dev.firmages.core.config.ServerConfig.allowSimulate()) {
            c.getSource().sendFailure(Component.literal("Disabled; set debug.allowSimulate = true in firmages-server.toml"));
            return 0;
        }
        if (!net.neoforged.fml.ModList.get().isLoaded("sgjourney")) {
            c.getSource().sendFailure(Component.literal("Stargate Journey is not loaded"));
            return 0;
        }
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        for (String l : dev.firmages.core.compat.sgjourney.SgjGates.trial(c.getSource().getLevel(), pos, OriginArena.GATE_TEMPLATE,
            new BlockPos(3, 2, 9), new int[] {9, 16, 21, 33, 2, 37}, OriginService.level(c.getSource().getServer()), OriginArena.gateBase())) {
            dev.firmages.core.FirmagesCore.LOGGER.info("origin gate trial: {}", l);
            c.getSource().sendSuccess(() -> Component.literal(l), false);
        }
        return 1;
    }

    static LiteralArgumentBuilder<CommandSourceStack> reactor() {
        return Commands.literal("reactor").then(Commands.literal("status")
            .executes(OriginCommands::reactorStatusAll)
            .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(OriginCommands::reactorStatusAt)));
    }

    private static ServerLevel origin(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerLevel level = OriginService.level(c.getSource().getServer());
        if (level == null) throw NO_ORIGIN.create();
        return level;
    }

    private static int tp(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, boolean force) throws CommandSyntaxException {
        ServerLevel level = origin(c);
        OriginService.ensureBuilt(c.getSource().getServer());
        BlockPos a = OriginArena.arrival();
        int moved = 0;
        for (ServerPlayer p : players) {
            if (force) OriginAccess.allowForcedVisit(p);
            p.teleportTo(level, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 180.0F, 0.0F);
            if (p.level() == level) {
                moved++;
            } else {
                if (force) OriginAccess.endForcedVisit(p);
                c.getSource().sendFailure(Component.literal(p.getGameProfile().getName() + " was not let into The Origin"
                    + (force ? "" : " (age_9 missing? add 'force' to pass the lock)")));
            }
        }
        int n = moved;
        c.getSource().sendSuccess(() -> Component.literal("Teleported " + n + " player(s) to The Origin"
            + (force ? " (forced visit: the age_9 lock lets them stay until they leave)" : "")), true);
        return moved;
    }

    private static int gathering(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerLevel level = origin(c);
        if (!OriginSavedData.get(c.getSource().getServer()).won() && OriginService.fightActive(level)) {
            c.getSource().sendFailure(Component.literal("The fight of the last Gathering is still running (finale gate or boss alive); "
                + "/firmages origin kill [boss] ends it"));
            return 0;
        }
        if (!OriginService.forceGathering(c.getSource().getServer())) {
            c.getSource().sendFailure(Component.literal("The finale is already won; /firmages origin reset lets the Gathering happen again"));
            return 0;
        }
        int n = OriginSavedData.get(c.getSource().getServer()).gatherings();
        c.getSource().sendSuccess(() -> Component.literal("The Origin: Gathering #" + n + " started now (radius and cooldown skipped)"), true);
        return 1;
    }

    private static int fight(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        origin(c);
        if (OriginSavedData.get(c.getSource().getServer()).won()) {
            c.getSource().sendFailure(Component.literal("The finale is already won; /firmages origin reset lets the boss be fought again"));
            return 0;
        }
        OriginEvent.SummonBoss e = OriginService.summonBoss(c.getSource().getServer());
        if (e == null || !e.answered()) {
            c.getSource().sendFailure(Component.literal("Nothing answered the boss summon (OriginEvent.SummonBoss)"));
            return 0;
        }
        if (!e.isSummoned()) {
            c.getSource().sendFailure(Component.literal("The boss was not summoned: " + e.getRefusal()));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("The Origin: final boss summoned at the altar (Gathering skipped)"), true);
        return 1;
    }

    private static int killHostiles(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int n = OriginService.killHostiles(origin(c));
        if (n == 0) {
            c.getSource().sendFailure(Component.literal("No hostile alive in the loaded part of The Origin (the final boss is spared; /firmages origin kill boss)"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("The Origin: " + n + " hostile(s) killed (the final boss is spared)"), true);
        return n;
    }

    private static int killBoss(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerLevel level = origin(c);
        boolean wonBefore = OriginSavedData.get(c.getSource().getServer()).won();
        int n = OriginService.killBoss(level);
        if (n == 0) {
            c.getSource().sendFailure(Component.literal("No final boss (tag " + OriginService.FINAL_BOSS_TAG + ") alive in the loaded part of The Origin"));
            return 0;
        }
        boolean won = !wonBefore && OriginSavedData.get(c.getSource().getServer()).won();
        c.getSource().sendSuccess(() -> Component.literal("The Origin: final boss killed" + (won ? "; finale_won granted" : wonBefore ? " (the finale was already won)" : "")), true);
        return n;
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerLevel level = origin(c);
        OriginSavedData sd = OriginSavedData.get(c.getSource().getServer());
        BlockPos alt = OriginArena.ALTAR;
        boolean altar = level.isLoaded(alt) && level.getBlockState(alt).is(OriginRegistry.ORIGIN_ALTAR.get());
        line(c, String.format(Locale.ROOT, "The Origin: arena v%d%s, return gate %s, altar at %d %d %d %s",
            sd.arenaVersion(), sd.arenaVersion() < OriginArena.VERSION ? " (outdated)" : "", sd.gateBuilt() ? "built" : "missing",
            alt.getX(), alt.getY(), alt.getZ(), altar ? "present" : (level.isLoaded(alt) ? "MISSING" : "(chunk not loaded)")));
        line(c, String.format(Locale.ROOT, "  Gathering: %d start(s), gathered now %s (radius %.0f), fight running %s; final boss %s",
            sd.gatherings(), OriginService.gathered(c.getSource().getServer()), OriginService.gatherRadius(), OriginService.fightActive(level),
            sd.won() ? "DEFEATED (finale_won)" : "alive"));
        long echoes = java.util.stream.IntStream.range(0, OriginArena.ISLANDS).filter(i -> level.getEntity(dev.firmages.core.origin.OriginEchoes.uuid(i)) != null).count();
        line(c, "  Island echoes loaded: " + echoes + " of " + OriginArena.ISLANDS + " (they appear while players are in The Origin)");
        line(c, "  Arena: " + OriginArena.layout().name().toLowerCase(Locale.ROOT) + ", radius " + OriginArena.radius()
            + (OriginArena.altars().isEmpty() ? ", no fight altars" : ", fight altars lit: " + dev.firmages.core.boss.ArenaAltars.activeCount(level) + " of 4"));
        line(c, "  Spawn list "
            + java.util.Arrays.stream(net.minecraft.world.entity.MobCategory.values()).filter(m -> !dev.firmages.core.origin.OriginSpawns.spawns(m).isEmpty())
                .map(m -> m.getSerializedName() + " " + dev.firmages.core.origin.OriginSpawns.spawns(m).stream()
                    .map(d -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(d.type) + " w" + d.getWeight().asInt()).toList()).toList());
        return 1;
    }

    /** {@code origin altars reset|complete} (round G3, SPEC §39.1): every fight altar dark or lit at once. */
    private static int altars(CommandContext<CommandSourceStack> c, boolean complete) throws CommandSyntaxException {
        ServerLevel level = origin(c);
        if (OriginArena.altars().isEmpty()) {
            c.getSource().sendFailure(Component.literal("This Origin has the classic arena; it has no fight altars"));
            return 0;
        }
        int n = complete ? dev.firmages.core.boss.ArenaAltars.completeAll(level) : dev.firmages.core.boss.ArenaAltars.resetAll(level);
        c.getSource().sendSuccess(() -> Component.literal("The Origin: " + n + " of 4 altars " + (complete ? "lit" : "dark")
            + (n < 4 ? " (the others are not loaded)" : "")), true);
        return n;
    }

    private static int reset(CommandContext<CommandSourceStack> c) {
        OriginService.reset(c.getSource().getServer());
        c.getSource().sendSuccess(() -> Component.literal("The Origin: Gathering and final boss reset (finale_won stays granted)"), true);
        return 1;
    }

    private static int rebuild(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerLevel level = origin(c);
        OriginArena.buildArena(level);
        dev.firmages.core.ark.ArkService.recordPlinths(c.getSource().getServer(), OriginArena.migrateV3(level));
        boolean gate = net.neoforged.fml.ModList.get().isLoaded("sgjourney") && OriginArena.buildGate(level);
        c.getSource().sendSuccess(() -> Component.literal("The Origin: arena rebuilt" + (gate ? ", return gate placed again" : ", no return gate (Stargate Journey missing)")), true);
        return 1;
    }

    private static int reactorStatusAll(CommandContext<CommandSourceStack> c) {
        Collection<ReactorControllerBlockEntity> all = ReactorControllerBlockEntity.loaded();
        if (all.isEmpty()) {
            line(c, "No reactor controller is loaded");
            return 0;
        }
        for (ReactorControllerBlockEntity be : all) be.statusLines().forEach(l -> line(c, l));
        return all.size();
    }

    private static int reactorStatusAt(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        if (!(c.getSource().getLevel().getBlockEntity(pos) instanceof ReactorControllerBlockEntity be)) {
            c.getSource().sendFailure(Component.literal("No reactor controller at " + pos.toShortString()));
            return 0;
        }
        be.statusLines().forEach(l -> line(c, l));
        return 1;
    }

    private static void line(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendSuccess(() -> Component.literal(text), false);
    }
}
