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
                    VALUES (?, datetime('now', '+25 hours'))
                    """;
                try (PreparedStatement ps = conn.prepareStatement(insert)) {
                    for (Contract c : selected) {
                        for (int i = 0; i < c.getDailySpawns(); i++) {
                            ps.setString(1, c.getId());
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
                        "<gold>[Глашатай]</gold> <yellow>Внимание! Новые контракты доступны на доске объявлений!</yellow>");
                String soundName = plugin.getConfig().getString("rotation.rotation-sound", "BLOCK_BELL_USE");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // The Herald (LoveTweaks) announces; the plain broadcast is only a fallback
                    // for servers running without it.
                    if (!announceViaHerald()) {
                        Bukkit.broadcast(mm.deserialize(msg));
                    }
                    if (soundName != null && !soundName.isBlank()) {
                        try {
                            String key = soundName.trim().toLowerCase().replace('_', '.');
                            net.kyori.adventure.sound.Sound snd = net.kyori.adventure.sound.Sound.sound(
                                    net.kyori.adventure.key.Key.key(key),
                                    net.kyori.adventure.sound.Sound.Source.MASTER,
                                    1.0f,
                                    1.0f
                            );
                            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                                p.playSound(snd);
                            }
                        } catch (Exception ignored) {}
                    }
                });
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

    /**
     * Asks LoveTweaks' Herald to announce the new contracts. LoveTweaks does not register a
     * service, so this reflects on LoveTweaks#getHeraldManager() ->
     * HeraldManager#announceContractsRotation(), same as LoveBehavior's hunt announcement.
     * Must run on the main thread. Returns false if the Herald is unavailable.
     */
    private boolean announceViaHerald() {
        org.bukkit.plugin.Plugin loveTweaks = Bukkit.getPluginManager().getPlugin("LoveTweaks");
        if (loveTweaks == null || !loveTweaks.isEnabled()) {
            return false;
        }
        try {
            Object herald = loveTweaks.getClass().getMethod("getHeraldManager").invoke(loveTweaks);
            if (herald == null) {
                return false;
            }
            herald.getClass().getMethod("announceContractsRotation").invoke(herald);
            return true;
        } catch (NoSuchMethodException e) {
            return false; // older LoveTweaks without this announcement
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Rotation] Herald announcement failed, using plain broadcast", e);
            return false;
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
