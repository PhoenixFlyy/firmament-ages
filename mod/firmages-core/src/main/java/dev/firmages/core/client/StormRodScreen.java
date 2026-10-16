package dev.firmages.core.client;

import dev.firmages.core.sky.StormRodMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The Storm Rod's basin (SPEC §26.5): a plain container screen drawn in the vanilla panel colours, sand on the left,
 * storm glass on the right. No texture file: the panel, the slot frames and the arrow are filled rectangles.
 */
public final class StormRodScreen extends AbstractContainerScreen<StormRodMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_DARK = 0xFF373737;

    public StormRodScreen(StormRodMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 186;
        inventoryLabelY = StormRodMenu.INV_Y - 11;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, DARK);
        g.fill(x, y, x + imageWidth - 1, y + imageHeight - 1, LIGHT);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, PANEL);
        for (Slot s : menu.slots) {
            int sx = x + s.x - 1;
            int sy = y + s.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, LIGHT);
            g.fill(sx, sy, sx + 17, sy + 17, SLOT_DARK);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT);
        }
        // arrow from the sand to the glass
        int ax = x + StormRodMenu.INPUT_X + 4 * 18 + 2;
        int ay = y + StormRodMenu.GRID_Y + 2 * 18 - 1;
        g.fill(ax, ay - 1, ax + 6, ay + 1, DARK);
        for (int i = 0; i < 4; i++) g.fill(ax + 6 + i, ay - 4 + i, ax + 7 + i, ay + 4 - i, DARK);
    }
}
