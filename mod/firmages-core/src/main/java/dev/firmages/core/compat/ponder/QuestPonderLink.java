package dev.firmages.core.compat.ponder;

import dev.architectury.event.EventResult;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.client.immersion.ClientAgeView;
import dev.firmages.core.compat.ftbquests.FtbQuestsScreens;
import dev.ftb.mods.ftblibrary.ui.CustomClickEvent;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Ponder from a quest picture and back (SPEC §22.5). A picture with
 * {@code click_action: custom_event:firmages:ponder/<namespace>/<path>} (FTB Quests posts FTB Library's
 * {@code CustomClickEvent} on the client, no server round trip [verified, javap]) opens the Ponder scenes of the item
 * {@code <namespace>:<path>} directly, without {@code /ponder}, which is for operators only (SPEC §21.6). An item of a
 * locked Age or one without scenes opens nothing. When the Ponder screens close (Escape, or the last one left), the
 * Chronicle opens again at the quest the player came from. Client only, with Ponder, FTB Library and FTB Quests.
 */
public final class QuestPonderLink {
    public static final String PREFIX = "ponder/";

    private static boolean returnToBook;
    private static boolean ponderSeen;
    /** Ticks to wait for the Ponder screen after the click before giving up. */
    private static int waitTicks;

    private QuestPonderLink() {}

    static void register() {
        CustomClickEvent.EVENT.register(QuestPonderLink::onClick);
        NeoForge.EVENT_BUS.addListener(QuestPonderLink::onTick);
    }

    /** {@code firmages:ponder/create/rope_pulley} to {@code create:rope_pulley}; null for any other id. */
    @Nullable
    public static ResourceLocation itemOf(ResourceLocation eventId) {
        if (!FirmagesCore.MOD_ID.equals(eventId.getNamespace()) || !eventId.getPath().startsWith(PREFIX)) return null;
        String rest = eventId.getPath().substring(PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) return null;
        return ResourceLocation.tryBuild(rest.substring(0, slash), rest.substring(slash + 1));
    }

    private static EventResult onClick(CustomClickEvent event) {
        ResourceLocation id = itemOf(event.id());
        if (id == null) return EventResult.pass();
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null || ClientAgeView.itemLocked(item) || !PonderIndex.getSceneAccess().doScenesExistForId(id)) {
            FirmagesCore.LOGGER.debug("Quest Ponder link {}: no scene to open", id);
            return EventResult.interruptTrue();
        }
        returnToBook = true;
        ponderSeen = false;
        waitTicks = 40;
        ScreenOpener.transitionTo(PonderUI.of(new ItemStack(item)));
        return EventResult.interruptTrue();
    }

    private static void onTick(ClientTickEvent.Post event) {
        if (!returnToBook) return;
        Screen s = Minecraft.getInstance().screen;
        if (s instanceof AbstractSimiScreen) {
            ponderSeen = true;
            return;
        }
        if (!ponderSeen && --waitTicks > 0) return; // the quest screen is still up for the tick of the click
        boolean back = ponderSeen;
        returnToBook = false;
        ponderSeen = false;
        if (back && s == null && Minecraft.getInstance().player != null) FtbQuestsScreens.open();
    }
}
