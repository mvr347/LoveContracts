package me.lovelace.lovecontracts.service;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.achievements.Achievements;
import me.lovelace.lovecontracts.LoveContracts;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.sql.*;
import java.util.*;

/** Completion entitlement is queued in the same SQLite transaction, including offline finishes. */
public final class ContractAchievementRewards {
    private ContractAchievementRewards() {}
    public static void enqueue(Connection c,UUID player,String achievement) throws SQLException {
        AchievementEntitlements.enqueue(c,player,achievement);
    }
    public static boolean allowed(UUID player,String achievement) {
        return achievement==null||achievement.isBlank()||LoveCore.service(Achievements.class).map(s->s.unlocked(player,achievement)).orElse(false);
    }
    public static void startDelivery(LoveContracts plugin) {
        Set<UUID> busy=new HashSet<>();
        Bukkit.getScheduler().runTaskTimer(plugin,()->{
            var service=LoveCore.service(Achievements.class);if(service.isEmpty())return;
            for(Player player:Bukkit.getOnlinePlayers()){
                UUID id=player.getUniqueId();if(!busy.add(id))continue;
                Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
                    List<String> pending=new ArrayList<>();
                    try(var c=plugin.getDatabase().getConnection();var q=c.prepareStatement("SELECT achievement FROM contract_achievement_rewards WHERE player=?")){
                        q.setString(1,id.toString());try(var rows=q.executeQuery()){while(rows.next())pending.add(rows.getString(1));}
                    }catch(SQLException error){plugin.getLogger().warning("Cannot read achievement rewards: "+error.getMessage());}
                    Bukkit.getScheduler().runTask(plugin,()->{
                        var current=LoveCore.service(Achievements.class);List<String> delivered=new ArrayList<>();
                        if(player.isOnline()&&current.isPresent())for(String achievement:pending){
                            try {
                                current.get().unlock(id,achievement);
                                if(current.get().unlocked(id,achievement))delivered.add(achievement);
                            } catch (RuntimeException error) { plugin.getLogger().warning("Cannot deliver achievement reward: "+error.getMessage()); }
                        }
                        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
                            try(var c=plugin.getDatabase().getConnection();var q=c.prepareStatement("DELETE FROM contract_achievement_rewards WHERE player=? AND achievement=?")){
                                for(String achievement:delivered){q.setString(1,id.toString());q.setString(2,achievement);q.addBatch();}q.executeBatch();
                            }catch(SQLException error){plugin.getLogger().warning("Cannot confirm achievement rewards: "+error.getMessage());}
                            Bukkit.getScheduler().runTask(plugin,()->busy.remove(id));
                        });
                    });
                });
            }
        },20,100);
    }
}
