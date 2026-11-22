package me.lovelace.lovecontracts.npc;

import java.sql.DriverManager;
import java.util.UUID;
import me.lovelace.lovecontracts.npc.storage.NpcQuestStorage;
import static me.lovelace.lovecontracts.npc.storage.NpcQuestStorage.Result.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcQuestStorageTest {
    @Test void repeatedAcceptAndStaleDialogueCannotResetOrSkipAChain() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            NpcQuestStorage.initialize(c);
            UUID player = UUID.randomUUID();
            assertTrue(NpcQuestStorage.start(c, player, "inn", true, 1440, 0));
            assertEquals(SAVED, NpcQuestStorage.advance(c, player, "inn", 0, 1, false, 240, 1800, 0));
            assertFalse(NpcQuestStorage.start(c, player, "inn", true, 1440, 0));
            assertEquals(STALE, NpcQuestStorage.advance(c, player, "inn", 0, 1, false, 240, 1800, 0));
            assertEquals(SAVED, NpcQuestStorage.advance(c, player, "inn", 1, 2, true, 240, 1800, 0));
            assertEquals(STALE, NpcQuestStorage.advance(c, player, "inn", 1, 2, true, 240, 1800, 0));
            try (var q = c.createStatement(); var rs = q.executeQuery("SELECT completion_count FROM player_npc_quests")) {
                assertTrue(rs.next()); assertEquals(1, rs.getInt(1));
            }
            try (var q = c.createStatement(); var rs = q.executeQuery("SELECT SUM(amount) FROM contract_income")) {
                assertTrue(rs.next()); assertEquals(240, rs.getLong(1));
            }
            assertFalse(NpcQuestStorage.start(c, player, "inn", true, 1440, 86_399_999));
            assertTrue(NpcQuestStorage.start(c, player, "inn", true, 1440, 86_400_000));
        }
    }
    @Test void legacyQuestDatabaseMigratesWithoutLosingActiveProgress() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            UUID player = UUID.randomUUID();
            try (var q = c.createStatement()) {
                q.executeUpdate("CREATE TABLE player_npc_quests(player_uuid TEXT,quest_id TEXT,current_step INTEGER,step_progress INTEGER,status TEXT,last_completed_at TIMESTAMP,PRIMARY KEY(player_uuid,quest_id))");
                q.executeUpdate("INSERT INTO player_npc_quests VALUES('" + player + "','old',1,17,'ACTIVE',NULL)");
            }
            NpcQuestStorage.initialize(c);
            NpcQuestStorage.initialize(c); // Repeatable initialization.
            try (var q = c.createStatement(); var rs = q.executeQuery("SELECT current_step,step_progress,completion_count FROM player_npc_quests")) {
                assertTrue(rs.next()); assertEquals(1,rs.getInt(1)); assertEquals(17,rs.getInt(2)); assertEquals(0,rs.getInt(3));
            }
            assertFalse(NpcQuestStorage.start(c, player, "old", true, 0, 0));
        }
    }

    @Test void incomeLimitLeavesFinalStageActiveAndRewardCanBeClaimedLater() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            NpcQuestStorage.initialize(c);
            UUID player = UUID.randomUUID();
            assertTrue(NpcQuestStorage.start(c, player, "one", false, 0, 0));
            assertEquals(SAVED, NpcQuestStorage.advance(c, player, "one", 0, 1, true, 1800, 1800, 0));
            assertTrue(NpcQuestStorage.start(c, player, "two", false, 0, 1));
            assertEquals(INCOME_LIMIT, NpcQuestStorage.advance(c, player, "two", 0, 1, true, 240, 1800, 1));
            assertEquals(SAVED, NpcQuestStorage.advance(c, player, "two", 0, 1, true, 240, 1800, 86_400_000));
            assertFalse(NpcQuestStorage.start(c, player, "one", false, 0, 86_400_000));
        }
    }

    @Test void abandonedChainCanBeTakenAgainAtOnceWithoutRewardOrCooldown() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            NpcQuestStorage.initialize(c);
            UUID player = UUID.randomUUID();
            assertTrue(NpcQuestStorage.start(c, player, "inn", false, 0, 0), "a one-time chain");
            assertEquals(SAVED, NpcQuestStorage.advance(c, player, "inn", 0, 1, false, 240, 1800, 0));

            assertTrue(NpcQuestStorage.abandon(c, player, "inn"));
            assertFalse(NpcQuestStorage.abandon(c, player, "inn"), "only an active chain can be given up");
            assertEquals(STALE, NpcQuestStorage.advance(c, player, "inn", 1, 2, true, 240, 1800, 0),
                    "an abandoned chain cannot be finished by a stale dialogue");
            try (var q = c.createStatement(); var rs = q.executeQuery("SELECT SUM(amount) FROM contract_income")) {
                assertTrue(rs.next()); assertEquals(0, rs.getLong(1), "giving up pays nothing");
            }

            assertTrue(NpcQuestStorage.start(c, player, "inn", false, 0, 1), "even a one-time chain restarts after giving up");
            try (var q = c.createStatement(); var rs = q.executeQuery("SELECT status,current_step,step_progress FROM player_npc_quests")) {
                assertTrue(rs.next());
                assertEquals("ACTIVE", rs.getString(1));
                assertEquals(0, rs.getInt(2));
                assertEquals(0, rs.getInt(3));
            }
        }
    }

    @Test void completedOneTimeChainStaysCompletedAndCannotBeAbandoned() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            NpcQuestStorage.initialize(c);
            UUID player = UUID.randomUUID();
            assertTrue(NpcQuestStorage.start(c, player, "once", false, 0, 0));
            assertEquals(SAVED, NpcQuestStorage.advance(c, player, "once", 0, 1, true, 100, 1800, 0));
            assertFalse(NpcQuestStorage.abandon(c, player, "once"));
            assertFalse(NpcQuestStorage.start(c, player, "once", false, 0, 1), "a one-time chain is not repeatable");
        }
    }

    @Test void adminClearErasesProgressHistoryAndCooldownSoAOneTimeChainCanBeTakenAgain() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            NpcQuestStorage.initialize(c);
            UUID player = UUID.randomUUID();
            assertTrue(NpcQuestStorage.start(c, player, "once", false, 0, 0));
            assertEquals(SAVED, NpcQuestStorage.advance(c, player, "once", 0, 1, true, 0, 1800, 0));
            assertFalse(NpcQuestStorage.start(c, player, "once", false, 0, 1), "one-time chain is closed");
            assertEquals(1, NpcQuestStorage.clear(c, player, "once"));
            assertEquals(0, NpcQuestStorage.clear(c, player, "once"), "nothing left to erase");
            assertTrue(NpcQuestStorage.start(c, player, "once", false, 0, 1), "a cleared chain starts from scratch");
        }
    }

    @Test void adminClearAllTouchesOnlyThatPlayer() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            NpcQuestStorage.initialize(c);
            UUID a = UUID.randomUUID(), b = UUID.randomUUID();
            NpcQuestStorage.forceStart(c, a, "one");
            NpcQuestStorage.forceStart(c, a, "two");
            NpcQuestStorage.forceStart(c, b, "one");
            assertEquals(2, NpcQuestStorage.clearAll(c, a));
            assertTrue(NpcQuestStorage.inspect(c, a).isEmpty());
            assertEquals(1, NpcQuestStorage.inspect(c, b).size());
        }
    }

    @Test void forceStartIgnoresCooldownAndTheOneTimeRule() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            NpcQuestStorage.initialize(c);
            UUID player = UUID.randomUUID();
            assertTrue(NpcQuestStorage.start(c, player, "once", false, 0, 0));
            assertEquals(SAVED, NpcQuestStorage.advance(c, player, "once", 0, 1, true, 0, 1800, 0));
            NpcQuestStorage.forceStart(c, player, "once");
            var row = NpcQuestStorage.inspect(c, player).getFirst();
            assertEquals("ACTIVE", row.status());
            assertEquals(0, row.currentStep());
            assertEquals(1, row.completionCount(), "the history counter is kept");
            NpcQuestStorage.forceStart(c, player, "fresh");
            assertEquals(2, NpcQuestStorage.inspect(c, player).size(), "a chain never seen before is created");
        }
    }

    @Test void setStepOnlyMovesAnActiveChain() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            NpcQuestStorage.initialize(c);
            UUID player = UUID.randomUUID();
            assertFalse(NpcQuestStorage.setStep(c, player, "inn", 2), "no row, nothing to move");
            assertTrue(NpcQuestStorage.start(c, player, "inn", true, 0, 0));
            assertTrue(NpcQuestStorage.setStep(c, player, "inn", 2));
            assertEquals(2, NpcQuestStorage.inspect(c, player).getFirst().currentStep());
            assertFalse(NpcQuestStorage.setStep(c, player, "inn", -1));
            assertTrue(NpcQuestStorage.abandon(c, player, "inn"));
            assertFalse(NpcQuestStorage.setStep(c, player, "inn", 1), "an abandoned chain is not active");
        }
    }

    @Test void forceCompleteWorksWithoutAStartCountsOnceAndQueuesTheAchievement() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            NpcQuestStorage.initialize(c);
            UUID player = UUID.randomUUID();
            NpcQuestStorage.forceComplete(c, player, "inn", 3, "inn_done", 86_400_000L);
            var row = NpcQuestStorage.inspect(c, player).getFirst();
            assertEquals("COMPLETED", row.status());
            assertEquals(3, row.currentStep());
            assertEquals(1, row.completionCount());
            assertEquals(86_400_000L, row.lastCompletedMillis());
            NpcQuestStorage.forceComplete(c, player, "inn", 3, "inn_done", 86_500_000L);
            assertEquals(2, NpcQuestStorage.inspect(c, player).getFirst().completionCount());
            try (var q = c.createStatement(); var rs = q.executeQuery("SELECT COUNT(*) FROM contract_achievement_rewards")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "the same achievement is queued once");
            }
            try (var q = c.createStatement(); var rs = q.executeQuery("SELECT COUNT(*) FROM contract_income")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "an admin completion does not touch the income limit");
            }
        }
    }

    @Test void inspectOfAnUnknownPlayerIsEmpty() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            NpcQuestStorage.initialize(c);
            assertTrue(NpcQuestStorage.inspect(c, UUID.randomUUID()).isEmpty());
        }
    }

    @Test void advanceIsReservedAgainstTheIncomeLimitAndSubtractedFromThePayout() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            NpcQuestStorage.initialize(c);
            UUID player = UUID.randomUUID();
            assertTrue(NpcQuestStorage.start(c, player, "big", false, 0, 1000));
            assertEquals(60, NpcQuestStorage.grantAdvance(c, player, "big", 60, 1800, 1000));
            try (var q = c.createStatement(); var rs = q.executeQuery("SELECT advance_paid,started_at FROM player_npc_quests")) {
                assertTrue(rs.next()); assertEquals(60, rs.getLong(1)); assertEquals(1000, rs.getLong(2));
            }
            try (var q = c.createStatement(); var rs = q.executeQuery("SELECT SUM(amount) FROM contract_income")) {
                assertTrue(rs.next()); assertEquals(60, rs.getLong(1));
            }
        }
    }

    @Test void advanceIsDeniedWhenTheIncomeLimitIsReached() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            NpcQuestStorage.initialize(c);
            UUID player = UUID.randomUUID();
            assertTrue(NpcQuestStorage.start(c, player, "a", false, 0, 1));
            assertEquals(1800, NpcQuestStorage.grantAdvance(c, player, "a", 1800, 1800, 1));
            assertTrue(NpcQuestStorage.start(c, player, "b", false, 0, 2));
            // the limit is spent: the quest is still taken, just without an advance
            assertEquals(0, NpcQuestStorage.grantAdvance(c, player, "b", 50, 1800, 2));
            try (var q = c.prepareStatement("SELECT advance_paid FROM player_npc_quests WHERE quest_id='b'"); var rs = q.executeQuery()) {
                assertTrue(rs.next()); assertEquals(0, rs.getLong(1));
            }
        }
    }

    @Test void expiredChainCanBeTakenAgainWithFreshTermsAndKeepsNoCooldown() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            NpcQuestStorage.initialize(c);
            UUID player = UUID.randomUUID();
            assertTrue(NpcQuestStorage.start(c, player, "late", true, 1440, 1000));
            NpcQuestStorage.addInteraction(c, player, "late");
            NpcQuestStorage.addDeath(c, player);
            assertTrue(NpcQuestStorage.expire(c, player, "late"));
            assertFalse(NpcQuestStorage.expire(c, player, "late")); // already ended
            assertTrue(NpcQuestStorage.start(c, player, "late", true, 1440, 2000));
            try (var q = c.createStatement(); var rs = q.executeQuery("SELECT started_at,interactions,deaths,advance_paid,status FROM player_npc_quests")) {
                assertTrue(rs.next());
                assertEquals(2000, rs.getLong(1)); assertEquals(0, rs.getInt(2)); assertEquals(0, rs.getInt(3));
                assertEquals(0, rs.getLong(4)); assertEquals("ACTIVE", rs.getString(5));
            }
        }
    }

    @Test void legacyRowsGetZeroTermsColumns() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            UUID player = UUID.randomUUID();
            try (var q = c.createStatement()) {
                q.executeUpdate("CREATE TABLE player_npc_quests(player_uuid TEXT,quest_id TEXT,current_step INTEGER,step_progress INTEGER,status TEXT,last_completed_at TIMESTAMP,PRIMARY KEY(player_uuid,quest_id))");
                q.executeUpdate("INSERT INTO player_npc_quests VALUES('" + player + "','old',1,17,'ACTIVE',NULL)");
            }
            NpcQuestStorage.initialize(c);
            try (var q = c.createStatement(); var rs = q.executeQuery("SELECT started_at,advance_paid,interactions,deaths FROM player_npc_quests")) {
                assertTrue(rs.next());
                for (int i = 1; i <= 4; i++) assertEquals(0, rs.getLong(i)); // 0 = unknown, so no limit applies to old rows
            }
        }
    }

    @Test void refusalStateRoundTripsAndAdminClearKeepsTheTotalOnRequest() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            NpcQuestStorage.initialize(c);
            UUID player = UUID.randomUUID();
            assertEquals(me.lovelace.lovecontracts.npc.model.RefusalPolicy.State.EMPTY,
                    me.lovelace.lovecontracts.npc.storage.QuestRefusalStorage.load(c, player));
            var state = new me.lovelace.lovecontracts.npc.model.RefusalPolicy.State(2, 1, 5000, 4000, 3);
            me.lovelace.lovecontracts.npc.storage.QuestRefusalStorage.save(c, player, state);
            assertEquals(state, me.lovelace.lovecontracts.npc.storage.QuestRefusalStorage.load(c, player));
            me.lovelace.lovecontracts.npc.storage.QuestRefusalStorage.clear(c, player, true);
            assertEquals(new me.lovelace.lovecontracts.npc.model.RefusalPolicy.State(0, 0, 0, 0, 3),
                    me.lovelace.lovecontracts.npc.storage.QuestRefusalStorage.load(c, player));
            me.lovelace.lovecontracts.npc.storage.QuestRefusalStorage.clear(c, player, false);
            assertEquals(me.lovelace.lovecontracts.npc.model.RefusalPolicy.State.EMPTY,
                    me.lovelace.lovecontracts.npc.storage.QuestRefusalStorage.load(c, player));
        }
    }
}
