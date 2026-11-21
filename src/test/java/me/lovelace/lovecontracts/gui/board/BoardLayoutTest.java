package me.lovelace.lovecontracts.gui.board;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The slot arithmetic of gui_gen RULE 4, 6 and 8 for the menu sizes of the standard. */
class BoardLayoutTest {

    @Test
    void controlButtonsAreCentredInSlotsTwoToSeven() {
        assertArrayEquals(new int[]{4}, BoardLayout.controlSlots(1));
        assertArrayEquals(new int[]{3, 5}, BoardLayout.controlSlots(2));
        assertArrayEquals(new int[]{2, 4, 6}, BoardLayout.controlSlots(3));
        assertArrayEquals(new int[]{2, 3, 5, 6}, BoardLayout.controlSlots(4));
        assertArrayEquals(new int[]{2, 3, 4, 6, 7}, BoardLayout.controlSlots(5));
        assertArrayEquals(new int[]{2, 3, 4, 5, 6, 7}, BoardLayout.controlSlots(6));
        assertThrows(IllegalArgumentException.class, () -> BoardLayout.controlSlots(0));
        assertThrows(IllegalArgumentException.class, () -> BoardLayout.controlSlots(7));
    }

    @Test
    void controlsNeverTouchTheBordersOfTheHeader() {
        for (int count = 1; count <= 6; count++) {
            for (int slot : BoardLayout.controlSlots(count)) {
                assertTrue(slot >= 2 && slot <= 7, "slot " + slot + " for " + count + " controls");
            }
        }
    }

    @Test
    void theBoardWorkZoneIsThreeRowsOfSevenWithEmptySideWalls() {
        int[] slots = BoardLayout.contentSlots(54);
        assertArrayEquals(new int[]{
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43}, slots);
        Set<Integer> used = new HashSet<>(Arrays.stream(slots).boxed().toList());
        for (int wall : new int[]{18, 26, 27, 35, 36, 44}) assertFalse(used.contains(wall), "wall " + wall);
    }

    @Test
    void smallerMenusHaveTheirWorkRowsRightAfterTheHeader() {
        assertArrayEquals(new int[]{10, 11, 12, 13, 14, 15, 16}, BoardLayout.contentSlots(27));
        assertEquals(14, BoardLayout.contentSlots(36).length);
        assertEquals(14, BoardLayout.contentSlots(45).length);
        assertEquals(9, BoardLayout.workStart(27));
        assertEquals(9, BoardLayout.workStart(36));
        assertEquals(18, BoardLayout.workStart(45));
        assertEquals(18, BoardLayout.workStart(54));
        assertEquals(1, BoardLayout.workRows(27));
        assertEquals(3, BoardLayout.workRows(54));
    }

    @Test
    void aSingleRowIsCentredByTheTableOfRuleSix() {
        // RULE 6 for a 27-slot menu (the row starts at 9): 1 -> 13, 2 -> 12 and 14, 3 -> 11 13 15, 4 -> 10 12 14 16
        assertArrayEquals(new int[]{13}, BoardLayout.rowSlots(9, 1));
        assertArrayEquals(new int[]{12, 14}, BoardLayout.rowSlots(9, 2));
        assertArrayEquals(new int[]{11, 13, 15}, BoardLayout.rowSlots(9, 3));
        assertArrayEquals(new int[]{10, 12, 14, 16}, BoardLayout.rowSlots(9, 4));
        assertArrayEquals(new int[]{11, 12, 13, 14, 15}, BoardLayout.rowSlots(9, 5));
        assertArrayEquals(new int[]{10, 11, 12, 14, 15, 16}, BoardLayout.rowSlots(9, 6));
        assertArrayEquals(new int[]{10, 11, 12, 13, 14, 15, 16}, BoardLayout.rowSlots(9, 7));
    }

    @Test
    void theFooterIsAlwaysTheLastRowWithExtraBackAndCloseAtItsEnd() {
        for (int size : new int[]{27, 36, 45, 54}) {
            assertEquals(size - 3, BoardLayout.extraSlot(size));
            assertEquals(size - 2, BoardLayout.backSlot(size));
            assertEquals(size - 1, BoardLayout.closeSlot(size));
        }
        assertEquals(51, BoardLayout.extraSlot(54));
        assertEquals(52, BoardLayout.backSlot(54));
        assertEquals(53, BoardLayout.closeSlot(54));
    }

    @Test
    void thePaginationArrowsSitOnTheSideWallsOfTheLastWorkRow() {
        assertEquals(36, BoardLayout.PAGE_PREVIOUS);
        assertEquals(44, BoardLayout.PAGE_NEXT);
        Set<Integer> content = new HashSet<>(Arrays.stream(BoardLayout.contentSlots(54)).boxed().toList());
        assertFalse(content.contains(BoardLayout.PAGE_PREVIOUS));
        assertFalse(content.contains(BoardLayout.PAGE_NEXT));
    }

    @Test
    void anEmptyListCardSitsInTheMiddleOfTheWorkZone() {
        assertEquals(31, BoardLayout.centerSlot(54));
        assertEquals(13, BoardLayout.centerSlot(27));
        assertTrue(Arrays.stream(BoardLayout.contentSlots(54)).anyMatch(s -> s == BoardLayout.centerSlot(54)));
    }
}
