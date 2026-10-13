package dev.firmages.core.mixin.ftbq;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftbquests.client.gui.quests.ChapterPanel;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The sliding chapter list inside the quest window (SPEC §18.9). FTB Quests 2101.1.36 {@code ChapterPanel.getX()}
 * [verified, javap] returns its slide position ({@code -width} collapsed .. 0 expanded) as an absolute x, which is
 * the screen's left edge only while the quest screen is fullscreen. Here it is relative to the screen, and the panel
 * is drawn clipped to the screen, so while collapsed or sliding it does not show in the margin beside the window.
 * Mouse input needs no change: FTB lets the mouse reach a child widget only inside its parent, the screen.
 */
@Mixin(value = ChapterPanel.class, remap = false)
public abstract class ChapterPanelWindowMixin {
    @ModifyReturnValue(method = "getX", at = @At("RETURN"))
    private int firmages$relativeToScreen(int x) {
        return x + ((Widget) (Object) this).getGui().getX();
    }

    @WrapMethod(method = "draw")
    private void firmages$clipToScreen(GuiGraphics graphics, Theme theme, int x, int y, int w, int h, Operation<Void> original) {
        BaseScreen gui = ((Widget) (Object) this).getGui();
        GuiHelper.pushScissor(gui.getWindow(), gui.getX(), gui.getY(), gui.width, gui.height);
        try {
            original.call(graphics, theme, x, y, w, h);
        } finally {
            GuiHelper.popScissor(gui.getWindow());
        }
    }
}
