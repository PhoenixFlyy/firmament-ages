package dev.firmages.core.immersion;

import dev.firmages.core.age.AgeId;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * TerraFirmaCraft's inventory tabs that open by Age (SPEC §18.6): each has a {@code firmages-server.toml [ui]} key
 * whose value is the first Age with the tab, or {@code never}. The Inventory tab itself always shows. Pure Java
 * (unit-tested); {@code compat/tfc/TfcTabs} maps these to TFC's own tab enum.
 */
public enum InventoryTab {
    /** Felix 2026-10-13: the calendar from the Stone Age. */
    CALENDAR("calendarTabFromAge", AgeId.AGE_0.id()),
    /** Felix 2026-10-13: the climate page from the Iron Age. */
    CLIMATE("climateTabFromAge", AgeId.AGE_2.id()),
    /** Never by default: nutrition only gives a bonus in this pack (§18.6), so the page has nothing to steer. */
    NUTRITION("nutritionTabFromAge", InventoryTab.NEVER),
    /** Felix 2026-10-13: the Field Guide tab is hidden for the whole pack. */
    FIELD_GUIDE("fieldGuideTabFromAge", InventoryTab.NEVER);

    public static final String NEVER = "never";

    private final String configKey;
    private final String defaultValue;

    InventoryTab(String configKey, String defaultValue) {
        this.configKey = configKey;
        this.defaultValue = defaultValue;
    }

    /** The key in {@code firmages-server.toml [ui]}. */
    public String configKey() {
        return configKey;
    }

    /** The shipped value: an Age id or {@code never}. */
    public String defaultValue() {
        return defaultValue;
    }

    /** The first Age with this tab for a config value: empty for {@code never}; an unknown value counts as the default. */
    public Optional<AgeId> fromAge(String value) {
        if (isNever(value)) return Optional.empty();
        Optional<AgeId> age = AgeId.byId(value);
        if (age.isPresent()) return age;
        return isNever(defaultValue) ? Optional.empty() : AgeId.byId(defaultValue);
    }

    /** The config validator: an Age id or {@code never}. */
    public static boolean isValid(Object o) {
        return o instanceof String s && (isNever(s) || AgeId.byId(s).isPresent());
    }

    private static boolean isNever(String value) {
        return value != null && value.trim().toLowerCase(Locale.ROOT).equals(NEVER);
    }

    /** The tabs open in Age {@code current}; {@code fromAge} gives each tab's first Age, empty for never. */
    public static Set<InventoryTab> open(AgeId current, Function<InventoryTab, Optional<AgeId>> fromAge) {
        EnumSet<InventoryTab> open = EnumSet.noneOf(InventoryTab.class);
        for (InventoryTab t : values()) {
            if (fromAge.apply(t).filter(from -> AgeStatus.reached(current, from)).isPresent()) open.add(t);
        }
        return Collections.unmodifiableSet(open);
    }

    /** One bit per tab (ordinal), for {@code firmages:age_status}. */
    public static int mask(Set<InventoryTab> tabs) {
        int mask = 0;
        for (InventoryTab t : tabs) mask |= 1 << t.ordinal();
        return mask;
    }

    public static Set<InventoryTab> fromMask(int mask) {
        EnumSet<InventoryTab> tabs = EnumSet.noneOf(InventoryTab.class);
        for (InventoryTab t : values()) if ((mask & (1 << t.ordinal())) != 0) tabs.add(t);
        return Collections.unmodifiableSet(tabs);
    }
}
