package dev.firmages.core.client.immersion;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.hardship.InventoryRows;
import dev.firmages.core.hardship.InventoryRowsService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;

/**
 * The client side of the inventory rows (SPEC §19.4): in every container screen the local player's locked inventory
 * slots are painted over with the panel colour #C6C6C6 of {@link GridLockClient}, slot frame included, so a locked row
 * reads as plain panel like the hidden crafting grid. NeoForge 21.1 {@code AbstractContainerScreen.render} [verified,
 * javap] posts {@code ContainerScreenEvent.Render.Background} right after the background and before the widgets and the
 * slots; the slot itself is inactive ({@code mixin/SlotMixin}), so no item, highlight or tooltip is drawn over the
 * grey. Creative mode is exempt, so the creative inventory is untouched. No text (SPEC §18).
 */
@EventBusSubscriber(modid = FirmagesCore.MOD_ID, value = Dist.CLIENT)
public final class InventoryRowsClient {
    private InventoryRowsClient() {}

    @SubscribeEvent
    public static void paintLockedRows(ContainerScreenEvent.Render.Background event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int open = InventoryRowsService.openRows(mc.player);
        if (open >= InventoryRows.ALL) return;
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        Inventory inventory = mc.player.getInventory();
        GuiGraphics g = event.getGuiGraphics();
        int left = screen.getGuiLeft(), top = screen.getGuiTop();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container != inventory || !InventoryRows.locked(slot.getContainerSlot(), open)) continue;
            g.fill(left + slot.x - 1, top + slot.y - 1, left + slot.x + 17, top + slot.y + 17, GridLockClient.BACKGROUND);
        }
    }
}
