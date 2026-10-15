package dev.firmages.core.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.crown.Chord;
import dev.firmages.core.crown.ChordLibrary;
import dev.firmages.core.crown.CrownService;
import dev.firmages.core.crown.LensMountBlockEntity;
import dev.firmages.core.crown.StarAnvilBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * {@code /firmages crown status [pos]|chords|swing <pos> <lens,...|none>} (SPEC §27.12). {@code swing} is a debug
 * override of the mounts' redstone lines (not saved) and needs {@code debug.allowSimulate}.
 */
final class CrownCommands {
    private static final SimpleCommandExceptionType SIMULATE_DISABLED = new SimpleCommandExceptionType(
        Component.literal("Simulation is disabled; set debug.allowSimulate = true in firmages-server.toml"));
    private static final SimpleCommandExceptionType NO_ANVIL = new SimpleCommandExceptionType(Component.literal("No Star Anvil there"));
    private static final SimpleCommandExceptionType NONE_LOADED = new SimpleCommandExceptionType(Component.literal("No Star Anvil is loaded in this dimension"));
    private static final SimpleCommandExceptionType BAD_LENSES = new SimpleCommandExceptionType(
        Component.literal("Lenses are a comma list of 0..8, or none"));

    private CrownCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> crown() {
        return Commands.literal("crown")
            .then(Commands.literal("status")
                .executes(c -> status(c, null))
                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> status(c, BlockPosArgument.getLoadedBlockPos(c, "pos")))))
            .then(Commands.literal("chords").executes(CrownCommands::chords))
            .then(Commands.literal("swing").then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.argument("lenses", StringArgumentType.word()).executes(CrownCommands::swing))));
    }

    private static int status(CommandContext<CommandSourceStack> c, BlockPos pos) throws CommandSyntaxException {
        ServerLevel level = c.getSource().getLevel();
        if (pos == null) {
            net.minecraft.world.phys.Vec3 at = c.getSource().getPosition();
            pos = CrownService.anvils(level).stream().min(Comparator.comparingDouble(p -> p.distToCenterSqr(at))).orElseThrow(NONE_LOADED::create);
        }
        for (Component line : CrownService.status(level, pos)) c.getSource().sendSuccess(() -> line, false);
        return level.getBlockEntity(pos) instanceof StarAnvilBlockEntity be && be.formed() ? 1 : 0;
    }

    private static int chords(CommandContext<CommandSourceStack> c) {
        ChordLibrary lib = ChordLibrary.current();
        c.getSource().sendSuccess(() -> Component.literal("Chord library: " + lib.chords().size() + " chords"
            + (lib.errors().isEmpty() ? "" : ", rejected: " + lib.errors())).withStyle(ChatFormatting.GOLD), false);
        for (Chord ch : lib.chords().values()) {
            c.getSource().sendSuccess(() -> Component.literal("  " + ch.signal() + ": " + ch.id() + " " + ch.lenses()), false);
        }
        return lib.chords().size();
    }

    private static int swing(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        if (!ServerConfig.allowSimulate()) throw SIMULATE_DISABLED.create();
        ServerLevel level = c.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        if (!(level.getBlockEntity(pos) instanceof StarAnvilBlockEntity be)) throw NO_ANVIL.create();
        String arg = StringArgumentType.getString(c, "lenses");
        Set<Integer> lenses = new HashSet<>();
        boolean clear = arg.equals("none");
        if (!clear) {
            try {
                for (String part : arg.split(",")) {
                    int n = Integer.parseInt(part.trim());
                    if (n < 0 || n >= Chord.LENSES) throw BAD_LENSES.create();
                    lenses.add(n);
                }
            } catch (NumberFormatException e) {
                throw BAD_LENSES.create();
            }
        }
        int n = 0;
        for (BlockPos m : be.mounts()) {
            if (level.getBlockEntity(m) instanceof LensMountBlockEntity mb) {
                mb.setForced(clear ? null : lenses.contains(mb.lensIndex()));
                n++;
            }
        }
        int mounts = n;
        c.getSource().sendSuccess(() -> Component.literal(clear ? "Mounts follow redstone again (" + mounts + ")"
            : "Swinging lenses " + lenses + " in on " + mounts + " mounts"), true);
        return mounts;
    }
}
