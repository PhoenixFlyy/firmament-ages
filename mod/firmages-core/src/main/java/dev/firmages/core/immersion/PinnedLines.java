package dev.firmages.core.immersion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The pinned quests on the Age sign (SPEC §18.4, round G2): which quests, in which order, how many lines and the
 * progress text. Pure Java (unit-tested, {@code PinnedLinesTest}); {@code compat/ftbquests/PinnedQuests} reads FTB Quests.
 */
public final class PinnedLines {
    /** At most this many lines; more pinned quests end the list with {@link #MORE}. */
    public static final int MAX_LINES = 8;
    public static final String MORE = "…";
    public static final String DONE = "✔";

    /**
     * One pinned quest.
     * @param tasks       number of tasks
     * @param tasksDone   completed tasks
     * @param progress    the progress text of the only task ("2"), or empty when the quest has several tasks
     * @param maxProgress the target text of the only task ("5"), or empty
     */
    public record Quest(String title, boolean complete, int tasks, int tasksDone, String progress, String maxProgress) {}

    /** One line of the sign; {@code more} is the closing "…" line (empty title and progress). */
    public record Line(String title, String progress, boolean more) {}

    private PinnedLines() {}

    /**
     * "✔" for a complete quest; the only task's "2/5" when it counts more than one; else completed tasks of all tasks
     * ("1/3", "0/1").
     */
    public static String progress(Quest q) {
        if (q.complete()) return DONE;
        if (q.tasks() == 1 && !q.progress().isEmpty() && !q.maxProgress().isEmpty() && !q.maxProgress().equals("1")) {
            return q.progress() + "/" + q.maxProgress();
        }
        return Math.min(q.tasksDone(), q.tasks()) + "/" + q.tasks();
    }

    /** Open quests first, then complete ones, each group in the given order; at most {@link #MAX_LINES} lines. */
    public static List<Line> lines(List<Quest> quests) {
        List<Quest> sorted = new ArrayList<>(quests);
        sorted.sort(Comparator.comparing(Quest::complete)); // stable: keeps the order within each group
        List<Line> out = new ArrayList<>();
        boolean overflow = sorted.size() > MAX_LINES;
        int take = overflow ? MAX_LINES - 1 : sorted.size();
        for (int i = 0; i < take; i++) out.add(new Line(sorted.get(i).title(), progress(sorted.get(i)), false));
        if (overflow) out.add(new Line("", "", true));
        return out;
    }
}
