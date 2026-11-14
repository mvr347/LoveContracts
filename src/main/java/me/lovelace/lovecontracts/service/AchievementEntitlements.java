package me.lovelace.lovecontracts.service;

import java.sql.*;
import java.util.UUID;

/** SQL portion is shared by normal and multi-stage completion transactions. */
public final class AchievementEntitlements {
    private AchievementEntitlements() {}
    public static void initialize(Connection c) throws SQLException {
        try(var q=c.createStatement()){q.executeUpdate("CREATE TABLE IF NOT EXISTS contract_achievement_rewards(player TEXT NOT NULL,achievement TEXT NOT NULL,PRIMARY KEY(player,achievement))");}
    }
    public static void enqueue(Connection c,UUID player,String achievement) throws SQLException {
        if(achievement==null||achievement.isBlank())return;
        try(var q=c.prepareStatement("INSERT OR IGNORE INTO contract_achievement_rewards VALUES(?,?)")){
            q.setString(1,player.toString());q.setString(2,achievement);q.executeUpdate();
        }
    }
}
