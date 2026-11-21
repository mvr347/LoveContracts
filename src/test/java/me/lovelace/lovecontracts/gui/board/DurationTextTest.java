package me.lovelace.lovecontracts.gui.board;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationTextTest {

    private static final long MIN = 60_000L;

    @Test
    void lessThanAMinuteIsNotZeroMinutes() {
        assertEquals("меньше минуты", DurationText.format(0));
        assertEquals("меньше минуты", DurationText.format(59_999));
        assertEquals("меньше минуты", DurationText.format(-5));
    }

    @Test
    void minutesAndHoursAreJoinedWithoutZeroParts() {
        assertEquals("45 мин", DurationText.format(45 * MIN));
        assertEquals("2 ч", DurationText.format(120 * MIN));
        assertEquals("2 ч 15 мин", DurationText.format(135 * MIN));
    }

    @Test
    void daysDropTheMinutes() {
        assertEquals("1 д", DurationText.format(1440 * MIN));
        assertEquals("1 д 3 ч", DurationText.format((1440 + 180 + 25) * MIN));
    }
}
