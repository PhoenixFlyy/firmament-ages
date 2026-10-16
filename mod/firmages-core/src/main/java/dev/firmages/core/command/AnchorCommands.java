package dev.firmages.core.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.firmages.core.anchor.AnchorRegistry;
import dev.firmages.core.anchor.AnchorRules;
import dev.firmages.core.anchor.AnchorSavedData;
import dev.firmages.core.anchor.AnchorService;
import dev.firmages.core.anchor.SkyAnchorBlockEntity;
import dev.firmages.core.shrine.Blessing;
import dev.firmages.core.shrine.BlessingSources;
import dev.firmages.core.shrine.Blessings;
import dev.firmages.core.shrine.ShrineState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** {@code /firmages anchor list|status [pos]} (SPEC-himmel §29.7). */
final class AnchorCommands {
    private static final SimpleCommandExceptionType NO_ANCHOR = new SimpleCommandExceptionType(Component.literal("No Sky Anchor within 16 blocks"));

    private AnchorCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> anchor() {
        return Commands.literal("anchor")
            .then(Commands.literal("list").executes(AnchorCommands::list))
            .then(Commands.literal("status").executes(c -> status(c, null))
                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> status(c, BlockPosArgument.getLoadedBlockPos(c, "pos")))));
    }

    /** "0, 2, 5" for a tier mask, "none" for 0. */
    static String tierList(int mask) {
        List<String> out = new ArrayList<>();
        for (int t = 0; t < BlessingSources.TIERS; t++) if ((mask & (1 << t)) != 0) out.add(Integer.toString(t));
        return out.isEmpty() ? "none" : String.join(", ", out);
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        Map<GlobalPos, Integer> all = AnchorSavedData.get(server).anchors();
        c.getSource().sendSuccess(() -> Component.literal("Sky Anchors: " + all.size() + ", Space Age " + (AnchorService.ageOpen(server) ? "open" : "closed")
            + ", radius " + dev.firmages.core.config.ServerConfig.anchorRadius()), false);
        all.forEach((where, tiers) -> {
            int active = AnchorService.activeMask(server, tiers);
            c.getSource().sendSuccess(() -> Component.literal("  " + where.dimension().location() + " " + where.pos().toShortString() + ": shards "
                + tierList(tiers) + ", in force " + tierList(active) + (active != 0 ? " (active)" : " (" + AnchorService.status(server, tiers) + ")")), false);
        });
        return all.size();
    }

    private static int status(CommandContext<CommandSourceStack> c, @Nullable BlockPos at) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        ServerLevel level = src.getLevel();
        BlockPos pos = at != null ? at : nearest(level, BlockPos.containing(src.getPosition()));
        if (pos == null || !(level.getBlockEntity(pos) instanceof SkyAnchorBlockEntity be)) throw NO_ANCHOR.create();
        MinecraftServer server = src.getServer();
        ShrineState.Snapshot st = ShrineState.get();
        int tiers = be.tiers();
        int active = AnchorService.activeMask(server, tiers);
        AnchorRules.Status status = AnchorService.status(server, tiers);
        List<String> names = Blessings.blessingsOfMask(active).stream().map(Blessing::id).toList();
        src.sendSuccess(() -> Component.literal("Sky Anchor at " + level.dimension().location() + " " + pos.toShortString() + ": shards " + tierList(tiers)
            + ", awakened " + tierList(st == null ? 0 : BlessingSources.allTiers(st.awakened())) + ", shrine " + (st != null && st.intact() ? "intact" : "not intact")
            + ", Space Age " + (AnchorService.ageOpen(server) ? "open" : "closed")), false);
        src.sendSuccess(() -> Component.literal("  " + status + ": in force " + tierList(active) + " " + names + ", Hearthward "
            + (AnchorRules.holds(active, 0) ? "yes" : "no") + ", radius " + dev.firmages.core.config.ServerConfig.anchorRadius()), false);
        return active;
    }

    @Nullable
    private static BlockPos nearest(ServerLevel level, BlockPos from) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(from.offset(-16, -8, -16), from.offset(16, 8, 16))) {
            if (!level.getBlockState(p).is(AnchorRegistry.SKY_ANCHOR.get())) continue;
            double d = p.distSqr(from);
            if (d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        return best;
    }
}
