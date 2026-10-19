package me.lovelace.lovecontracts.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoreBarTest {

    @Test
    void percentIsClampedAndEmptyMaximumCountsAsZero() {
        assertEquals(0, LoreBar.percent(5, 0));
        assertEquals(0, LoreBar.percent(-3, 10));
        assertEquals(50, LoreBar.percent(5, 10));
        assertEquals(100, LoreBar.percent(40, 10));
    }

    @Test
    void barHasSixteenCellsAndTheFilledPartIsGold() {
        String half = LoreBar.bar(5, 10);

        assertEquals(16, count(half, '█'));
        assertEquals(8, filledCells(half));
        assertTrue(half.endsWith("(<gold>50%</gold><dark_gray>)"), half);
    }

    @Test
    void almostFullBarIsNotFull() {
        assertEquals(15, filledCells(LoreBar.bar(99, 100)));
    }

    @Test
    void completeBarIsGreen() {
        String full = LoreBar.bar(10, 10);

        assertEquals(16, filledCells(full));
        assertTrue(full.contains("<green>"), full);
        assertTrue(full.endsWith("(<green>100%</green><dark_gray>)"), full);
    }

    @Test
    void zeroMaximumGivesAnEmptyBar() {
        String empty = LoreBar.bar(3, 0);

        assertEquals(0, filledCells(empty));
        assertTrue(empty.endsWith("(<gold>0%</gold><dark_gray>)"), empty);
    }

    /** Cells between the first gold tag and its closing tag. */
    private static long filledCells(String bar) {
        int from = bar.indexOf("<gold>") >= 0 ? bar.indexOf("<gold>") + "<gold>".length() : bar.indexOf("<green>") + "<green>".length();
        int to = bar.indexOf("</gold>") >= 0 ? bar.indexOf("</gold>") : bar.indexOf("</green>");
        return bar.substring(from, to).codePoints().filter(cp -> cp == '█').count();
    }

    private static long count(String text, char ch) {
        return text.chars().filter(c -> c == ch).count();
    }
}
