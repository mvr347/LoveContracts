package me.lovelace.lovecontracts.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * Одноразовый пересчёт сумм в БД при смене масштаба экономики ({@code economy.scale-version} в LoveCore).
 *
 * <p>Версия, под которую записаны суммы, хранится в {@code economy_meta}. Если таблицы ещё пусты
 * (свежая установка), версия просто фиксируется. Иначе суммы живых записей умножаются на
 * {@code factor ^ (target - stored)}. Завершённые/отменённые заказы игроков не трогаются: это история,
 * по ней уже ничего не выплачивается.</p>
 *
 * <p>Идемпотентность: версия пишется в той же транзакции, что и пересчёт, поэтому повторный запуск
 * ничего не делает.</p>
 */
public final class EconomyMigration {

    private static final String META = "scale_version";

    private EconomyMigration() {
    }

    /** true, если версия записана ниже целевой (или не записана) и в таблицах есть живые суммы - перед пересчётом нужна копия БД. */
    public static boolean needsRescale(Connection conn, int targetVersion) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS economy_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }
        Integer stored = readVersion(conn);
        if (stored != null && stored >= targetVersion) return false;
        return hasRows(conn, "pending_fines", "amount > 0")
                || hasRows(conn, "pcontracts", "gold_reward > 0 AND status NOT IN ('COMPLETED','ABANDONED','CANCELLED','EXPIRED')")
                || hasRows(conn, "pcontract_payouts", "gold_amount > 0");
    }

    private static boolean hasRows(Connection conn, String table, String where) throws SQLException {
        if (!tableExists(conn, table)) return false;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 FROM " + table + " WHERE " + where + " LIMIT 1")) {
            return rs.next();
        }
    }

    /** @return сколько строк пересчитано (0 — миграция не понадобилась). */
    public static int migrate(Connection conn, int targetVersion, double factor, Logger log) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS economy_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }
        Integer stored = readVersion(conn);
        if (stored != null && stored >= targetVersion) {
            return 0;
        }

        boolean wasAuto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            int rows = 0;
            // No stored marker + live rows = a pre-economy-v2 database (version 1).
            int from = stored != null ? stored : 1;
            if (from < targetVersion && factor > 0 && factor != 1.0) {
                double mult = Math.pow(factor, targetVersion - from);
                rows += scale(conn, "pending_fines", "amount", null, mult);
                rows += scale(conn, "pcontracts", "gold_reward",
                        "status NOT IN ('COMPLETED','ABANDONED','CANCELLED','EXPIRED')", mult);
                rows += scale(conn, "pcontract_payouts", "gold_amount", null, mult);
            }
            writeVersion(conn, targetVersion);
            conn.commit();
            if (rows > 0) {
                log.info("Economy migration v" + from + " -> v" + targetVersion + ": rescaled " + rows
                        + " rows (x" + factor + " per step)");
            }
            return rows;
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(wasAuto);
        }
    }

    private static int scale(Connection conn, String table, String column, String where, double mult)
            throws SQLException {
        if (!tableExists(conn, table)) return 0;
        String sql = "UPDATE " + table + " SET " + column + " = MAX(1, CAST(ROUND(" + column + " * ?) AS INTEGER)) "
                + "WHERE " + column + " > 0" + (where != null ? " AND " + where : "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, mult);
            return ps.executeUpdate();
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Integer readVersion(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT value FROM economy_meta WHERE key = ?")) {
            ps.setString(1, META);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                try {
                    return Integer.parseInt(rs.getString(1).trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
    }

    private static void writeVersion(Connection conn, int version) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO economy_meta (key, value) VALUES (?, ?) "
                        + "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, META);
            ps.setString(2, String.valueOf(version));
            ps.executeUpdate();
        }
    }
}
