package me.lovelace.lovecontracts.task;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.manager.SyncManager;
import me.lovelace.lovecontracts.model.Contract;
import me.lovelace.lovecontracts.model.Difficulty;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ContractRotationTask implements Runnable {

    private final LoveContracts plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ContractRotationTask(LoveContracts plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("rotation.enabled", true)) {
            return;
        }

        plugin.getLogger().info("[Rotation] Starting daily contract rotation...");

        try {
            List<Contract> pool = plugin.getRegistry().getEnabled();
            if (pool.isEmpty()) {
                plugin.getLogger().warning("[Rotation] No enabled contracts in pool");
                return;
            }

            int total = plugin.getConfig().getInt("rotation.daily-count", 20);
            int easyPct = plugin.getConfig().getInt("rotation.difficulty-distribution.easy", 60);
            int medPct = plugin.getConfig().getInt("rotation.difficulty-distribution.medium", 30);

            int easyCount = (int) Math.round(total * easyPct / 100.0);
            int medCount = (int) Math.round(total * medPct / 100.0);
            int hardCount = Math.max(0, total - easyCount - medCount);

            List<Contract> selected = new ArrayList<>();
            selected.addAll(pickWeighted(pool, Difficulty.EASY, easyCount));
            selected.addAll(pickWeighted(pool, Difficulty.MEDIUM, medCount));
            selected.addAll(pickWeighted(pool, Difficulty.HARD, hardCount));

            if (selected.isEmpty()) {
                plugin.getLogger().warning("[Rotation] Selection produced 0 contracts");
                return;
            }

            try (Connection conn = plugin.getDatabase().getConnection()) {
                conn.setAutoCommit(false);
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("DELETE FROM active_contracts");
                }

                String insert = """
                    INSERT INTO active_contracts (contract_id, expires_at)
                    VALUES (?, datetime('now', '+' || ? || ' minutes'))
                    """;
                try (PreparedStatement ps = conn.prepareStatement(insert)) {
                    for (Contract c : selected) {
                        for (int i = 0; i < c.getDailySpawns(); i++) {
                            ps.setString(1, c.getId());
                            ps.setInt(2, c.getExpirationMinutes());
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
                }
                conn.commit();
            }

            plugin.getDatabase().resetDailyStats();

            plugin.getContractManager().setCurrentActiveIds(
                    selected.stream().map(Contract::getId).distinct().toList()
            );

            if (plugin.getConfig().getBoolean("rotation.notify-rotation", true)) {
                String msg = plugin.getConfig().getString("rotation.rotation-announcement",
                        "<green>✓ New contracts are available! Use /contracts</green>");
                Bukkit.getScheduler().runTask(plugin, () ->
                        Bukkit.broadcast(mm.deserialize(msg))
                );
            }

            SyncManager sync = plugin.getSyncManager();
            if (sync != null) {
                Bukkit.getScheduler().runTask(plugin, sync::syncGUIAll);
            }

            plugin.getLogger().info("[Rotation] Done — " + selected.size()
                    + " contracts (E:" + easyCount + " M:" + medCount + " H:" + hardCount + ")");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Rotation] Failed", e);
        }
    }

    private List<Contract> pickWeighted(List<Contract> pool, Difficulty diff, int count) {
        List<Contract> candidates = pool.stream()
                .filter(c -> c.getDifficulty() == diff)
                .collect(Collectors.toCollection(ArrayList::new));

        if (candidates.isEmpty() || count <= 0) return List.of();

        List<Contract> result = new ArrayList<>();
        for (int i = 0; i < count && !candidates.isEmpty(); i++) {
            int totalWeight = candidates.stream().mapToInt(Contract::getWeight).sum();
            int roll = ThreadLocalRandom.current().nextInt(Math.max(1, totalWeight));
            int cumulative = 0;
            for (Iterator<Contract> it = candidates.iterator(); it.hasNext(); ) {
                Contract c = it.next();
                cumulative += c.getWeight();
                if (roll < cumulative) {
                    result.add(c);
                    it.remove();
                    break;
                }
            }
        }
        return result;
    }
}
