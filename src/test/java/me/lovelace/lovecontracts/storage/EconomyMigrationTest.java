package me.lovelace.lovecontracts.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class EconomyMigrationTest {

    private static final Logger LOG = Logger.getLogger("test");
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE pending_fines (player_uuid TEXT PRIMARY KEY, amount INTEGER NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE pcontracts (id TEXT PRIMARY KEY, gold_reward INTEGER NOT NULL, status TEXT NOT NULL)");
            st.execute("CREATE TABLE pcontract_payouts (payout_id INTEGER PRIMARY KEY AUTOINCREMENT, gold_amount INTEGER NOT NULL)");
            st.execute("INSERT INTO pending_fines VALUES ('a', 100), ('b', 7)");
            st.execute("INSERT INTO pcontracts VALUES ('open', 1000, 'OPEN'), ('prog', 200, 'IN_PROGRESS'), "
                    + "('done', 500, 'COMPLETED'), ('exp', 300, 'EXPIRED')");
            st.execute("INSERT INTO pcontract_payouts (gold_amount) VALUES (50), (0)");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    private long scalar(String sql) throws Exception {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void rescalesLiveRowsAndLeavesHistoryAlone() throws Exception {
        assertTrue(EconomyMigration.needsRescale(conn, 2));
        int rows = EconomyMigration.migrate(conn, 2, 5.0, LOG);
        assertEquals(2 + 2 + 1, rows);
        assertEquals(500, scalar("SELECT amount FROM pending_fines WHERE player_uuid='a'"));
        assertEquals(35, scalar("SELECT amount FROM pending_fines WHERE player_uuid='b'"));
        assertEquals(5000, scalar("SELECT gold_reward FROM pcontracts WHERE id='open'"));
        assertEquals(1000, scalar("SELECT gold_reward FROM pcontracts WHERE id='prog'"));
        assertEquals(500, scalar("SELECT gold_reward FROM pcontracts WHERE id='done'"));
        assertEquals(300, scalar("SELECT gold_reward FROM pcontracts WHERE id='exp'"));
        assertEquals(250, scalar("SELECT gold_amount FROM pcontract_payouts WHERE gold_amount > 0"));
        assertEquals(0, scalar("SELECT gold_amount FROM pcontract_payouts WHERE payout_id = 2"));
    }

    @Test
    void isIdempotent() throws Exception {
        EconomyMigration.migrate(conn, 2, 5.0, LOG);
        assertFalse(EconomyMigration.needsRescale(conn, 2));
        assertEquals(0, EconomyMigration.migrate(conn, 2, 5.0, LOG));
        assertEquals(500, scalar("SELECT amount FROM pending_fines WHERE player_uuid='a'"));
    }

    @Test
    void freshDatabaseOnlyRecordsVersion() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("DELETE FROM pending_fines");
            st.execute("DELETE FROM pcontracts");
            st.execute("DELETE FROM pcontract_payouts");
        }
        assertFalse(EconomyMigration.needsRescale(conn, 2));
        assertEquals(0, EconomyMigration.migrate(conn, 2, 5.0, LOG));
        // a later bump must still apply on top of the recorded version
        try (Statement st = conn.createStatement()) {
            st.execute("INSERT INTO pending_fines VALUES ('c', 10)");
        }
        assertEquals(1, EconomyMigration.migrate(conn, 3, 5.0, LOG));
        assertEquals(50, scalar("SELECT amount FROM pending_fines WHERE player_uuid='c'"));
    }

    @Test
    void skipsMissingTables() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("DROP TABLE pcontracts");
        }
        assertEquals(2 + 1, EconomyMigration.migrate(conn, 2, 5.0, LOG));
    }

    @Test
    void multiStepBumpAppliesFactorPerStep() throws Exception {
        EconomyMigration.migrate(conn, 3, 2.0, LOG); // stored none = v1 -> v3: x2^2
        assertEquals(400, scalar("SELECT amount FROM pending_fines WHERE player_uuid='a'"));
    }
}
