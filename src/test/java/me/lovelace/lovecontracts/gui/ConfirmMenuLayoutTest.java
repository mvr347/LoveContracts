package me.lovelace.lovecontracts.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfirmMenuLayoutTest {
    @Test
    void zonesAreTwoDisjointThreeByThreeBlocksInsideTwentySevenSlots() {
        Set<Integer> all = new HashSet<>();
        for (int s : ConfirmMenuLayout.CONFIRM_ZONE) assertTrue(all.add(s) && s >= 0 && s < 27);
        for (int s : ConfirmMenuLayout.CANCEL_ZONE) assertTrue(all.add(s) && s >= 0 && s < 27);
        assertEquals(18, all.size());
        assertTrue(ConfirmMenuLayout.isConfirm(19) && ConfirmMenuLayout.isCancel(23));
        assertFalse(ConfirmMenuLayout.isConfirm(4) || ConfirmMenuLayout.isCancel(4));
    }
}
