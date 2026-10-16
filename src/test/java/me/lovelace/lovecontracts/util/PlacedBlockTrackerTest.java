package me.lovelace.lovecontracts.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class PlacedBlockTrackerTest {
    private static final UUID W = UUID.randomUUID();

    private static PlacedBlockTracker.Key k(int x) {
        return new PlacedBlockTracker.Key(W, x, 64, 0);
    }

    @Test
    void marksAndExpires() {
        AtomicLong now = new AtomicLong(0);
        PlacedBlockTracker t = new PlacedBlockTracker(null, 10, 1000, now::get);
        t.markPlaced(k(1));
        assertTrue(t.isPlaced(k(1)));
        assertFalse(t.isPlaced(k(2)));
        now.set(1001);
        assertFalse(t.isPlaced(k(1)));
    }

    @Test
    void boundedSizeEvictsOldest() {
        PlacedBlockTracker t = new PlacedBlockTracker(null, 2, 1000, () -> 0L);
        t.markPlaced(k(1));
        t.markPlaced(k(2));
        t.markPlaced(k(3));
        assertEquals(2, t.size());
        assertFalse(t.isPlaced(k(1)));
        assertTrue(t.isPlaced(k(3)));
    }

    @Test
    void clearRemoves() {
        PlacedBlockTracker t = new PlacedBlockTracker(null, 5, 1000, () -> 0L);
        t.markPlaced(k(1));
        t.clear(k(1));
        assertFalse(t.isPlaced(k(1)));
    }
}
