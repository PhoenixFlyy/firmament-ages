package dev.firmages.core.isles;

import java.util.Locale;
import java.util.Optional;

/** The three isle sizes of SPEC §30.3; cluster satellites are always small. */
public enum IsleSize {
    SMALL,
    MEDIUM,
    LARGE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<IsleSize> byId(String id) {
        for (IsleSize s : values()) if (s.id().equals(id)) return Optional.of(s);
        return Optional.empty();
    }
}
