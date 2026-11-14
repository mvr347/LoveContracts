package me.lovelace.lovecontracts.npc;

import me.lovelace.lovecontracts.service.ActiveDefinitionGuard;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActiveDefinitionGuardTest {
    @Test void removalAndReorderingOfRunningStoryAreRefused() {
        var old=Map.<String,Object>of("story",List.of(Map.of("type","VISIT_NPC"),Map.of("type","DELIVER_ITEM")));
        assertThrows(IllegalArgumentException.class,()->ActiveDefinitionGuard.validate(old,Map.of(),Set.of("story")));
        assertThrows(IllegalArgumentException.class,()->ActiveDefinitionGuard.validate(old,Map.of("story",List.of(Map.of("type","DELIVER_ITEM"),Map.of("type","VISIT_NPC"))),Set.of("story")));
    }
    @Test void numericSerializationAndNewVersionsDoNotChangeOldProgress() {
        var old=Map.<String,Object>of("order",Map.of("count",16));
        assertDoesNotThrow(()->ActiveDefinitionGuard.validate(old,Map.of("order",Map.of("count",16L)),Set.of("order")));
        assertDoesNotThrow(()->ActiveDefinitionGuard.validate(old,Map.of("order",Map.of("count",16),"order_v2",Map.of("count",32)),Set.of("order")));
    }
}
