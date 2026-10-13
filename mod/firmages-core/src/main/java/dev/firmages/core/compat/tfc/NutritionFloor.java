package dev.firmages.core.compat.tfc;

/**
 * TerraFirmaCraft nutrition as a bonus only (SPEC §18.6, Felix 2026-10-13). TFC scales incoming damage by
 * {@code 1 / healthModifier} and shows health times the modifier; the modifier runs from
 * {@code nutritionMinimumHealthModifier} (0 % average nutrition) over {@code nutritionDefaultHealthModifier} (40 %,
 * the starting nutrition) to {@code nutritionMaximumHealthModifier} (95 %) [verified, javap of
 * {@code IPlayerInfo.getHealthModifier} in TFC 4.2.11]. The floor keeps it at the starting value, so a one-sided
 * diet never costs health while a varied one still raises it. Pure Java (unit-tested).
 */
public final class NutritionFloor {
    private NutritionFloor() {}

    /**
     * @param modifier TFC's modifier for the player's current nutrition
     * @param baseline TFC's modifier at the starting nutrition ({@code nutritionDefaultHealthModifier})
     * @param enabled  {@code tfc.noNutritionMalus}
     */
    public static float apply(float modifier, float baseline, boolean enabled) {
        if (!enabled || !(baseline > 0f) || Float.isNaN(modifier)) return modifier;
        return Math.max(modifier, baseline);
    }
}
