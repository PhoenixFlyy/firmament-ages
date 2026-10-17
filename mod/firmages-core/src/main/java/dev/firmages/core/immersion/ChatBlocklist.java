package dev.firmages.core.immersion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The client chat safety net (SPEC §18.1): system and mod chat lines whose plain text matches one of the patterns
 * (Java regex, {@code find} semantics) are dropped. Invalid patterns are skipped and listed in {@link #errors()}.
 * Pure Java (unit-tested).
 */
public final class ChatBlocklist {
    /**
     * Built in, always applied besides {@code chat.blocklist} ({@link #withBuiltIns}): the Create Train Utilities join
     * notice, Ars Nouveau's remote Makeship ad and JourneyMap 6.0.9's join line ({@code ChatLog.announceMod}:
     * "§eJourneyMap:§f Press [§bJ§f]", with the web map "... or open browser to ...", or "Mapping disabled."; keys
     * {@code jm.common.chat_announcement}, {@code jm.common.mapgui_only_ready}, {@code jm.common.webserver_and_mapgui_ready},
     * {@code jm.common.enable_mapping_false_text} [verified, javap and lang]). Until 1.0.0 they were only the config's
     * default, so a {@code firmages-client.toml} written by an older version kept its old list and missed the
     * JourneyMap line.
     */
    public static final List<String> BUILT_IN_PATTERNS = List.of(
        "^ATTENTION \\(Create Train Utilities\\)",
        "(?i)Starbuncle Plush|makeship",
        "JourneyMap:(§.)*\\s*(Press \\[|Mapping disabled)");

    public static final ChatBlocklist EMPTY = new ChatBlocklist(List.of(), List.of(), List.of());

    private final List<String> source;
    private final List<Pattern> patterns;
    private final List<String> errors;

    private ChatBlocklist(List<String> source, List<Pattern> patterns, List<String> errors) {
        this.source = source;
        this.patterns = patterns;
        this.errors = errors;
    }

    /**
     * The built-in patterns plus the configured ones (duplicates once). {@link #source()} is the configured list, to
     * detect config changes.
     */
    public static ChatBlocklist withBuiltIns(List<? extends String> configured) {
        java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>(BUILT_IN_PATTERNS);
        for (String r : configured) if (r != null) all.add(r);
        ChatBlocklist compiled = compile(List.copyOf(all));
        return new ChatBlocklist(List.copyOf(configured), compiled.patterns, compiled.errors);
    }

    public static ChatBlocklist compile(List<? extends String> regexes) {
        List<Pattern> out = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (String r : regexes) {
            if (r == null || r.isBlank()) continue;
            try {
                out.add(Pattern.compile(r, Pattern.DOTALL));
            } catch (PatternSyntaxException e) {
                errors.add(r + ": " + e.getDescription());
            }
        }
        return new ChatBlocklist(List.copyOf(regexes), List.copyOf(out), List.copyOf(errors));
    }

    /** True if the line should be dropped. */
    public boolean blocks(String text) {
        if (text == null || text.isEmpty()) return false;
        for (Pattern p : patterns) if (p.matcher(text).find()) return true;
        return false;
    }

    /** The configured list this was compiled from (to detect config changes; without the built-in patterns). */
    public List<String> source() {
        return source;
    }

    public List<String> errors() {
        return errors;
    }
}
