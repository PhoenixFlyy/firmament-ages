package dev.firmages.core.immersion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §18.4 (round G2): the pinned quests on the Age sign. */
class PinnedLinesTest {

    private static PinnedLines.Quest quest(String title, boolean complete, int tasks, int done, String progress, String max) {
        return new PinnedLines.Quest(title, complete, tasks, done, progress, max);
    }

    @Test
    void progressText() {
        assertEquals(PinnedLines.DONE, PinnedLines.progress(quest("A", true, 3, 3, "", "")));
        assertEquals("2/5", PinnedLines.progress(quest("Iron", false, 1, 0, "2", "5")), "the count of the only task");
        assertEquals("1/3", PinnedLines.progress(quest("Kit", false, 3, 1, "", "")), "tasks done of all");
        assertEquals("0/1", PinnedLines.progress(quest("Look", false, 1, 0, "0", "1")), "a single yes-no task");
        assertEquals("0/0", PinnedLines.progress(quest("Read", false, 0, 0, "", "")));
    }

    @Test
    void openQuestsFirstAtMostEightLines() {
        List<PinnedLines.Quest> qs = new ArrayList<>();
        qs.add(quest("done", true, 1, 1, "1", "1"));
        for (int i = 0; i < 3; i++) qs.add(quest("q" + i, false, 2, 1, "", ""));
        List<PinnedLines.Line> lines = PinnedLines.lines(qs);
        assertEquals(List.of("q0", "q1", "q2", "done"), lines.stream().map(PinnedLines.Line::title).toList());
        assertFalse(lines.stream().anyMatch(PinnedLines.Line::more));

        for (int i = 3; i < 10; i++) qs.add(quest("q" + i, false, 2, 1, "", ""));
        lines = PinnedLines.lines(qs);
        assertEquals(PinnedLines.MAX_LINES, lines.size(), "eight lines, the last one the ellipsis");
        assertTrue(lines.get(7).more());
        assertEquals("q6", lines.get(6).title());
        assertEquals(List.of(), PinnedLines.lines(List.of()));
    }

    @Test
    void exactlyEightFitWithoutEllipsis() {
        List<PinnedLines.Quest> qs = new ArrayList<>();
        for (int i = 0; i < 8; i++) qs.add(quest("q" + i, false, 1, 0, "0", "4"));
        List<PinnedLines.Line> lines = PinnedLines.lines(qs);
        assertEquals(8, lines.size());
        assertFalse(lines.get(7).more());
        assertEquals("0/4", lines.get(7).progress());
    }
}
