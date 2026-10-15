package dev.firmages.core.text;

/**
 * Quantities in player text (SPEC §24.3 rule 5, {@code dev/style-guide.md}): up to twelve as words, 13 and up as
 * digits. Pure, shared by the Jade lines and the actionbar lines of the sky features.
 */
public final class Numbers {
    private static final String[] WORDS = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve"};

    private Numbers() {}

    /** "three" for 3, "13" for 13, "-1" for -1. */
    public static String words(long n) {
        return n >= 0 && n < WORDS.length ? WORDS[(int) n] : Long.toString(n);
    }

    /** {@link #words} with the first letter in upper case, for the start of a sentence ("Seven blocks are missing."). */
    public static String capitalized(long n) {
        String w = words(n);
        return Character.toUpperCase(w.charAt(0)) + w.substring(1);
    }
}
