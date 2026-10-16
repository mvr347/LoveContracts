package me.lovelace.lovecontracts.util;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Remembers blocks placed by players (bounded size + TTL) so that place-and-break loops
 * cannot farm mining progress. Memory only: after a restart the marks are gone, which is an
 * accepted trade-off against writing chunk PDC on every placement.
 */
public final class PlacedBlockTracker implements Listener {

    public record Key(UUID world, int x, int y, int z) {}

    private final Map<Key, Long> placed = new LinkedHashMap<>();
    private final int maxEntries;
    private final long ttlMillis;
    private final LongSupplier clock;
    private final Plugin plugin;

    public PlacedBlockTracker(Plugin plugin, int maxEntries, long ttlMillis) {
        this(plugin, maxEntries, ttlMillis, System::currentTimeMillis);
    }

    public PlacedBlockTracker(Plugin plugin, int maxEntries, long ttlMillis, LongSupplier clock) {
        this.plugin = plugin;
        this.maxEntries = Math.max(1, maxEntries);
        this.ttlMillis = ttlMillis;
        this.clock = clock;
    }

    public synchronized void markPlaced(Key key) {
        long now = clock.getAsLong();
        placed.remove(key); // re-insert so iteration order stays oldest-first
        placed.put(key, now);
        evict(now);
    }

    public synchronized boolean isPlaced(Key key) {
        Long at = placed.get(key);
        if (at == null) return false;
        if (clock.getAsLong() - at > ttlMillis) {
            placed.remove(key);
            return false;
        }
        return true;
    }

    public synchronized void clear(Key key) {
        placed.remove(key);
    }

    public synchronized int size() {
        return placed.size();
    }

    private void evict(long now) {
        // Entries are ordered oldest-first: drop from the head while over the bound or expired.
        while (!placed.isEmpty()) {
            Map.Entry<Key, Long> oldest = placed.entrySet().iterator().next();
            if (placed.size() > maxEntries || now - oldest.getValue() > ttlMillis) {
                placed.remove(oldest.getKey());
            } else {
                break;
            }
        }
    }

    public boolean isPlaced(Block block) {
        return isPlaced(keyOf(block));
    }

    private static Key keyOf(Block b) {
        return new Key(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        markPlaced(keyOf(event.getBlockPlaced()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        // Clear one tick later: several MONITOR listeners (mining contracts, NPC quests) must all
        // see the mark for this very break before it is dropped.
        Key key = keyOf(event.getBlock());
        Bukkit.getScheduler().runTask(plugin, () -> clear(key));
    }
}
