package dev.firmages.core.mixin.ftbq;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestPanel;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The map centre on a windowed quest screen (SPEC §18.9). FTB Quests 2101.1.36 {@code QuestPanel.draw} [verified,
 * javap] computes the map point at the screen's centre as {@code questScreen.width / 2 - (getX() - scrollX)}: half the
 * width is relative, {@code getX()} absolute. That point is kept when zooming ({@code withPreservedPos}, every frame of
 * Certain Questing Additions' smooth zoom) and saved as the scroll position for the next opening, so on a window the
 * map would jump sideways by the screen's offset on every zoom step and every reopening. Here the centre is absolute:
 * {@code width / 2 + screen x}, written as {@code (width + 2 * screen x) / 2}; the same for the height.
 */
@Mixin(value = QuestPanel.class, remap = false)
public abstract class QuestPanelCentreMixin {
    @ModifyExpressionValue(method = "draw", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
        target = "Ldev/ftb/mods/ftbquests/client/gui/quests/QuestScreen;width:I"))
    private int firmages$absoluteCentreX(int width) {
        return width + 2 * ((Widget) (Object) this).getGui().getX();
    }

    @ModifyExpressionValue(method = "draw", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
        target = "Ldev/ftb/mods/ftbquests/client/gui/quests/QuestScreen;height:I"))
    private int firmages$absoluteCentreY(int height) {
        return height + 2 * ((Widget) (Object) this).getGui().getY();
    }
}
