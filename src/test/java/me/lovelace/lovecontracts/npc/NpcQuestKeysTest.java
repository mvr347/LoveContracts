package me.lovelace.lovecontracts.npc;

import me.lovelace.lovecontracts.integration.NpcSettings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The quest files read NPC keys through {@link NpcSettings#readKey}; both spellings must keep working. */
class NpcQuestKeysTest {

    @Test
    void legacyAndNewSpellingsOfAQuestGiverResolveToTheSameKey() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new StringReader("""
                npc_quests:
                  old:
                    npc-id: 1
                  new:
                    npc-key: "ext-ab12cd34"
                  numeric-text:
                    npc-key: "7"
                  none:
                    display-name: x
                """));
        ConfigurationSection root = yaml.getConfigurationSection("npc_quests");

        assertEquals("1", key(root.getConfigurationSection("old")));
        assertEquals("ext-ab12cd34", key(root.getConfigurationSection("new")));
        assertEquals("7", key(root.getConfigurationSection("numeric-text")));
        assertNull(key(root.getConfigurationSection("none")));
    }

    @Test
    void visitStepTargetsAcceptBothSpellingsInTheStepMap() {
        Map<String, Object> legacy = Map.of("target-npc-id", 2);
        Map<String, Object> modern = Map.of("target-npc-key", "ext-1", "target-npc-id", 2);

        assertEquals("2", NpcSettings.readKey(legacy.get("target-npc-key"), legacy.get("target-npc-id")));
        assertEquals("ext-1", NpcSettings.readKey(modern.get("target-npc-key"), modern.get("target-npc-id")));
    }

    private static String key(ConfigurationSection quest) {
        return NpcSettings.readKey(quest.get("npc-key"), quest.get("npc-id"));
    }
}
