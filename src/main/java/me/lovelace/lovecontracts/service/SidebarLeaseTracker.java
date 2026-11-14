package me.lovelace.lovecontracts.service;

import java.util.*;

/** Main-thread lease lifecycle, independent of Bukkit so expiry and restoration can be tested. */
public final class SidebarLeaseTracker<B> implements AutoCloseable {
    public interface Lease<B> extends AutoCloseable {
        void update(B board);
        @Override void close();
    }
    public interface Provider<B> { Lease<B> show(UUID player, B board); }
    private record Entry<B>(Lease<B> lease, B board, long renewed) {}
    private final Map<UUID, Entry<B>> leases = new HashMap<>();
    private Provider<B> provider;

    public void provider(Provider<B> next) {
        if (provider != next) { close(); provider = next; }
    }

    public void render(UUID player, B board, long now) {
        if (board == null || provider == null) { remove(player); return; }
        Entry<B> old = leases.get(player);
        // API leases have a one-second TTL. Renew before expiry; never accumulate requests.
        if (old == null || now - old.renewed() >= 500_000_000L) {
            remove(player);
            leases.put(player, new Entry<>(provider.show(player, board), board, now));
        } else if (!Objects.equals(old.board(), board)) {
            old.lease().update(board);
            leases.put(player, new Entry<>(old.lease(), board, old.renewed()));
        }
    }

    public void remove(UUID player) {
        Entry<B> old = leases.remove(player);
        if (old != null) old.lease().close();
    }
    @Override public void close() {
        for (UUID player : List.copyOf(leases.keySet())) remove(player);
    }
}
