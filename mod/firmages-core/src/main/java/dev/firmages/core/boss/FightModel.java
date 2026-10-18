package dev.firmages.core.boss;

import java.util.Locale;

/**
 * The fight model of The Primordial (SPEC §39, docs/primordial.md "Numbers"): the expected length of the fight for 1, 2
 * and 4 players in Chaotic Draconic gear, from the stats in the Draconic Evolution jar and the boss's own rules. The
 * defaults of {@code [boss]} are set so that every party size lands in 6 to 10 minutes; {@code FightModelTest} holds
 * the numbers and {@code PrimordialEntity.initFight} logs the model for the party it wakes for. Pure Java.
 *
 * <p>Draconic Evolution 1.21.1-3.1.4.633 [verified, javap of {@code EquipCfg}, {@code DEModules}, {@code ModuleCfg},
 * {@code IModularTieredItem}; the pack's {@code DraconicEvolution.cfg} keeps the internal defaults]: a modular sword
 * deals {@code tierDamage * swordDamageMultiplier + damage modules} (Chaotic 2.5 x 7.0 = 17.5, each Chaotic damage
 * module +16) and swings {@code tierSpeed * swordSwingSpeedMultiplier * (1 + speed modules)} times a second (Chaotic
 * 2.0 x 1.6 = 3.2); a Chaotic tool grid is 8 x 6 cells, a damage module one cell. With the whole grid in damage modules
 * a hit would be about 650: the boss therefore counts at most {@link #HIT_CAP} of a hit after armour (a well-modded
 * Draconic or Chaotic sword reaches it), so the fight does not depend on the module count, and each attacker counts at
 * most once per {@link #IFRAMES} ticks (2 hits a second; the sword's 3.2 swings cannot beat it).
 *
 * <p>Assumptions (to be checked against a recorded fight): a player is in reach and hitting {@link #UPTIME} of the time
 * (slam rings, beam, star-fall and the pull cost the rest); the veil is open {@link #VEIL_OPEN} of the time per phase
 * (phase 1 and 2: a countered slam or pull opens 6 s of a 12 to 20 s cycle and not every counter lands; phase 3: six
 * shards per shatter, each 8 s, half of them spent on the Firmament Ward); in phase 3 the shatter makes it untouchable
 * 3 s of every 25 s; the two phase locks cost 3 s.
 */
public final class FightModel {
    /** Draconic Evolution, Chaotic tier (see above). */
    public static final double CHAOTIC_DAMAGE = 2.5, SWORD_DAMAGE_MULTIPLIER = 7.0, CHAOTIC_DAMAGE_MODULE = 16;
    public static final double CHAOTIC_SWING_SPEED = 2.0, SWORD_SWING_SPEED_MULTIPLIER = 1.6;
    /** The reference build: a Chaotic sword with two Chaotic damage modules (49.5 per hit, 44.7 after the boss's armour). */
    public static final int REFERENCE_DAMAGE_MODULES = 2;

    /** The boss's armour and toughness ({@code PrimordialEntity.createAttributes}). */
    public static final double ARMOUR = 12, TOUGHNESS = 8;
    /** Ticks between two hits of the same attacker that count. */
    public static final int IFRAMES = 10;
    /** Entering phase 2 and 3 locks it for 1 s and 2 s. */
    public static final int PHASE2_LOCK = 20, PHASE3_LOCK = 40;
    /** Phase 2 starts below 69 % health, phase 3 below 38 %: each phase takes about a third of the fight. */
    public static final float PHASE2_AT = 0.69F, PHASE3_AT = 0.38F;
    /** Shatter: every 25 s it is untouchable for 3 s (phase 3). */
    public static final double SHATTER_CYCLE_S = 25, SHATTER_UNTOUCHABLE_S = 3;

    /** The defaults of {@code [boss]} in firmages-server.toml. */
    public static final double EFFECTIVE_HEALTH = 13000, PER_PLAYER_FACTOR = 0.7, VEIL_FACTOR = 0.25, HIT_CAP = 40;
    /** Round G3: 12 and 14 (were 10 and 12), so the altar time of a lone player (+86 s) does not run into them. */
    public static final int ENRAGE_MINUTES = 12, STARFALL_ENRAGE_MINUTES = 14;

    /**
     * The altar shield of phase 2 and 3 (round G3, SPEC §39.1): four altars, a 3 s channel each, a walk of about 4 s
     * between two of them (16 blocks out, 22.6 apart, sprinting), the boss strikes one lit altar every 20 s.
     */
    public static final int ALTARS = 4;
    public static final double ALTAR_CHANNEL_S = 3, ALTAR_WALK_S = 4, ALTAR_STRIKE_S = 20;
    /** The fight may grow by at most this share through the altars (Felix A2b). */
    public static final double ALTAR_MAX_GROWTH = 0.20;

