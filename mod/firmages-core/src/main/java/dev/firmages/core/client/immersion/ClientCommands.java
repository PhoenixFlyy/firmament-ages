package dev.firmages.core.client.immersion;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import dev.firmages.core.FirmagesCore;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Client commands (SPEC §21): {@code /firmages_client emi_audit} writes the EMI audit (§21.4); GuideME's client command
 * {@code guidemec} is removed (§21.7). GuideME 21.1.19 [verified, javap: {@code GuideMEClient.registerClientCommands},
 * {@code GuideClientCommand}] registers {@code /guidemec open <guide> [page]} without a permission check, which opened
 * the AE2 guide with its ExtendedAE, AdvancedAE and MEGA Cells pages from Dawn on. Brigadier has no removal API and
 * {@code addChild} only merges, so the root's {@code children} and {@code literals} maps lose the node by reflection
 * (Brigadier is not obfuscated). GuideME's G key on a held item keeps working.
 */
@EventBusSubscriber(modid = FirmagesCore.MOD_ID, value = Dist.CLIENT)
public final class ClientCommands {
    static final String GUIDEME_CLIENT_COMMAND = "guidemec";

    private ClientCommands() {}

    @SubscribeEvent(priority = EventPriority.LOWEST) // after GuideME registered its command
    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        if (removeRootChild(d, GUIDEME_CLIENT_COMMAND)) FirmagesCore.LOGGER.debug("Client command /{} removed", GUIDEME_CLIENT_COMMAND);
        if (ModList.get().isLoaded("emi")) {
            d.register(Commands.literal("firmages_client").then(Commands.literal("emi_audit").executes(ctx -> {
                EmiAuditCommand.run();
                return 1;
            })));
        }
    }

    /** Removes a literal below the root. @return true if it was there */
    @SuppressWarnings("unchecked")
    static boolean removeRootChild(CommandDispatcher<?> dispatcher, String name) {
        CommandNode<?> root = dispatcher.getRoot();
        if (root.getChild(name) == null) return false;
        try {
            for (String field : new String[] {"children", "literals"}) {
                Field f = CommandNode.class.getDeclaredField(field);
                f.setAccessible(true);
                ((Map<String, ?>) f.get(root)).remove(name);
            }
            return root.getChild(name) == null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            FirmagesCore.LOGGER.warn("Cannot remove the client command /{}: {}", name, e.toString());
            return false;
        }
    }

    /** EMI classes stay behind this holder, so the command class loads without EMI. */
    private static final class EmiAuditCommand {
        static void run() {
            Minecraft mc = Minecraft.getInstance();
            dev.firmages.core.compat.emi.EmiAudit.writeJson().whenCompleteAsync((file, err) -> {
                if (mc.player == null) return;
                Component msg = err != null ? Component.literal("EMI audit failed: " + err.getMessage()).withStyle(ChatFormatting.RED)
                    : file == null ? Component.literal("EMI is still loading.").withStyle(ChatFormatting.GRAY)
                    : Component.literal("EMI audit written to logs/" + file.getFileName()).withStyle(ChatFormatting.GRAY);
                mc.player.displayClientMessage(msg, false);
            }, mc);
        }
    }
}
