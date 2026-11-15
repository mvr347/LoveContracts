package me.lovelace.lovecontracts.npc;

import me.lovelace.lovecontracts.npc.storage.NpcQuestStorage;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContractAchievementChainTest {
    @Test void achievementEntitlementCommitsOnceWithFinalStageAndSurvivesOfflineCompletion()throws Exception{
        try(var c=DriverManager.getConnection("jdbc:sqlite::memory:")){
            NpcQuestStorage.initialize(c);UUID player=UUID.randomUUID();
            assertTrue(NpcQuestStorage.start(c,player,"story",false,0,0));
            assertEquals(NpcQuestStorage.Result.SAVED,NpcQuestStorage.advance(c,player,"story",0,1,true,100,1800,0,"helper"));
            assertEquals(NpcQuestStorage.Result.STALE,NpcQuestStorage.advance(c,player,"story",0,1,true,100,1800,0,"helper"));
            try(var q=c.createStatement();var rs=q.executeQuery("SELECT COUNT(*) FROM contract_achievement_rewards WHERE achievement='helper'")){
                assertTrue(rs.next());assertEquals(1,rs.getInt(1));
            }
        }
    }
    @Test void blockedRewardDoesNotGrantAchievementBeforeCompletion()throws Exception{
        try(var c=DriverManager.getConnection("jdbc:sqlite::memory:")){
            NpcQuestStorage.initialize(c);UUID player=UUID.randomUUID();NpcQuestStorage.start(c,player,"story",false,0,0);
            assertEquals(NpcQuestStorage.Result.INCOME_LIMIT,NpcQuestStorage.advance(c,player,"story",0,1,true,2000,1800,0,"helper"));
            try(var q=c.createStatement();var rs=q.executeQuery("SELECT COUNT(*) FROM contract_achievement_rewards")){assertTrue(rs.next());assertEquals(0,rs.getInt(1));}
        }
    }
}