    /** Assumptions (see the class comment). */
    public static final double UPTIME = 0.6;
    public static final double[] VEIL_OPEN = {1.0 / 3, 1.0 / 3, 0.6};

    private FightModel() {}

    /**
     * Seconds the party needs to light the four altars once: {@code players} players light {@code players / (walk +
     * channel)} altars a second, the strikes undo one every 20 s, so the net rate is the difference.
     */
    public static double altarSeconds(int players) {
        double rate = Math.max(1, players) / (ALTAR_WALK_S + ALTAR_CHANNEL_S) - 1 / ALTAR_STRIKE_S;
        return ALTARS / rate;
    }

    /** Vanilla armour ({@code CombatRules.getDamageAfterAbsorb}) without enchantments or effects. */
    public static double afterArmour(double damage, double armour, double toughness) {
        double f = Math.min(20, Math.max(armour / 5, armour - 4 * damage / (toughness + 8)));
        return damage * (1 - f / 25);
    }

    /** Damage of a Chaotic sword with {@code modules} Chaotic damage modules. */
    public static double chaoticSword(int modules) {
        return CHAOTIC_DAMAGE * SWORD_DAMAGE_MULTIPLIER + modules * CHAOTIC_DAMAGE_MODULE;
    }

    /** Hits a second that count: the sword's swings, at most one per {@link #IFRAMES}. */
    public static double hitsPerSecond(int speedModulesPercent) {
        double swings = CHAOTIC_SWING_SPEED * SWORD_SWING_SPEED_MULTIPLIER * (1 + speedModulesPercent / 100.0);
        return Math.min(swings, 20.0 / IFRAMES);
    }

    /**
     * The model for one set of boss settings.
     *
     * @param effectiveHealth {@code boss.effectiveHealth}
     * @param perPlayer       {@code boss.perPlayerFactor}
     * @param veilFactor      {@code boss.veilFactor}
     * @param hitCap          {@code boss.hitCap}
     */
    public record Settings(double effectiveHealth, double perPlayer, double veilFactor, double hitCap) {
        public static final Settings DEFAULTS = new Settings(EFFECTIVE_HEALTH, PER_PLAYER_FACTOR, VEIL_FACTOR, HIT_CAP);

        public double pool(int players) {
            return effectiveHealth * ((1 - perPlayer) + perPlayer * Math.max(1, players));
        }

        /** What one hit of {@code damage} takes off the pool with the veil open: after armour, at most the cap. */
        public double poolPerHit(double damage) {
            double a = afterArmour(damage, ARMOUR, TOUGHNESS);
            return hitCap > 0 ? Math.min(a, hitCap) : a;
        }

        /** Pool damage a second of one player in phase {@code phase} (1..3) with a sword of {@code damage}. */
        public double dps(int phase, double damage) {
            double v = VEIL_OPEN[phase - 1];
            double avail = phase == 3 ? 1 - SHATTER_UNTOUCHABLE_S / SHATTER_CYCLE_S : 1;
            return hitsPerSecond(0) * UPTIME * poolPerHit(damage) * (v + (1 - v) * veilFactor) * avail;
        }

        /** Seconds per phase (1..3) for {@code players} players with a sword of {@code damage}. */
        public double[] phaseSeconds(int players, double damage) {
            double pool = pool(players);
            double[] share = {1 - PHASE2_AT, PHASE2_AT - PHASE3_AT, PHASE3_AT};
            double[] t = new double[3];
            for (int i = 0; i < 3; i++) t[i] = pool * share[i] / (players * dps(i + 1, damage));
            return t;
        }

        /** The fight without the altars (the model before round G3): the three phases and the phase locks. */
        public double fightSecondsWithoutAltars(int players, double damage) {
            double[] t = phaseSeconds(players, damage);
            return t[0] + t[1] + t[2] + (PHASE2_LOCK + PHASE3_LOCK) / 20.0;
        }

        /** The whole fight in seconds: the three phases, the phase locks and the altar shield of phase 2 and 3. */
        public double fightSeconds(int players, double damage) {
            return fightSecondsWithoutAltars(players, damage) + 2 * altarSeconds(players);
        }

        /** One log line: the expected length for 1, 2 and 4 players and the party at hand, reference build. */
        public String describe(int party) {
            double d = chaoticSword(REFERENCE_DAMAGE_MODULES);
            return String.format(Locale.ROOT,
                "fight model (Chaotic sword +%d damage modules, %.1f per hit, %.1f to the pool, %.1f hits/s, uptime %.0f%%): "
                    + "1 player %.1f min, 2 players %.1f min, 4 players %.1f min; this party of %d: %.1f min, pool %.0f",
                REFERENCE_DAMAGE_MODULES, d, poolPerHit(d), hitsPerSecond(0), UPTIME * 100, fightSeconds(1, d) / 60, fightSeconds(2, d) / 60,
                fightSeconds(4, d) / 60, party, fightSeconds(party, d) / 60, pool(party));
        }
    }
}
