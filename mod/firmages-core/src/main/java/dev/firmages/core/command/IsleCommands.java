package dev.firmages.core.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.isles.IsleSavedData;
import dev.firmages.core.isles.IsleSearch;
import dev.firmages.core.isles.IsleService;
import dev.firmages.core.isles.IsleSpec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Optional;

/**
 * {@code /firmages isles locate [radius] | shrine | list | lode <pos> | fallback status|check|place [pos] | place <pos>}
 * (SPEC §30.9, operators only like every {@code /firmages} command). {@code place} records an operator cluster with the
 * Wind Shrine at the position and places it once its chunks are loaded (no site checks); {@code fallback place} does the
 * same for the fallback record (replacing it), or picks a site around the heart without a position.
 */
final class IsleCommands {
    private IsleCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> isles() {
        return Commands.literal("isles")
            .then(Commands.literal("locate").executes(c -> locate(c, 2048))
                .then(Commands.argument("radius", IntegerArgumentType.integer(64, 16384)).executes(c -> locate(c, IntegerArgumentType.getInteger(c, "radius")))))
            .then(Commands.literal("shrine").executes(IsleCommands::shrine))
            .then(Commands.literal("list").executes(IsleCommands::list))
            .then(Commands.literal("lode").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(IsleCommands::lode)))
            .then(Commands.literal("fallback")
                .then(Commands.literal("status").executes(IsleCommands::fallbackStatus))
                .then(Commands.literal("check").executes(IsleCommands::fallbackCheck))
                .then(Commands.literal("place").executes(c -> fallbackPlace(c, null))
                    .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> fallbackPlace(c, BlockPosArgument.getBlockPos(c, "pos"))))))
            .then(Commands.literal("place").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(IsleCommands::place)));
    }

    private static void line(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }

    private static String describe(IsleSpec s) {
        return s.size().id() + " r" + s.radius() + " at " + s.x() + " " + s.top() + " " + s.z() + " (tip " + s.tip() + (s.lode() ? ", lode" : "")
            + (s.shrine() ? ", Wind Shrine" : "") + ", " + s.trees() + " trees, " + s.crystals() + " clusters)";
    }

    private static int locate(CommandContext<CommandSourceStack> c, int radius) {
        ServerLevel ow = c.getSource().getServer().overworld();
        BlockPos from = BlockPos.containing(c.getSource().getPosition());
        long t0 = System.nanoTime();
        List<IsleSearch.Found> found = IsleSearch.nearest(ow, from, radius, p -> true, 1);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        if (found.isEmpty()) {
            c.getSource().sendFailure(Component.literal("No Drifting Isle within " + radius + " blocks (" + ms + " ms)"));
            return 0;
        }
        IsleSearch.Found f = found.get(0);
        line(c.getSource(), "Drifting Isle cluster in chunk " + f.chunk().x + " " + f.chunk().z + ", " + (int) f.distance() + " blocks away, "
            + f.isles().size() + " isles (" + ms + " ms)");
        for (IsleSpec s : f.isles()) line(c.getSource(), "  " + describe(s));
        return f.isles().size();
    }

    private static int shrine(CommandContext<CommandSourceStack> c) {
        ServerLevel ow = c.getSource().getServer().overworld();
        BlockPos from = BlockPos.containing(c.getSource().getPosition());
        int radius = ServerConfig.wayshrinePointerRadiusChunks() * 16;
        long t0 = System.nanoTime();
        Optional<BlockPos> core = IsleService.nearestShrine(ow, from, radius, p -> false);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        if (core.isEmpty()) {
            c.getSource().sendFailure(Component.literal("No Wind Shrine on an isle within " + radius + " blocks (" + ms + " ms)"));
            return 0;
        }
        BlockPos p = core.get();
        line(c.getSource(), "Wind Shrine core at " + p.getX() + " " + p.getY() + " " + p.getZ() + ", " + (int) Math.sqrt(p.distSqr(from.atY(p.getY())))
            + " blocks away (" + ms + " ms)");
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        IsleSavedData d = IsleSavedData.get(c.getSource().getServer());
        if (d.clusters().isEmpty()) line(c.getSource(), "No clusters placed by code");
        for (IsleSavedData.Cluster k : d.clusters()) {
            line(c.getSource(), "#" + k.id + " " + k.origin + " at " + k.x + " " + k.z + (k.done() ? ", stands" : ", waits for its chunks"));
            for (int i = 0; i < k.isles.size(); i++) {
                line(c.getSource(), "  " + i + ": " + describe(k.isles.get(i)) + (k.skipped[i] ? " skipped (blocked)" : k.placed[i] ? " placed" : " waiting"));
            }
        }
        line(c.getSource(), d.reform().size() + " lodes wait to re-form; placer jobs " + IsleService.jobs());
        return d.clusters().size();
    }

    private static int lode(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getBlockPos(c, "pos");
        boolean queued = IsleSavedData.get(c.getSource().getServer()).reform().contains(GlobalPos.of(level.dimension(), pos));
        line(c.getSource(), "At " + pos.toShortString() + ": " + BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock())
            + (queued ? ", queued to re-form" : ", not queued") + "; lode yield " + ServerConfig.islesLodeYield());
        return queued ? 1 : 0;
    }

    private static int fallbackStatus(CommandContext<CommandSourceStack> c) {
        IsleSavedData d = IsleSavedData.get(c.getSource().getServer());
        BlockPos anchor = IsleService.anchor(c.getSource().getServer());
        line(c.getSource(), "Anchor " + anchor.toShortString() + ", radius " + ServerConfig.islesFallbackRadius() + ", fallback "
            + (ServerConfig.islesFallback() ? "on" : "off"));
        line(c.getSource(), d.fallback().map(k -> "Fallback cluster #" + k.id + " at " + k.x + " " + k.z + (k.done() ? ", stands" : ", waits for its chunks")
            + k.shrineCore().map(p -> "; Wind Shrine core " + p.toShortString()).orElse("")).orElse("No fallback cluster"));
        line(c.getSource(), d.knownStart().map(p -> "Known isle start " + p.toShortString()).orElse("No known isle start"));
        return d.fallback().isPresent() ? 1 : 0;
    }

    private static int fallbackCheck(CommandContext<CommandSourceStack> c) {
        line(c.getSource(), "Fallback check: " + IsleService.ensureFallback(c.getSource().getServer()));
        return 1;
    }

    private static int fallbackPlace(CommandContext<CommandSourceStack> c, BlockPos pos) {
        ServerLevel ow = c.getSource().getServer().overworld();
        Optional<IsleSavedData.Cluster> k = pos == null
            ? IsleService.newFallback(ow, IsleService.anchor(c.getSource().getServer()), RandomSource.create())
            : IsleService.placeCluster(ow, pos.getX(), pos.getZ(), IsleSavedData.Origin.FALLBACK, true, ow.getSharedSpawnPos(), false);
        return report(c, k);
    }

    private static int place(CommandContext<CommandSourceStack> c) {
        ServerLevel ow = c.getSource().getServer().overworld();
        BlockPos pos = BlockPosArgument.getBlockPos(c, "pos");
        return report(c, IsleService.placeCluster(ow, pos.getX(), pos.getZ(), IsleSavedData.Origin.MANUAL, true, ow.getSharedSpawnPos(), false));
    }

    private static int report(CommandContext<CommandSourceStack> c, Optional<IsleSavedData.Cluster> k) {
        if (k.isEmpty()) {
            c.getSource().sendFailure(Component.literal("No room for a cluster there (the terrain is too high for the band)"));
            return 0;
        }
        line(c.getSource(), "Cluster #" + k.get().id + " recorded at " + k.get().x + " " + k.get().z + "; it is placed once its chunks are loaded");
        for (IsleSpec s : k.get().isles) line(c.getSource(), "  " + describe(s));
        return k.get().isles.size();
    }
}
