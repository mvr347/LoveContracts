package me.lovelace.lovecontracts.npc.reward;

/** Money rules around the advance a giver pays when a medium or hard quest is taken. */
public final class QuestSettlement {
    private QuestSettlement() {
    }

    /** What is still paid at the end: the advance was already handed out, so it is not paid twice. */
    public static long payout(long total, long advancePaid) {
        return Math.max(0, total - Math.max(0, advancePaid));
    }

    /** Advance given on taking the quest: a share of the base reward. */
    public static long advance(long base, int advancePercent) {
        if (advancePercent <= 0 || base <= 0) return 0;
        return Math.round(base * Math.min(100, advancePercent) / 100.0);
    }

    /** What a failed or expired quest costs: the advance back plus a penalty share of the base reward. */
    public static long debt(long base, long advancePaid, int penaltyPercent) {
        long penalty = penaltyPercent <= 0 || base <= 0 ? 0 : Math.round(base * Math.min(100, penaltyPercent) / 100.0);
        return Math.max(0, advancePaid) + penalty;
    }
}
