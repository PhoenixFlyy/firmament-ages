package dev.firmages.core.compat.tfc;

import dev.firmages.core.config.ServerConfig;
import net.dries007.tfc.common.player.PlayerInfo;
import net.dries007.tfc.config.TFCConfig;

/**
 * TerraFirmaCraft survival tweaks (SPEC §18.6): no thirst, nutrition never below the starting health. Called only
 * from the {@code mixin.tfc} mixins, which apply only when TFC is installed, so this class is loaded only then.
 */
public final class TfcTweaks {
    private TfcTweaks() {}

    /** {@code tfc.disableThirst}: thirst stays at the maximum. */
    public static boolean thirstDisabled() {
        return ServerConfig.tfcDisableThirst();
    }

    public static float maxThirst() {
        return PlayerInfo.MAX_THIRST;
    }

    /** TFC's health modifier with the {@code tfc.noNutritionMalus} floor applied. */
    public static float healthModifier(float tfcModifier) {
        float baseline;
        try {
            baseline = TFCConfig.SERVER.nutritionDefaultHealthModifier.get().floatValue();
        } catch (RuntimeException e) {
            return tfcModifier; // TFC's server config not loaded (main menu)
        }
        return NutritionFloor.apply(tfcModifier, baseline, ServerConfig.tfcNoNutritionMalus());
    }
}
