package dev.firmages.core.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.firmages.core.ark.ArkData;
import dev.firmages.core.ark.ArkService;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.origin.OriginService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /firmages ark status | check <island> | grant <island> <group> | reset <island>} (SPEC §31.9, operators like
 * every {@code /firmages} command). {@code check} runs the plinth's work check as a right-click would; {@code grant}
 * and {@code reset} are debug commands and need {@code debug.allowSimulate}. Islands are 0..8 (the shrine Ages).
 */
final class ArkCommands {
    private ArkCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> ark() {
        return Commands.literal("ark")
            .then(Commands.literal("status").executes(ArkCommands::status))
            .then(Commands.literal("check").then(island().executes(ArkCommands::check)))
            .then(Commands.literal("grant").then(island().then(Commands.argument("group", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(ArkData.ALL_GROUPS, b)).executes(ArkCommands::grant))))
            .then(Commands.literal("reset").then(island().executes(ArkCommands::reset)));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> island() {
        return Commands.argument("island", IntegerArgumentType.integer(0, ArkData.ISLANDS - 1));
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        for (String l : ArkService.statusLines(c.getSource().getServer())) c.getSource().sendSuccess(() -> Component.literal(l), false);
        return Integer.bitCount(dev.firmages.core.origin.OriginSavedData.get(c.getSource().getServer()).ark().completeMask());
    }

    private static int check(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        int i = IntegerArgumentType.getInteger(c, "island");
        ServerLevel level = OriginService.level(server);
        if (level == null || !ArkService.awake(server)) {
            c.getSource().sendFailure(Component.literal(level == null ? "The Origin is not loaded" : "The island sleeps: the finale is not won"));
            return 0;
        }
        boolean now = ArkService.checkWork(level, i);
        boolean done = ArkService.progress(server, i).has("work");
        c.getSource().sendSuccess(() -> Component.literal("Island age_" + i + ": work " + (now ? "found now" : done ? "was already done" : "not found on the island")), false);
        return done ? 1 : 0;
    }

    private static int grant(CommandContext<CommandSourceStack> c) {
        if (!allowed(c)) return 0;
        MinecraftServer server = c.getSource().getServer();
        int i = IntegerArgumentType.getInteger(c, "island");
        String group = StringArgumentType.getString(c, "group");
        if (!ArkData.ALL_GROUPS.contains(group)) {
            c.getSource().sendFailure(Component.literal("Unknown group " + group + "; one of " + ArkData.ALL_GROUPS));
            return 0;
        }
        if (!ArkService.grant(server, i, group)) {
            c.getSource().sendFailure(Component.literal("Island age_" + i + ": " + group + " not granted (already done, no rules, or the finale is not won)"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Island age_" + i + ": " + group + " granted"), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> c) {
        if (!allowed(c)) return 0;
        int i = IntegerArgumentType.getInteger(c, "island");
        int removed = ArkService.reset(c.getSource().getServer(), i);
        c.getSource().sendSuccess(() -> Component.literal("Island age_" + i + " reset, " + removed + " grown blocks removed"), true);
        return 1;
    }

    private static boolean allowed(CommandContext<CommandSourceStack> c) {
        if (ServerConfig.allowSimulate()) return true;
        c.getSource().sendFailure(Component.literal("Debug command: set debug.allowSimulate = true in firmages-server.toml"));
        return false;
    }
}
