package me.lovelace.lovecontracts.npc.storage;

import me.lovelace.lovecontracts.LoveContracts;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;

public class NpcQuestDatabase implements AutoCloseable {

    private final LoveContracts plugin;

    public NpcQuestDatabase(LoveContracts plugin) {
        this.plugin = plugin;
    }

    @Override
    public void close() {
        // Connection pool managed by ContractDatabase
    }

    public void initialize() {
        try (Connection conn = plugin.getDatabase().getConnection()) {
            NpcQuestStorage.initialize(conn);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize player_npc_quests table", e);
        }
    }
}
