package dev.firmages.core.client.immersion;

import dev.firmages.core.compat.ftbquests.FtbQuestsScreens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The one-time welcome screen (SPEC §18.5): three short panels in the voice of the world (the Ages and the Shrine,
 * the quest screen on its key as the one place for goals, Caelum), and a button that opens the FTB Quests screen.
 * All text is lang ({@code firmages.welcome.*}). Wide windows show the panels side by side, narrow ones stacked.
 */
public final class WelcomeScreen extends Screen {
    private static final int PANEL_GAP = 10;
    private static final int MAX_PANEL_WIDTH = 150;
    private static final String[] PANELS = {"ages", "path", "caelum"};

    private int panelWidth;
    private boolean stacked;
    private int panelsTop;

    public WelcomeScreen() {
        super(Component.translatable("firmages.welcome.title"));
    }

    @Override
    protected void init() {
        int usable = width - 40;
        stacked = usable < 3 * 110 + 2 * PANEL_GAP;
        panelWidth = stacked ? Math.min(usable, 260) : Math.min(MAX_PANEL_WIDTH, (usable - 2 * PANEL_GAP) / 3);
        panelsTop = Math.max(36, height / 6);
        int buttonsY = Math.min(height - 28, panelsTop + panelsHeight() + 16);
        addRenderableWidget(Button.builder(Component.translatable("firmages.welcome.open", questKeyName()), b -> openQuests())
            .bounds(width / 2 - 154, buttonsY, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("firmages.welcome.close"), b -> onClose())
            .bounds(width / 2 + 4, buttonsY, 150, 20).build());
    }

    /** The bound key of FTB Quests' "Quest Book" mapping, K in the pack's default options. */
    static Component questKeyName() {
        for (KeyMapping k : Minecraft.getInstance().options.keyMappings) {
            if (k.getName().equals("key.ftbquests.quests") && !k.isUnbound()) return k.getTranslatedKeyMessage();
        }
        return Component.literal("K");
    }

    private void openQuests() {
        onClose();
        FtbQuestsScreens.open(); // after closing, so Esc in the quest screen returns to the game, not here
    }

    private List<FormattedCharSequence> body(String panel) {
        Component text = Component.translatable("firmages.welcome." + panel + ".text", questKeyName());
        return font.split(text, panelWidth - 12);
    }

    private int panelHeight(String panel) {
        return 12 + font.lineHeight + 6 + body(panel).size() * (font.lineHeight + 1) + 8;
    }

    private int panelsHeight() {
        if (!stacked) {
            int max = 0;
            for (String p : PANELS) max = Math.max(max, panelHeight(p));
            return max;
        }
        int sum = 0;
        for (String p : PANELS) sum += panelHeight(p) + PANEL_GAP;
        return sum - PANEL_GAP;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, Component.translatable("firmages.welcome.title").withStyle(ChatFormatting.GOLD), width / 2, panelsTop - 24, 0xFFFFFF);
        int sameHeight = stacked ? 0 : panelsHeight();
        int x0 = stacked ? (width - panelWidth) / 2 : (width - (3 * panelWidth + 2 * PANEL_GAP)) / 2;
        int y = panelsTop;
        for (int i = 0; i < PANELS.length; i++) {
            String p = PANELS[i];
            int x = stacked ? x0 : x0 + i * (panelWidth + PANEL_GAP);
            int top = stacked ? y : panelsTop;
            int h = stacked ? panelHeight(p) : sameHeight;
            g.fill(x, top, x + panelWidth, top + h, 0xB0140E08);
            g.fill(x, top, x + panelWidth, top + 1, 0xFFC8963C);
            g.drawString(font, Component.translatable("firmages.welcome." + p + ".heading").withStyle(ChatFormatting.GOLD), x + 6, top + 8, 0xFFFFFF, true);
            int ty = top + 8 + font.lineHeight + 6;
            for (FormattedCharSequence line : body(p)) {
                g.drawString(font, line, x + 6, ty, 0xE6DCC8, true);
                ty += font.lineHeight + 1;
            }
            if (stacked) y += h + PANEL_GAP;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
