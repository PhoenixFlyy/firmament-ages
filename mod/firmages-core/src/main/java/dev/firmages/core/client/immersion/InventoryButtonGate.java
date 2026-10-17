package dev.firmages.core.client.immersion;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.compat.apothic.AttributesPanel;
import dev.firmages.core.compat.curios.CuriosKey;
import dev.firmages.core.immersion.InventoryButtons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Client half of {@link InventoryButtons} (SPEC §18.2): while the world's Age is before
 * {@code ui.inventoryButtonsFromAge} and the player is not in creative mode (operators in survival included),
 * <ul>
 * <li>the Curios button and Apothic Attributes' panel, toggle and filter button leave the survival inventory
 * ({@code ScreenEvent.Init.Post}, LOWEST, after both mods added them; {@code removeListener}), and Apothic's
 * remembered open panel is cleared before the layout ({@code Init.Pre}) so the inventory is not shifted;</li>
 * <li>the Curios key's presses are dropped before Curios reads them ({@code ClientTickEvent.Pre});</li>
 * <li>the Curios screen does not open ({@code ScreenEvent.Opening}); its menu, already set by
 * {@code MenuScreens.create}, is closed on both sides as the screen's own close would.</li>
 * </ul>
 * The Attributes panel is no screen (a widget of the inventory), so removing it is the whole gate. Nothing is linked:
 * class names only, and without the mods nothing matches.
 */
@EventBusSubscriber(modid = FirmagesCore.MOD_ID, value = Dist.CLIENT)
public final class InventoryButtonGate {
    private InventoryButtonGate() {}

    /** The buttons are there for the local player (before the first status the world counts as Dawn). */
    public static boolean shown() {
        LocalPlayer p = Minecraft.getInstance().player;
        return InventoryButtons.shown(ClientAgeStatus.get().inventoryButtons(), p != null && p.isCreative());
    }

    @SubscribeEvent
    public static void initPre(ScreenEvent.Init.Pre event) {
        if (event.getScreen() instanceof InventoryScreen && !shown()) AttributesPanel.keepClosed();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) // Curios adds its button at NORMAL, Apothic its panel at HIGH
    public static void initPost(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen) || shown()) return;
        List<GuiEventListener> gone = new ArrayList<>();
        for (GuiEventListener l : event.getListenersList()) if (InventoryButtons.removed(l.getClass().getName())) gone.add(l);
        for (GuiEventListener l : gone) event.removeListener(l);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre event) {
        if (Minecraft.getInstance().player != null && !shown()) CuriosKey.drain();
    }

    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        if (event.getNewScreen() == null || !InventoryButtons.screenBlocked(event.getNewScreen().getClass().getName()) || shown()) return;
        event.setCanceled(true);
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && p.containerMenu != p.inventoryMenu) {
            // LocalPlayer.closeContainer without its setScreen(null), which would re-enter setScreen
            p.connection.send(new ServerboundContainerClosePacket(p.containerMenu.containerId));
            p.containerMenu = p.inventoryMenu;
        }
    }
}
