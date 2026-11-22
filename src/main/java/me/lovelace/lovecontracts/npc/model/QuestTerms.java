package me.lovelace.lovecontracts.npc.model;

import me.lovelace.lovecontracts.npc.reward.RewardTiers;

/**
 * The commercial side of a quest: how hard it is, how long it takes, what the card says and what money rules apply.
 * Kept apart from {@link NpcQuest} so a chain written before these fields existed keeps working unchanged.
 *
 * @param timeLimitMinutes 0 means no time limit
 * @param advancePercent   share of the base reward paid as an advance on taking (0 for none)
 * @param penaltyPercent   extra share of the base reward owed on failure, on top of the advance
 * @param summary          the short text of the board card; empty falls back to the display name only
 */
public record QuestTerms(QuestTier tier, int timeLimitMinutes, String summary, int advancePercent,
                         int penaltyPercent, RewardTiers rewardTiers) {

    public static final QuestTerms NONE = new QuestTerms(QuestTier.EASY, 0, "", 0, 0, RewardTiers.none());

    public QuestTerms {
        tier = tier == null ? QuestTier.EASY : tier;
        timeLimitMinutes = Math.max(0, timeLimitMinutes);
        summary = summary == null ? "" : summary;
        advancePercent = tier.hasAdvance() ? Math.max(0, Math.min(100, advancePercent)) : 0;
        penaltyPercent = Math.max(0, Math.min(100, penaltyPercent));
        rewardTiers = rewardTiers == null ? RewardTiers.none() : rewardTiers;
    }

    /**
     * Reads the terms of one quest. A quest that does not declare a {@code tier} keeps the old behavior (no time limit,
     * no advance): hand-written chains and chains saved by the builder must not start expiring after an update.
     * Missing values come from the plugin's {@code quests} section: per-tier defaults and shared reward tiers.
     */
    public static QuestTerms read(org.bukkit.configuration.ConfigurationSection quest,
                                  org.bukkit.configuration.ConfigurationSection defaults) {
        String summary = quest.getString("summary", "");
        RewardTiers shared = RewardTiers.read(defaults == null ? null : defaults.getConfigurationSection("reward-tiers"),
                RewardTiers.defaults());
        if (!quest.contains("tier")) {
            return new QuestTerms(QuestTier.EASY, 0, summary, 0, 0, RewardTiers.none());
        }
        QuestTier tier = QuestTier.parse(quest.getString("tier"));
        var tierDefaults = defaults == null ? null
                : defaults.getConfigurationSection("tiers." + tier.name().toLowerCase(java.util.Locale.ROOT));
        // an older config.yml has no "quests" section yet: fall back to the built-in values of the tier
        int[] builtIn = builtIn(tier);
        return new QuestTerms(tier,
                quest.getInt("time-limit-minutes", tierDefaults == null ? builtIn[0] : tierDefaults.getInt("time-limit-minutes", builtIn[0])),
                summary,
                quest.getInt("advance-percent", tierDefaults == null ? builtIn[1] : tierDefaults.getInt("advance-percent", builtIn[1])),
                quest.getInt("penalty-percent", tierDefaults == null ? builtIn[2] : tierDefaults.getInt("penalty-percent", builtIn[2])),
                RewardTiers.read(quest.getConfigurationSection("reward-tiers"), shared));
    }

    /** {time limit minutes, advance percent, penalty percent} when neither the quest nor config.yml says. */
    static int[] builtIn(QuestTier tier) {
        return switch (tier) {
            case EASY -> new int[] {45, 0, 0};
            case MEDIUM -> new int[] {105, 20, 10};
            case HARD -> new int[] {210, 30, 20};
        };
    }

    public boolean timed() {
        return timeLimitMinutes > 0;
    }

    public long timeLimitMillis() {
        return timeLimitMinutes * 60_000L;
    }
}
