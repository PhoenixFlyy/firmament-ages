package dev.firmages.core.text;

import java.util.Locale;

/**
 * Quantities in player text (SPEC-himmel §24.3 rule 5, {@code dev/style-guide.md}): up to twelve as words, 13 and up as
 * digits with thousands separators ("1,200"). Pure Java, unit-tested.
 */
public final class Numbers {
    private static final String[] WORDS = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve"};

    private Numbers() {}

    /** {@code n} in words up to twelve, digits from 13 on (negative numbers as digits). */
    public static String words(long n) {
        if (n >= 0 && n < WORDS.length) return WORDS[(int) n];
        return String.format(Locale.ROOT, "%,d", n);
    }

    /** {@link #words} with a capital first letter, for the start of a sentence ("Seven blocks are missing."). */
    public static String capitalized(long n) {
        String w = words(n);
        return w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1);
    }
}
