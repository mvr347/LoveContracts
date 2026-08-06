package me.lovelace.lovecontracts.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.lovelace.lovecontracts.LoveContracts;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class ContractDatabase implements AutoCloseable {

    private final LoveContracts plugin;
    private HikariDataSource dataSource;

    public ContractDatabase(LoveContracts plugin) {
        this.plugin = plugin;
    }

    public void initialize() throws SQLException {
        File dbFile = new File(plugin.getDataFolder(),
                plugin.getConfig().getString("database.file", "contracts.db"));

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new SQLException("Cannot create data folder");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setMaximumPoolSize(plugin.getConfig().getInt("database.pool-size", 10));
        config.setConnectionTimeout(plugin.getConfig().getLong("database.connection-timeout", 30000));
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        config.setAutoCommit(true);
        config.setLeakDetectionThreshold(plugin.getConfig().getLong("database.leak-detection-threshold", 15000));
        config.setPoolName("LoveContracts-Pool");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("busy_timeout", "5000");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(config);

        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(5)) {
                throw new SQLException("Database connection invalid");
            }
            createTables(conn);
        }

        plugin.getLogger().info("Database initialized: " + dbFile.getName());
    }

    private void createTables(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA busy_timeout=5000;");
            st.execute("PRAGMA synchronous=NORMAL;");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS contracts (
                    id TEXT PRIMARY KEY,
                    display_name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    difficulty TEXT NOT NULL,
                    type TEXT NOT NULL,
                    condition_type TEXT NOT NULL,
                    condition_data TEXT NOT NULL,
                    reward_data TEXT NOT NULL,
                    penalty_data TEXT NOT NULL,
                    enabled INTEGER DEFAULT 1,
                    weight INTEGER DEFAULT 1,
                    daily_spawns INTEGER DEFAULT 1,
                    max_acceptances INTEGER DEFAULT -1,
                    expiration_hours INTEGER DEFAULT 24,
                    created_at TEXT DEFAULT (datetime('now'))
                )
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS active_contracts (
                    active_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    contract_id TEXT NOT NULL,
                    spawned_at TEXT DEFAULT (datetime('now')),
                    expires_at TEXT NOT NULL,
                    taken_by_count INTEGER DEFAULT 0,
                    FOREIGN KEY (contract_id) REFERENCES contracts(id)
                )
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_active_not_expired
                ON active_contracts(expires_at)
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_contracts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT,
                    contract_id TEXT NOT NULL,
                    active_id INTEGER,
                    accepted_at TEXT DEFAULT (datetime('now')),
                    completed_at TEXT,
                    failed_at TEXT,
                    progress_data TEXT,
                    accepted_date TEXT GENERATED ALWAYS AS (date(accepted_at)) VIRTUAL,
                    UNIQUE(player_uuid, contract_id, accepted_date)
                )
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_player_active
                ON player_contracts(player_uuid, completed_at, failed_at)
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS contract_stats (
                    player_uuid TEXT PRIMARY KEY,
                    total_accepted INTEGER DEFAULT 0,
                    total_completed INTEGER DEFAULT 0,
                    total_failed INTEGER DEFAULT 0,
                    daily_completed INTEGER DEFAULT 0,
                    daily_accepted INTEGER DEFAULT 0,
                    daily_failed INTEGER DEFAULT 0,
                    last_daily_reset TEXT DEFAULT (datetime('now')),
                    best_streak INTEGER DEFAULT 0,
                    current_streak INTEGER DEFAULT 0,
                    reputation REAL DEFAULT 0.0,
                    reputation_easy REAL DEFAULT 0.0,
                    reputation_medium REAL DEFAULT 0.0,
                    reputation_hard REAL DEFAULT 0.0
                )
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS disabled_players (
                    player_uuid TEXT PRIMARY KEY,
                    disabled_at TEXT DEFAULT (datetime('now'))
                )
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pending_fines (
                    player_uuid TEXT PRIMARY KEY,
                    amount INTEGER NOT NULL DEFAULT 0,
                    updated_at TEXT DEFAULT (datetime('now'))
                )
                """);
        }
    }

    public void resetDailyStats() {
        try (Connection conn = getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("UPDATE contract_stats SET daily_completed = 0, daily_accepted = 0, daily_failed = 0, last_daily_reset = datetime('now')");
            plugin.getLogger().info("Daily contract stats reset to 0");
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to reset daily contract stats: " + e.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("DataSource is not available");
        }
        Connection conn = dataSource.getConnection();
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Failed to obtain valid connection");
        }
        return conn;
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed");
        }
    }
}
