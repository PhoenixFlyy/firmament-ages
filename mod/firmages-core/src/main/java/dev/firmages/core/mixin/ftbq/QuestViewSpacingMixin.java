package dev.firmages.core.mixin.ftbq;

import dev.firmages.core.config.ClientConfig;
import dev.firmages.core.immersion.QuestWindow;
import dev.ftb.mods.ftbquests.client.gui.quests.ViewQuestPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Tighter section gaps of the quest view (SPEC §18.9; paragraphs and subtitle: {@code ftbquests.ViewQuestPanelMixin},
 * §18.8). FTB Quests 2101.1.36 {@code ViewQuestPanel.addWidgets} [verified, javap] has them fixed in code, no theme
 * key: the content (task and reward headings, icons, text) starts at {@code max(16, title height + 8)}, the separator
 * sits at {@code 16 + h + 6} below the icon rows of height {@code h} (the vertical divider ends there too), the text at
 * {@code 16 + h + 12}, a non-fullscreen view is {@code + 12} taller than its content and title, and the content panel
 * is {@code height - 17} high. Each gap shrinks by {@link QuestWindow#sectionGapReduction} of
 * {@code ui.questParagraphGap} (default 4: 2 px); line height and the 6 px text column inset stay.
 */
@Mixin(value = ViewQuestPanel.class, remap = false)
public abstract class QuestViewSpacingMixin {
    @Unique
    private static int firmages$reduction() {
        return QuestWindow.sectionGapReduction(ClientConfig.get(ClientConfig.QUEST_PARAGRAPH_GAP, QuestWindow.FTB_SECTION_GAP));
    }

    /** {@code panelContent.setPosAndSize(0, max(16, titleField.height + 8), w, 0)}, the first BlankPanel placement. */
    @ModifyArg(method = "addWidgets", index = 1, at = @At(value = "INVOKE", ordinal = 0,
        target = "Ldev/ftb/mods/ftblibrary/ui/BlankPanel;setPosAndSize(IIII)Ldev/ftb/mods/ftblibrary/ui/Widget;"))
    private int firmages$contentTop(int y) {
        return y - firmages$reduction();
    }

    /** {@code panelText.setPosAndSize(3, 16 + h + 12, w - 6, 0)}: the first 12 of the method. */
    @ModifyConstant(method = "addWidgets", constant = @Constant(intValue = 12, ordinal = 0))
    private int firmages$textTop(int original) {
        return original - 2 * firmages$reduction();
    }

    /** {@code 16 + h + 6}: the vertical divider's height (second 6; the first is the text column inset). */
    @ModifyConstant(method = "addWidgets", constant = @Constant(intValue = 6, ordinal = 1))
    private int firmages$dividerEnd(int original) {
        return original - firmages$reduction();
    }

    /** {@code 16 + h + 6}: the separator's y (third 6). */
    @ModifyConstant(method = "addWidgets", constant = @Constant(intValue = 6, ordinal = 2))
    private int firmages$separator(int original) {
        return original - firmages$reduction();
    }

    /** {@code getContentHeight() + titleField.height + 12}: a non-fullscreen view's height (fourth 12; 2nd and 3rd are an icon size). */
    @ModifyConstant(method = "addWidgets", constant = @Constant(intValue = 12, ordinal = 3))
    private int firmages$viewHeight(int original) {
        return original - firmages$reduction();
    }

    /** {@code panelContent.setHeight(height - 17)}: the content panel again ends 1 px above the view's bottom edge. */
    @ModifyConstant(method = "addWidgets", constant = @Constant(intValue = 17))
    private int firmages$contentHeight(int original) {
        return original - firmages$reduction();
    }
}
