package dev.firmages.core.mixin.ftbq;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.firmages.core.immersion.QuestWindow;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.ModalPanel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftbquests.client.gui.quests.ViewQuestPanel;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Modal panels on a screen smaller than the window (SPEC §18.9).
 * <ul>
 *   <li><b>Clamp.</b> FTB Library 2101.1.37 {@code BaseScreen.pushModalPanel} [verified, javap] does
 *       {@code panel.setX(min(panel.getX(), getWidth() - panel.getWidth() - 10))}: {@code getX()} is absolute,
 *       {@code setX} relative, so on a windowed screen every pop-up moves by the screen's offset, and a panel as large
 *       as the screen (the quest view with {@code full_screen_quest: 1}) lands at -10, cut off at the left and top.
 *       The clamp is redone in screen coordinates by {@link QuestWindow#modalPos}.</li>
 *   <li><b>Quest view drawing.</b> Certain Questing Additions 1.2.0.4 wraps {@code ViewQuestPanel.draw} [verified,
 *       decompiled]: it stores {@code getY()} (absolute), sets {@code setY(y + slide)} with the absolute draw y and
 *       finally {@code setY} of the stored value, i.e. it writes absolute values into the relative position. On a
 *       fullscreen screen both are equal; on a window the view would be drawn a screen offset too low and creep down
 *       by that offset every frame. While {@code BaseScreen.draw} draws the quest view, the view's position is made
 *       absolute and the screen's {@code getY()} reads 0, so absolute and relative agree again; afterwards the
 *       relative position is restored.</li>
 * </ul>
 */
@Mixin(value = BaseScreen.class, remap = false)
public abstract class BaseScreenModalMixin {
    @Unique
    private boolean firmages$drawingQuestView;

    @WrapOperation(method = "pushModalPanel", at = @At(value = "INVOKE", target = "Ldev/ftb/mods/ftblibrary/ui/ModalPanel;setX(I)V"))
    private void firmages$clampX(ModalPanel panel, int ftbX, Operation<Void> original) {
        BaseScreen self = (BaseScreen) (Object) this;
        int rel = panel.getX() - self.getX();
        original.call(panel, panel.getPosX() + QuestWindow.modalPos(rel, panel.getWidth(), self.getWidth()) - rel);
    }

    @WrapOperation(method = "pushModalPanel", at = @At(value = "INVOKE", target = "Ldev/ftb/mods/ftblibrary/ui/ModalPanel;setY(I)V"))
    private void firmages$clampY(ModalPanel panel, int ftbY, Operation<Void> original) {
        BaseScreen self = (BaseScreen) (Object) this;
        int rel = panel.getY() - self.getY();
        original.call(panel, panel.getPosY() + QuestWindow.modalPos(rel, panel.getHeight(), self.getHeight()) - rel);
    }

    @WrapOperation(method = "draw", at = @At(value = "INVOKE",
        target = "Ldev/ftb/mods/ftblibrary/ui/ModalPanel;draw(Lnet/minecraft/client/gui/GuiGraphics;Ldev/ftb/mods/ftblibrary/ui/Theme;IIII)V"))
    private void firmages$drawQuestView(ModalPanel panel, GuiGraphics graphics, Theme theme, int x, int y, int w, int h,
                                        Operation<Void> original) {
        if (!(panel instanceof ViewQuestPanel) || panel.getParent() != (Object) this || firmages$drawingQuestView) {
            original.call(panel, graphics, theme, x, y, w, h);
            return;
        }
        int posY = panel.getPosY();
        panel.setY(y);
        firmages$drawingQuestView = true;
        try {
            original.call(panel, graphics, theme, x, y, w, h);
        } finally {
            firmages$drawingQuestView = false;
            panel.setY(posY);
        }
    }

    @ModifyReturnValue(method = "getY", at = @At("RETURN"))
    private int firmages$questViewOrigin(int y) {
        return firmages$drawingQuestView ? 0 : y;
    }
}
