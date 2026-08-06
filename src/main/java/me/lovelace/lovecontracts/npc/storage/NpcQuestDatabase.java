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
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 CREATE TABLE IF NOT EXISTS player_npc_quests (
                     player_uuid VARCHAR(36) NOT NULL,
                     quest_id VARCHAR(64) NOT NULL,
                     current_step INT NOT NULL DEFAULT 0,
                     step_progress INT NOT NULL DEFAULT 0,
                     status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                     last_completed_at TIMESTAMP NULL,
                     PRIMARY KEY (player_uuid, quest_id)
                 );
                 """)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize player_npc_quests table", e);
        }
    }
}
