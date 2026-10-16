package dev.firmages.core.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.launch.LaunchControlBlockEntity;
import dev.firmages.core.launch.LaunchService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;

/**
 * {@code /firmages launch status [pos]|finish <pos>} (SPEC §31.9). {@code finish} lands a probe in flight at once
 * (the returns follow on the next tick) and needs {@code debug.allowSimulate}.
 */
final class LaunchCommands {
    private static final SimpleCommandExceptionType SIMULATE_DISABLED = new SimpleCommandExceptionType(
        Component.literal("Simulation is disabled; set debug.allowSimulate = true in firmages-server.toml"));
    private static final SimpleCommandExceptionType NO_CONTROL = new SimpleCommandExceptionType(Component.literal("No launch control there"));
    private static final SimpleCommandExceptionType NONE_LOADED = new SimpleCommandExceptionType(Component.literal("No launch control is loaded in this dimension"));
    private static final SimpleCommandExceptionType NO_MISSION = new SimpleCommandExceptionType(Component.literal("No probe is in flight there"));

    private LaunchCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> launch() {
        return Commands.literal("launch")
            .then(Commands.literal("status")
                .executes(c -> status(c, null))
                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> status(c, BlockPosArgument.getLoadedBlockPos(c, "pos")))))
            .then(Commands.literal("finish").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(LaunchCommands::finish)));
    }

    private static int status(CommandContext<CommandSourceStack> c, BlockPos pos) throws CommandSyntaxException {
        ServerLevel level = c.getSource().getLevel();
        if (pos == null) {
            net.minecraft.world.phys.Vec3 at = c.getSource().getPosition();
            pos = LaunchService.controls(level).stream().min(Comparator.comparingDouble(p -> p.distToCenterSqr(at))).orElseThrow(NONE_LOADED::create);
        }
        for (Component line : LaunchService.status(level, pos)) c.getSource().sendSuccess(() -> line, false);
        return level.getBlockEntity(pos) instanceof LaunchControlBlockEntity be && be.complex().complete() ? 1 : 0;
    }

    private static int finish(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        if (!ServerConfig.allowSimulate()) throw SIMULATE_DISABLED.create();
        ServerLevel level = c.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        if (!(level.getBlockEntity(pos) instanceof LaunchControlBlockEntity be)) throw NO_CONTROL.create();
        if (!be.finishNow()) throw NO_MISSION.create();
        c.getSource().sendSuccess(() -> Component.literal("The probe at " + pos.toShortString() + " lands now"), true);
        return 1;
    }
}
