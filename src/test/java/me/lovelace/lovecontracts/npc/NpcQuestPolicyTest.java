package me.lovelace.lovecontracts.npc;

import me.lovelace.lovecontracts.npc.model.NpcQuestPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcQuestPolicyTest {
    @Test void unloadedAndAlreadyActiveQuestsCannotBeAccepted() {
        assertFalse(NpcQuestPolicy.available(false, false, true, null, 0, 0));
        assertFalse(NpcQuestPolicy.available(true, true, true, null, 0, 0));
        assertTrue(NpcQuestPolicy.available(true, false, false, null, 0, 0));
    }
    @Test void nonrepeatableStaysCompletedEvenAfterADay() {
        assertFalse(NpcQuestPolicy.available(true, false, false, 0L, 0, 86_400_000));
    }
    @Test void repeatableUnlocksAtExactCooldownAndNotBefore() {
        assertFalse(NpcQuestPolicy.available(true, false, true, 0L, 1440, 86_399_999));
        assertTrue(NpcQuestPolicy.available(true, false, true, 0L, 1440, 86_400_000));
        assertFalse(NpcQuestPolicy.available(true, false, true, 100L, 0, 99));
    }
}
