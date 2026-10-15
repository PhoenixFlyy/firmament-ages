package dev.firmages.core.text;

/**
 * Counts in player text (SPEC §24.3 rule 5, {@code dev/style-guide.md}): up to twelve as a word, 13 and up as digits.
 * Pure Java.
 */
public final class Numbers {
    private static final String[] WORDS = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve"};

    private Numbers() {}

    /** {@code 3 -> "three"}, {@code 13 -> "13"}; negative numbers as digits. */
    public static String words(int n) {
        return n >= 0 && n < WORDS.length ? WORDS[n] : Integer.toString(n);
    }
}
