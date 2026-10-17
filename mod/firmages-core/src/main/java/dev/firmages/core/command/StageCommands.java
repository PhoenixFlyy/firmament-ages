package dev.firmages.core.command;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.shrine.ShrineService;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@code /firmages stage} (SPEC §10.2, round G2, Felix: everything about stages under {@code /firmages}): grant, revoke,
 * list and clear with the semantics of ProgressiveStages' {@code /stage} (permission 2 like {@code /stage}), through its
 * API, never through a command string. Age stages ({@code dawn}, {@code age_0} .. {@code age_9}) take the shrine's way
 * ({@link ShrineService#grantTo}): AgeState, the SHORT ceremony and the Age swap follow through the stage event as for
 * any grant outside the shrine, and {@link StageRules} keeps the Ages contiguous.
 */
public final class StageCommands {
    /** A grant with missing dependencies runs without them when the same source repeats it within this window (as {@code /stage}). */
    static final long BYPASS_WINDOW_MS = 10_000L;
    private static final ServerControl.Confirmations BYPASS = new ServerControl.Confirmations(BYPASS_WINDOW_MS);

    static final List<String> HELP = List.of(
        "/firmages stage grant <targets> <stage>: as /stage grant; with missing dependencies run it again within 10 s to grant anyway",
        "/firmages stage revoke <targets> <stage>: as /stage revoke",
        "/firmages stage list [<target>]: every stage, the held ones ticked",
        "/firmages stage clear <targets>: revokes every stage (dawn and mob_0 included, as /stage bulk revoke)",
        "Age stages (dawn, age_0..age_9) take the shrine's way: a grant hands out every earlier Age the player lacks first, "
            + "a revoke takes every later Age too; the world's Age, the short ceremony and the Age swap follow. "
            + "/firmages ages simulate only changes the world's Age state (tests, debug.allowSimulate).");

    private StageCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> stage() {
        return Commands.literal("stage")
            .executes(StageCommands::help)
            .then(Commands.literal("grant").then(Commands.argument("targets", EntityArgument.players())
                .then(stageArg().executes(c -> change(c, true)))))
            .then(Commands.literal("revoke").then(Commands.argument("targets", EntityArgument.players())
                .then(stageArg().executes(c -> change(c, false)))))
            .then(Commands.literal("list")
                .executes(c -> list(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(c -> list(c.getSource(), EntityArgument.getPlayer(c, "target")))))
            .then(Commands.literal("clear").then(Commands.argument("targets", EntityArgument.players())
                .executes(StageCommands::clear)));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> stageArg() {
        return Commands.argument("stage", StringArgumentType.word())
            .suggests((c, b) -> SharedSuggestionProvider.suggest(stageIds(), b));
    }

    /** Every stage of the stage files (ProgressiveStages' loaded definitions), Ages first. */
    static List<String> stageIds() {
        try {
            List<String> ids = ProgressiveStagesAPI.getAllStageIds().stream().map(StageId::getPath).toList();
            return StageRules.listOrder(ids);
        } catch (RuntimeException | LinkageError e) {
            FirmagesCore.LOGGER.error("Cannot read the ProgressiveStages stage list", e);
            return List.of();
        }
    }

    private static int help(CommandContext<CommandSourceStack> c) {
        for (String l : HELP) c.getSource().sendSuccess(() -> Component.literal(l), false);
        return HELP.size();
    }

    private static int change(CommandContext<CommandSourceStack> c, boolean grant) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
        String raw = StringArgumentType.getString(c, "stage");
        StageId id = StageId.tryParse(raw);
        if (id == null || !ProgressiveStagesAPI.stageExists(id)) {
            src.sendFailure(Component.literal("Unknown stage '" + raw + "'"));
            return 0;
        }
        Optional<AgeId> age = id.isDefaultNamespace() ? AgeId.byId(id.getPath()) : Optional.empty();
        int n = 0;
        for (ServerPlayer p : targets) {
            if (age.isPresent()) n += grant ? grantAge(src, p, age.get()) : revokeAge(src, p, age.get());
            else n += grant ? grantStage(src, p, id) : revokeStage(src, p, id);
        }
        return n;
    }

    /** As ProgressiveStages' {@code /stage grant}: refused while held; missing dependencies ask for a repeat within 10 s. */
    private static int grantStage(CommandSourceStack src, ServerPlayer p, StageId id) {
        String who = p.getGameProfile().getName();
        if (ProgressiveStagesAPI.hasStage(p, id)) {
            src.sendFailure(Component.literal(who + " already has stage " + id.getPath()));
            return 0;
        }
        List<StageId> missing = ProgressiveStagesAPI.getMissingDependencies(p, id);
        if (!missing.isEmpty() && !StageConfig.isLinearProgression()) {
            String deps = String.join(", ", missing.stream().map(StageId::getPath).toList());
            String key = src.getTextName() + "|" + p.getUUID() + "|" + id;
            long now = Util.getMillis();
            if (BYPASS.confirm(key, now)) {
                ProgressiveStagesAPI.grantStageBypass(p, id, StageCause.COMMAND);
                src.sendSuccess(() -> Component.literal("Granted stage " + id.getPath() + " to " + who + " without its dependencies (" + deps + ")")
                    .withStyle(ChatFormatting.YELLOW), true);
                return 1;
            }
            BYPASS.ask(key, now);
            src.sendFailure(Component.literal("Cannot grant " + id.getPath() + " to " + who + ": missing " + deps
                + ". Run the same command again within 10 seconds to grant it anyway."));
            return 0;
        }
        if (!ProgressiveStagesAPI.grantStage(p, id, StageCause.COMMAND) && !ProgressiveStagesAPI.hasStage(p, id)) {
            src.sendFailure(Component.literal("ProgressiveStages refused " + id.getPath() + " for " + who));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Granted stage " + id.getPath() + " to " + who), true);
        return 1;
    }

    private static int revokeStage(CommandSourceStack src, ServerPlayer p, StageId id) {
        String who = p.getGameProfile().getName();
        if (!ProgressiveStagesAPI.hasStage(p, id)) {
            src.sendFailure(Component.literal(who + " does not have stage " + id.getPath()));
            return 0;
        }
        ProgressiveStagesAPI.revokeStage(p, id, StageCause.COMMAND);
        src.sendSuccess(() -> Component.literal("Revoked stage " + id.getPath() + " from " + who), true);
        return 1;
    }

    /** The Age path: every missing Age up to {@code age} in order, each the shrine's way. */
    private static int grantAge(CommandSourceStack src, ServerPlayer p, AgeId age) {
        String who = p.getGameProfile().getName();
        List<AgeId> todo = new ArrayList<>(StageRules.agesToGrant(heldAges(p), age));
        todo.removeIf(a -> !ProgressiveStagesAPI.stageExists(a.id())); // an Age without a stage file (GameTest stand-ins)
        if (todo.isEmpty()) {
            src.sendFailure(Component.literal(who + " already has " + age.id()));
            return 0;
        }
        List<String> done = new ArrayList<>();
        for (AgeId a : todo) {
            if (!ShrineService.grantTo(p, a, StageCause.COMMAND)) {
                src.sendFailure(Component.literal("ProgressiveStages refused " + a.id() + " for " + who
                    + (done.isEmpty() ? "" : "; granted before: " + String.join(", ", done))));
                return 0;
            }
            done.add(a.id());
        }
        String with = done.size() > 1 ? " (with " + String.join(", ", done.subList(0, done.size() - 1)) + ")" : "";
        src.sendSuccess(() -> Component.literal("Granted " + age.id() + " to " + who + with), true);
        return 1;
    }

    /** The Age path of a revoke: {@code age} and every later Age the player has, latest first. */
    private static int revokeAge(CommandSourceStack src, ServerPlayer p, AgeId age) {
        String who = p.getGameProfile().getName();
        List<AgeId> todo = StageRules.agesToRevoke(heldAges(p), age);
        if (todo.isEmpty()) {
            src.sendFailure(Component.literal(who + " does not have " + age.id()));
            return 0;
        }
        for (AgeId a : todo) ProgressiveStagesAPI.revokeStage(p, StageId.of(a.id()), StageCause.COMMAND);
        List<String> later = todo.stream().filter(a -> a != age).map(AgeId::id).toList();
        String with = later.isEmpty() ? "" : " (with " + String.join(", ", later) + ")";
        src.sendSuccess(() -> Component.literal("Revoked " + age.id() + " from " + who + with), true);
        return 1;
    }

    /**
     * Every stage of each target, Ages latest first. A revoked Age re-derives the mob and helper stages
     * ({@code kubejs/server_scripts/stages/grants.js}), so up to three passes run until nothing is left.
     */
    private static int clear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        int total = 0;
        for (ServerPlayer p : EntityArgument.getPlayers(c, "targets")) {
            Set<String> removed = new LinkedHashSet<>();
            for (int pass = 0; pass < 3; pass++) {
                List<String> held = StageRules.clearOrder(held(p));
                if (held.isEmpty()) break;
                for (String s : held) {
                    ProgressiveStagesAPI.revokeStage(p, StageId.of(s), StageCause.COMMAND);
                    removed.add(s);
                }
            }
            Set<String> left = held(p);
            String who = p.getGameProfile().getName();
            int n = removed.size();
            if (left.isEmpty()) {
                src.sendSuccess(() -> Component.literal("Cleared " + n + " stage(s) of " + who), true);
            } else {
                src.sendFailure(Component.literal("Cleared " + n + " stage(s) of " + who + "; still held: " + String.join(", ", left)));
            }
            total += n;
        }
        return total;
    }

    /** As ProgressiveStages' {@code /stage list}: every stage, the held ones ticked, Ages first. */
    private static int list(CommandSourceStack src, ServerPlayer p) {
        Set<String> held = held(p);
        Set<String> all = new LinkedHashSet<>(stageIds());
        all.addAll(held);
        List<String> ordered = StageRules.listOrder(all);
        String top = AgeId.all().reversed().stream().filter(a -> held.contains(a.id())).findFirst().map(AgeId::id).orElse("none");
        src.sendSuccess(() -> Component.literal(p.getGameProfile().getName() + ": " + held.size() + " of " + ordered.size()
            + " stages, Age " + top).withStyle(ChatFormatting.GOLD), false);
        for (String s : ordered) {
            boolean has = held.contains(s);
            String name = ProgressiveStagesAPI.getDefinition(s).map(StageDefinition::getDisplayName).filter(d -> !d.isBlank() && !d.equals(s))
                .map(d -> " (" + d + ")").orElse("");
            Component line = Component.literal(has ? "  ✔ " : "  ✘ ").withStyle(has ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                .append(Component.literal(s + name).withStyle(has ? ChatFormatting.WHITE : ChatFormatting.GRAY));
            src.sendSuccess(() -> line, false);
        }
        return held.size();
    }

    private static Set<String> held(ServerPlayer p) {
        Set<String> out = new LinkedHashSet<>();
        for (StageId s : ProgressiveStagesAPI.getStages(p)) out.add(s.getPath());
        return out;
    }

    private static Set<AgeId> heldAges(ServerPlayer p) {
        Set<AgeId> out = EnumSet.noneOf(AgeId.class);
        for (StageId s : ProgressiveStagesAPI.getStages(p)) {
            if (s.isDefaultNamespace()) AgeId.byId(s.getPath()).ifPresent(out::add);
        }
        return out;
    }
}
