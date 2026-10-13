package dev.firmages.core.immersion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §18.9: quest window size, modal clamp and section gaps. GUI-scaled sizes of 1920x1080 at scale 2, 3 and 4 (auto). */
class QuestWindowTest {
    @Test
    void windowAtSeventyFivePercentIsCentred() {
        assertEquals(new QuestWindow.Rect(120, 67, 720, 405), QuestWindow.rect(960, 540, 75));
        assertEquals(new QuestWindow.Rect(80, 45, 480, 270), QuestWindow.rect(640, 360, 75));
        assertEquals(new QuestWindow.Rect(60, 34, 360, 202), QuestWindow.rect(480, 270, 75));
        // Felix's ultrawide, maximised window at GUI scale 3
        assertEquals(new QuestWindow.Rect(143, 57, 859, 339), QuestWindow.rect(1146, 453, 75));
    }

    @Test
    void zeroAndHundredAreFullscreenSmallValuesAreHalf() {
        assertEquals(new QuestWindow.Rect(0, 0, 640, 360), QuestWindow.rect(640, 360, 0));
        assertEquals(new QuestWindow.Rect(0, 0, 640, 360), QuestWindow.rect(640, 360, 100));
        assertEquals(new QuestWindow.Rect(0, 0, 640, 360), QuestWindow.rect(640, 360, 250));
        assertEquals(new QuestWindow.Rect(0, 0, 640, 360), QuestWindow.rect(640, 360, -5));
        assertEquals(new QuestWindow.Rect(160, 90, 320, 180), QuestWindow.rect(640, 360, 10));
        assertEquals(50, QuestWindow.effectivePercent(49));
        assertEquals(99, QuestWindow.effectivePercent(99));
    }

    @Test
    void windowStaysInsideTheGameWindowForEveryPercentAndSize() {
        for (int gw = 160; gw <= 1800; gw += 37) {
            for (int gh = 120; gh <= 900; gh += 41) {
                for (int p = -1; p <= 101; p++) {
                    QuestWindow.Rect r = QuestWindow.rect(gw, gh, p);
                    assertTrue(r.x() >= 0 && r.y() >= 0 && r.x() + r.width() <= gw && r.y() + r.height() <= gh, r + " in " + gw + "x" + gh);
                    assertTrue(Math.abs(r.x() - (gw - r.x() - r.width())) <= 1, "centred horizontally: " + r);
                }
            }
        }
    }

    @Test
    void screenSizedModalStartsAtTheEdge() {
        // The quest view with full_screen_quest: 1 is as large as the screen; FTB Library put it at -10.
        assertEquals(0, QuestWindow.modalPos(0, 480, 480));
        assertEquals(0, QuestWindow.modalPos(0, 479, 480));
        assertEquals(0, QuestWindow.modalPos(0, 270, 270));
    }

    @Test
    void smallModalKeepsFtbMarginAndNeverStartsBeforeTheEdge() {
        assertEquals(100, QuestWindow.modalPos(100, 150, 480));
        assertEquals(320, QuestWindow.modalPos(400, 150, 480), "10 px from the right edge");
        assertEquals(0, QuestWindow.modalPos(-30, 150, 480));
        assertEquals(0, QuestWindow.modalPos(50, 475, 480), "no room for the margin: at the edge");
    }

    @Test
    void modalLargerThanTheScreenIsCentredAndFitsTheWindow() {
        // 75 % screen (480 of 640) and a panel 3/4 of the window high (TaskButton's item list): 270 of 360 window, 270 screen
        assertEquals(0, QuestWindow.modalPos(30, 270, 270));
        // a 520 px panel on a 480 px screen in a 640 px window: -20, i.e. window x 80 - 20 = 60 .. 580
        int pos = QuestWindow.modalPos(0, 520, 480);
        assertEquals(-20, pos);
        int screenX = (640 - 480) / 2;
        assertTrue(screenX + pos >= 0 && screenX + pos + 520 <= 640);
        assertEquals(-2, QuestWindow.modalPos(5, 483, 480), "odd overflow rounds towards the left");
    }

    @Test
    void sectionGapsFollowTheParagraphGap() {
        assertEquals(2, QuestWindow.sectionGapReduction(4), "default: 2 px less per gap");
        assertEquals(0, QuestWindow.sectionGapReduction(6));
        assertEquals(0, QuestWindow.sectionGapReduction(9), "9 = FTB's spacing everywhere");
        assertEquals(1, QuestWindow.sectionGapReduction(5));
        assertEquals(4, QuestWindow.sectionGapReduction(2));
        assertEquals(4, QuestWindow.sectionGapReduction(0), "at most 4");
    }
}
