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
        // 2026-11-22: terms of a running quest (start time for the time limit and speed bonus, advance already paid,
        // and the quality counters). Every column defaults to 0, which reads as "unknown" for rows from older versions.
        for (String column : new String[] {"started_at", "advance_paid", "interactions", "deaths", "surplus"}) {
            ensureColumn(c, "player_npc_quests", column, "INTEGER NOT NULL DEFAULT 0");
        }
        QuestRefusalStorage.initialize(c);
        ContractIncome.initialize(c);
        me.lovelace.lovecontracts.service.AchievementEntitlements.initialize(c);
    }

    private static void ensureColumn(Connection c, String table, String column, String definition) throws SQLException {
        boolean present = false;
        try (var q = c.createStatement(); var rs = q.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) if (rs.getString("name").equals(column)) present = true;
        }
        if (!present) try (var q = c.createStatement()) {
            q.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    public static boolean start(Connection c, UUID player, String id, boolean repeatable, int cooldown, long now) throws SQLException {
        return start(c, player, id, repeatable, cooldown, now, 0);
    }

    /**
     * Takes the chain. {@code advancePaid} is what the giver hands out on taking (0 for none): it is remembered so the
     * final payout can subtract it and a failure can ask it back. A chain given up or expired may be taken again.
     */
    public static boolean start(Connection c, UUID player, String id, boolean repeatable, int cooldown, long now,
                                long advancePaid) throws SQLException {
        try (var q = c.prepareStatement("""
                INSERT INTO player_npc_quests(player_uuid,quest_id,current_step,step_progress,status,
                                              started_at,advance_paid,interactions,deaths,surplus)
                VALUES(?,?,0,0,'ACTIVE',?,?,0,0,0) ON CONFLICT(player_uuid,quest_id) DO UPDATE SET
                current_step=0,step_progress=0,status='ACTIVE',started_at=excluded.started_at,
                advance_paid=excluded.advance_paid,interactions=0,deaths=0,surplus=0
                WHERE player_npc_quests.status IN ('ABANDONED','EXPIRED')
                OR (player_npc_quests.status='COMPLETED' AND ?=1
                AND CAST(strftime('%s', last_completed_at) AS INTEGER) * 1000 <= ?)
                """)) {
            q.setString(1, player.toString()); q.setString(2, id);
            q.setLong(3, now); q.setLong(4, Math.max(0, advancePaid));
            q.setBoolean(5, repeatable); q.setLong(6, now - Math.max(0, cooldown) * 60_000L);
            return q.executeUpdate() == 1;
        }
    }

    /**
     * Records an advance for a chain just taken, reserving it against the rolling income limit like any other payout.
     * Returns what was actually granted: 0 when the limit is reached (the quest is then taken without an advance).
     */
    public static long grantAdvance(Connection c, UUID player, String id, long amount, long limit, long now) throws SQLException {
        if (amount <= 0) return 0;
        c.setAutoCommit(false);
        try {
            if (!ContractIncome.reserve(c, player, amount, limit, now)) {
                c.rollback();
                return 0;
            }
            try (var q = c.prepareStatement("UPDATE player_npc_quests SET advance_paid=? WHERE player_uuid=? AND quest_id=? AND status='ACTIVE'")) {
                q.setLong(1, amount);
                q.setString(2, player.toString());
                q.setString(3, id);
                if (q.executeUpdate() != 1) {
                    c.rollback();
                    return 0;
                }
            }
            c.commit();
            return amount;
        } catch (SQLException error) {
            c.rollback();
            throw error;
        } finally {
            c.setAutoCommit(true);
        }
    }

    /** A running chain whose time limit ran out: it ends without reward or cooldown, the row stays as EXPIRED. */
    public static boolean expire(Connection c, UUID player, String id) throws SQLException {
        try (var q = c.prepareStatement("""
                UPDATE player_npc_quests SET status='EXPIRED',current_step=0,step_progress=0
                WHERE player_uuid=? AND quest_id=? AND status='ACTIVE'
                """)) {
            q.setString(1, player.toString());
            q.setString(2, id);
            return q.executeUpdate() == 1;
        }
    }

    /** Counts one polite dialogue choice on a running chain (feeds the interaction bonus). */
    public static void addInteraction(Connection c, UUID player, String id) throws SQLException {
        bump(c, "interactions", player, id);
    }

    /** Marks a delivery with a surplus on a running chain (feeds the quality bonus). */
    public static void addSurplus(Connection c, UUID player, String id) throws SQLException {
        bump(c, "surplus", player, id);
    }

    /** A death spoils the "no deaths" quality bonus of every chain the player is running. */
    public static void addDeath(Connection c, UUID player) throws SQLException {
        try (var q = c.prepareStatement("UPDATE player_npc_quests SET deaths=deaths+1 WHERE player_uuid=? AND status='ACTIVE'")) {
            q.setString(1, player.toString());
            q.executeUpdate();
        }
    }

    private static void bump(Connection c, String column, UUID player, String id) throws SQLException {
        try (var q = c.prepareStatement("UPDATE player_npc_quests SET " + column + "=" + column
                + "+1 WHERE player_uuid=? AND quest_id=? AND status='ACTIVE'")) {
            q.setString(1, player.toString());
            q.setString(2, id);
            q.executeUpdate();
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
