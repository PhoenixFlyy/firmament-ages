package dev.firmages.core.compat.fancymenu;

import de.keksuccino.fancymenu.customization.placeholder.DeserializedPlaceholderString;
import de.keksuccino.fancymenu.customization.placeholder.Placeholder;
import de.keksuccino.fancymenu.customization.placeholder.PlaceholderRegistry;
import dev.firmages.core.FirmagesCore;

import java.util.List;
import java.util.HashMap;
import java.util.function.Function;

/**
 * FancyMenu placeholders of the pack (SPEC §18.7), used by the Drippy loading screen layout:
 * {@code {"placeholder":"firmages_ram_used_mb"}}, {@code firmages_ram_max_mb} and {@code firmages_ram_used_pct}.
 * Only loaded when FancyMenu is (a client-only mod); FancyMenu 3.9.14's API verified with javap.
 */
public final class FancyMenuPlaceholders {
    private static final RamGauge GAUGE = RamGauge.runtime();
    private static boolean registered;

    private FancyMenuPlaceholders() {}

    /** Registers the placeholders once; on the main thread (FancyMenu's registry is a plain LinkedHashMap). */
    public static void register() {
        if (registered) return;
        registered = true;
        PlaceholderRegistry.register(new Ram("firmages_ram_used_mb", "RAM used (MB)", "Heap in use, in MB (refreshed every 250 ms).",
            s -> Long.toString(s.usedMb())));
        PlaceholderRegistry.register(new Ram("firmages_ram_max_mb", "RAM allocated (MB)", "Heap limit (-Xmx), in MB.",
            s -> Long.toString(s.maxMb())));
        PlaceholderRegistry.register(new Ram("firmages_ram_used_pct", "RAM used (%)", "Heap in use, in percent of the limit (refreshed every 250 ms).",
            s -> Integer.toString(s.usedPct())));
        FirmagesCore.LOGGER.info("FancyMenu placeholders registered: firmages_ram_used_mb, firmages_ram_max_mb, firmages_ram_used_pct");
    }

    private static final class Ram extends Placeholder {
        private final String name;
        private final String description;
        private final Function<RamGauge.Sample, String> value;

        Ram(String id, String name, String description, Function<RamGauge.Sample, String> value) {
            super(id);
            this.name = name;
            this.description = description;
            this.value = value;
        }

        @Override
        public String getReplacementFor(DeserializedPlaceholderString dps) {
            return value.apply(GAUGE.get());
        }

        @Override
        public List<String> getValueNames() {
            return null; // no arguments
        }

        @Override
        public String getDisplayName() {
            return name;
        }

        @Override
        public List<String> getDescription() {
            return List.of(description);
        }

        @Override
        public String getCategory() {
            return "Firmament Ages";
        }

        @Override
        public DeserializedPlaceholderString getDefaultPlaceholderString() {
            return new DeserializedPlaceholderString(getIdentifier(), new HashMap<>(), "");
        }

        @Override
        public boolean canRunAsync() {
            return true; // reads only the Runtime and a volatile sample
        }
    }
}
