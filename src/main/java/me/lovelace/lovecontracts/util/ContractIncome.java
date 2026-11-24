package me.lovelace.lovecontracts.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/** Rolling income budget for generated contracts only; player escrow is never taxed. */
public final class ContractIncome {
    public static final long WINDOW = 86_400_000L;
    private ContractIncome() {}

    public static void initialize(Connection connection) throws SQLException {
        try (var s = connection.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS contract_income (player TEXT NOT NULL, earned_at INTEGER NOT NULL, amount INTEGER NOT NULL)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS contract_income_player_time ON contract_income(player, earned_at)");
        }
    }

    /** Must share the completion transaction: a failed completion also rolls back the reservation. */
    public static boolean reserve(Connection connection, UUID player, long amount, long limit, long now) throws SQLException {
        if (amount <= 0 || limit <= 0) return true;
        try (var s = connection.prepareStatement("""
                INSERT INTO contract_income(player, earned_at, amount)
                SELECT ?, ?, ? WHERE ? <= ? - COALESCE(
                    (SELECT SUM(amount) FROM contract_income WHERE player=? AND earned_at > ?), 0)
                """)) {
            s.setString(1, player.toString());
            s.setLong(2, now);
            s.setLong(3, amount);
            s.setLong(4, amount);
            s.setLong(5, limit);
            s.setString(6, player.toString());
            s.setLong(7, now - WINDOW);
            return s.executeUpdate() == 1;
        }
    }

    /** Gives back part of a reservation that was never paid: a negative entry inside the same window cancels it. */
    public static void release(Connection connection, UUID player, long amount, long now) throws SQLException {
        if (amount <= 0) return;
        try (var s = connection.prepareStatement("INSERT INTO contract_income(player, earned_at, amount) VALUES (?, ?, ?)")) {
            s.setString(1, player.toString());
            s.setLong(2, now);
            s.setLong(3, -amount);
            s.executeUpdate();
        }
    }
}
