package dev.firmages.core.immersion;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.ParsedCommandNode;
import dev.firmages.core.FirmagesCore;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.event.CommandEvent;

import java.util.List;

/**
 * {@code /ponder} is for operators only (SPEC §21.6). Ponder 1.0.82 registers {@code /ponder [index|tags|<scene>]}
 * on the server with permission level 0 [verified, javap: {@code PonderCommand}], which opened every scene of every
 * later Age (Create, its addons, Mekanism Ponders) from Dawn on. Below level 2 the command is cancelled without a
 * word (Felix 2026-10-17, SPEC §18: no hint names what replaces a removed feature); holding the Ponder key on an item
 * tooltip still opens that item's scenes (the client opens them without the command, and only for items the player
 * can hover).
 */
public final class PonderCommandGate {
    public static final String COMMAND = "ponder";

    private PonderCommandGate() {}

    public static void onCommand(CommandEvent event) {
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        List<ParsedCommandNode<CommandSourceStack>> nodes = parse.getContext().getNodes();
        if (nodes.isEmpty() || !COMMAND.equals(nodes.get(0).getNode().getName())) return;
        CommandSourceStack source = parse.getContext().getSource();
        if (source.hasPermission(2)) return;
        event.setCanceled(true);
        FirmagesCore.LOGGER.debug("/ponder refused for {}", source.getTextName());
    }
}
