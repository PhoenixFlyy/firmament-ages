package dev.firmages.core.hardship;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.immersion.AgeStatus;

import java.util.Locale;
import java.util.Optional;

/**
 * Hand-breaking rule of the earliest Age (SPEC §19.2, Felix 2026-10-14, option A): while the world is below
 * {@code firmages-server.toml [hardship] toolRuleUntilAge} (default {@code age_0}, so only in Dawn) a block in
 * {@code #firmages:needs_tool_early} (logs, raw and hardened rock, ores, sandstone) cannot be broken at all without a
 * tool that is correct for it: no crack, no progress. Since round G2 (Felix 2026-10-17, SPEC §19.5) the same span
 * also has a positive list: only a block in {@code #firmages:dawn_breakable} (soil, plants, leaves, logs, loose rocks,
 * twigs) breaks at all; an empty tag turns the list off. {@link ToolRuleService} applies it on both sides. Pure Java
 * (unit-tested).
 */
public final class ToolRule {
    /** Felix 2026-10-14 (option A): the rule holds in Dawn only. */
    public static final AgeId DEFAULT_UNTIL = AgeId.AGE_0;
    public static final String NEVER = "never";

    private ToolRule() {}

    /** The first Age without the rule for a config value: empty for {@code never} (the rule is off); an unknown value counts as the default. */
    public static Optional<AgeId> until(String value) {
        if (isNever(value)) return Optional.empty();
        return Optional.of(AgeId.byId(value).orElse(DEFAULT_UNTIL));
    }

    /** The config validator: an Age id or {@code never}. */
    public static boolean isValid(Object o) {
        return o instanceof String s && (isNever(s) || AgeId.byId(s).isPresent());
    }

    /** True while the rule holds in Age {@code current}: below {@code until}; never with {@code never}. */
    public static boolean active(AgeId current, Optional<AgeId> until) {
        return until.map(u -> !AgeStatus.reached(current, u)).orElse(false);
    }

    /**
     * The rule itself: breaking is stopped when the rule is active, the player is a real survival player and either
     * the block is not on the Dawn list ({@code listed} is true for every block while the list is off) or it is in the
     * tool tag and the held item is not a correct tool for it.
     */
    public static boolean blocks(boolean ruleActive, boolean listed, boolean inTag, boolean exempt, boolean correctTool) {
        return ruleActive && !exempt && (!listed || inTag && !correctTool);
    }

    private static boolean isNever(String value) {
        return value != null && value.trim().toLowerCase(Locale.ROOT).equals(NEVER);
    }
}
