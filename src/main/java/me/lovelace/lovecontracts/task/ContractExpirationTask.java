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
            List<String[]> activeContracts = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT player_uuid, contract_id, accepted_at FROM player_contracts
                WHERE completed_at IS NULL AND failed_at IS NULL
                """);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    activeContracts.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3)});
                }
            }

            for (String[] row : activeContracts) {
                UUID uuid = UUID.fromString(row[0]);
                Contract c = plugin.getRegistry().getContract(row[1]);
                if (c == null) continue;

                java.time.Instant acceptedAt;
                try {
                    acceptedAt = java.time.Instant.parse(row[2].replace(" ", "T") + "Z");
                } catch (Exception e) {
                    acceptedAt = java.time.Instant.now();
                }

                long elapsedMinutes = java.time.Duration.between(acceptedAt, java.time.Instant.now()).toMinutes();
                long totalMinutes = c.getExpirationMinutes();

                if (elapsedMinutes >= totalMinutes) {
                    plugin.getContractManager().failContract(uuid, c);
                } else if (totalMinutes - elapsedMinutes <= warnMinutes && totalMinutes - elapsedMinutes > 0) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        String msg = plugin.getConfig().getString("rotation.expiration-warning",
                                        "<yellow>⚠ Срок действия вашего контракта истекает через {TIME} мин.!</yellow>")
                                .replace("{TIME}", String.valueOf(totalMinutes - elapsedMinutes));
                        p.sendMessage(mm.deserialize(msg));
                    }
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Expiration] Task error", e);
        }
    }
}
