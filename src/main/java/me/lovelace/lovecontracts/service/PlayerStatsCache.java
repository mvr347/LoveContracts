package me.lovelace.lovecontracts.service;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory contract stats for ONLINE players only. PlaceholderAPI asks for these on the main
 * thread (scoreboards, tab), so onRequest must never touch SQL: values are loaded on join,
 * refreshed after writes and evicted on quit. Offline players read defaults and are not cached
 * (caching them would leak one entry per player ever asked about).
 */
public final class PlayerStatsCache {

    public record Stats(int dailyCompleted, int dailyFailed, int dailyAccepted,
                        int totalCompleted, int totalFailed, int totalAccepted,
                        int currentStreak, int bestStreak) {
        public static final Stats EMPTY = new Stats(0, 0, 0, 0, 0, 0, 0, 0);
    }

    private final Map<UUID, Stats> cache = new ConcurrentHashMap<>();

    /** Cached stats or {@link Stats#EMPTY}; never loads and never inserts. */
    public Stats get(UUID uuid) {
        if (uuid == null) return Stats.EMPTY;
        return cache.getOrDefault(uuid, Stats.EMPTY);
    }

    public boolean isCached(UUID uuid) {
        return uuid != null && cache.containsKey(uuid);
    }

    public void put(UUID uuid, Stats stats) {
        if (uuid != null && stats != null) cache.put(uuid, stats);
    }

    /** Replaces the entry only if the player is still tracked (online); returns whether it did. */
    public boolean replaceIfCached(UUID uuid, Stats stats) {
        return uuid != null && stats != null && cache.replace(uuid, stats) != null;
    }

    public void evict(UUID uuid) {
        if (uuid != null) cache.remove(uuid);
    }

    public java.util.Set<UUID> cachedPlayers() {
        return java.util.Set.copyOf(cache.keySet());
    }

    public void clear() {
        cache.clear();
    }

    public static String successRate(int accepted, int completed) {
        if (accepted <= 0) return "0.0%";
        return String.format(Locale.ROOT, "%.1f%%", (completed * 100.0) / accepted);
    }
}
