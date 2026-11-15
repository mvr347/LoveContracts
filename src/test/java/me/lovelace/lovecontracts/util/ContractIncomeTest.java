package me.lovelace.lovecontracts.util;

import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContractIncomeTest {
    @Test void rollingWindowDoesNotResetAtMidnightAndIsPerPlayer() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ContractIncome.initialize(c);
            UUID player = UUID.randomUUID();
            long beforeMidnight = ContractIncome.WINDOW - 1000;
            assertTrue(ContractIncome.reserve(c, player, 1800, 1800, beforeMidnight));
            assertFalse(ContractIncome.reserve(c, player, 1, 1800, ContractIncome.WINDOW + 1000));
            assertTrue(ContractIncome.reserve(c, UUID.randomUUID(), 1800, 1800, ContractIncome.WINDOW + 1000));
            assertFalse(ContractIncome.reserve(c, player, 1, 1800, beforeMidnight + ContractIncome.WINDOW - 1));
            assertTrue(ContractIncome.reserve(c, player, 1800, 1800, beforeMidnight + ContractIncome.WINDOW));
        }
    }
    @Test void failedCompletionRollsBackReservationAndBoundaryIsInclusive() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ContractIncome.initialize(c);
            UUID player = UUID.randomUUID();
            c.setAutoCommit(false);
            assertTrue(ContractIncome.reserve(c, player, 240, 1800, 0));
            c.rollback();
            assertTrue(ContractIncome.reserve(c, player, 1800, 1800, 0));
            c.commit();
            assertFalse(ContractIncome.reserve(c, player, 1, 1800, 1));
            c.rollback();
        }
    }
    @Test void invalidLargeRewardCannotExceedBudgetAndDisabledBudgetAllowsIt() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ContractIncome.initialize(c);
            UUID player = UUID.randomUUID();
            assertFalse(ContractIncome.reserve(c, player, Long.MAX_VALUE, 1800, 0));
            assertTrue(ContractIncome.reserve(c, player, 2000, 0, 0));
            assertTrue(ContractIncome.reserve(c, player, 0, 1800, 0));
        }
    }
}
