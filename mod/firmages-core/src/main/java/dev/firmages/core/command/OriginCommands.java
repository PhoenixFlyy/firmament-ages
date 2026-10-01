package dev.firmages.core.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.firmages.core.origin.OriginArena;
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
 * {@code /firmages origin tp [players] | status | reset | rebuild} and {@code /firmages reactor status [pos]}
 * (SPEC §10). {@code origin tp} uses the normal teleport path, so ProgressiveStages' dimension lock applies: a
 * player without {@code age_9} (and outside creative) stays where they are.
 */
final class OriginCommands {
    private static final SimpleCommandExceptionType NO_ORIGIN = new SimpleCommandExceptionType(
        Component.literal("The Origin dimension (firmages:origin) is not loaded"));

    private OriginCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> origin() {
        return Commands.literal("origin")
            .then(Commands.literal("tp")
                .executes(c -> tp(c, List.of(c.getSource().getPlayerOrException())))
                .then(Commands.argument("players", EntityArgument.players()).executes(c -> tp(c, EntityArgument.getPlayers(c, "players")))))
            .then(Commands.literal("status").executes(OriginCommands::status))
            .then(Commands.literal("reset").executes(OriginCommands::reset))
            .then(Commands.literal("rebuild").executes(OriginCommands::rebuild));
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

    private static int tp(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) throws CommandSyntaxException {
        ServerLevel level = origin(c);
        OriginService.ensureBuilt(c.getSource().getServer());
        BlockPos a = OriginArena.ARRIVAL;
        int moved = 0;
        for (ServerPlayer p : players) {
            p.teleportTo(level, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 180.0F, 0.0F);
            if (p.level() == level) {
                moved++;
            } else {
                c.getSource().sendFailure(Component.literal(p.getGameProfile().getName() + " was not let into The Origin (age_9 missing?)"));
            }
        }
        int n = moved;
        c.getSource().sendSuccess(() -> Component.literal("Teleported " + n + " player(s) to The Origin"), true);
        return moved;
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerLevel level = origin(c);
        OriginSavedData sd = OriginSavedData.get(c.getSource().getServer());
        BlockPos alt = OriginArena.ALTAR;
        boolean altar = level.isLoaded(alt) && level.getBlockState(alt).is(OriginRegistry.ORIGIN_ALTAR.get());
        line(c, String.format(Locale.ROOT, "The Origin: arena v%d%s, return gate %s, altar at %d %d %d %s",
            sd.arenaVersion(), sd.arenaVersion() < OriginArena.VERSION ? " (outdated)" : "", sd.gateBuilt() ? "built" : "missing",
            alt.getX(), alt.getY(), alt.getZ(), altar ? "present" : (level.isLoaded(alt) ? "MISSING" : "(chunk not loaded)")));
        line(c, String.format(Locale.ROOT, "  Gathering: %d start(s), gathered now %s (radius %.0f); final boss %s",
            sd.gatherings(), OriginService.gathered(c.getSource().getServer()), OriginService.gatherRadius(), sd.won() ? "DEFEATED (finale_won)" : "alive"));
        line(c, "  Finale gateway " + dev.firmages.core.config.ServerConfig.finaleGateway() + "; spawn list "
            + java.util.Arrays.stream(net.minecraft.world.entity.MobCategory.values()).filter(m -> !dev.firmages.core.origin.OriginSpawns.spawns(m).isEmpty())
                .map(m -> m.getSerializedName() + " " + dev.firmages.core.origin.OriginSpawns.spawns(m).stream()
                    .map(d -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(d.type) + " w" + d.getWeight().asInt()).toList()).toList());
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> c) {
        OriginService.reset(c.getSource().getServer());
        c.getSource().sendSuccess(() -> Component.literal("The Origin: Gathering and final boss reset (finale_won stays granted)"), true);
        return 1;
    }

    private static int rebuild(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerLevel level = origin(c);
        OriginArena.buildArena(level);
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
