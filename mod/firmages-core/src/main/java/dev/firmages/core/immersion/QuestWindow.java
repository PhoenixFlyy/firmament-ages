package dev.firmages.core.immersion;

/**
 * Geometry of the FTB Quests screen as a window (SPEC §18.9). Pure Java (unit-tested); all values in GUI-scaled
 * pixels, positions relative to the quest screen unless noted.
 */
public final class QuestWindow {
    /** Smallest window share that is honoured; below it the chapter list, the map and the quest view do not fit. */
    public static final int MIN_PERCENT = 50;
    /** FTB Library keeps a pushed modal panel this far from the right and bottom edge of its screen. */
    public static final int MODAL_MARGIN = 10;
    /** FTB Quests 2101.1.36 puts the separator 6 px below the task icons and the text 6 px below that. */
    public static final int FTB_SECTION_GAP = 6;
    /** Smallest paragraph gap the section gaps follow, so the separator never touches the icons or the subtitle. */
    public static final int MIN_SECTION_GAP = 2;

    /** The quest screen inside the game window: x and y from the window's top left corner. */
    public record Rect(int x, int y, int width, int height) {}

    private QuestWindow() {}

    /** The share actually used: 0 and 100 or more mean fullscreen (100), 1..49 count as {@link #MIN_PERCENT}. */
    public static int effectivePercent(int percent) {
        if (percent <= 0 || percent >= 100) return 100;
        return Math.max(MIN_PERCENT, percent);
    }

    /** The quest screen at {@code percent} of the GUI-scaled window, centred the way FTB Library's {@code BaseScreen} centres. */
    public static Rect rect(int guiWidth, int guiHeight, int percent) {
        int p = effectivePercent(percent);
        int w = guiWidth * p / 100;
        int h = guiHeight * p / 100;
        return new Rect((guiWidth - w) / 2, (guiHeight - h) / 2, w, h);
    }

    /**
     * Position of a modal panel along one axis when it is pushed. FTB Library 2101.1.37 {@code pushModalPanel} does
     * {@code min(pos, screenSize - size - 10)} but mixes the panel's absolute position with its relative one, and lets a
     * panel as large as the screen go to -10 (the cut-off quest view). Here: a panel that fits the screen keeps FTB's
     * 10 px margin where there is room and never starts before the screen's edge; a larger one is centred on the screen,
     * which is centred in the window, so it stays inside the window as long as it fits there.
     *
     * @param pos        the panel's position relative to the screen before clamping
     * @param size       the panel's width or height
     * @param screenSize the screen's width or height
     */
    public static int modalPos(int pos, int size, int screenSize) {
        if (size > screenSize) return Math.floorDiv(screenSize - size, 2);
        return Math.max(0, Math.min(pos, screenSize - size - MODAL_MARGIN));
    }

    /**
     * Pixels taken off each section gap of the quest view (title to task headings, task icons to separator,
     * separator to text). Follows {@code ui.questParagraphGap}: at 6 or more FTB's gaps stay; below that each gap
     * shrinks by 6 minus the paragraph gap, by at most 4 (default 4: 2 px less each).
     */
    public static int sectionGapReduction(int paragraphGap) {
        return FTB_SECTION_GAP - Math.clamp(paragraphGap, MIN_SECTION_GAP, FTB_SECTION_GAP);
    }
}
