package dev.firmages.core.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.firmages.core.sky.AuroraRules;
import dev.firmages.core.sky.AuroraService;
import dev.firmages.core.sky.ClimateSource;
import dev.firmages.core.sky.ClimateSources;
import dev.firmages.core.sky.HarvestRegistry;
import dev.firmages.core.sky.SkyHarvestData;
import dev.firmages.core.sky.SkyHarvestDataLoader;
import dev.firmages.core.sky.SkySavedData;
import dev.firmages.core.sky.StormRodBlock;
import dev.firmages.core.sky.StormRodBlockEntity;
import dev.firmages.core.sky.StormWeather;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * {@code /firmages sky aurora on|off|auto | status [pos] | band [radius]} (SPEC §26.8) and {@code /firmages sky storm
 * rain|thunder|clear [seconds] | auto | strike <pos> | status <pos>} (operators only, like every {@code /firmages} command).
 * The override is saved in {@code firmages_sky}; {@code band} reads far columns without loading their chunks.
 */
final class SkyHarvestCommands {
    private SkyHarvestCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> sky() {
        return Commands.literal("sky")
            .then(Commands.literal("aurora")
                .then(Commands.literal("on").executes(c -> override(c, AuroraRules.Override.ON)))
                .then(Commands.literal("off").executes(c -> override(c, AuroraRules.Override.OFF)))
                .then(Commands.literal("auto").executes(c -> override(c, AuroraRules.Override.AUTO)))
                .then(Commands.literal("status").executes(c -> status(c, BlockPos.containing(c.getSource().getPosition())))
                    .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> status(c, BlockPosArgument.getBlockPos(c, "pos")))))
                .then(Commands.literal("band").executes(c -> band(c, 2048))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(256, 16384)).executes(c -> band(c, IntegerArgumentType.getInteger(c, "radius"))))))
            .then(Commands.literal("storm")
                .then(Commands.literal("rain").executes(c -> weather(c, StormWeather.Mode.RAIN, 600))
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 86400)).executes(c -> weather(c, StormWeather.Mode.RAIN, IntegerArgumentType.getInteger(c, "seconds")))))
                .then(Commands.literal("thunder").executes(c -> weather(c, StormWeather.Mode.THUNDER, 600))
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 86400)).executes(c -> weather(c, StormWeather.Mode.THUNDER, IntegerArgumentType.getInteger(c, "seconds")))))
                .then(Commands.literal("clear").executes(c -> weather(c, StormWeather.Mode.CLEAR, 600))
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 86400)).executes(c -> weather(c, StormWeather.Mode.CLEAR, IntegerArgumentType.getInteger(c, "seconds")))))
                .then(Commands.literal("auto").executes(c -> weather(c, StormWeather.Mode.AUTO, 0)))
                .then(Commands.literal("strike").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(SkyHarvestCommands::strike)))
                .then(Commands.literal("status").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(SkyHarvestCommands::rodStatus)))
                .then(Commands.literal("forecast").executes(c -> forecast(c, 5))
                    .then(Commands.argument("hours", IntegerArgumentType.integer(1, 200)).executes(c -> forecast(c, IntegerArgumentType.getInteger(c, "hours"))))));
    }

    /**
     * {@code sky storm forecast [hours]} (SPEC §26.10, round H3): one Storm Rod's expected yield over the next real hours
     * of TerraFirmaCraft's own weather (no override), replayed from the climate model; logged as well.
     */
    private static int forecast(CommandContext<CommandSourceStack> c, int hours) {
        if (!net.neoforged.fml.ModList.get().isLoaded("tfc")) {
            c.getSource().sendFailure(Component.literal("The forecast reads TerraFirmaCraft's weather; TFC is not loaded"));
            return 0;
        }
        long ticks = hours * dev.firmages.core.sky.StormForecast.REAL_HOUR;
        var f = dev.firmages.core.compat.tfc.TfcWeather.forecast(c.getSource().getServer().overworld(), SkyHarvestDataLoader.current().storm(), ticks);
        String text = String.format(Locale.ROOT, "Storm forecast, next %d real hours (%d ticks) of TFC weather: rain %.1f %% of the time, "
                + "thunder %.1f %%; one rod: %.1f strikes, %.1f storm glass, %.1f per real hour", hours, ticks, 100.0 * f.rainTicks() / ticks,
            100.0 * f.thunderTicks() / ticks, f.strikes(), f.glass(), f.glassPerRealHour());
        dev.firmages.core.FirmagesCore.LOGGER.info(text);
        line(c.getSource(), text);
        return (int) Math.round(f.glass());
    }

    private static void line(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static int override(CommandContext<CommandSourceStack> c, AuroraRules.Override o) {
        SkySavedData.get(c.getSource().getServer()).setAuroraOverride(o.id());
        line(c.getSource(), "Aurora override: " + o.id());
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c, BlockPos pos) {
        ServerLevel level = c.getSource().getLevel();
        SkyHarvestData.Aurora a = SkyHarvestDataLoader.current().aurora();
        if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            float avg = ClimateSources.get().averageTempUnloaded(c.getSource().getServer().overworld(), pos.getX(), a.referenceY(), pos.getZ());
            line(c.getSource(), "Column " + pos.getX() + " " + pos.getZ() + " is not loaded; average at Y " + a.referenceY() + ": " + f(avg)
                + " C (band at or below " + f(AuroraService.band(c.getSource().getServer())) + ")");
            return 1;
        }
        AuroraRules.Result r = AuroraService.at(level, pos.getX(), pos.getZ());
        ClimateSource climate = ClimateSources.get();
        ServerLevel ow = c.getSource().getServer().overworld();
        float avg = climate.averageTemp(ow, pos.getX(), a.referenceY(), pos.getZ());
        float inst = climate.instantTemp(ow, pos.getX(), a.referenceY(), pos.getZ());
        boolean sky = level.canSeeSky(pos.above());
        line(c.getSource(), "Aurora at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ": " + (r.active() ? "ACTIVE" : "inactive")
            + " (override " + r.override().id() + ", " + level.dimension().location() + ")");
        line(c.getSource(), "  night " + r.night() + " (day time " + Math.floorMod(ow.getDayTime(), 24000L) + "), rain " + r.raining());
        line(c.getSource(), "  at Y " + a.referenceY() + ": average " + f(avg) + " C, now " + f(inst) + " C, band at or below " + f(AuroraService.band(c.getSource().getServer()))
            + " -> " + (r.inBand() ? "in the cold band" : "too warm") + ", " + (r.coldHalf() ? "cold half" : "warm half"));
        line(c.getSource(), "  roll " + f(r.roll()) + " against chance " + f(r.chance()) + ", intensity " + f(r.intensity()));
        line(c.getSource(), "  height " + pos.getY() + " (nets need " + a.minY() + "), open sky " + sky);
        return r.active() ? 1 : 0;
    }

    /**
     * Samples the yearly average at the reference height every 256 blocks along the four bearings from the caller and
     * prints the nearest point in the cold band (SPEC §26.10 PoC). Far columns are read from the generator, never
     * loaded.
     */
    private static int band(CommandContext<CommandSourceStack> c, int radius) {
        ServerLevel ow = c.getSource().getServer().overworld();
        SkyHarvestData.Aurora a = SkyHarvestDataLoader.current().aurora();
        ClimateSource climate = ClimateSources.get();
        BlockPos o = BlockPos.containing(c.getSource().getPosition());
        int[][] dirs = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}};
        String[] names = {"north", "south", "east", "west"};
        float here = climate.averageTempUnloaded(ow, o.getX(), a.referenceY(), o.getZ());
        double threshold = AuroraService.band(c.getSource().getServer());
        line(c.getSource(), "Average at Y " + a.referenceY() + " here (" + o.getX() + " " + o.getZ() + "): " + f(here) + " C; band at or below "
            + f(threshold) + " C (max_average_temp " + f(a.maxAverageTemp()) + ", spawn offset " + f(a.spawnOffset()) + ")");
        int best = -1;
        String bestText = "";
        for (int d = 0; d < 4; d++) {
            float min = Float.MAX_VALUE;
            int first = -1;
            for (int dist = 256; dist <= radius; dist += 256) {
                int x = o.getX() + dirs[d][0] * dist;
                int z = o.getZ() + dirs[d][1] * dist;
                float t = climate.averageTempUnloaded(ow, x, a.referenceY(), z);
                if (Float.isNaN(t)) continue;
                min = Math.min(min, t);
                if (first < 0 && t <= threshold) {
                    first = dist;
                    if (best < 0 || dist < best) {
                        best = dist;
                        bestText = x + " " + z + " (" + names[d] + ", " + dist + " blocks, " + f(t) + " C)";
                    }
                }
            }
            line(c.getSource(), "  " + names[d] + ": " + (first < 0 ? "no cold band within " + radius : "cold band from " + first + " blocks")
                + (min == Float.MAX_VALUE ? "" : ", coldest " + f(min) + " C"));
        }
        line(c.getSource(), best < 0 ? "No cold-band point within " + radius + " blocks." : "Nearest cold-band point: " + bestText);
        return Math.max(best, 0);
    }

    /**
     * A test storm for the rods ({@link StormWeather}): TerraFirmaCraft owns the overworld's weather and refuses
     * vanilla {@code /weather}, so this only changes what Storm Rods see; {@code /time set rain} is TFC's own jump.
     */
    private static int weather(CommandContext<CommandSourceStack> c, StormWeather.Mode mode, int seconds) {
        ServerLevel ow = c.getSource().getServer().overworld();
        StormWeather.force(ow, mode, seconds * 20L);
        line(c.getSource(), "Storm Rods see " + mode.id() + " for " + seconds + " s (the world's own weather: raining " + ow.isRaining()
            + ", thundering " + ow.isThundering() + ")");
        return 1;
    }

    private static StormRodBlockEntity rod(CommandContext<CommandSourceStack> c, BlockPos pos) {
        ServerLevel level = c.getSource().getLevel();
        BlockState s = level.getBlockState(pos);
        if (!s.is(HarvestRegistry.STORM_ROD.get())) return null;
        return level.getBlockEntity(StormRodBlock.basin(s, pos)) instanceof StormRodBlockEntity be ? be : null;
    }

    private static int strike(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        StormRodBlockEntity be = rod(c, BlockPosArgument.getLoadedBlockPos(c, "pos"));
        if (be == null) {
            c.getSource().sendFailure(Component.literal("No Storm Rod there"));
            return 0;
        }
        int fused = be.strike(c.getSource().getLevel());
        line(c.getSource(), "Strike: " + fused + " sand fused, " + be.strikes() + " strikes since load, reason " + be.reason());
        return fused;
    }

    private static int rodStatus(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        StormRodBlockEntity be = rod(c, BlockPosArgument.getLoadedBlockPos(c, "pos"));
        if (be == null) {
            c.getSource().sendFailure(Component.literal("No Storm Rod there"));
            return 0;
        }
        ServerLevel level = c.getSource().getLevel();
        line(c.getSource(), "Storm Rod: reason " + be.reason() + ", sand " + be.sand() + ", output room " + be.outputRoom() + ", strikes "
            + be.strikes() + ", open sky " + be.skyOpen(level) + ", raining " + StormWeather.raining(level) + ", thundering "
            + StormWeather.thundering(level) + " (override " + StormWeather.active(level).id() + ", " + StormWeather.remaining(level) / 20 + " s left)");
        return (int) be.strikes();
    }
}
