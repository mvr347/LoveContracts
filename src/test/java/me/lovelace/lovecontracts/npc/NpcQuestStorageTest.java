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
}
