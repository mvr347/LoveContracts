package me.lovelace.lovecontracts.npc;

import me.lovelace.lovecontracts.npc.reward.QuestSettlement;
import me.lovelace.lovecontracts.npc.reward.RewardTiers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RewardTiersTest {
    private static final long LIMIT = 100 * 60_000L;
    private final RewardTiers tiers = RewardTiers.defaults();

    @Test
    void fastFinishEarnsTheBestSpeedBonus() {
        var result = tiers.compute(200, LIMIT, 30 * 60_000L, 0, false, false);
        assertEquals(0.25, result.speed(), 1e-9);
        assertEquals(250, result.total());
    }

    @Test
    void slowerFinishEarnsTheSmallerBonusAndLastMinuteNone() {
        assertEquals(0.10, tiers.compute(200, LIMIT, 60 * 60_000L, 0, false, false).speed(), 1e-9);
        assertEquals(0.0, tiers.compute(200, LIMIT, 95 * 60_000L, 0, false, false).speed(), 1e-9);
        assertEquals(200, tiers.compute(200, LIMIT, 95 * 60_000L, 0, false, false).total());
    }

    @Test
    void noTimeLimitMeansNoSpeedBonus() {
        assertEquals(0.0, tiers.compute(200, 0, 1, 0, false, false).speed(), 1e-9);
    }

    @Test
    void politeChoicesAreCapped() {
        assertEquals(0.05, tiers.compute(100, 0, 0, 1, false, false).interactions(), 1e-9);
        assertEquals(0.15, tiers.compute(100, 0, 0, 9, false, false).interactions(), 1e-9);
    }

    @Test
    void qualityAddsNoDeathsAndSurplus() {
        assertEquals(0.10, tiers.compute(100, 0, 0, 0, true, true).quality(), 1e-9);
    }

    @Test
    void totalMultiplierIsClamped() {
        var capped = new RewardTiers(java.util.List.of(new RewardTiers.Speed(1.0, 1.0)), 0.5, 1.0, 0.5, 0.5, 1.5);
        assertEquals(150, capped.compute(100, LIMIT, 1, 5, true, true).total());
        assertEquals(150, capped.maximum(100, true));
    }

    @Test
    void maximumReflectsEveryBonus() {
        // 1 + 0.25 + 0.15 + 0.05 + 0.05 = 1.5, exactly the default clamp
        assertEquals(300, tiers.maximum(200, true));
        // without a time limit the speed bonus is out of reach: 1 + 0.15 + 0.05 + 0.05 = 1.25
        assertEquals(250, tiers.maximum(200, false));
    }

    @Test
    void advanceIsNotPaidTwice() {
        assertEquals(150, QuestSettlement.payout(250, 100));
        assertEquals(0, QuestSettlement.payout(80, 100));
        assertEquals(250, QuestSettlement.payout(250, 0));
    }

    @Test
    void advanceIsAShareOfTheBaseReward() {
        assertEquals(50, QuestSettlement.advance(200, 25));
        assertEquals(0, QuestSettlement.advance(200, 0));
    }

    @Test
    void failureOwesTheAdvancePlusAPenaltyShare() {
        assertEquals(50 + 20, QuestSettlement.debt(200, 50, 10));
        assertEquals(50, QuestSettlement.debt(200, 50, 0));
        assertEquals(0, QuestSettlement.debt(200, 0, 0));
    }
}
