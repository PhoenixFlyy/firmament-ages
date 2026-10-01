package dev.firmages.core.shrine;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One shrine tier (SPEC §2.6), parsed from {@code data/<ns>/firmages_shrine/tier/ring_<N>.json} or the generic
 * {@code tier/fallback.json}. Pure Java (ids are strings), so the parser is unit-tested.
 * <p>
 * Tier N is worked during Age N ({@code age_N}): its ring is built from Age-N materials, its plinth takes the
 * Age-N signature item, and the prayer grants {@code age_(N+1)}.
 *
 * @param multiblock   Modonomicon multiblock id of the ring; empty for the fallback (no own ring)
 * @param plinthKey    pattern character of this tier's plinth in the ring multiblock
 * @param plinthRadius fallback only: any empty plinth within this horizontal radius of the heart takes the offering
 * @param offering     item id override; otherwise {@code firmages_shrine/offerings.json} decides
 * @param prayerTicks  override of {@code shrine.prayerSeconds}
 * @param fallback     true for the generic definition used by tiers without an own file
 */
public record ShrineTier(int tier, Optional<String> multiblock, char plinthKey, int plinthRadius, Optional<String> offering,
                         List<Rite> rites, OptionalInt prayerTicks, Response response, Optional<String> blessing, boolean fallback) {

    public static final char DEFAULT_PLINTH_KEY = 'P';
    public static final int DEFAULT_PLINTH_RADIUS = 12;

    /**
     * The deity's answer (m7).
     *
     * @param beamColor ARGB beam colour
     * @param skyTint   RGB sky and fog tint
     * @param particles particle type ids (simple particle types only)
     * @param sting     sound event id played at the start
     * @param voice     lang key of the voice line; empty = {@code firmages.shrine.voice.<granted stage>}
     * @param lightning visual-only lightning around the shrine
     */
    public record Response(int beamColor, int skyTint, List<String> particles, String sting, Optional<String> voice, boolean lightning) {
        public static final Response DEFAULT = new Response(0xFFFFD27F, 0xFFC070, List.of("minecraft:end_rod"),
            "firmages:shrine.sting.stone", Optional.empty(), false);
    }

    /**
     * A rite: one small Age-flavoured act that must be done before the prayer is heard.
     *
     * @param hint lang key of the message that tells the players what to do (empty = a generic text)
     */
    public record Rite(Type type, char key, String property, String value, String tag, long amount, int min, String hint) {
        public enum Type {
            /** All ring positions with {@code key} have blockstate property {@code property} = {@code value}. */
            BLOCKSTATE(true),
            /** A player used a block of block tag {@code tag} inside the shrine since the last awakening. */
            INTERACT(true),
            /** The heart holds {@code amount} FE. Not implemented yet (no energy buffer in the MVP). */
            ENERGY(false),
            /** Night, the heart sees the sky, no rain. */
            SKY(true),
            /** At least {@code min} players pray at once (only when that many players are online). */
            PLAYERS_PRAYING(true),
            /** The lent Keystone is back. Not implemented yet (Keystone lending is M6). */
            RELIC_RETURNED(false);

            private final boolean supported;

            Type(boolean supported) {
                this.supported = supported;
            }

            /** Unsupported rites are logged at load and never block a prayer. */
            public boolean supported() {
                return supported;
            }
        }
    }

    /** Stage granted when this tier's prayer completes. */
    public String grants() {
        return "age_" + (tier + 1);
    }

    /** The voice line lang key. */
    public String voiceKey() {
        return response.voice().orElse("firmages.shrine.voice." + grants());
    }

    /** This definition used for another tier number (the fallback is shared by every tier without an own file). */
    public ShrineTier asTier(int t) {
        return new ShrineTier(t, multiblock, plinthKey, plinthRadius, offering, rites, prayerTicks, response, blessing, fallback);
    }
}
