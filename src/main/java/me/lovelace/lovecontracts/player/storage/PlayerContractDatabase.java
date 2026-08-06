package me.lovelace.lovecontracts.player.storage;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.player.model.PlayerContract;
import me.lovelace.lovecontracts.player.model.PlayerContractObjectiveType;
import me.lovelace.lovecontracts.player.model.PlayerContractStatus;
import me.lovelace.lovecontracts.player.model.PlayerContractVisibility;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Хранилище player-to-player контрактов. Использует тот же SQLite-файл и пул соединений,
 * что и {@link me.lovelace.lovecontracts.storage.ContractDatabase} — отдельная БД тут не нужна.
 */
public class PlayerContractDatabase implements AutoCloseable {

    private final LoveContracts plugin;

    public PlayerContractDatabase(LoveContracts plugin) {
        this.plugin = plugin;
    }

    @Override
    public void close() {
        // Shared connection pool closed by ContractDatabase
    }

    public void initialize() throws SQLException {
        try (Connection conn = plugin.getDatabase().getConnection();
             Statement st = conn.createStatement()) {

            // item_rewards и pcontract_payouts.items остаются в схеме, но код их больше не
            // заполняет — награда теперь только золото через LoveCore (см. LoveCoreBridge).
            // Колонки не дропаются: этот проект не использует ALTER TABLE/миграции, только
            // CREATE TABLE IF NOT EXISTS, поэтому старые строки со значением в item_rewards
            // просто тихо игнорируются при чтении (см. #map), а не приводят к ошибке.
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pcontracts (
                    id TEXT PRIMARY KEY,
                    creator_id TEXT NOT NULL,
                    creator_name TEXT NOT NULL,
                    executor_id TEXT,
                    executor_name TEXT,
                    description TEXT NOT NULL,
                    objective_type TEXT NOT NULL,
                    objective_target TEXT,
                    objective_amount INTEGER NOT NULL DEFAULT 1,
                    objective_progress INTEGER NOT NULL DEFAULT 0,
                    gold_reward INTEGER NOT NULL DEFAULT 0,
                    item_rewards TEXT,
                    reputation_hint INTEGER NOT NULL DEFAULT 0,
                    visibility TEXT NOT NULL DEFAULT 'PUBLIC',
                    status TEXT NOT NULL DEFAULT 'OPEN',
                    created_at INTEGER NOT NULL,
                    deadline INTEGER NOT NULL,
                    accepted_at INTEGER,
                    completed_at INTEGER
                )
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_pcontracts_status
                ON pcontracts(status)
                """);

            // items тоже больше не заполняется — см. комментарий над pcontracts выше.
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pcontract_payouts (
                    payout_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_id TEXT NOT NULL,
                    gold_amount INTEGER NOT NULL DEFAULT 0,
                    items TEXT,
                    reason TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_pcontract_payouts_player
                ON pcontract_payouts(player_id)
                """);
        }

