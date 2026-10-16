package dev.firmages.core.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.balance.EnergyAudit;
import dev.firmages.core.balance.EnergyMeter;
import dev.firmages.core.balance.OreAudit;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * The measuring commands of the harmony plan (SPEC §38), permission level 2 like all of {@code /firmages}:
 * {@code energy audit [radius] [seconds]} meters the FE of every block around the caller ({@link EnergyAudit}),
 * {@code ore audit} measures the ore ladder of the live recipes ({@link OreAudit}).
 */
final class BalanceCommands {
    static final int DEFAULT_RADIUS = 16;
    static final int DEFAULT_SECONDS = 10;

    private BalanceCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> energy() {
        return Commands.literal("energy")
            .then(Commands.literal("audit")
                .executes(c -> energyAudit(c, DEFAULT_RADIUS, DEFAULT_SECONDS))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                    .executes(c -> energyAudit(c, IntegerArgumentType.getInteger(c, "radius"), DEFAULT_SECONDS))
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                        .executes(c -> energyAudit(c, IntegerArgumentType.getInteger(c, "radius"), IntegerArgumentType.getInteger(c, "seconds"))))))
            .then(Commands.literal("charge").then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                .then(Commands.argument("fePerTick", IntegerArgumentType.integer(1, Integer.MAX_VALUE))
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600)).executes(BalanceCommands::charge)))));
    }

    /** SPEC §38: a test power line into one block for a copy boot (debug.allowSimulate). */
    private static int charge(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!dev.firmages.core.config.ServerConfig.allowSimulate()) {
            c.getSource().sendFailure(Component.literal("Disabled; set debug.allowSimulate = true in firmages-server.toml"));
            return 0;
        }
        BlockPos pos = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(c, "pos");
        int fe = IntegerArgumentType.getInteger(c, "fePerTick");
        int s = IntegerArgumentType.getInteger(c, "seconds");
        dev.firmages.core.balance.EnergyCharge.start(c.getSource().getLevel(), pos, fe, s);
        c.getSource().sendSuccess(() -> Component.literal("Charging " + pos.toShortString() + " with up to " + fe + " FE/t for " + s + " s"), true);
        return 1;
    }

    static LiteralArgumentBuilder<CommandSourceStack> ore() {
        return Commands.literal("ore").then(Commands.literal("audit").executes(BalanceCommands::oreAudit));
    }

    private static int energyAudit(CommandContext<CommandSourceStack> c, int radius, int seconds) {
        CommandSourceStack src = c.getSource();
        BlockPos at = BlockPos.containing(src.getPosition());
        EnergyAudit audit = new EnergyAudit(src.getLevel(), at, radius, seconds * 20, src);
        EnergyMeter.start(audit);
        src.sendSuccess(() -> Component.literal("Energy audit at " + at.toShortString() + ", radius " + radius + ", for " + seconds
            + " s; the report follows"), true);
        return 1;
    }

    private static int oreAudit(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        long t0 = System.nanoTime();
        Optional<OreAudit.Result> r = OreAudit.run(src.getServer());
        if (r.isEmpty()) {
            src.sendFailure(Component.literal("No metal table at data/" + OreAudit.TABLE.getNamespace() + "/" + OreAudit.TABLE.getPath()));
            return 0;
        }
        OreAudit.Result res = r.get();
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        for (String l : res.lines()) {
            FirmagesCore.LOGGER.info(l);
            src.sendSuccess(() -> Component.literal(l), false);
        }
        boolean ok = res.table().isEmpty() && res.stepsBack().isEmpty() && res.magic().isEmpty() && res.unlisted().isEmpty();
        String tail = "Ore audit report (" + ms + " ms): " + res.file();
        FirmagesCore.LOGGER.info(tail);
        src.sendSuccess(() -> Component.literal(tail).withStyle(ok ? ChatFormatting.GRAY : ChatFormatting.RED), false);
        return res.perAge().size();
    }
}
