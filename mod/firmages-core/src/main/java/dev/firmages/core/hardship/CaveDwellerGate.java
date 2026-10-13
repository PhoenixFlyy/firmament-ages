package dev.firmages.core.hardship;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.immersion.AgeStatus;

import java.util.Locale;
import java.util.Optional;

/**
 * Cave Dweller ReEvolved only hunts in the early Ages (SPEC §19, Felix 2026-10-13, H12). {@code firmages-server.toml
 * [hardship] caveDwellerUntilAge} is the first Age without it, or {@code never}. From that Age on the server refuses
 * the entity ({@link CaveDwellerService}) and the clients drop its sounds, whose cave noises arrive by the mod's own
 * {@code cave_dweller:cave_sound} packet on a timer, independent of any dweller. Pure Java (unit-tested).
 */
public final class CaveDwellerGate {
    /** Cave Dweller ReEvolved 1.4-rc2 [verified, javap]: {@code ModEntityTypes}, {@code sounds.json}. */
    public static final String ENTITY_ID = "cave_dweller:cave_dweller";
    public static final String SOUND_NAMESPACE = "cave_dweller";
    /** Felix 2026-10-13 (H12): gone from the Iron Age. */
    public static final AgeId DEFAULT_UNTIL = AgeId.AGE_2;
    public static final String NEVER = "never";

    private CaveDwellerGate() {}

    /** The first Age without the dweller for a config value: empty for {@code never}; an unknown value counts as the default. */
    public static Optional<AgeId> until(String value) {
        if (isNever(value)) return Optional.empty();
        return Optional.of(AgeId.byId(value).orElse(DEFAULT_UNTIL));
    }

    /** The config validator: an Age id or {@code never}. */
    public static boolean isValid(Object o) {
        return o instanceof String s && (isNever(s) || AgeId.byId(s).isPresent());
    }

    /** True while the dweller may spawn and be heard in Age {@code current}. */
    public static boolean hunts(AgeId current, Optional<AgeId> until) {
        return until.map(u -> !AgeStatus.reached(current, u)).orElse(true);
    }

    private static boolean isNever(String value) {
        return value != null && value.trim().toLowerCase(Locale.ROOT).equals(NEVER);
    }
}
