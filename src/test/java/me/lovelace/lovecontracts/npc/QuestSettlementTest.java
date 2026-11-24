package me.lovelace.lovecontracts.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import me.lovelace.lovecontracts.npc.reward.QuestSettlement;
import org.junit.jupiter.api.Test;

class QuestSettlementTest {

    @Test
    void advanceIsAShareOfTheBaseRewardAndZeroWhenNotApplicable() {
        assertEquals(200, QuestSettlement.advance(1000, 20));
        assertEquals(0, QuestSettlement.advance(1000, 0));
        assertEquals(0, QuestSettlement.advance(0, 30));
        assertEquals(1000, QuestSettlement.advance(1000, 250)); // capped at the whole reward
    }

    @Test
    void payoutNeverPaysTheAdvanceTwiceNorGoesNegative() {
        assertEquals(800, QuestSettlement.payout(1000, 200));
        assertEquals(0, QuestSettlement.payout(100, 200));
        assertEquals(1000, QuestSettlement.payout(1000, 0));
    }

    @Test
    void failureOwesTheAdvancePlusThePenaltyShare() {
        assertEquals(300, QuestSettlement.debt(1000, 200, 10));
        assertEquals(200, QuestSettlement.debt(1000, 200, 0));
        assertEquals(0, QuestSettlement.debt(1000, 0, 0));
    }
}
