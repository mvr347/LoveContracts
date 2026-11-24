package me.lovelace.lovecontracts.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import me.lovelace.lovecontracts.npc.storage.NpcQuestStorage;
import org.junit.jupiter.api.Test;

class AdvanceStorageTest {

    private static long advancePaid(Connection c, UUID player, String id) throws Exception {
        try (var q = c.prepareStatement("SELECT advance_paid FROM player_npc_quests WHERE player_uuid=? AND quest_id=?")) {
            q.setString(1, player.toString());
            q.setString(2, id);
            try (var rs = q.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    private static long income(Connection c, UUID player) throws Exception {
        try (var q = c.prepareStatement("SELECT COALESCE(SUM(amount),0) FROM contract_income WHERE player=?")) {
            q.setString(1, player.toString());
            try (var rs = q.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test
    void restartingAQuestClearsTheOldAdvanceAndARevokedAdvanceReleasesTheIncomeLimit() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            NpcQuestStorage.initialize(c);
            UUID player = UUID.randomUUID();
            long now = System.currentTimeMillis();

            assertTrue(NpcQuestStorage.start(c, player, "q", true, 0, now));
            assertEquals(200, NpcQuestStorage.grantAdvance(c, player, "q", 200, 1800, now));
            assertEquals(200, advancePaid(c, player, "q"));
            assertEquals(200, income(c, player));

            // the player leaves before the advance is handed over: it is revoked and the limit is freed
            NpcQuestStorage.revokeAdvance(c, player, "q", 200, now);
            assertEquals(0, advancePaid(c, player, "q"));
            assertEquals(0, income(c, player));

            // a cancelled run with an advance, then a restart without one: nothing stale is left behind
            assertEquals(200, NpcQuestStorage.grantAdvance(c, player, "q", 200, 1800, now));
            assertTrue(NpcQuestStorage.abandon(c, player, "q"));
            assertTrue(NpcQuestStorage.start(c, player, "q", true, 0, now + 1));
            assertEquals(0, advancePaid(c, player, "q"));
        }
    }

    @Test
    void anAdvanceBeyondTheIncomeLimitIsNotGranted() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            NpcQuestStorage.initialize(c);
            UUID player = UUID.randomUUID();
            long now = System.currentTimeMillis();
            assertTrue(NpcQuestStorage.start(c, player, "q", true, 0, now));
            assertEquals(0, NpcQuestStorage.grantAdvance(c, player, "q", 500, 100, now));
            assertEquals(0, advancePaid(c, player, "q"));
        }
    }
}
