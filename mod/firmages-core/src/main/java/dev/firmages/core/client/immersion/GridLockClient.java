package dev.firmages.core.client.immersion;

import dev.firmages.core.hardship.GridLockService;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.RecipeBookType;

import java.util.List;

/**
 * The client side of the Dawn grid lock (SPEC §19.3) for the screen mixins of the player inventory and the Curios
 * screen: whether the local player's grid is locked, the paint-over of grid, arrow and result frame, and the closed
 * recipe book. Both screens draw the vanilla {@code textures/gui/container/inventory.png} at their left/top
 * position; everything of the crafting area lies in x 97..170, y 17..52 on the background colour #C6C6C6 (measured
 * from the 1.21.1 texture), the player model (x 26..75) stays clear. The blank area carries a short hint how Dawn
 * crafts (x 97..170, y 8..80: the hidden title row, the painted grid and the free background below it), and the first
 * inventory opened while locked shows the full sentence in the action bar once per client session.
 */
public final class GridLockClient {
    public static final int X0 = 97, Y0 = 17, X1 = 171, Y1 = 53;
    public static final int BACKGROUND = 0xFFC6C6C6;
    /** The hint's box: from the hidden "Crafting" title row down to the top of the main inventory (y 84). */
    public static final int HINT_Y0 = 8, HINT_Y1 = 80;
    /** The label colour of vanilla container screens. */
    public static final int LABEL = 0x404040;
    private static boolean toldThisSession;

    private GridLockClient() {}

    /** The local player's grid is locked (false in creative and spectator mode and before the first status). */
    public static boolean locked() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && GridLockService.locked(mc.player);
    }

    /** Paints the crafting area of a screen whose background is the vanilla inventory texture at {@code left, top}. */
    public static void paintOver(GuiGraphics g, int left, int top) {
        g.fill(left + X0, top + Y0, left + X1, top + Y1, BACKGROUND);
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> lines = font.split(Component.translatable("firmages.gridlock.screen_hint", placeKey(), useKey()), X1 - X0 - 1);
        int max = (HINT_Y1 - HINT_Y0) / font.lineHeight;
        for (int i = 0; i < Math.min(max, lines.size()); i++) {
            g.drawString(font, lines.get(i), left + X0, top + HINT_Y0 + i * font.lineHeight, LABEL, false);
        }
    }

    /** The first grid screen opened while locked in this client session: the full sentence in the action bar. */
    public static void tellOnce() {
        Minecraft mc = Minecraft.getInstance();
        if (toldThisSession || mc.player == null || !locked()) return;
        toldThisSession = true;
        mc.player.displayClientMessage(Component.translatable("firmages.gridlock.first_open", placeKey(), useKey()), true);
    }

    /** TerraFirmaCraft's place key ("tfc.key.place_block", V by default). */
    static Component placeKey() {
        for (KeyMapping k : Minecraft.getInstance().options.keyMappings) {
            if (k.getName().equals("tfc.key.place_block") && !k.isUnbound()) return k.getTranslatedKeyMessage();
        }
        return Component.literal("V");
    }

    /** The use key (right-click by default). */
    static Component useKey() {
        return Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage();
    }

    /** Before a grid screen lays itself out: a remembered open recipe book would shift it and show the book. */
    public static void closeRecipeBook() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && locked()) {
            mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
            tellOnce();
        }
    }
}
