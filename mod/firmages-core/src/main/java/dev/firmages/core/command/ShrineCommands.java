package dev.firmages.core.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.ceremony.CeremonyService;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.shrine.ShrineSavedData;
import dev.firmages.core.shrine.ShrineService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * {@code /firmages shrine status|locate|relics|extract <plinth>|simulate_pray} and
 * {@code /firmages ceremony preview <stage> [full|short]} (SPEC §10).
 */
final class ShrineCommands {
    private static final SimpleCommandExceptionType SIMULATE_DISABLED = new SimpleCommandExceptionType(
        Component.literal("Simulation is disabled; set debug.allowSimulate = true in firmages-server.toml"));
    private static final SimpleCommandExceptionType NO_HEART = new SimpleCommandExceptionType(Component.literal("No shrine heart is placed"));
    private static final SimpleCommandExceptionType UNKNOWN_AGE = new SimpleCommandExceptionType(Component.literal("Unknown Age"));

    private ShrineCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> shrine() {
        return Commands.literal("shrine")
            .then(Commands.literal("status").executes(ShrineCommands::status))
            .then(Commands.literal("locate").executes(ShrineCommands::locate))
            .then(Commands.literal("relics").executes(ShrineCommands::relics))
            .then(Commands.literal("extract").then(Commands.argument("plinth", BlockPosArgument.blockPos()).executes(ShrineCommands::extract)))
            .then(Commands.literal("simulate_pray").executes(ShrineCommands::simulatePray));
    }

    static LiteralArgumentBuilder<CommandSourceStack> ceremony() {
        return Commands.literal("ceremony").then(Commands.literal("preview").then(Commands.argument("stage", StringArgumentType.word())
            .suggests((c, b) -> SharedSuggestionProvider.suggest(AgeId.all().stream().skip(1).map(AgeId::id), b))
            .executes(c -> preview(c, true))
            .then(Commands.literal("full").executes(c -> preview(c, true)))
            .then(Commands.literal("short").executes(c -> preview(c, false)))));
    }

    private static void line(CommandSourceStack src, Component c) {
        src.sendSuccess(() -> c, false);
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        List<Component> lines = ShrineService.status(c.getSource().getServer());
        lines.forEach(l -> line(c.getSource(), l));
        CeremonyService.last().ifPresent(p -> line(c.getSource(), Component.literal("Last ceremony: " + p.stage() + (p.full() ? " FULL" : " SHORT")
            + " tier " + p.tier() + "; " + CeremonyService.sentCount() + " sent since start")));
        return lines.size();
    }

    private static int locate(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        GlobalPos gp = ShrineSavedData.get(c.getSource().getServer()).heart().orElseThrow(NO_HEART::create);
        BlockPos p = gp.pos();
        line(c.getSource(), Component.literal("Shrine heart at " + p.getX() + " " + p.getY() + " " + p.getZ() + " in " + gp.dimension().location()));
        return 1;
    }

    private static int relics(CommandContext<CommandSourceStack> c) {
        var relics = ShrineSavedData.get(c.getSource().getServer()).relics().values();
        line(c.getSource(), Component.literal(relics.size() + " relic(s)"));
        relics.stream().sorted(java.util.Comparator.comparingInt(ShrineSavedData.Relic::tier)).forEach(r -> line(c.getSource(),
            Component.literal("  tier " + r.tier() + ": " + r.item() + " on " + r.plinth().toShortString())));
        return relics.size();
    }

    private static int extract(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "plinth");
        ServerLevel level = src.getLevel();
        Optional<ItemStack> out = ShrineService.extract(level, pos);
        if (out.isEmpty()) {
            src.sendFailure(Component.literal("No plinth with an item at " + pos.toShortString()));
            return 0;
        }
        ItemStack stack = out.get();
        ServerPlayer player = src.getPlayer();
        String what = stack.toString();
        if (player != null && player.getInventory().add(stack)) {
            src.sendSuccess(() -> Component.literal("Extracted " + what + " into your inventory"), true);
        } else {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, stack);
            src.sendSuccess(() -> Component.literal("Extracted " + what + "; dropped on the plinth"), true);
        }
        return 1;
    }

    private static int simulatePray(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        if (!ServerConfig.allowSimulate()) throw SIMULATE_DISABLED.create();
        Component no = ShrineService.simulatePray(c.getSource().getServer());
        if (no != null) {
            c.getSource().sendFailure(no);
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Prayer completed (rites and praying players skipped)"), true);
        return 1;
    }

    private static int preview(CommandContext<CommandSourceStack> c, boolean full) throws CommandSyntaxException {
        AgeId age = AgeId.byId(StringArgumentType.getString(c, "stage")).filter(a -> a != AgeId.DAWN).orElseThrow(UNKNOWN_AGE::create);
        ServerPlayer player = c.getSource().getPlayerOrException();
        CeremonyService.preview(player, age, full);
        c.getSource().sendSuccess(() -> Component.literal("Ceremony preview " + age.id() + (full ? " FULL" : " SHORT") + " (no grant)"), false);
        return 1;
    }
}
