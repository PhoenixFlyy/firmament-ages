package dev.firmages.core.client.immersion;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.config.ClientConfig;
import dev.firmages.core.immersion.AgeStatus;
import dev.firmages.core.immersion.HudFade;
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

/**
 * GUI layer {@code firmages:age_indicator} (SPEC §18.4): a small panel top left, the offering's icon and
 * "&lt;Age&gt; · Goal: &lt;offering&gt;" (Dawn and the Singularity Age name their goal by lang key). It shows for {@code ui.hudFadeSeconds} after joining and after an
 * Age change, then slides up and fades out; holding the "Show Age" key shows it again ({@code ui.hudMode}).
 */
public final class AgeIndicatorLayer {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "age_indicator");
    private static final int MARGIN = 4;
    private static final int PAD = 4;
    private static final int ICON = 16;

    private AgeIndicatorLayer() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || mc.screen != null) return;
        if (!ClientAgeStatus.known()) return;
        HudFade.Mode mode = ClientConfig.get(ClientConfig.HUD_MODE, HudFade.Mode.FADE);
        long showMs = ClientConfig.get(ClientConfig.HUD_FADE_SECONDS, 6) * 1000L;
        float a = HudFade.alpha(mode, Util.getMillis(), ClientAgeStatus.shownAt(), showMs,
            ImmersionClient.ageKeyHeld(), ImmersionClient.ageKeyReleasedAt());
        if (a < 0.05f) return;

        AgeStatus st = ClientAgeStatus.get();
        Font font = mc.font;
        ItemStack goal = goalStack(st.goalItem());
        Component goalName = goal.isEmpty()
            ? Component.translatableWithFallback("firmages.hud.goal." + st.age().id(), "")
            : goal.getHoverName();
        Component line = Component.translatable("firmages.age." + st.age().id() + ".name").withStyle(ChatFormatting.GOLD);
        if (!goalName.getString().isEmpty()) {
            line = line.copy().append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable("firmages.hud.goal", goalName).withStyle(s -> s.withColor(0xE6DCC8)));
        }

        int textX = PAD + (goal.isEmpty() ? 0 : ICON + 3);
        int w = textX + font.width(line) + PAD;
        int h = (goal.isEmpty() ? font.lineHeight : ICON) + PAD * 2;
        int x = MARGIN;
        int y = MARGIN - Math.round((1f - a) * (h + MARGIN)); // slides up while it fades

        g.pose().pushPose();
        g.pose().translate(0, 0, 50);
        g.fill(x, y, x + w, y + h, (Math.round(a * 0xA0) << 24) | 0x140E08);
        g.fill(x, y + h - 1, x + w, y + h, (Math.round(a * 0xC0) << 24) | 0xC8963C); // gold base line
        if (!goal.isEmpty()) g.renderItem(goal, x + PAD, y + PAD);
        g.drawString(font, line, x + textX, y + (h - font.lineHeight) / 2 + 1, 0xFFFFFF | (Math.round(a * 255) << 24), true);
        g.pose().popPose();
    }

    private static ItemStack goalStack(String id) {
        if (id.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(rl);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}
