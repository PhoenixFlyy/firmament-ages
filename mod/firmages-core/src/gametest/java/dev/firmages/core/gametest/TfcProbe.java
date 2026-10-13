package dev.firmages.core.gametest;

import dev.firmages.core.compat.tfc.TfcTabs;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.immersion.InventoryTab;
import net.dries007.tfc.client.screen.button.PlayerInventoryTabButton.Tab;
import net.dries007.tfc.common.player.IPlayerInfo;
import net.dries007.tfc.config.TFCConfig;
import net.dries007.tfc.network.SwitchInventoryTabPacket;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.lang.reflect.Method;

/** TFC checks of {@link ImmersionGameTests}; only loaded when TerraFirmaCraft is in the run. */
final class TfcProbe {
    private TfcProbe() {}

    /**
     * SPEC §18.6: TFC's own {@code SwitchInventoryTabPacket.handle} (package-private, called the way TFC's payload
     * handler calls it) opens the calendar only while its Age is open; the Inventory tab always works; the Field Guide
     * is refused while {@code fieldGuideTabFromAge = never}.
     */
    static void tabGuard(GameTestHelper h, ServerPlayer p) {
        ModConfigSpec.ConfigValue<String> calendar = ServerConfig.UI_TAB_FROM_AGE.get(InventoryTab.CALENDAR);
        ModConfigSpec.ConfigValue<String> guide = ServerConfig.UI_TAB_FROM_AGE.get(InventoryTab.FIELD_GUIDE);
        String calendarWas = calendar.get();
        String guideWas = guide.get();
        try {
            calendar.set(InventoryTab.NEVER);
            handle(p, Tab.CALENDAR);
            if (p.containerMenu != p.inventoryMenu) h.fail("calendarTabFromAge = never: the calendar must not open, menu " + p.containerMenu);
            calendar.set("dawn");
            handle(p, Tab.CALENDAR);
            if (p.containerMenu == p.inventoryMenu) h.fail("calendarTabFromAge = dawn: the calendar opens");
            handle(p, Tab.INVENTORY);
            if (p.containerMenu != p.inventoryMenu) h.fail("the Inventory tab always switches back, menu " + p.containerMenu);
            guide.set(InventoryTab.NEVER);
            if (TfcTabs.allowed(p, Tab.BOOK)) h.fail("fieldGuideTabFromAge = never: the Field Guide is refused");
            guide.set("dawn");
            if (!TfcTabs.allowed(p, Tab.BOOK)) h.fail("fieldGuideTabFromAge = dawn: the Field Guide is allowed");
        } finally {
            calendar.set(calendarWas);
            guide.set(guideWas);
            p.closeContainer();
        }
    }

    private static void handle(ServerPlayer p, Tab tab) {
        try {
            Method m = SwitchInventoryTabPacket.class.getDeclaredMethod("handle", ServerPlayer.class);
            m.setAccessible(true);
            m.invoke(new SwitchInventoryTabPacket(tab), p);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("SwitchInventoryTabPacket.handle", e);
        }
    }

    static void check(GameTestHelper h, ServerPlayer p) {
        IPlayerInfo info = IPlayerInfo.get(p);

        // thirst: pinned to the maximum through setThirst and addThirst (TFC's tick uses addThirst)
        info.setThirst(10f);
        if (info.getThirst() != 100f) h.fail("setThirst(10) must stay at 100, was " + info.getThirst());
        info.addThirst(-60f);
        if (info.getThirst() != 100f) h.fail("addThirst(-60) must stay at 100, was " + info.getThirst());
        ServerConfig.TFC_DISABLE_THIRST.set(false);
        try {
            info.setThirst(40f);
            if (info.getThirst() != 40f) h.fail("tfc.disableThirst = false: TFC thirst as shipped, was " + info.getThirst());
            p.getFoodData().tick(p); // the next tick refills only while thirst is off again
            ServerConfig.TFC_DISABLE_THIRST.set(true);
            p.getFoodData().tick(p);
            if (info.getThirst() != 100f) h.fail("thirst off again: the tick refills an old value, was " + info.getThirst());
        } finally {
            ServerConfig.TFC_DISABLE_THIRST.set(true);
        }

        // nutrition: average 0 gives TFC's minimum modifier; incoming damage is still divided by the starting modifier
        float baseline = TFCConfig.SERVER.nutritionDefaultHealthModifier.get().floatValue();
        float minimum = TFCConfig.SERVER.nutritionMinimumHealthModifier.get().floatValue();
        info.nutrition().onClientUpdate(new float[info.nutrition().getNutrients().length]);
        if (Math.abs(info.getHealthModifier() - minimum) > 1e-4) h.fail("TFC modifier at 0 nutrition should be " + minimum + ", was " + info.getHealthModifier());
        p.setHealth(p.getMaxHealth());
        float before = p.getHealth();
        p.hurt(p.damageSources().genericKill(), 2.0f);
        float lost = before - p.getHealth();
        float expected = 2.0f / baseline;
        if (Math.abs(lost - expected) > 1e-3) h.fail("2 damage at 0 nutrition must cost 2 / " + baseline + " = " + expected + " (not 2 / " + minimum + "), cost " + lost);

        ServerConfig.TFC_NO_NUTRITION_MALUS.set(false);
        try {
            p.invulnerableTime = 0;
            p.setHealth(p.getMaxHealth());
            before = p.getHealth();
            p.hurt(p.damageSources().genericKill(), 1.0f);
            lost = before - p.getHealth();
            if (Math.abs(lost - 1.0f / minimum) > 1e-3) h.fail("tfc.noNutritionMalus = false: TFC as shipped (1 / " + minimum + "), cost " + lost);
        } finally {
            ServerConfig.TFC_NO_NUTRITION_MALUS.set(true);
        }
    }
}
