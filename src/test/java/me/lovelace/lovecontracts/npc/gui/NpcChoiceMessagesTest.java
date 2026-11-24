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
}
