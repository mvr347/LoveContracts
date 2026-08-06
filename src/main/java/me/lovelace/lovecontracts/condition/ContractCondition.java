package me.lovelace.lovecontracts.condition;

import me.lovelace.lovecontracts.model.Contract;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for all contract conditions.
 * Handles listener registration lifecycle and per-player progress tracking.
 */
public abstract class ContractCondition implements Listener {

    protected final Contract contract;
    protected final Set<UUID> registeredPlayers = ConcurrentHashMap.newKeySet();
    protected final Map<UUID, Integer> progress = new ConcurrentHashMap<>();

    private boolean listenerRegistered = false;

    protected ContractCondition(Contract contract) {
        this.contract = contract;
    }

    public final Contract getContract() {
        return contract;
    }

    public final void register(Player player) {
        UUID uuid = player.getUniqueId();
        if (registeredPlayers.add(uuid)) {
            progress.putIfAbsent(uuid, 0);
            onRegister(player);
            ensureListenerRegistered();
        }
    }

    public final void unregister(Player player) {
        UUID uuid = player.getUniqueId();
        if (registeredPlayers.remove(uuid)) {
            onUnregister(player);
            progress.remove(uuid);
            if (registeredPlayers.isEmpty()) {
                unregisterListener();
            }
        }
    }

    public final boolean isRegistered(Player player) {
        return registeredPlayers.contains(player.getUniqueId());
    }

    public final int getProgress(Player player) {
        return progress.getOrDefault(player.getUniqueId(), 0);
    }

    public final void setProgress(Player player, int value) {
        int newVal = Math.max(0, value);
        progress.put(player.getUniqueId(), newVal);
        saveProgressAsync(player.getUniqueId(), newVal);
    }

    public final void incrementProgress(Player player) {
        incrementProgress(player, 1);
    }

    public final void incrementProgress(Player player, int amount) {
        int newVal = progress.merge(player.getUniqueId(), amount, Integer::sum);
        saveProgressAsync(player.getUniqueId(), newVal);
    }

    public final void loadProgress(UUID uuid, int value) {
        progress.put(uuid, Math.max(0, value));
    }

    private void saveProgressAsync(UUID uuid, int currentProgress) {
        me.lovelace.lovecontracts.LoveContracts plugin = me.lovelace.lovecontracts.LoveContracts.getInstance();
        if (plugin == null || contract == null) return;
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (java.sql.Connection conn = plugin.getDatabase().getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(
                         "UPDATE player_contracts SET progress_data = ? WHERE player_uuid = ? AND contract_id = ? AND completed_at IS NULL AND failed_at IS NULL")) {
                ps.setString(1, String.valueOf(currentProgress));
                ps.setString(2, uuid.toString());
                ps.setString(3, contract.getId());
                ps.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to save condition progress to DB", e);
            }
        });
    }

    public abstract boolean isCompleted(Player player);

    public abstract String getProgressString(Player player);

    public abstract int getRequiredAmount();

    protected void onRegister(Player player) {
    }

    protected void onUnregister(Player player) {
    }

    public void reset(Player player) {
        progress.put(player.getUniqueId(), 0);
    }

    private void ensureListenerRegistered() {
        if (!listenerRegistered) {
            org.bukkit.Bukkit.getPluginManager().registerEvents(this,
                    org.bukkit.Bukkit.getPluginManager().getPlugin("LoveContracts"));
            listenerRegistered = true;
        }
    }

    private void unregisterListener() {
        if (listenerRegistered) {
            org.bukkit.event.HandlerList.unregisterAll(this);
            listenerRegistered = false;
        }
    }

    public void shutdown() {
        registeredPlayers.clear();
        progress.clear();
        unregisterListener();
    }
}
