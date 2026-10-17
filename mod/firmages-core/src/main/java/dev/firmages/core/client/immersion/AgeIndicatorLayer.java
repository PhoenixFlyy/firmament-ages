package dev.firmages.core.client.immersion;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.compat.ftbquests.PinnedQuests;
import dev.firmages.core.config.ClientConfig;
import dev.firmages.core.immersion.AgeStatus;
import dev.firmages.core.immersion.HudFade;
import dev.firmages.core.immersion.PinnedLines;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * GUI layer {@code firmages:age_indicator} (SPEC §18.4): the Age sign, a small panel top left with the offering's icon
 * and "&lt;Age&gt; · Goal: &lt;offering&gt;" (Dawn and the Singularity Age name their goal by lang key) and, below it,
 * the player's pinned FTB quests with their progress (round G2, at most {@link PinnedLines#MAX_LINES} lines). It shows
 * for {@code ui.hudSignSeconds} after joining, after an Age change and after a press of the "Show Age" key (and while it
 * is held), then slides up and fades out ({@code ui.hudMode}).
 */
public final class AgeIndicatorLayer {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "age_indicator");
    private static final int MARGIN = 4;
    private static final int PAD = 4;
    private static final int ICON = 16;
    /** Gap between the Age row and the first pinned quest. */
    private static final int ROW_GAP = 4;
    /** Pitch of the pinned quest lines. */
    private static final int LINE_GAP = 2;
    /** A quest title wider than this is cut with "…". */
    private static final int MAX_TITLE_WIDTH = 200;
    private static final int TEXT = 0xE6DCC8;

    private AgeIndicatorLayer() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || mc.screen != null) return;
        if (!ClientAgeStatus.known()) return;
        HudFade.Mode mode = ClientConfig.get(ClientConfig.HUD_MODE, HudFade.Mode.FADE);
        long showMs = ClientConfig.get(ClientConfig.HUD_SIGN_SECONDS, 8) * 1000L;
        float a = HudFade.alpha(mode, Util.getMillis(), ClientAgeStatus.shownAt(), showMs,
            ImmersionClient.ageKeyHeld(), ImmersionClient.ageKeyReleasedAt());
        if (a < 0.05f) return;

        AgeStatus st = ClientAgeStatus.get();
        Font font = mc.font;
        ItemStack goal = goalStack(st.goalItem());
        Component goalName = goal.isEmpty()
            ? Component.translatableWithFallback(st.goalKey(), "")
            : goal.getHoverName();
        Component line = Component.translatable("firmages.age." + st.age().id() + ".name").withStyle(ChatFormatting.GOLD);
        if (!goalName.getString().isEmpty()) {
            line = line.copy().append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable("firmages.hud.goal", goalName).withStyle(s -> s.withColor(TEXT)));
        }

        List<Component> pinned = pinnedLines(font);
        int textX = PAD + (goal.isEmpty() ? 0 : ICON + 3);
        int rowH = goal.isEmpty() ? font.lineHeight : ICON;
        int w = textX + font.width(line) + PAD;
        for (Component p : pinned) w = Math.max(w, PAD + font.width(p) + PAD);
        int listH = pinned.isEmpty() ? 0 : ROW_GAP + pinned.size() * (font.lineHeight + LINE_GAP) - LINE_GAP;
        int h = rowH + listH + PAD * 2;
        int x = MARGIN;
        int y = MARGIN - Math.round((1f - a) * (h + MARGIN)); // slides up while it fades
        int color = 0xFFFFFF | (Math.round(a * 255) << 24);

        g.pose().pushPose();
        g.pose().translate(0, 0, 50);
        g.fill(x, y, x + w, y + h, (Math.round(a * 0xA0) << 24) | 0x140E08);
        g.fill(x, y + h - 1, x + w, y + h, (Math.round(a * 0xC0) << 24) | 0xC8963C); // gold base line
        if (!goal.isEmpty()) g.renderItem(goal, x + PAD, y + PAD);
        g.drawString(font, line, x + textX, y + PAD + (rowH - font.lineHeight) / 2 + 1, color, true);
        int ly = y + PAD + rowH + ROW_GAP;
        for (Component p : pinned) {
            g.drawString(font, p, x + PAD, ly, color, true);
            ly += font.lineHeight + LINE_GAP;
        }
        g.pose().popPose();
    }

    /** The pinned quests in the sign's colours: the title as the goal text, the progress gold; "…" for the rest. */
    private static List<Component> pinnedLines(Font font) {
        return PinnedQuests.lines().stream().map(l -> {
            if (l.more()) return (Component) Component.literal(PinnedLines.MORE).withStyle(s -> s.withColor(TEXT));
            String title = l.title();
            if (font.width(title) > MAX_TITLE_WIDTH) {
                title = font.plainSubstrByWidth(title, MAX_TITLE_WIDTH - font.width(PinnedLines.MORE)) + PinnedLines.MORE;
            }
            return (Component) Component.literal(title).withStyle(s -> s.withColor(TEXT))
                .append(Component.literal("  " + l.progress()).withStyle(ChatFormatting.GOLD));
        }).toList();
    }

    private static ItemStack goalStack(String id) {
        if (id.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(rl);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}
