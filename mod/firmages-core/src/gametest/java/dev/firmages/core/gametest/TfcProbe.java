package dev.firmages.core.gametest;

import dev.firmages.core.config.ServerConfig;
import net.dries007.tfc.common.player.IPlayerInfo;
import net.dries007.tfc.config.TFCConfig;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/** TFC checks of {@link ImmersionGameTests}; only loaded when TerraFirmaCraft is in the run. */
final class TfcProbe {
    private TfcProbe() {}

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