        plugin.getLogger().info("Player contract tables ready");
    }

    public void insert(PlayerContract c) throws SQLException {
        // item_rewards намеренно не заполняется — награда только золотом (см. bindAll).
        String sql = """
            INSERT INTO pcontracts
            (id, creator_id, creator_name, executor_id, executor_name, description, objective_type,
             objective_target, objective_amount, objective_progress, gold_reward,
             reputation_hint, visibility, status, created_at, deadline, accepted_at, completed_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindAll(ps, c);
            ps.executeUpdate();
        }
    }

    public void update(PlayerContract c) throws SQLException {
        String sql = """
            UPDATE pcontracts SET
                executor_id=?, executor_name=?, objective_progress=?, status=?,
                accepted_at=?, completed_at=?
            WHERE id=?
            """;
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getExecutorId() != null ? c.getExecutorId().toString() : null);
            ps.setString(2, c.getExecutorName());
            ps.setInt(3, c.getObjectiveProgress());
            ps.setString(4, c.getStatus().name());
            ps.setObject(5, c.getAcceptedAt() != null ? c.getAcceptedAt().toEpochMilli() : null);
            ps.setObject(6, c.getCompletedAt() != null ? c.getCompletedAt().toEpochMilli() : null);
            ps.setString(7, c.getId().toString());
            ps.executeUpdate();
        }
    }

    public Optional<PlayerContract> findById(UUID id) throws SQLException {
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM pcontracts WHERE id = ?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Резолвит короткий id (первые символы UUID, как показывается игроку в GUI) в контракт. */
    public Optional<PlayerContract> findByIdPrefix(String prefix) throws SQLException {
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM pcontracts WHERE id LIKE ? LIMIT 2")) {
            ps.setString(1, prefix.toLowerCase() + "%");
            List<PlayerContract> matches = collect(ps);
            return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
        }
    }

    public List<PlayerContract> findOpen() throws SQLException {
        return queryAll("SELECT * FROM pcontracts WHERE status = 'OPEN' ORDER BY created_at DESC");
    }

    public List<PlayerContract> findByCreator(UUID creatorId) throws SQLException {
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM pcontracts WHERE creator_id = ? ORDER BY created_at DESC")) {
            ps.setString(1, creatorId.toString());
            return collect(ps);
        }
    }

    public List<PlayerContract> findByExecutor(UUID executorId) throws SQLException {
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM pcontracts WHERE executor_id = ? ORDER BY created_at DESC")) {
            ps.setString(1, executorId.toString());
            return collect(ps);
        }
    }

    public int countActiveByCreator(UUID creatorId) throws SQLException {
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                SELECT COUNT(*) FROM pcontracts
                WHERE creator_id = ? AND status IN ('OPEN','IN_PROGRESS','PENDING_REVIEW')
                """)) {
            ps.setString(1, creatorId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int countActiveByExecutor(UUID executorId) throws SQLException {
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                SELECT COUNT(*) FROM pcontracts
                WHERE executor_id = ? AND status IN ('IN_PROGRESS','PENDING_REVIEW')
                """)) {
            ps.setString(1, executorId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Атомарно принимает контракт: обновляет строку только если она всё ещё OPEN.
     * Так гонка двух исполнителей на один контракт решается на уровне SQL, а не в Java.
     */
    public boolean tryAccept(UUID contractId, UUID executorId, String executorName, long acceptedAtMillis) throws SQLException {
        String sql = """
            UPDATE pcontracts
            SET executor_id = ?, executor_name = ?, status = 'IN_PROGRESS', accepted_at = ?
            WHERE id = ? AND status = 'OPEN'
            """;
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, executorId.toString());
            ps.setString(2, executorName);
            ps.setLong(3, acceptedAtMillis);
            ps.setString(4, contractId.toString());
            return ps.executeUpdate() > 0;
        }
    }

    public List<PlayerContract> findActiveKillContractsForExecutor(UUID executorId) throws SQLException {
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                SELECT * FROM pcontracts
                WHERE executor_id = ? AND status = 'IN_PROGRESS' AND objective_type = 'KILL_ENTITY'
                """)) {
            ps.setString(1, executorId.toString());
            return collect(ps);
        }
    }

    public List<PlayerContract> findOverdue() throws SQLException {
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                SELECT * FROM pcontracts
                WHERE status IN ('OPEN','IN_PROGRESS','PENDING_REVIEW') AND deadline < ?
                """)) {
            ps.setLong(1, Instant.now().toEpochMilli());
            return collect(ps);
        }
    }

    public void addPendingPayout(UUID playerId, long goldAmount, String reason) throws SQLException {
        // items не заполняется — отложенная выплата теперь только золотом (см. класс-javadoc).
        String sql = "INSERT INTO pcontract_payouts (player_id, gold_amount, reason, created_at) VALUES (?,?,?,?)";
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.setLong(2, goldAmount);
            ps.setString(3, reason);
            ps.setLong(4, Instant.now().toEpochMilli());
            ps.executeUpdate();
        }
    }

    public List<PendingPayout> getPendingPayouts(UUID playerId) throws SQLException {
        List<PendingPayout> result = new ArrayList<>();
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM pcontract_payouts WHERE player_id = ?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // items из старых строк (до отказа от предметных наград) намеренно
                    // игнорируется — колонка остаётся в схеме, но больше не читается.
                    result.add(new PendingPayout(
                            rs.getLong("payout_id"),
                            UUID.fromString(rs.getString("player_id")),
                            rs.getLong("gold_amount"),
                            rs.getString("reason")
                    ));
                }
            }
        }
        return result;
    }

    public void deletePendingPayout(long payoutId) throws SQLException {
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM pcontract_payouts WHERE payout_id = ?")) {
            ps.setLong(1, payoutId);
            ps.executeUpdate();
        }
    }

    private List<PlayerContract> queryAll(String sql) throws SQLException {
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            return collect(ps);
        }
    }

    private List<PlayerContract> collect(PreparedStatement ps) throws SQLException {
        List<PlayerContract> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        }
        return result;
    }

    private void bindAll(PreparedStatement ps, PlayerContract c) throws SQLException {
        ps.setString(1, c.getId().toString());
        ps.setString(2, c.getCreatorId().toString());
        ps.setString(3, c.getCreatorName());
        ps.setString(4, c.getExecutorId() != null ? c.getExecutorId().toString() : null);
        ps.setString(5, c.getExecutorName());
        ps.setString(6, c.getDescription());
        ps.setString(7, c.getObjectiveType().name());
        ps.setString(8, c.getObjectiveTarget());
        ps.setInt(9, c.getObjectiveAmount());
        ps.setInt(10, c.getObjectiveProgress());
        ps.setLong(11, c.getGoldReward());
        ps.setInt(12, c.getReputationHint());
        ps.setString(13, c.getVisibility().name());
        ps.setString(14, c.getStatus().name());
        ps.setLong(15, c.getCreatedAt().toEpochMilli());
        ps.setLong(16, c.getDeadline().toEpochMilli());
        ps.setObject(17, c.getAcceptedAt() != null ? c.getAcceptedAt().toEpochMilli() : null);
        ps.setObject(18, c.getCompletedAt() != null ? c.getCompletedAt().toEpochMilli() : null);
    }

    private PlayerContract map(ResultSet rs) throws SQLException {
        String executorIdStr = rs.getString("executor_id");
        Long acceptedAtMillis = (Long) rs.getObject("accepted_at");
        Long completedAtMillis = (Long) rs.getObject("completed_at");
        // item_rewards может содержать данные из старой версии плагина (до отказа от
        // предметных наград) — колонка намеренно не читается и не удаляется из схемы, так
        // как проект не использует ALTER TABLE. Значение просто игнорируется, без падений.

        return new PlayerContract(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("creator_id")),
                rs.getString("creator_name"),
                executorIdStr != null ? UUID.fromString(executorIdStr) : null,
                rs.getString("executor_name"),
                rs.getString("description"),
                PlayerContractObjectiveType.valueOf(rs.getString("objective_type")),
                rs.getString("objective_target"),
                rs.getInt("objective_amount"),
                rs.getInt("objective_progress"),
                rs.getLong("gold_reward"),
                rs.getInt("reputation_hint"),
                PlayerContractVisibility.valueOf(rs.getString("visibility")),
                PlayerContractStatus.valueOf(rs.getString("status")),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                Instant.ofEpochMilli(rs.getLong("deadline")),
                acceptedAtMillis != null ? Instant.ofEpochMilli(acceptedAtMillis) : null,
                completedAtMillis != null ? Instant.ofEpochMilli(completedAtMillis) : null
        );
    }
}
