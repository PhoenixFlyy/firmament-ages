package dev.firmages.core.hardship;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.immersion.AgeStatus;

import java.util.Locale;
import java.util.Optional;

/**
 * Hand-breaking rule of the earliest Age (SPEC §19.2, Felix 2026-10-14, option A): while the world is below
 * {@code firmages-server.toml [hardship] toolRuleUntilAge} (default {@code age_0}, so only in Dawn) a block in
 * {@code #firmages:needs_tool_early} (logs, raw and hardened rock, ores, sandstone) cannot be broken at all without a
 * tool that is correct for it: no crack, no progress. {@link ToolRuleService} applies it on both sides. Pure Java
 * (unit-tested).
 */
public final class ToolRule {
    /** Felix 2026-10-14 (option A): the rule holds in Dawn only. */
    public static final AgeId DEFAULT_UNTIL = AgeId.AGE_0;
    public static final String NEVER = "never";
    /** At most one action-bar hint per player in this many milliseconds. */
    public static final long HINT_INTERVAL_MS = 60_000L;

    /** The action-bar hint for a blocked block, by the tool its mineable tag names. */
    public enum Hint {
        AXE("firmages.hint.needs_axe"),
        PICKAXE("firmages.hint.needs_pickaxe"),
        TOOL("firmages.hint.needs_tool");

        private final String key;

        Hint(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

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
     * The rule itself: breaking is stopped when the rule is active, the block is in the tag, the player is a real
     * survival player and the held item is not a correct tool for the block.
     */
    public static boolean blocks(boolean ruleActive, boolean inTag, boolean exempt, boolean correctTool) {
        return ruleActive && inTag && !exempt && !correctTool;
    }

    /** The hint for a block that is mineable with an axe and/or a pickaxe (axe first: logs are both axe and hammer blocks). */
    public static Hint hintFor(boolean axe, boolean pickaxe) {
        if (axe) return Hint.AXE;
        if (pickaxe) return Hint.PICKAXE;
        return Hint.TOOL;
    }

    /** True if a hint may be shown at {@code nowMs} when the last one went out at {@code lastMs} (null: none yet). */
    public static boolean hintDue(Long lastMs, long nowMs) {
        return lastMs == null || nowMs - lastMs >= HINT_INTERVAL_MS;
    }

    private static boolean isNever(String value) {
        return value != null && value.trim().toLowerCase(Locale.ROOT).equals(NEVER);
    }
}
