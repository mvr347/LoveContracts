package me.lovelace.lovecontracts.npc;

import me.lovelace.lovecontracts.npc.model.RewardTiers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RewardTiersTest {

    @Test
    void fastSpeedPolitenessAndQualityStackUpToCap() {
        // 10 min elapsed of 60 min limit = 16.7% <= 40% (+25%), 1 bonus interaction (+10%), no deaths (+10%), surplus (+5%) -> +50%
        var calc = RewardTiers.DEFAULT.calculate(200, 10 * 60_000L, 60, 1, true, 4, 1.5);
        assertEquals(300L, calc.finalReward());
        assertEquals(1.5, calc.multiplier(), 0.001);
        assertTrue(calc.breakdown().contains("Скорость +25%"));
        assertTrue(calc.breakdown().contains("Вежливость +10%"));
        assertTrue(calc.breakdown().contains("Качество +15%"));
    }

    @Test
    void multiplierIsCappedByConfiguredMax() {
        // 2 bonus interactions (+15%) + fast speed (+25%) + no deaths & surplus (+15%) = +55%, capped at 1.5
        var calc = RewardTiers.DEFAULT.calculate(200, 5 * 60_000L, 60, 2, true, 5, 1.5);
        assertEquals(300L, calc.finalReward());
        assertEquals(1.5, calc.multiplier(), 0.001);
    }

    @Test
    void mediumSpeedGivesSmallerBonusAndDeathsDisableNoDeathBonus() {
        // 35 min elapsed of 60 min limit = 58% <= 70% (+10%), 0 interactions, died (noDeaths=false), no surplus -> 1.10
        var calc = RewardTiers.DEFAULT.calculate(100, 35 * 60_000L, 60, 0, false, 0, 1.5);
        assertEquals(110L, calc.finalReward());
        assertEquals(1, calc.breakdown().size());
        assertEquals("Скорость +10%", calc.breakdown().getFirst());
    }

    @Test
    void maxRewardComputesUpperBoundAndZeroBaseStaysZero() {
        assertEquals(360L, RewardTiers.DEFAULT.maxReward(240, 1.5));
        assertEquals(0L, RewardTiers.DEFAULT.maxReward(0, 1.5));
        assertEquals(0L, RewardTiers.DEFAULT.calculate(0, 1000, 30, 2, true, 5, 1.5).finalReward());
    }
}
