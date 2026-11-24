package me.lovelace.lovecontracts.npc;

import me.lovelace.lovecontracts.npc.model.RefusalPolicy;
import me.lovelace.lovecontracts.npc.model.RefusalPolicy.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefusalPolicyTest {
    private static final long HOUR = 3_600_000L, DAY = 24 * HOUR, WEEK = 7 * DAY;
    private final RefusalPolicy.Config config = RefusalPolicy.Config.defaults();

    private State cancels(State from, int count, long now) {
        State state = from;
        for (int i = 0; i < count; i++) state = RefusalPolicy.onCancel(state, now, config);
        return state;
    }

    @Test
    void twoCancelsDoNotBlock() {
        State state = cancels(State.EMPTY, 2, 1000);
        assertEquals(2, state.streak());
        assertFalse(RefusalPolicy.locked(state, 1000));
    }

    @Test
    void thirdCancelInARowBlocksForAnHour() {
        State state = cancels(State.EMPTY, 3, 1000);
        assertTrue(RefusalPolicy.locked(state, 1000));
        assertEquals(HOUR, RefusalPolicy.remaining(state, 1000));
        assertEquals(0, state.streak());
        assertEquals(1, state.totalLocks());
    }

    @Test
    void repeatsEscalateToADayThenAWeekAndStayThere() {
        long now = 0;
        State state = cancels(State.EMPTY, 3, now);
        now += HOUR + 1;
        state = cancels(state, 3, now);
        assertEquals(DAY, RefusalPolicy.remaining(state, now));
        now += DAY + 1;
        state = cancels(state, 3, now);
        assertEquals(WEEK, RefusalPolicy.remaining(state, now));
        now += WEEK + 1;
        state = cancels(state, 3, now);
        assertEquals(WEEK, RefusalPolicy.remaining(state, now));
        assertEquals(4, state.totalLocks());
    }

    @Test
    void completedQuestResetsTheStreakButNotTheLevel() {
        State state = cancels(State.EMPTY, 2, 1000);
        state = RefusalPolicy.onCompleted(state);
        assertEquals(0, state.streak());
        state = cancels(state, 2, 2000);
        assertFalse(RefusalPolicy.locked(state, 2000));
    }

    @Test
    void levelDecaysAfterAQuietPeriod() {
        long start = 1000; // 0 is the "never locked" marker, so the test clock must not start there
        State state = cancels(State.EMPTY, 3, start);
        long later = start + HOUR + 31 * DAY;
        state = cancels(state, 3, later);
        // the old level decayed, so this is a first-level block again
        assertEquals(HOUR, RefusalPolicy.remaining(state, later));
    }
}
