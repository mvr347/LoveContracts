package me.lovelace.lovecontracts.service;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerStatsCacheTest {
    @Test
    void offlineGetsDefaultsAndIsNotCached() {
        PlayerStatsCache c = new PlayerStatsCache();
        UUID u = UUID.randomUUID();
        assertEquals(PlayerStatsCache.Stats.EMPTY, c.get(u));
        assertFalse(c.isCached(u));
    }

    @Test
    void replaceOnlyWhenCachedAndEvict() {
        PlayerStatsCache c = new PlayerStatsCache();
        UUID u = UUID.randomUUID();
        var s = new PlayerStatsCache.Stats(1, 0, 1, 5, 0, 6, 2, 3);
        assertFalse(c.replaceIfCached(u, s));
        c.put(u, PlayerStatsCache.Stats.EMPTY);
        assertTrue(c.replaceIfCached(u, s));
        assertEquals(5, c.get(u).totalCompleted());
        c.evict(u);
        assertFalse(c.isCached(u));
    }

    @Test
    void successRateIgnoresDefaultLocale() {
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ru-RU"));
            assertEquals("83.3%", PlayerStatsCache.successRate(6, 5));
            assertEquals("0.0%", PlayerStatsCache.successRate(0, 0));
        } finally {
            Locale.setDefault(old);
        }
    }
}
