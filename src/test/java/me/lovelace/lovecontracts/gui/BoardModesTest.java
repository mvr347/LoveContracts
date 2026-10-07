package me.lovelace.lovecontracts.gui;

import me.lovelace.lovecontracts.gui.BoardModes.FilterMode;
import me.lovelace.lovecontracts.gui.BoardModes.SortMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoardModesTest {

    @Test
    void filterNextAndPrevWrapAround() {
        assertEquals(FilterMode.EASY, FilterMode.ALL.next());
        assertEquals(FilterMode.ALL, FilterMode.FAILED.next());
        assertEquals(FilterMode.FAILED, FilterMode.ALL.prev());
        assertEquals(FilterMode.ALL, FilterMode.EASY.prev());
    }

    @Test
    void sortNextAndPrevWrapAround() {
        assertEquals(SortMode.REWARD_HIGH, SortMode.ALL.next());
        assertEquals(SortMode.ALL, SortMode.DIFFICULTY.next());
        assertEquals(SortMode.DIFFICULTY, SortMode.ALL.prev());
    }

    @Test
    void nextThenPrevIsIdentity() {
        for (FilterMode m : FilterMode.values()) assertEquals(m, m.next().prev());
        for (SortMode m : SortMode.values()) assertEquals(m, m.prev().next());
    }

    @Test
    void messageKeysMatchMessagesYml() {
        assertEquals("filters.all", FilterMode.ALL.messageKey());
        assertEquals("sorts.reward-high", SortMode.REWARD_HIGH.messageKey());
    }
}
