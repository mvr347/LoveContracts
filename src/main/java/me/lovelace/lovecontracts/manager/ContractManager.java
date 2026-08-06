package me.lovelace.lovecontracts.manager;

import dev.lovelace.lovecore.api.stats.Metrics;
import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.model.Contract;
import me.lovelace.lovecontracts.model.Difficulty;
import me.lovelace.lovecontracts.task.ContractRotationTask;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Runtime contract lifecycle: which contracts are active right now, accepting one,
 * completing it, failing it (expiration or admin force). Definitions live in
 * {@link ContractRegistry}; this class only tracks the daily rotation + player progress
 * in the SQLite tables set up by {@code ContractDatabase}.
 */
public class ContractManager {

    private final LoveContracts plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private volatile List<String> activeIds = new ArrayList<>();
    private final Map<UUID, Set<String>> acceptedTodayCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> activeContractCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> completedTodayCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> failedCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> completedCountCache = new ConcurrentHashMap<>();
    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();

    public ContractManager(LoveContracts plugin) {
        this.plugin = plugin;
        loadActiveIdsFromDatabase();
    }

    public void cleanupPlayer(UUID uuid) {
        if (uuid == null) return;
        Contract activeContract = getActiveContract(uuid);
        if (activeContract != null && activeContract.getCondition() != null) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                activeContract.getCondition().unregister(online);
            }
        }
        acceptedTodayCache.remove(uuid);
        activeContractCache.remove(uuid);
        completedTodayCache.remove(uuid);
        failedCache.remove(uuid);
        completedCountCache.remove(uuid);
        disabledPlayers.remove(uuid);
    }

    public void loadActiveContractForPlayer(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT contract_id, progress_data FROM player_contracts WHERE player_uuid = ? AND completed_at IS NULL AND failed_at IS NULL")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String contractId = rs.getString(1);
                        String progressData = rs.getString(2);
                        int progress = 0;
                        if (progressData != null && !progressData.isEmpty()) {
                            try { progress = Integer.parseInt(progressData); } catch (NumberFormatException ignored) {}
                        }
                        Contract contract = plugin.getRegistry().getContract(contractId);
                        if (contract != null) {
                            activeContractCache.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(contractId);
                            if (contract.getCondition() != null) {
                                int finalProgress = progress;
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (player.isOnline()) {
                                        contract.getCondition().loadProgress(uuid, finalProgress);
                                        contract.getCondition().register(player);
                                    }
                                });
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load active contract on join for " + uuid, e);
            }
        });
    }

    private void loadActiveIdsFromDatabase() {
        List<String> ids = new ArrayList<>();
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT DISTINCT contract_id FROM active_contracts WHERE expires_at > datetime('now')");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getString(1));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load active contracts on startup", e);
        }
        activeIds = ids;
    }

    /** Called once at startup; if nothing is active yet (fresh install / DB wiped) kick off a rotation. */
    public void startRotationTask() {
        if (activeIds.isEmpty()) {
            plugin.getLogger().info("No active contracts on startup — running initial rotation");
            Bukkit.getScheduler().runTaskAsynchronously(plugin, new ContractRotationTask(plugin));
        }
    }

    public void forceRotate() {
        acceptedTodayCache.clear();
        activeContractCache.clear();
        completedTodayCache.clear();
        failedCache.clear();
        completedCountCache.clear();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new ContractRotationTask(plugin));
    }

    public void setCurrentActiveIds(List<String> ids) {
        this.activeIds = new ArrayList<>(ids);
        this.acceptedTodayCache.clear();
        this.activeContractCache.clear();
        this.completedTodayCache.clear();
        this.failedCache.clear();
        this.completedCountCache.clear();
        if (plugin.getSignManager() != null) {
            plugin.getSignManager().updateAllSigns();
        }
    }

    public void addActiveContract(Contract contract) {
        if (contract == null) return;
        plugin.getRegistry().registerContract(contract);
        if (!activeIds.contains(contract.getId())) {
            activeIds.add(contract.getId());
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO active_contracts (contract_id, expires_at) VALUES (?, datetime('now', '+1 day'))")) {
                ps.setString(1, contract.getId());
                ps.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to persist new active contract", e);
            }
        });
        if (plugin.getSignManager() != null) {
            plugin.getSignManager().updateAllSigns();
        }
    }

    public boolean isActive(String id) {
        return activeIds.contains(id);
    }

    public List<Contract> getActiveContracts() {
        List<Contract> result = new ArrayList<>();
        for (String id : activeIds) {
            Contract c = plugin.getRegistry().getContract(id);
            if (c != null && c.isEnabled()) {
                result.add(c);
            }
        }
        return result;
    }

    public boolean togglePlayerContracts(UUID uuid) {
        if (disabledPlayers.contains(uuid)) {
            disabledPlayers.remove(uuid);
            return true; // Now enabled
        } else {
            disabledPlayers.add(uuid);
            return false; // Now disabled
        }
    }

    public boolean isContractsDisabled(Player player) {
        if (player == null) return false;
        return disabledPlayers.contains(player.getUniqueId());
    }

    public int getCompletedContractsCount(UUID uuid) {
        if (uuid == null) return 0;
        return completedCountCache.computeIfAbsent(uuid, this::fetchCompletedContractsCountFromDb);
    }

    private int fetchCompletedContractsCountFromDb(UUID uuid) {
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM player_contracts WHERE player_uuid = ? AND completed_at IS NOT NULL")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get completed contracts count", e);
        }
        return 0;
    }

    public boolean hasCompletedStarterContracts(Player player) {
        if (player == null) return false;
        int starterCompleted = 0;
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT DISTINCT contract_id FROM player_contracts WHERE player_uuid = ? AND completed_at IS NOT NULL")) {
            ps.setString(1, player.getUniqueId().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString(1);
                    Contract c = plugin.getRegistry().getContract(id);
                    if (c != null && (c.isStarter() || c.getDifficulty() == Difficulty.STARTER)) {
                        starterCompleted++;
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to check starter contracts count", e);
        }
        return starterCompleted >= 3 || getCompletedContractsCount(player.getUniqueId()) >= 3;
    }

    public boolean hasAcceptedToday(UUID uuid, String contractId) {
        Set<String> accepted = acceptedTodayCache.get(uuid);
        if (accepted == null) {
            accepted = loadAcceptedTodayForPlayer(uuid);
            acceptedTodayCache.put(uuid, accepted);
        }
        return accepted.contains(contractId);
    }

    private Set<String> loadAcceptedTodayForPlayer(UUID uuid) {
        Set<String> set = ConcurrentHashMap.newKeySet();
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT contract_id FROM player_contracts WHERE player_uuid = ? " +
                     "AND date(accepted_at) = date('now')")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    set.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "loadAcceptedTodayForPlayer query failed", e);
        }
        return set;
    }

    public void acceptContract(Player player, String id) {
        if (player == null) return;

        Contract contract = plugin.getRegistry().getContract(id);
        if (contract == null || !contract.isEnabled() || !isActive(id)) {
            player.sendMessage(mm.deserialize("<red>Этот контракт больше недоступен.</red>"));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean accepted;
            try {
                accepted = tryInsertAcceptance(player, contract);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Accept-contract failed", e);
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(mm.deserialize("<red>Ошибка базы данных. Обратитесь к администратору.</red>")));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (!accepted) {
                    player.sendMessage(mm.deserialize("<red>Вы уже приняли этот контракт сегодня или свободные слоты закончились.</red>"));
                    return;
                }
                if (contract.getCondition() != null) {
                    contract.getCondition().reset(player);
                    contract.getCondition().register(player);
                }
                plugin.getMessageManager().sendMessage(player, "messages.contract-accepted",
                        "<green>Вы приняли контракт:</green> <gold>{CONTRACT}</gold>",
                        java.util.Map.of("CONTRACT", strip(contract.getDisplayName())));
                plugin.getSyncManager().broadcastAccept(player, contract);
                plugin.getContractGUI().open(player);
            });
        });
    }

    private boolean tryInsertAcceptance(Player player, Contract contract) throws SQLException {
        try (Connection conn = plugin.getDatabase().getConnection()) {
            conn.setAutoCommit(false);
            try {
                int activeId = -1;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT active_id, taken_by_count FROM active_contracts " +
                        "WHERE contract_id = ? AND expires_at > datetime('now') ORDER BY active_id")) {
                    ps.setString(1, contract.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int taken = rs.getInt(2);
                            if (contract.getMaxAcceptances() < 0 || taken < contract.getMaxAcceptances()) {
                                activeId = rs.getInt(1);
                                break;
                            }
                        }
                    }
                }

                if (activeId == -1) {
                    conn.rollback();
                    return false;
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO player_contracts (player_uuid, player_name, contract_id, active_id) " +
                        "VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, player.getUniqueId().toString());
                    ps.setString(2, player.getName());
                    ps.setString(3, contract.getId());
                    ps.setInt(4, activeId);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    // UNIQUE(player_uuid, contract_id, accepted_date) — already accepted today.
                    conn.rollback();
                    return false;
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE active_contracts SET taken_by_count = taken_by_count + 1 WHERE active_id = ?")) {
                    ps.setInt(1, activeId);
                    ps.executeUpdate();
                }

                upsertStat(conn, player.getUniqueId(), "daily_accepted", 1);
                upsertStat(conn, player.getUniqueId(), "total_accepted", 1);

                conn.commit();
                acceptedTodayCache.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).add(contract.getId());
                activeContractCache.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).add(contract.getId());
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public void completeContract(Player player, Contract contract) {
        if (player == null || contract == null) return;
        if (contract.getCondition() != null) {
            contract.getCondition().unregister(player);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = false;
            try (Connection conn = plugin.getDatabase().getConnection()) {
                conn.setAutoCommit(false);
                int rows = 0;
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE player_contracts SET completed_at = datetime('now') " +
                        "WHERE player_uuid = ? AND contract_id = ? AND completed_at IS NULL AND failed_at IS NULL")) {
                    ps.setString(1, player.getUniqueId().toString());
                    ps.setString(2, contract.getId());
                    rows = ps.executeUpdate();
                }
                if (rows > 0) {
                    upsertStat(conn, player.getUniqueId(), "daily_completed", 1);
                    upsertStat(conn, player.getUniqueId(), "total_completed", 1);
                    bumpStreak(conn, player.getUniqueId(), true);
                    conn.commit();
                    success = true;

                    Set<String> active = activeContractCache.get(player.getUniqueId());
                    if (active != null) active.remove(contract.getId());
                    completedTodayCache.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).add(contract.getId());
                    completedCountCache.compute(player.getUniqueId(), (k, v) -> v == null ? 1 : v + 1);
                } else {
                    conn.rollback();
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Complete-contract update failed", e);
            }

            if (success) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getRewardProcessor().giveRewards(player, contract);
                    plugin.getStatBus().ifPresent(bus -> bus.record(player.getUniqueId(), Metrics.CONTRACTS_COMPLETED, 1.0));
                    if (player.isOnline()) {
                        plugin.getMessageManager().sendMessage(player, "messages.contract-completed",
                                "<green>Выполнено:</green> <gold>{CONTRACT}</gold>",
                                java.util.Map.of("CONTRACT", strip(contract.getDisplayName())));
                    }
                    plugin.getSyncManager().broadcastComplete(player, contract);
                    plugin.getSyncManager().syncGUIForPlayer(player);
                });
            }
        });
    }

    /** Convenience overload for callers that already have an online {@link Player} (admin force-fail, GUI). */
    public void failContract(Player player, Contract contract) {
        if (player == null || contract == null) return;
        failContract(player.getUniqueId(), contract);
    }

    /**
     * UUID-based entry point: the expiration sweep only has the UUID from the DB row, the
     * player may well be offline. Money penalties only apply if the player is online — physical
     * coins can't be taken out of an inventory that isn't loaded.
     */
    public void failContract(UUID uuid, Contract contract) {
        if (uuid == null || contract == null) return;

        Player online = Bukkit.getPlayer(uuid);
        if (online != null && contract.getCondition() != null) {
            contract.getCondition().unregister(online);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabase().getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE player_contracts SET failed_at = datetime('now') " +
                        "WHERE player_uuid = ? AND contract_id = ? AND completed_at IS NULL AND failed_at IS NULL")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, contract.getId());
                    ps.executeUpdate();
                }
                upsertStat(conn, uuid, "daily_failed", 1);
                upsertStat(conn, uuid, "total_failed", 1);
                bumpStreak(conn, uuid, false);
                conn.commit();

                Set<String> active = activeContractCache.get(uuid);
                if (active != null) active.remove(contract.getId());
                failedCache.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(contract.getId());
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Fail-contract update failed", e);
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    if (!contract.isStarter()) {
                        plugin.getRewardProcessor().applyPenalties(player, contract);
                    }
                    plugin.getMessageManager().sendMessage(player, "messages.contract-failed",
                            "<red>Провал:</red> <gold>{CONTRACT}</gold>",
                            java.util.Map.of("CONTRACT", strip(contract.getDisplayName())));
                    plugin.getSyncManager().syncGUIForPlayer(player);
                }
                plugin.getSyncManager().broadcastFail(player, contract);
            });
        });
    }

    public Contract getActiveContract(UUID uuid) {
        if (uuid == null) return null;
        Set<String> activeIds = getActiveContractIds(uuid);
        if (activeIds.isEmpty()) return null;
        for (String id : activeIds) {
            Contract c = plugin.getRegistry().getContract(id);
            if (c != null) return c;
        }
        return null;
    }

    public void cancelContract(Player player, Contract contract) {
        if (player == null || contract == null) return;
        failContract(player, contract);
    }

    public boolean hasActiveContract(UUID uuid) {
        if (uuid == null) return false;
        return !getActiveContractIds(uuid).isEmpty();
    }

    public Set<String> getCompletedTodayContractIds(UUID uuid) {
        if (uuid == null) return java.util.Collections.emptySet();
        return completedTodayCache.computeIfAbsent(uuid, this::loadCompletedTodayContractIdsFromDb);
    }

    private Set<String> loadCompletedTodayContractIdsFromDb(UUID uuid) {
        Set<String> set = ConcurrentHashMap.newKeySet();
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT contract_id FROM player_contracts WHERE player_uuid = ? AND completed_at IS NOT NULL AND date(completed_at) = date('now')")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    set.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "getCompletedTodayContractIds query failed", e);
        }
        return set;
    }

    public Set<String> getFailedContractIds(UUID uuid) {
        if (uuid == null) return java.util.Collections.emptySet();
        return failedCache.computeIfAbsent(uuid, this::loadFailedContractIdsFromDb);
    }

    private Set<String> loadFailedContractIdsFromDb(UUID uuid) {
        Set<String> set = ConcurrentHashMap.newKeySet();
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT contract_id FROM player_contracts WHERE player_uuid = ? AND failed_at IS NOT NULL")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    set.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "getFailedContractIds query failed", e);
        }
        return set;
    }

    public Set<String> getActiveContractIds(UUID uuid) {
        if (uuid == null) return java.util.Collections.emptySet();
        return activeContractCache.computeIfAbsent(uuid, this::loadActiveContractIdsFromDb);
    }

    private Set<String> loadActiveContractIdsFromDb(UUID uuid) {
        Set<String> set = ConcurrentHashMap.newKeySet();
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT contract_id FROM player_contracts WHERE player_uuid = ? AND completed_at IS NULL AND failed_at IS NULL")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    set.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "getActiveContractIds query failed", e);
        }
        return set;
    }

    private void upsertStat(Connection conn, UUID uuid, String column, int delta) throws SQLException {
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT OR IGNORE INTO contract_stats (player_uuid) VALUES (?)")) {
            ins.setString(1, uuid.toString());
            ins.executeUpdate();
        }
        try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE contract_stats SET " + column + " = " + column + " + ? WHERE player_uuid = ?")) {
            upd.setInt(1, delta);
            upd.setString(2, uuid.toString());
            upd.executeUpdate();
        }
    }

    private void bumpStreak(Connection conn, UUID uuid, boolean success) throws SQLException {
        String sql = success
                ? "UPDATE contract_stats SET current_streak = current_streak + 1, " +
                  "best_streak = MAX(best_streak, current_streak + 1) WHERE player_uuid = ?"
                : "UPDATE contract_stats SET current_streak = 0 WHERE player_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    private String strip(String input) {
        return input.replaceAll("<[^>]+>", "");
    }

    public void shutdown() {
        activeIds = new ArrayList<>();
    }
}
