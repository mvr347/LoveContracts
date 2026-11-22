package me.lovelace.lovecontracts.service;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.util.LoreBar;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
        var nqm = plugin.getNpcQuestManager();
        // No SQL here: onRequest runs on the main thread. All values come from in-memory caches.
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
                case "pending_fine" -> String.valueOf(plugin.getFineManager() != null ? plugin.getFineManager().getDebt(uuid) : 0L);
                case "success_rate" -> PlayerStatsCache.successRate(st.totalAccepted(), st.totalCompleted());
                case "active_contracts" -> String.valueOf(nqm != null ? nqm.allQuests().size() : 0);

                case "questboard_enabled" -> (nqm == null || nqm.isQuestboardEnabled(uuid)) ? "yes" : "no";
                case "quest_count" -> {
                    Player online = player.getPlayer();
                    yield online != null && nqm != null ? String.valueOf(nqm.activeCount(online)) : "0";
                }
                case "quest_active", "has_active" -> {
                    boolean active = focusedObjective(player) != null;
                    yield params.equalsIgnoreCase("has_active") ? String.valueOf(active) : (active ? "yes" : "no");
                }
                case "quest_name", "active_name" -> {
                    QuestSidebarText.Objective obj = focusedObjective(player);
                    yield obj != null ? obj.name() : "—";
                }
                case "quest_goal" -> {
                    QuestSidebarText.Objective obj = focusedObjective(player);
                    yield obj != null ? obj.task() : "—";
                }
                case "quest_progress", "active_progress" -> {
                    QuestSidebarText.Objective obj = focusedObjective(player);
                    yield obj != null ? obj.progress() + "/" + obj.required() : "0/0";
                }
                case "quest_bar" -> {
                    QuestSidebarText.Objective obj = focusedObjective(player);
                    yield obj != null ? LoreBar.bar(obj.progress(), obj.required()) : "—";
                }
                case "quest_time_left" -> {
                    QuestSidebarText.Objective obj = focusedObjective(player);
                    yield obj != null && !obj.timeLeft().isBlank() ? obj.timeLeft() : "—";
                }
                case "quest_reward" -> {
                    QuestSidebarText.Objective obj = focusedObjective(player);
                    yield obj != null && !obj.rewardText().isBlank() ? obj.rewardText() : "—";
                }
                default -> null;
            };
        } catch (Exception e) {
            plugin.getLogger().warning("Placeholder error for " + params + ": " + e.getMessage());
            return "ERROR";
        }
    }

    private QuestSidebarText.Objective focusedObjective(OfflinePlayer offlinePlayer) {
        Player online = offlinePlayer.getPlayer();
        var nqm = plugin.getNpcQuestManager();
        if (online == null || nqm == null) return null;
        List<QuestSidebarText.Objective> list = nqm.objectives(online);
        String focus = plugin.getQuestSidebar() != null ? plugin.getQuestSidebar().focusedId(online.getUniqueId()) : null;
        return QuestSidebarText.select(list, focus);
    }
}
