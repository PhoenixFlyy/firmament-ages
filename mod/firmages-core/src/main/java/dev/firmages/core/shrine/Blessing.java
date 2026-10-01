package dev.firmages.core.shrine;

import java.util.List;
import java.util.Set;

/**
 * A blessing (SPEC §7.6), parsed from {@code data/<ns>/firmages_shrine/blessing/<name>.json}. Blessings act only
 * while the shrine is intact, on the players within the blessing radius of the heart (the sanctuary radius;
 * {@code shrine.blessings.everywhere} lifts the radius for attributes and XP). Pure Java, so the parser is unit-tested.
 *
 * @param id             {@code <ns>:<name>}
 * @param nameKey        lang key of the name shown in the ceremony
 * @param descriptionKey lang key of the one-line effect description
 * @param flags          effect flags ({@code sanctuary}: no natural monster spawns near the heart)
 * @param effects        data-driven effects on the blessed players
 */
public record Blessing(String id, String nameKey, String descriptionKey, Set<String> flags, List<Effect> effects) {
    public static final String SANCTUARY = "sanctuary";
    /** Flags this build implements; others are logged at load. */
    public static final Set<String> SUPPORTED_FLAGS = Set.of(SANCTUARY);

    public boolean has(String flag) {
        return flags.contains(flag);
    }

    /**
     * One effect of a blessing.
     *
     * @param kind      what it does
     * @param target    {@code attribute}: the attribute id; {@code mob_effect}: the effect id; {@code xp_bonus}: empty
     * @param operation {@code attribute} only: {@code add_value}, {@code add_multiplied_base} or {@code add_multiplied_total}
     * @param amount    {@code attribute}: the modifier amount; {@code xp_bonus}: the extra share of picked-up XP (0.10 = +10 %)
     * @param amplifier {@code mob_effect} only: 0 = level I
     */
    public record Effect(Kind kind, String target, String operation, double amount, int amplifier) {
        public enum Kind {
            /** A transient attribute modifier {@code firmages:blessing/<name>/<index>} on every blessed player. */
            ATTRIBUTE,
            /** A status effect, refreshed every second, for players within the blessing radius only (never everywhere). */
            MOB_EFFECT,
            /** Extra experience points when a blessed player picks up an orb. */
            XP_BONUS
        }

        public static final Set<String> OPERATIONS = Set.of("add_value", "add_multiplied_base", "add_multiplied_total");
    }
}
