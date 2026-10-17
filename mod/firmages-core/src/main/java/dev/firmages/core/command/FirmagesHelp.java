package dev.firmages.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** {@code /firmages} without arguments (SPEC §10, round G2): every subcommand the source may use, one usage line each. */
public final class FirmagesHelp {
    private FirmagesHelp() {}

    static int list(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        List<String> lines = lines(src.getServer().getCommands().getDispatcher(), src);
        src.sendSuccess(() -> Component.literal("Firmament Ages commands").withStyle(ChatFormatting.GOLD), false);
        for (String l : lines) src.sendSuccess(() -> Component.literal(l), false);
        return lines.size();
    }

    /** "/firmages &lt;usage&gt;" per child of the {@code firmages} node, sorted by name; empty without the node. */
    static List<String> lines(CommandDispatcher<CommandSourceStack> d, CommandSourceStack src) {
        CommandNode<CommandSourceStack> root = d.getRoot().getChild("firmages");
        List<String> out = new ArrayList<>();
        if (root == null) return out;
        Map<CommandNode<CommandSourceStack>, String> usage = d.getSmartUsage(root, src);
        root.getChildren().stream().filter(n -> n.canUse(src)).sorted((a, b) -> a.getName().compareTo(b.getName()))
            .forEach(n -> out.add("/firmages " + unbracket(n.getName(), usage.getOrDefault(n, n.getUsageText()))));
        return out;
    }

    /** Brigadier brackets a child of an executable node ("[stage] ..."); the list names it plainly ("stage ..."). */
    static String unbracket(String name, String usage) {
        String b = "[" + name + "]";
        return usage.startsWith(b) ? name + usage.substring(b.length()) : usage;
    }
}
