package me.lovelace.lovecontracts.npc;

import me.lovelace.lovecontracts.npc.model.QuestLocation;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class QuestLocationTest {
    @Test void arrivalAndWorkingAreaUseWorldAndThreeDimensionalRadius() {
        var location = new QuestLocation("swamp", 10, 64, 20, 5);
        assertTrue(location.contains("swamp", 13, 68, 20));
        assertFalse(location.contains("swamp", 13, 69, 20));
        assertFalse(location.contains("other", 10, 64, 20));
    }
    @Test void configurationRejectsMissingAndNonfiniteCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> QuestLocation.parse(Map.of("x", 1, "y", 64, "z", 1)));
        assertThrows(IllegalArgumentException.class, () -> QuestLocation.parse(Map.of("world", "world", "x", 1, "y", 64)));
        assertThrows(IllegalArgumentException.class, () -> new QuestLocation("world", Double.NaN, 64, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> new QuestLocation("world", 0, 64, 0, 0));
    }
    @Test void defaultRadiusAndNegativeCoordinatesAreReportedCorrectly() {
        var location = QuestLocation.parse(Map.of("world", "world", "x", -1.5, "y", "64", "z", 20));
        assertEquals(4, location.radius());
        assertEquals("world -2, 64, 20", location.address());
    }
}
