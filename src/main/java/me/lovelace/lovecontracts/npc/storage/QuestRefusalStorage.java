package me.lovelace.lovecontracts.npc.storage;

import me.lovelace.lovecontracts.npc.model.RefusalPolicy.State;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/** Persistence of the cancel streak and block of each player. Runs on the quest writer thread only. */
public final class QuestRefusalStorage {
    private QuestRefusalStorage() {
    }

    public static void initialize(Connection c) throws SQLException {
        try (var q = c.createStatement()) {
            q.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS quest_refusals (
                      player_uuid VARCHAR(36) PRIMARY KEY,
                      streak INT NOT NULL DEFAULT 0, lock_level INT NOT NULL DEFAULT 0,
                      locked_until INTEGER NOT NULL DEFAULT 0, last_lock_at INTEGER NOT NULL DEFAULT 0,
                      total_locks INT NOT NULL DEFAULT 0)
                    """);
        }
    }

    public static State load(Connection c, UUID player) throws SQLException {
        try (var q = c.prepareStatement("SELECT streak,lock_level,locked_until,last_lock_at,total_locks FROM quest_refusals WHERE player_uuid=?")) {
            q.setString(1, player.toString());
            try (var rs = q.executeQuery()) {
                return rs.next() ? new State(rs.getInt(1), rs.getInt(2), rs.getLong(3), rs.getLong(4), rs.getInt(5)) : State.EMPTY;
            }
        }
    }

    public static void save(Connection c, UUID player, State state) throws SQLException {
        try (var q = c.prepareStatement("""
                INSERT INTO quest_refusals(player_uuid,streak,lock_level,locked_until,last_lock_at,total_locks)
                VALUES(?,?,?,?,?,?) ON CONFLICT(player_uuid) DO UPDATE SET streak=excluded.streak,
                lock_level=excluded.lock_level,locked_until=excluded.locked_until,
                last_lock_at=excluded.last_lock_at,total_locks=excluded.total_locks
                """)) {
            q.setString(1, player.toString());
            q.setInt(2, state.streak());
            q.setInt(3, state.lockLevel());
            q.setLong(4, state.lockedUntil());
            q.setLong(5, state.lastLockAt());
            q.setInt(6, state.totalLocks());
            q.executeUpdate();
        }
    }

    /** Admin reset: lifts the block and zeroes the streak and level. {@code keepTotal} keeps the lifetime block counter. */
    public static void clear(Connection c, UUID player, boolean keepTotal) throws SQLException {
        State old = load(c, player);
        save(c, player, new State(0, 0, 0, 0, keepTotal ? old.totalLocks() : 0));
    }
}
