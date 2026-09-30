package dev.firmages.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeIndex;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.config.ServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** {@code /firmages} (SPEC §10), permission level 2. M1 subset: ages, ages sync, ages simulate, dump registry, reload, selftest. */
public final class FirmagesCommands {
    private static final List<String> SUITES = List.of("all", "core", "server");
    private static final SimpleCommandExceptionType SIMULATE_DISABLED = new SimpleCommandExceptionType(
        Component.literal("Simulation is disabled; set debug.allowSimulate = true in firmages-server.toml"));
    private static final SimpleCommandExceptionType UNKNOWN_AGE = new SimpleCommandExceptionType(Component.literal("Unknown Age"));
    private static final SimpleCommandExceptionType UNKNOWN_SUITE = new SimpleCommandExceptionType(
        Component.literal("Unknown suite; use one of " + SUITES));

    private FirmagesCommands() {}

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("firmages").requires(s -> s.hasPermission(2))
            .then(Commands.literal("ages")
                .executes(FirmagesCommands::ages)
                .then(Commands.literal("sync").executes(FirmagesCommands::sync))
                .then(Commands.literal("simulate")
                    .then(Commands.literal("grant").then(stageArg().executes(c -> simulate(c, true))))
                    .then(Commands.literal("revoke").then(stageArg().executes(c -> simulate(c, false))))))
            .then(Commands.literal("dump").then(Commands.literal("registry").executes(FirmagesCommands::dumpRegistry)))
            .then(Commands.literal("reload").executes(FirmagesCommands::reload))
            .then(Commands.literal("selftest")
                .executes(c -> selftest(c, "all"))
                .then(Commands.argument("suite", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(SUITES, b))
                    .executes(c -> selftest(c, StringArgumentType.getString(c, "suite"))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> stageArg() {
        return Commands.argument("stage", StringArgumentType.word())
            .suggests((c, b) -> SharedSuggestionProvider.suggest(AgeId.all().stream().map(AgeId::id), b));
    }

    private static void line(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }

    private static int ages(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        MinecraftServer server = src.getServer();
        AgeService.Status st = AgeService.status(server);
        AgeIndex idx = AgeIndex.current();
        src.sendSuccess(() -> Component.literal("Firmament Ages").withStyle(ChatFormatting.GOLD), false);
        line(src, "Unlocked (" + st.live().unlocked().size() + "): " + String.join(", ", st.live().unlockedIds()));
        line(src, "Locked (" + st.live().locked().size() + "): " + String.join(", ", st.live().locked().stream().map(AgeId::id).toList()));
        line(src, "AgeState version " + st.live().version() + ", last reload at game time " + st.live().lastReloadGameTime());
        line(src, "Mirror " + st.mirrorPath() + ": on disk " + st.mirrorOnDisk().status() + " (" + st.mirrorOnDisk().detail() + "), last write " + st.lastMirrorWrite());
        line(src, "Boot snapshot " + st.boot().snapshot().unlockedIds() + " from " + st.boot().source() + " (" + st.boot().detail()
            + "), used during load: " + st.bootUsed() + ", stale binding answers: " + st.bootAnswersStale() + ", boot reload requested: " + st.bootReconcileRequested());
        String reload = switch (st.phase()) {
            case IDLE -> "idle";
            case PENDING -> "pending, due in " + st.ticksUntilDue() + " ticks";
            case RUNNING -> "running" + (st.followUpQueued() ? ", follow-up queued" : "");
        };
        line(src, "Reload: " + reload + "; " + st.reloadsStarted() + " since start; last: " + st.lastReloadResult()
            + (st.lastReloadMillis() >= 0 ? " (" + String.format(Locale.ROOT, "%.1f", st.lastReloadMillis() / 1000.0) + " s)" : ""));
        if (idx.report() == null) {
            line(src, "Age index: not built");
        } else {
            line(src, "Age index gen " + idx.generation() + ": " + idx.items().size() + " items, " + idx.blocks().size() + " blocks, "
                + idx.fluids().size() + " fluids; items per Age " + idx.report().itemTags().perAge()
                + "; datapack loads captured: " + AgeIndex.loadsBegun());
        }
        if (idx.misconfigured()) {
            src.sendSuccess(() -> Component.literal("All firmages:age_* tags are empty: the Age gate cannot lock anything").withStyle(ChatFormatting.RED), false);
        }
        if (AgeService.multiTeamDetected()) {
            src.sendSuccess(() -> Component.literal("More than one team holds Age stages: " + AgeService.teamsWithAges()).withStyle(ChatFormatting.RED), false);
        }
        return st.live().unlocked().size();
    }

    private static int sync(CommandContext<CommandSourceStack> c) {
        List<AgeId> added = AgeService.sync(c.getSource().getServer());
        line(c.getSource(), added.isEmpty() ? "AgeState already matches ProgressiveStages; reload requested"
            : "Added from ProgressiveStages: " + added + "; reload requested");
        return added.size();
    }

    private static int simulate(CommandContext<CommandSourceStack> c, boolean grant) throws CommandSyntaxException {
        if (!ServerConfig.allowSimulate()) throw SIMULATE_DISABLED.create();
        AgeId age = AgeId.byId(StringArgumentType.getString(c, "stage")).orElseThrow(UNKNOWN_AGE::create);
        boolean changed = AgeService.simulate(c.getSource().getServer(), age, grant);
        c.getSource().sendSuccess(() -> Component.literal((grant ? "Simulated grant of " : "Simulated revoke of ") + age.id()
            + (changed ? "; reload scheduled" : "; no change")), true);
        return changed ? 1 : 0;
    }

    private static int dumpRegistry(CommandContext<CommandSourceStack> c) {
        try {
            RegistryDump.Result r = RegistryDump.write();
            line(c.getSource(), "Wrote " + r.items() + " items, " + r.blocks() + " blocks, " + r.fluids() + " fluids of " + r.mods() + " mods to " + r.file());
            return r.items();
        } catch (IOException e) {
            FirmagesCore.LOGGER.error("Registry dump failed", e);
            c.getSource().sendFailure(Component.literal("Registry dump failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        AgeService.forceReload("operator /firmages reload");
        c.getSource().sendSuccess(() -> Component.literal("Filtered datapack reload on the next tick"), true);
        return 1;
    }

    private static int selftest(CommandContext<CommandSourceStack> c, String suite) throws CommandSyntaxException {
        MinecraftServer server = c.getSource().getServer();
        List<SelfTest.Suite> suites = switch (suite) {
            case "all" -> List.of(new CoreSuite(), new ServerSuite(server));
            case "core" -> List.of(new CoreSuite());
            case "server" -> List.of(new ServerSuite(server));
            default -> throw UNKNOWN_SUITE.create();
        };
        SelfTest.Report report = SelfTest.run(suites);
        Path file = FMLPaths.GAMEDIR.get().resolve("logs").resolve(SelfTest.REPORT_FILE);
        try {
            report.write(file);
        } catch (IOException e) {
            FirmagesCore.LOGGER.error("Cannot write {}", file, e);
        }
        for (SelfTest.Case k : report.cases()) {
            String msg = "[" + (k.passed() ? "PASS" : "FAIL") + "] " + k.suite() + "/" + k.name() + (k.detail().isEmpty() ? "" : ": " + k.detail());
            if (k.passed()) FirmagesCore.LOGGER.info("selftest {}", msg);
            else FirmagesCore.LOGGER.warn("selftest {}", msg);
            if (!k.passed()) c.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.RED), false);
        }
        String summary = "selftest " + suite + ": " + report.passed() + " passed, " + report.failed() + " failed; report " + file;
        FirmagesCore.LOGGER.info(summary);
        c.getSource().sendSuccess(() -> Component.literal(summary).withStyle(report.failed() == 0 ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return (int) report.failed();
    }
}
