package me.lovelace.lovecontracts.service;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.lovelace.lovecontracts.LoveContracts;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class ContractPlaceholderExpansion extends PlaceholderExpansion {

    private final LoveContracts plugin;

    public ContractPlaceholderExpansion(LoveContracts plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "lovecontracts";
    }

    @Override
    public @NotNull String getAuthor() {
        return "mvr347";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "N/A";

        UUID uuid = player.getUniqueId();

        try {
            return switch (params.toLowerCase()) {
                case "daily_completed" -> String.valueOf(getStat(uuid, "daily_completed"));
                case "daily_failed" -> String.valueOf(getStat(uuid, "daily_failed"));
                case "daily_accepted" -> String.valueOf(getStat(uuid, "daily_accepted"));
                case "total_completed" -> String.valueOf(plugin.getContractManager() != null ?
                        plugin.getContractManager().getCompletedContractsCount(uuid) : getStat(uuid, "total_completed"));
                case "total_failed" -> String.valueOf(getStat(uuid, "total_failed"));
                case "total_accepted" -> String.valueOf(getStat(uuid, "total_accepted"));
                case "current_streak" -> String.valueOf(getStat(uuid, "current_streak"));
                case "best_streak" -> String.valueOf(getStat(uuid, "best_streak"));
                case "has_active" -> String.valueOf(plugin.getContractManager() != null && plugin.getContractManager().hasActiveContract(uuid));
                case "active_name" -> {
                    if (plugin.getContractManager() == null) yield "None";
                    me.lovelace.lovecontracts.model.Contract active = plugin.getContractManager().getActiveContract(uuid);
                    yield active != null ? active.getDisplayName().replaceAll("<[^>]+>", "") : "None";
                }
                case "active_progress" -> {
                    if (plugin.getContractManager() == null || player.getPlayer() == null) yield "0/0";
                    me.lovelace.lovecontracts.model.Contract active = plugin.getContractManager().getActiveContract(uuid);
                    if (active == null || active.getCondition() == null) yield "0/0";
                    yield active.getCondition().getProgressString(player.getPlayer());
                }
                case "pending_fine" -> String.valueOf(plugin.getFineManager() != null ? plugin.getFineManager().getDebt(uuid) : 0L);
                case "success_rate" -> {
                    int accepted = getStat(uuid, "total_accepted");
                    int completed = getStat(uuid, "total_completed");
                    if (accepted == 0) yield "0.0%";
                    yield String.format("%.1f%%", (completed * 100.0) / accepted);
                }
                case "active_contracts" -> String.valueOf(plugin.getContractManager() != null ? plugin.getContractManager().getActiveContracts().size() : 0);
                default -> null;
            };
        } catch (Exception e) {
            plugin.getLogger().warning("Placeholder error for " + params + ": " + e.getMessage());
            return "ERROR";
        }
    }

    private int getStat(UUID uuid, String column) {
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT " + column + " FROM contract_stats WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }
}
