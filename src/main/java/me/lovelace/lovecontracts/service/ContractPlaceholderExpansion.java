package me.lovelace.lovecontracts.service;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.lovelace.lovecontracts.LoveContracts;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
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
        var cm = plugin.getContractManager();
        // No SQL here: onRequest runs on the main thread. Stats come from the online-player
        // cache; offline players get defaults.
        PlayerStatsCache.Stats st = plugin.getStatsCache().get(uuid);

        try {
            return switch (params.toLowerCase(Locale.ROOT)) {
                case "daily_completed" -> String.valueOf(st.dailyCompleted());
                case "daily_failed" -> String.valueOf(st.dailyFailed());
                case "daily_accepted" -> String.valueOf(st.dailyAccepted());
                case "total_completed" -> String.valueOf(st.totalCompleted());
                case "total_failed" -> String.valueOf(st.totalFailed());
                case "total_accepted" -> String.valueOf(st.totalAccepted());
                case "current_streak" -> String.valueOf(st.currentStreak());
                case "best_streak" -> String.valueOf(st.bestStreak());
                case "has_active" -> String.valueOf(cm != null && cm.peekActiveContract(uuid) != null);
                case "active_name" -> {
                    if (cm == null) yield "None";
                    me.lovelace.lovecontracts.model.Contract active = cm.peekActiveContract(uuid);
                    yield active != null ? active.getDisplayName().replaceAll("<[^>]+>", "") : "None";
                }
                case "active_progress" -> {
                    if (cm == null || player.getPlayer() == null) yield "0/0";
                    me.lovelace.lovecontracts.model.Contract active = cm.peekActiveContract(uuid);
                    if (active == null || active.getCondition() == null) yield "0/0";
                    yield active.getCondition().getProgressString(player.getPlayer());
                }
                case "pending_fine" -> String.valueOf(plugin.getFineManager() != null ? plugin.getFineManager().getDebt(uuid) : 0L);
                case "success_rate" -> PlayerStatsCache.successRate(st.totalAccepted(), st.totalCompleted());
                case "active_contracts" -> String.valueOf(cm != null ? cm.getActiveContracts().size() : 0);
                default -> null;
            };
        } catch (Exception e) {
            plugin.getLogger().warning("Placeholder error for " + params + ": " + e.getMessage());
            return "ERROR";
        }
    }
}
