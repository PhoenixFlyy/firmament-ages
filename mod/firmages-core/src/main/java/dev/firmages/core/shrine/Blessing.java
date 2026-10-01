package dev.firmages.core.shrine;

import java.util.Set;

/**
 * A blessing (SPEC §7.6), parsed from {@code data/<ns>/firmages_shrine/blessing/<name>.json}. Blessings act only
 * while the shrine is intact. MVP: the flag {@code sanctuary} (no natural monster spawns near the heart; radius
 * from {@code shrine.sanctuary.*}). Attribute modifiers, XP and Skyreading come with M6.
 *
 * @param id             {@code <ns>:<name>}
 * @param nameKey        lang key of the name shown in the ceremony
 * @param descriptionKey lang key of the one-line effect description
 * @param flags          effect flags
 */
public record Blessing(String id, String nameKey, String descriptionKey, Set<String> flags) {
    public static final String SANCTUARY = "sanctuary";
    /** Flags this build implements; others are logged at load. */
    public static final Set<String> SUPPORTED_FLAGS = Set.of(SANCTUARY);

    public boolean has(String flag) {
        return flags.contains(flag);
    }
}
