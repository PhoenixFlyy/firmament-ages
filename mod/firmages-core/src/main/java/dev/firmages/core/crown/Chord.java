package dev.firmages.core.crown;

import java.util.ArrayList;
import java.util.List;

/**
 * One named chord of the Lens Crown (SPEC §27.6): a set of lens indices 0..8 as a bit mask and the comparator signal
 * 1..15 the Star Anvil gives while a recipe of this chord waits. Pure Java.
 *
 * @param id     {@code <namespace>:<name>} of the chords file the chord comes from
 * @param mask   bit N set = lens N is part of the chord
 * @param signal comparator signal
 */
public record Chord(String id, int mask, int signal) {
    public static final int LENSES = 9;
    public static final int ALL = (1 << LENSES) - 1;

    /** The chord's name without namespace (lang key {@code firmages.chord.<name>}). */
    public String name() {
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(i + 1);
    }

    public List<Integer> lenses() {
        return lenses(mask);
    }

    public static List<Integer> lenses(int mask) {
        List<Integer> out = new ArrayList<>();
        for (int n = 0; n < LENSES; n++) if ((mask & (1 << n)) != 0) out.add(n);
        return out;
    }

    public static int mask(Iterable<Integer> lenses) {
        int m = 0;
        for (int n : lenses) m |= 1 << n;
        return m;
    }

    /** Lang key of a chord name. */
    public static String langKey(String id) {
        int i = id.indexOf(':');
        return "firmages.chord." + (i < 0 ? id : id.substring(i + 1));
    }
}
