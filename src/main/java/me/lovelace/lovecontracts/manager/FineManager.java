package me.lovelace.lovecontracts.manager;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import me.lovelace.lovecontracts.LoveContracts;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Управляет незакрытыми задолженностями по штрафам (pending fines).
 * Списание происходит событийно (без таймеров/потоков), как только у игрока появляются монеты в инвентаре.
 */
public class FineManager {

    private final LoveContracts plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, Long> playerDebts = new ConcurrentHashMap<>();

    public FineManager(LoveContracts plugin) {
        this.plugin = plugin;
        loadDebtsFromDatabase();
    }

    private void loadDebtsFromDatabase() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT player_uuid, amount FROM pending_fines WHERE amount > 0");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString(1));
                    long amount = rs.getLong(2);
                    playerDebts.put(uuid, amount);
                }
                plugin.getLogger().info("Loaded " + playerDebts.size() + " active fine debts from database.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load pending fines from database", e);
            }
        });
    }

    public boolean hasDebt(UUID uuid) {
        if (uuid == null) return false;
        Long debt = playerDebts.get(uuid);
        return debt != null && debt > 0;
    }

    public long getDebt(UUID uuid) {
        if (uuid == null) return 0L;
        return playerDebts.getOrDefault(uuid, 0L);
    }

    public void addDebt(UUID uuid, long amount) {
        if (uuid == null || amount <= 0) return;

        long newTotal = playerDebts.compute(uuid, (k, v) -> (v == null ? 0L : v) + amount);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO pending_fines (player_uuid, amount, updated_at) VALUES (?, ?, datetime('now')) " +
                         "ON CONFLICT(player_uuid) DO UPDATE SET amount = ?, updated_at = datetime('now')")) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, newTotal);
                ps.setLong(3, newTotal);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save pending fine for " + uuid, e);
            }
        });
    }

    public void collectDebt(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();

        Long debt = playerDebts.get(uuid);
        if (debt == null || debt <= 0) return;

        LoveCore.service(LoveEconomy.class).ifPresent(economy -> {
            long balance = economy.balance(player);
            if (balance <= 0) return;

            long charge = Math.min(debt, balance);
            if (charge <= 0) return;

            economy.charge(player, charge);
            long remaining = debt - charge;

            if (remaining <= 0) {
                playerDebts.remove(uuid);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try (Connection conn = plugin.getDatabase().getConnection();
                         PreparedStatement ps = conn.prepareStatement("DELETE FROM pending_fines WHERE player_uuid = ?")) {
                        ps.setString(1, uuid.toString());
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to clear pending fine for " + uuid, e);
                    }
                });
                plugin.getMessageManager().sendMessage(player, "messages.fine-cleared",
                        "<green>✔ Взыскано <gold>{AMOUNT} монет</gold> в счет штрафа. Ваш долг полностью погашен!</green>",
                        java.util.Map.of("AMOUNT", String.valueOf(charge)));
            } else {
                playerDebts.put(uuid, remaining);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try (Connection conn = plugin.getDatabase().getConnection();
                         PreparedStatement ps = conn.prepareStatement(
                                 "UPDATE pending_fines SET amount = ?, updated_at = datetime('now') WHERE player_uuid = ?")) {
                        ps.setLong(1, remaining);
                        ps.setString(2, uuid.toString());
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to update pending fine for " + uuid, e);
                    }
                });
                plugin.getMessageManager().sendMessage(player, "messages.fine-collected",
                        "<red>⚠ Взыскано <gold>{AMOUNT} монет</gold> в счет штрафа. Остаток долга: <gold>{REMAINING} монет</gold>.</red>",
                        java.util.Map.of("AMOUNT", String.valueOf(charge), "REMAINING", String.valueOf(remaining)));
            }
        });
    }
}
