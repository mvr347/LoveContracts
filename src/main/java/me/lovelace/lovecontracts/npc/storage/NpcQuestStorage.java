package me.lovelace.lovecontracts.npc.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import me.lovelace.lovecontracts.util.ContractIncome;

/** Durable compare-and-set transitions. All calls run on the quest writer, never on a Bukkit event thread. */
public final class NpcQuestStorage {
    public enum Result { SAVED, STALE, INCOME_LIMIT }
    private NpcQuestStorage() {}

    public static void initialize(Connection c) throws SQLException {
        try (var q = c.createStatement()) {
            q.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_npc_quests (
                      player_uuid VARCHAR(36) NOT NULL, quest_id VARCHAR(64) NOT NULL,
                      current_step INT NOT NULL DEFAULT 0, step_progress INT NOT NULL DEFAULT 0,
                      status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', last_completed_at TIMESTAMP NULL,
                      completion_count INTEGER NOT NULL DEFAULT 0,
                      PRIMARY KEY (player_uuid, quest_id))
                    """);
        }
        boolean counter = false;
        try (var q = c.createStatement(); var rs = q.executeQuery("PRAGMA table_info(player_npc_quests)")) {
            while (rs.next()) if (rs.getString("name").equals("completion_count")) counter = true;
        }
        if (!counter) try (var q = c.createStatement()) {
            q.executeUpdate("ALTER TABLE player_npc_quests ADD COLUMN completion_count INTEGER NOT NULL DEFAULT 0");
            q.executeUpdate("UPDATE player_npc_quests SET completion_count=1 WHERE last_completed_at IS NOT NULL");
        }
        ContractIncome.initialize(c);
        me.lovelace.lovecontracts.service.AchievementEntitlements.initialize(c);
    }

    public static boolean start(Connection c, UUID player, String id, boolean repeatable, int cooldown, long now) throws SQLException {
        try (var q = c.prepareStatement("""
                INSERT INTO player_npc_quests(player_uuid,quest_id,current_step,step_progress,status)
                VALUES(?,?,0,0,'ACTIVE') ON CONFLICT(player_uuid,quest_id) DO UPDATE SET
                current_step=0,step_progress=0,status='ACTIVE'
                WHERE player_npc_quests.status='ABANDONED'
                OR (player_npc_quests.status='COMPLETED' AND ?=1
                AND CAST(strftime('%s', last_completed_at) AS INTEGER) * 1000 <= ?)
                """)) {
            q.setString(1, player.toString()); q.setString(2, id);
            q.setBoolean(3, repeatable); q.setLong(4, now - Math.max(0, cooldown) * 60_000L);
            return q.executeUpdate() == 1;
        }
    }

    /**
     * Gives up an active chain: no reward and no cooldown. The row stays as {@code ABANDONED} so the chain can be
     * taken again at once ({@link #start} restarts it). Returns false when the chain was not active.
     */
    public static boolean abandon(Connection c, UUID player, String id) throws SQLException {
        try (var q = c.prepareStatement("""
                UPDATE player_npc_quests SET status='ABANDONED',current_step=0,step_progress=0
                WHERE player_uuid=? AND quest_id=? AND status='ACTIVE'
                """)) {
            q.setString(1, player.toString());
            q.setString(2, id);
            return q.executeUpdate() == 1;
        }
    }

    public static Result advance(Connection c, UUID player, String id, int expected, int next,
                                 boolean finish, long reward, long limit, long now) throws SQLException {
        return advance(c, player, id, expected, next, finish, reward, limit, now, null);
    }
    public static Result advance(Connection c, UUID player, String id, int expected, int next,
                                 boolean finish, long reward, long limit, long now, String achievement) throws SQLException {
        if (expected < 0 || next != expected + 1) return Result.STALE;
        c.setAutoCommit(false);
        try {
            try (var q = c.prepareStatement("""
                    UPDATE player_npc_quests SET current_step=?,step_progress=0,status=?,
                      completion_count=completion_count + CASE WHEN status='ACTIVE' AND ? THEN 1 ELSE 0 END,
                      last_completed_at=CASE WHEN ? THEN datetime(? / 1000, 'unixepoch') ELSE last_completed_at END
                    WHERE player_uuid=? AND quest_id=? AND status='ACTIVE' AND current_step=?
                    """)) {
                q.setInt(1, next); q.setString(2, finish ? "COMPLETED" : "ACTIVE");
                q.setBoolean(3, finish); q.setBoolean(4, finish); q.setLong(5, now);
                q.setString(6, player.toString()); q.setString(7, id); q.setInt(8, expected);
                if (q.executeUpdate() != 1) { c.rollback(); return Result.STALE; }
            }
            if (finish && !ContractIncome.reserve(c, player, reward, limit, now)) {
                c.rollback(); return Result.INCOME_LIMIT;
            }
            if (finish) me.lovelace.lovecontracts.service.AchievementEntitlements.enqueue(c, player, achievement);
            c.commit();
            return Result.SAVED;
        } catch (SQLException error) {
            c.rollback(); throw error;
        } finally { c.setAutoCommit(true); }
    }

    // ------------------------------------------------------------------ admin operations (no gameplay rules apply)

    /** One stored row, for the admin overview. {@code lastCompletedMillis} is 0 when the chain was never completed. */
    public record Row(String questId, int currentStep, int stepProgress, String status, int completionCount, long lastCompletedMillis) {
    }

    /** Puts the chain in progress at step 0 whatever its state was: cooldown, one-time rule and the chain limit are ignored. */
    public static void forceStart(Connection c, UUID player, String id) throws SQLException {
        try (var q = c.prepareStatement("""
                INSERT INTO player_npc_quests(player_uuid,quest_id,current_step,step_progress,status)
                VALUES(?,?,0,0,'ACTIVE') ON CONFLICT(player_uuid,quest_id) DO UPDATE SET
                current_step=0,step_progress=0,status='ACTIVE'
                """)) {
            q.setString(1, player.toString());
            q.setString(2, id);
            q.executeUpdate();
        }
    }

    /** Erases the row (progress, history, cooldown and counter): the chain starts from scratch next time. */
    public static int clear(Connection c, UUID player, String id) throws SQLException {
        try (var q = c.prepareStatement("DELETE FROM player_npc_quests WHERE player_uuid=? AND quest_id=?")) {
            q.setString(1, player.toString());
            q.setString(2, id);
            return q.executeUpdate();
        }
    }

    /** Erases every chain row of the player; returns how many there were. */
    public static int clearAll(Connection c, UUID player) throws SQLException {
        try (var q = c.prepareStatement("DELETE FROM player_npc_quests WHERE player_uuid=?")) {
            q.setString(1, player.toString());
            return q.executeUpdate();
        }
    }

    /** Moves an active chain to another step (0-based) and resets its progress; false when the chain is not active. */
    public static boolean setStep(Connection c, UUID player, String id, int step) throws SQLException {
        if (step < 0) return false;
        try (var q = c.prepareStatement("""
                UPDATE player_npc_quests SET current_step=?,step_progress=0
                WHERE player_uuid=? AND quest_id=? AND status='ACTIVE'
                """)) {
            q.setInt(1, step);
            q.setString(2, player.toString());
            q.setString(3, id);
            return q.executeUpdate() == 1;
        }
    }

    /**
     * Marks the chain completed whatever its state was and queues its achievement. The rolling income limit is not
     * applied: an admin completion is not farming. The counter and the cooldown clock move as for a normal finish.
     */
    public static void forceComplete(Connection c, UUID player, String id, int totalSteps, String achievement, long now) throws SQLException {
        c.setAutoCommit(false);
        try {
            try (var q = c.prepareStatement("""
                    INSERT INTO player_npc_quests(player_uuid,quest_id,current_step,step_progress,status,last_completed_at,completion_count)
                    VALUES(?,?,?,0,'COMPLETED',datetime(? / 1000, 'unixepoch'),1)
                    ON CONFLICT(player_uuid,quest_id) DO UPDATE SET
                    current_step=excluded.current_step,step_progress=0,status='COMPLETED',
                    last_completed_at=excluded.last_completed_at,
                    completion_count=player_npc_quests.completion_count+1
                    """)) {
                q.setString(1, player.toString());
                q.setString(2, id);
                q.setInt(3, Math.max(0, totalSteps));
                q.setLong(4, now);
                q.executeUpdate();
            }
            me.lovelace.lovecontracts.service.AchievementEntitlements.enqueue(c, player, achievement);
            c.commit();
        } catch (SQLException error) {
            c.rollback();
            throw error;
        } finally {
            c.setAutoCommit(true);
        }
    }

    /** All stored rows of a player, for the admin overview (works for offline players: it reads the database only). */
    public static List<Row> inspect(Connection c, UUID player) throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (var q = c.prepareStatement("""
                SELECT quest_id,current_step,step_progress,status,completion_count,
                       COALESCE(CAST(strftime('%s', last_completed_at) AS INTEGER),0)
                FROM player_npc_quests WHERE player_uuid=? ORDER BY quest_id
                """)) {
            q.setString(1, player.toString());
            try (var rs = q.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Row(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getString(4), rs.getInt(5), rs.getLong(6) * 1000));
                }
            }
        }
        return rows;
    }
}
