package dev.firmages.core.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.firmages.core.compat.ie.StarfallVeins;
import dev.firmages.core.sky.CraterCarver;
import dev.firmages.core.sky.SkySavedData;
import dev.firmages.core.sky.StarfallData;
import dev.firmages.core.sky.StarfallDataLoader;
import dev.firmages.core.sky.StarfallRules;
import dev.firmages.core.sky.StarfallService;
import dev.firmages.core.sky.StarfallSite;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.util.Locale;
import java.util.Optional;

/**
 * {@code /firmages starfall now [pos] [metal] [size] | list | carve <id> | cool <id> | heat <id> | old <pos> | tonight
 * | veins} (SPEC §25.10, operators only like every {@code /firmages} command). {@code now} with a position places the
 * fall exactly there (no site search, no base check, no move).
 */
final class StarfallCommands {
    private static final SimpleCommandExceptionType NO_SITE = new SimpleCommandExceptionType(Component.literal("No open Starfall site with that id"));
    private static final SimpleCommandExceptionType NO_DATA = new SimpleCommandExceptionType(
        Component.literal("Starfall data is not loaded (data/firmages/firmages_sky/starfall.json rejected?)"));

    private StarfallCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> starfall() {
        return Commands.literal("starfall")
            .then(Commands.literal("now").executes(c -> now(c, null, null, null))
                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> now(c, BlockPosArgument.getBlockPos(c, "pos"), null, null))
                    .then(Commands.argument("metal", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(StarfallDataLoader.current().metals().keySet(), b))
                        .executes(c -> now(c, BlockPosArgument.getBlockPos(c, "pos"), StringArgumentType.getString(c, "metal"), null))
                        .then(Commands.argument("size", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(StarfallDataLoader.current().sizes().keySet(), b))
                            .executes(c -> now(c, BlockPosArgument.getBlockPos(c, "pos"), StringArgumentType.getString(c, "metal"),
                                StringArgumentType.getString(c, "size")))))))
            .then(Commands.literal("list").executes(StarfallCommands::list))
            .then(Commands.literal("carve").then(Commands.argument("id", LongArgumentType.longArg(1)).executes(StarfallCommands::carve)))
            .then(Commands.literal("cool").then(Commands.argument("id", LongArgumentType.longArg(1)).executes(c -> hot(c, false))))
            .then(Commands.literal("heat").then(Commands.argument("id", LongArgumentType.longArg(1)).executes(c -> hot(c, true))))
            .then(Commands.literal("old").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(StarfallCommands::old)))
            .then(Commands.literal("tonight").executes(StarfallCommands::tonight))
            .then(Commands.literal("veins").executes(StarfallCommands::veins));
    }

    private static void line(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }

    private static int now(CommandContext<CommandSourceStack> c, BlockPos pos, String metal, String size) throws CommandSyntaxException {
        ServerLevel ow = c.getSource().getServer().overworld();
        StarfallData data = StarfallDataLoader.current();
        if (!data.valid()) throw NO_DATA.create();
        StarfallData.AgeEntry entry = data.entryFor(StarfallService.currentAge(c.getSource().getServer()))
            .orElseGet(() -> data.ages().values().iterator().next());
        String m = metal == null ? entry.metal() : metal.toLowerCase(Locale.ROOT);
        String s = size == null ? entry.size() : size.toLowerCase(Locale.ROOT);
        if (data.metal(m).isEmpty() || data.size(s).isEmpty()) {
            c.getSource().sendFailure(Component.literal("Unknown metal or size: metals " + data.metals().keySet() + ", sizes " + data.sizes().keySet()));
            return 0;
        }
        int x;
        int z;
        boolean forced = pos != null;
        if (pos != null) {
            x = pos.getX();
            z = pos.getZ();
        } else {
            Optional<int[]> col = StarfallService.chooseSite(ow, entry.minDistance(), entry.maxDistance(), new java.util.Random());
            if (col.isEmpty()) {
                c.getSource().sendFailure(Component.literal("No free site within " + entry.minDistance() + ".." + entry.maxDistance() + " blocks"));
                return 0;
            }
            x = col.get()[0];
            z = col.get()[1];
        }
        SkySavedData d = SkySavedData.get(c.getSource().getServer());
        StarfallSite site = StarfallService.plan(ow, d, x, z, m, s, ow.getGameTime() + StarfallRules.STREAK_TICKS, forced);
        c.getSource().sendSuccess(() -> Component.literal("Starfall " + site + " in " + StarfallRules.STREAK_TICKS + " ticks"), true);
        return (int) site.id;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        SkySavedData d = SkySavedData.get(src.getServer());
        long now = src.getServer().overworld().getGameTime();
        Vec3 at = src.getPosition();
        line(src, "Starfall: " + d.sites().size() + " sites, " + d.openCount() + " open; nights since a fall " + d.nightsSinceFall()
            + ", last metal " + (d.lastMetal().isEmpty() ? "-" : d.lastMetal()) + ", guaranteed tonight " + d.guaranteedTonight()
            + ", last roll day " + d.lastRollDay() + ", data " + (StarfallDataLoader.current().valid() ? "ok" : "MISSING"));
        for (StarfallSite s : d.sites()) {
            long dist = Math.round(Math.sqrt((s.x - at.x) * (s.x - at.x) + (s.z - at.z) * (s.z - at.z)));
            String hot = StarfallRules.cold(now, s.coolsAt) ? "cold" : "hot " + (s.coolsAt - now) + " t";
            String impact = s.origin == StarfallSite.Origin.LIVE ? (s.impacted ? "impacted" : "impact in " + (s.impactTime - now) + " t") : "old";
            line(src, "#" + s.id + " " + s.state + " " + s.metal + "/" + s.size + " " + impact + ", " + dist + " blocks away at " + s.x + " "
                + (s.centreY == Integer.MIN_VALUE ? "~" : s.centreY) + " " + s.z + ", " + hot + ", moves " + s.moves + ", cursor " + s.cursor
                + "/" + CraterCarver.plan(s).ops().size() + ", core " + (s.core == null ? "-" : s.core.toShortString()) + ", vein " + s.veinAdded);
        }
        return d.sites().size();
    }

    private static int carve(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        long id = LongArgumentType.getLong(c, "id");
        if (!StarfallService.carveNow(c.getSource().getServer().overworld(), id)) throw NO_SITE.create();
        c.getSource().sendSuccess(() -> Component.literal("Starfall #" + id + ": ticket added, carving once the chunks are loaded"), true);
        return 1;
    }

    private static int hot(CommandContext<CommandSourceStack> c, boolean hot) throws CommandSyntaxException {
        long id = LongArgumentType.getLong(c, "id");
        if (!StarfallService.setHot(c.getSource().getServer().overworld(), id, hot)) throw NO_SITE.create();
        c.getSource().sendSuccess(() -> Component.literal("Starfall #" + id + (hot ? " glows again" : " cooled")), true);
        return 1;
    }

    private static int old(CommandContext<CommandSourceStack> c) {
        BlockPos pos = BlockPosArgument.getBlockPos(c, "pos");
        ServerLevel ow = c.getSource().getServer().overworld();
        StarfallSite s = StarfallService.oldCrater(ow, SkySavedData.get(c.getSource().getServer()), pos.getX(), pos.getZ(), true);
        c.getSource().sendSuccess(() -> Component.literal("Old crater " + s), true);
        return (int) s.id;
    }

    private static int tonight(CommandContext<CommandSourceStack> c) {
        StarfallService.requestTonight(c.getSource().getServer());
        c.getSource().sendSuccess(() -> Component.literal("The next clear night falls for sure"), true);
        return 1;
    }

    private static int veins(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        if (!ModList.get().isLoaded(StarfallVeins.MOD_ID)) {
            src.sendFailure(Component.literal("Immersive Engineering is not loaded"));
            return 0;
        }
        if (!StarfallService.automationOpen(src.getServer())) line(src, "The automation Age is not open: no veins yet");
        int added = StarfallService.reconcileVeins(src.getServer(), true);
        line(src, "Veins re-added: " + added);
        for (StarfallSite s : SkySavedData.get(src.getServer()).sites()) {
            if (s.state != StarfallSite.State.CARVED) continue;
            line(src, "#" + s.id + " at " + s.x + " " + s.z + ": " + String.join("; ", StarfallVeins.describe(s.x, s.z)));
        }
        return added;
    }
}
