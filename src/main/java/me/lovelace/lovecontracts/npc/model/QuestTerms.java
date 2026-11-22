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

    public static final QuestTerms NONE = new QuestTerms(QuestTier.EASY, 0, "", 0, 0, RewardTiers.defaults());

    public QuestTerms {
        tier = tier == null ? QuestTier.EASY : tier;
        timeLimitMinutes = Math.max(0, timeLimitMinutes);
        summary = summary == null ? "" : summary;
        advancePercent = tier.hasAdvance() ? Math.max(0, Math.min(100, advancePercent)) : 0;
        penaltyPercent = Math.max(0, Math.min(100, penaltyPercent));
        rewardTiers = rewardTiers == null ? RewardTiers.defaults() : rewardTiers;
    }

    public boolean timed() {
        return timeLimitMinutes > 0;
    }

    public long timeLimitMillis() {
        return timeLimitMinutes * 60_000L;
    }
}
