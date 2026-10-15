package dev.firmages.core.boss;

/**
 * The Primordial's abilities. Each runs for {@link #length} ticks, which equals its animation in
 * primordial.animation.json (dev/gen_primordial.py); the timeline inside each is in {@link PrimordialEntity}.
 */
public enum PrimordialAbility {
    NONE(0, ""),
    /** Phase 1: telegraph 30 t (the body glides over a player and drops), impact, keel rests 40 t, rises 30 t. */
    KEEL_SLAM(100, "keel_slam"),
    /** Phase 1: one node charges 20 t (glows white), then fires three bolts. */
    NODE_VOLLEY(30, "volley_charge"),
    /** Phase 2: one node washes in its colour 20 t, then its beam sweeps 180 degrees over the floor for 120 t. */
    BEAM_SWEEP(140, "beam_sweep"),
    /** Phase 2: the veil opens, every player within 24 blocks is drawn under the core for 40 t, then a slam. */
    PULL(110, "pull"),
    /** Phase 3: rises 3 blocks for 30 t, nine star-falls and six consecrated shards, sinks back 30 t. */
    SHATTER(60, "shatter");

    public final int length;
    public final String animation;

    PrimordialAbility(int length, String animation) {
        this.length = length;
        this.animation = animation;
    }

    public static PrimordialAbility byId(int id) {
        PrimordialAbility[] v = values();
        return id >= 0 && id < v.length ? v[id] : NONE;
    }
}
