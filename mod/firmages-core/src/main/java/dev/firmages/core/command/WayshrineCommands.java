package dev.firmages.core.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.wayshrine.WayshrineCoreBlockEntity;
import dev.firmages.core.wayshrine.WayshrineDataLoader;
import dev.firmages.core.wayshrine.WayshrinePointer;
import dev.firmages.core.wayshrine.WayshrineSavedData;
import dev.firmages.core.wayshrine.WayshrineService;
import dev.firmages.core.wayshrine.WayshrineTutorial;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * {@code /firmages wayshrine list|status [pos]|locate <type>|tutorial [here]|light <pos>|reset <pos> [stone]|stone <pos>|census [radius]}
 * (SPEC-himmel §28.9).
 */
final class WayshrineCommands {
    private static final SimpleCommandExceptionType SIMULATE_DISABLED = new SimpleCommandExceptionType(
        Component.literal("Simulation is disabled; set debug.allowSimulate = true in firmages-server.toml"));
    private static final SimpleCommandExceptionType NO_CORE = new SimpleCommandExceptionType(Component.literal("No Wayshrine core within 32 blocks"));

    private WayshrineCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> wayshrine() {
        return Commands.literal("wayshrine")
            .then(Commands.literal("list").executes(WayshrineCommands::list))
            .then(Commands.literal("status").executes(c -> status(c, null))
                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> status(c, BlockPosArgument.getLoadedBlockPos(c, "pos")))))
            .then(Commands.literal("locate").then(Commands.argument("type", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(WayshrineDataLoader.current().types().keySet(), b))
                .executes(WayshrineCommands::locate)))
            .then(Commands.literal("tutorial").executes(c -> tutorial(c, false))
                .then(Commands.literal("here").executes(c -> tutorial(c, true))))
            .then(Commands.literal("light").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(WayshrineCommands::light)))
            .then(Commands.literal("reset").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> reset(c, false))
                .then(Commands.literal("stone").executes(c -> reset(c, true)))))
            .then(Commands.literal("stone").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(WayshrineCommands::stone)))
            .then(Commands.literal("census").executes(c -> census(c, 4000, 0, 0))
                .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(16, 20000))
                    .executes(c -> census(c, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "radius"), 0, 0))
                    .then(Commands.argument("spacing", com.mojang.brigadier.arguments.IntegerArgumentType.integer(2, 4096))
                        .then(Commands.argument("separation", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 4095))
                            .executes(c -> census(c, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "radius"),
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "spacing"),
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "separation")))))));
    }

    /**
     * {@code census [radius [spacing separation]]}: the ground Wayshrines around the world spawn (SPEC-himmel §28.11,
     * acceptance 2), logged; with a spacing, as if the structure sets had it (nothing changes in the world).
     */
    private static int census(CommandContext<CommandSourceStack> c, int radius, int spacing, int separation) {
        if (spacing > 0 && separation >= spacing) {
            c.getSource().sendFailure(Component.literal("separation must be smaller than spacing"));
            return 0;
        }
        ServerLevel overworld = c.getSource().getServer().overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        long t0 = System.nanoTime();
        java.util.List<WayshrinePointer.Census> found = WayshrinePointer.census(overworld, spawn, radius, spacing, separation);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        java.util.Map<String, Integer> per = new java.util.TreeMap<>();
        found.forEach(f -> per.merge(f.type(), 1, Integer::sum));
        String head = "Wayshrine census: seed " + overworld.getSeed() + ", spacing " + (spacing > 0 ? spacing + "/" + separation : "shipped")
            + ", spawn " + spawn.toShortString() + ", " + found.size()
            + " ground Wayshrines within " + radius + " blocks " + per + " (" + ms + " ms)";
        dev.firmages.core.FirmagesCore.LOGGER.info(head);
        for (WayshrinePointer.Census f : found) {
            dev.firmages.core.FirmagesCore.LOGGER.info("  {} at {} ~ {}, {} blocks", f.type(), f.pos().getX(), f.pos().getZ(), (int) f.distance());
        }
        line(c.getSource(), Component.literal(head));
        return found.size();
    }

    private static void line(CommandSourceStack src, Component c) {
        src.sendSuccess(() -> c, false);
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        WayshrineSavedData sd = WayshrineSavedData.get(c.getSource().getServer());
        line(c.getSource(), Component.literal("Wayshrines: " + sd.records().size() + " known, tutorial "
            + sd.tutorialPos().map(BlockPos::toShortString).orElse("not placed") + ", cache " + sd.found().size()).withStyle(ChatFormatting.GOLD));
        for (WayshrineSavedData.Record r : sd.records()) {
            line(c.getSource(), Component.literal(r.type() + " at " + r.where().pos().toShortString() + " in " + r.where().dimension().location()
                + ": " + r.phase().getSerializedName() + (r.intact() ? ", intact" : ", not standing") + (r.litAge().isEmpty() ? "" : ", lit in " + r.litAge())
                + (r.stoneGiven() ? ", stone given" : "")));
        }
        return sd.records().size();
    }

    /** The core at {@code pos}, or the nearest known core within 32 blocks of the source. */
    private static BlockPos coreNear(CommandContext<CommandSourceStack> c, BlockPos pos) throws CommandSyntaxException {
        ServerLevel level = c.getSource().getLevel();
        if (pos != null) return pos;
        BlockPos from = BlockPos.containing(c.getSource().getPosition());
        Optional<BlockPos> best = WayshrineSavedData.get(c.getSource().getServer()).records().stream()
            .filter(r -> r.where().dimension() == level.dimension() && r.where().pos().distSqr(from) <= 32 * 32)
            .map(r -> r.where().pos()).min(java.util.Comparator.comparingDouble(p -> p.distSqr(from)));
        return best.orElseThrow(NO_CORE::create);
    }

    private static int status(CommandContext<CommandSourceStack> c, BlockPos pos) throws CommandSyntaxException {
        BlockPos core = coreNear(c, pos);
        WayshrineService.status(c.getSource().getLevel(), core).forEach(l -> line(c.getSource(), l));
        return 1;
    }

    private static int locate(CommandContext<CommandSourceStack> c) {
        String type = StringArgumentType.getString(c, "type");
        ServerLevel overworld = c.getSource().getServer().overworld();
        BlockPos from = BlockPos.containing(c.getSource().getPosition());
        long t0 = System.nanoTime();
        Optional<WayshrinePointer.Target> t = WayshrinePointer.search(overworld, from, type, false);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        if (t.isEmpty()) {
            c.getSource().sendFailure(Component.literal("No " + type + " within " + ServerConfig.wayshrinePointerRadiusChunks() + " chunks (" + ms + " ms)"));
            return 0;
        }
        WayshrineSavedData.get(c.getSource().getServer()).addFound(new WayshrineSavedData.Found(t.get().pos(), t.get().type()));
        BlockPos p = t.get().pos();
        line(c.getSource(), Component.literal(type + " at " + p.getX() + " ~ " + p.getZ() + ", " + (int) Math.sqrt(p.distSqr(from.atY(p.getY())))
            + " blocks away (" + ms + " ms)"));
        return 1;
    }

    private static int tutorial(CommandContext<CommandSourceStack> c, boolean here) {
        ServerLevel level = c.getSource().getServer().overworld();
        Optional<BlockPos> core = here
            ? WayshrineTutorial.placeAt(c.getSource().getLevel(), (int) Math.floor(c.getSource().getPosition().x), (int) Math.floor(c.getSource().getPosition().z),
                level.getRandom(), false)
            : WayshrineTutorial.placeAround(level, level.getSharedSpawnPos(), level.getRandom());
        if (core.isEmpty()) {
            c.getSource().sendFailure(Component.literal("No tutorial Wayshrine placed (no fit site, or the template is missing)"));
            return 0;
        }
        line(c.getSource(), Component.literal("Tutorial Wayshrine placed at " + core.get().toShortString()));
        return 1;
    }

    private static int light(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        if (!ServerConfig.allowSimulate()) throw SIMULATE_DISABLED.create();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        Component no = WayshrineService.simulateLight(c.getSource().getLevel(), pos);
        if (no != null) {
            c.getSource().sendFailure(no);
            return 0;
        }
        line(c.getSource(), Component.literal("Wayshrine at " + pos.toShortString() + " lit"));
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> c, boolean stone) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        Component no = WayshrineService.reset(c.getSource().getLevel(), pos, stone);
        if (no != null) {
            c.getSource().sendFailure(no);
            return 0;
        }
        line(c.getSource(), Component.literal("Wayshrine at " + pos.toShortString() + " reset" + (stone ? "; its stone can drop again" : "")));
        return 1;
    }

    private static int stone(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        ServerLevel level = c.getSource().getLevel();
        if (!(level.getBlockEntity(pos) instanceof WayshrineCoreBlockEntity be)) {
            c.getSource().sendFailure(Component.literal("No Wayshrine core at " + pos.toShortString()));
            return 0;
        }
        WayshrineService.dropStone(level, pos, be.type());
        WayshrineSavedData sd = WayshrineSavedData.get(c.getSource().getServer());
        GlobalPos gp = GlobalPos.of(level.dimension(), pos.immutable());
        sd.record(gp).ifPresent(r -> sd.put(new WayshrineSavedData.Record(gp, r.type(), r.phase(), r.intact(), r.litAge(), r.litAt(), true)));
        line(c.getSource(), Component.literal("Wayshrine Stone of " + be.type() + " dropped at " + pos.above().toShortString()));
        return 1;
    }
}
