package dev.firmages.core.mixin.ftbq;

import com.mojang.blaze3d.platform.Window;
import dev.firmages.core.config.ClientConfig;
import dev.firmages.core.immersion.QuestWindow;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The quest screen as a window of {@code ui.questWindowPercent} of the game window (SPEC §18.9). FTB Quests 2101.1.36
 * {@code QuestScreen.onInit()} always calls {@code setFullscreen()} [verified, javap]; FTB Library centres a smaller
 * screen itself ({@code BaseScreen.getX/getY}) and runs {@code onInit} again on every window resize and GUI scale
 * change ({@code ScreenWrapper.init} -> {@code initGui}), so the size follows both.
 */
@Mixin(value = QuestScreen.class, remap = false)
public abstract class QuestScreenSizeMixin {
    @Inject(method = "onInit", at = @At("RETURN"))
    private void firmages$windowSize(CallbackInfoReturnable<Boolean> cir) {
        QuestScreen self = (QuestScreen) (Object) this;
        Window window = self.getWindow();
        QuestWindow.Rect rect = QuestWindow.rect(window.getGuiScaledWidth(), window.getGuiScaledHeight(),
            ClientConfig.get(ClientConfig.QUEST_WINDOW_PERCENT, 0));
        self.setSize(rect.width(), rect.height());
        // initGui lays out only the screen's own widgets; an open quest view would keep its old size until reopened.
        if (self.isViewingQuest()) self.viewQuestPanel.refreshWidgets();
    }

    /** As a window, the world around it is blurred and dimmed like around other menus (FTB Quests draws no background). */
    @Inject(method = "drawDefaultBackground", at = @At("HEAD"), cancellable = true)
    private void firmages$backgroundAround(GuiGraphics graphics, CallbackInfoReturnable<Boolean> cir) {
        QuestScreen self = (QuestScreen) (Object) this;
        Window window = self.getWindow();
        if (self.width < window.getGuiScaledWidth() || self.height < window.getGuiScaledHeight()) cir.setReturnValue(true);
    }
}
