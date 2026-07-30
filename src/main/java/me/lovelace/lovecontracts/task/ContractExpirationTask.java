package me.lovelace.lovecontracts.task;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.model.Contract;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class ContractExpirationTask implements Runnable {

    private final LoveContracts plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ContractExpirationTask(LoveContracts plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        int warnMinutes = plugin.getConfig().getInt("rotation.warning-before-expiration-minutes", 5);

        try (Connection conn = plugin.getDatabase().getConnection()) {
            String offset = "-" + (24 * 60 - warnMinutes) + " minutes";

            List<String[]> toWarn = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT player_uuid, contract_id FROM player_contracts
                WHERE completed_at IS NULL AND failed_at IS NULL
                  AND accepted_at <= datetime('now', ?)
                  AND accepted_at > datetime('now', '-24 hours')
                """)) {
                ps.setString(1, offset);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    toWarn.add(new String[]{rs.getString(1), rs.getString(2)});
                }
            }

            for (String[] row : toWarn) {
                Player p = Bukkit.getPlayer(UUID.fromString(row[0]));
                if (p != null && p.isOnline()) {
                    String msg = plugin.getConfig().getString("rotation.expiration-warning",
                                    "<yellow>⚠ Your contracts expire in {TIME} minutes!</yellow>")
                            .replace("{TIME}", String.valueOf(warnMinutes));
                    p.sendMessage(mm.deserialize(msg));
                }
            }

            List<String[]> toFail = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT player_uuid, contract_id FROM player_contracts
                WHERE completed_at IS NULL AND failed_at IS NULL
                  AND accepted_at < datetime('now', '-24 hours')
                """);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    toFail.add(new String[]{rs.getString(1), rs.getString(2)});
                }
            }

            for (String[] row : toFail) {
                UUID uuid = UUID.fromString(row[0]);
                Contract c = plugin.getRegistry().getContract(row[1]);
                if (c == null) continue;
                // UUID-based overload: the player may be offline, dropping the id via
                // Bukkit.getPlayer() first would make an offline player's expiry unfixable.
                plugin.getContractManager().failContract(uuid, c);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Expiration] Task error", e);
        }
    }
}
