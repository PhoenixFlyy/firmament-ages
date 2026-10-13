package dev.firmages.core.mixin.ftbquests;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.firmages.core.config.ClientConfig;
import dev.ftb.mods.ftblibrary.ui.BlankPanel;
import dev.ftb.mods.ftblibrary.ui.TextField;
import dev.ftb.mods.ftblibrary.ui.VerticalSpaceWidget;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftbquests.client.gui.quests.ViewQuestPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Tighter quest description (SPEC §18.8). {@code ViewQuestPanel.addDescriptionText} (FTB Quests 2101.1.36
 * [verified, javap]) puts every description line in its own text field at a 9 px line pitch and stacks them with a
 * 1 px gap, so a blank line (the pack's paragraph break) costs a full 9 px line, and the subtitle is followed by a
 * fixed 7 px spacer. Both become {@code ui.questParagraphGap} px. In edit mode a blank line stays a clickable field.
 */
@Mixin(value = ViewQuestPanel.class, remap = false)
public abstract class ViewQuestPanelMixin {
    /** The spacer after the subtitle: {@code new VerticalSpaceWidget(panelText, 7)}, the only 7 in the method. */
    @ModifyConstant(method = "addDescriptionText", constant = @Constant(intValue = 7))
    private int firmages$subtitleGap(int original) {
        return ClientConfig.get(ClientConfig.QUEST_PARAGRAPH_GAP, original);
    }

    /** The third {@code panelText.add} of the method adds the text field of one description line. */
    @WrapOperation(method = "addDescriptionText", at = @At(value = "INVOKE", ordinal = 2,
        target = "Ldev/ftb/mods/ftblibrary/ui/BlankPanel;add(Ldev/ftb/mods/ftblibrary/ui/Widget;)V"))
    private void firmages$paragraphGap(BlankPanel panel, Widget widget, Operation<Void> original,
                                       @Local(argsOnly = true) boolean canEdit) {
        if (!canEdit && widget instanceof TextField field && ((TextFieldAccessor) field).firmages$rawText().getString().isBlank()) {
            original.call(panel, new VerticalSpaceWidget(panel, ClientConfig.get(ClientConfig.QUEST_PARAGRAPH_GAP, 9)));
        } else {
            original.call(panel, widget);
        }
    }
}
