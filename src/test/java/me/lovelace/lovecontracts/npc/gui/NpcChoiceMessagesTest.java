package me.lovelace.lovecontracts.npc.gui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** The choice window reads its texts from messages.yml: a missing key would show up as a raw key in the menu. */
class NpcChoiceMessagesTest {

    private static YamlConfiguration yaml(String resource) {
        var in = NpcChoiceMessagesTest.class.getResourceAsStream("/" + resource);
        assertNotNull(in, resource);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    @Test
    void everyKeyOfTheChoiceWindowExists() {
        var messages = yaml("messages.yml");
        for (String key : new String[]{"title", "contract", "contract-sub", "contract-hint", "contract-action",
                "ordinary", "ordinary-sub", "ordinary-hint", "ordinary-action"}) {
            String text = messages.getString("gui.npc-choice-" + key);
            assertNotNull(text, "gui.npc-choice-" + key);
            assertFalse(text.isBlank(), key);
        }
    }

    @Test
    void clickModeDefaultsToTheOldBehaviour() {
        assertEquals("contract", yaml("config.yml").getString("quests.npc-click-mode"));
    }

    @Test
    void oneDialogueQuestAtATimeByDefault() {
        assertEquals(1, yaml("config.yml").getInt("quests.max-active"));
    }

    @Test
    void advanceAndAntifarmDefaultsAreShipped() {
        var config = yaml("config.yml");
        assertEquals(0, config.getInt("quests.economy.easy.advance-percent"));
        assertEquals(20, config.getInt("quests.economy.medium.advance-percent"));
        assertEquals(30, config.getInt("quests.economy.hard.advance-percent"));
        assertEquals(3, config.getInt("quests.antifarm.cancel-streak"));
        assertEquals(3, config.getIntegerList("quests.antifarm.lock-durations-minutes").size());
    }
}
