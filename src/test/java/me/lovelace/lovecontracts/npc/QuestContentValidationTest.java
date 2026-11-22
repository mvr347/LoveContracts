package me.lovelace.lovecontracts.npc;

import me.lovelace.lovecontracts.model.Difficulty;
import me.lovelace.lovecontracts.npc.model.NpcQuestStep;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Validates the bundled quest pool: counts per tier, valid targets/NPC keys, and zero Nether/End content. */
class QuestContentValidationTest {

    private static final List<String> FORBIDDEN_NETHER_END = List.of(
            "world_nether", "world_the_end", "ancient_debris", "nether_gold", "nether_quartz",
            "netherite", "wither_skeleton", "ghast", "blaze", "enderman", "ender_dragon", "beacon"
    );

    private static String readResource(String path) throws Exception {
        try (InputStream in = QuestContentValidationTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " must be on classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static YamlConfiguration loadYaml(String path) throws Exception {
        try (InputStream in = QuestContentValidationTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " must be on classpath");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void bundledStoriesHaveExpectedTiersValidTargetsAndValidNpcActors() throws Exception {
        YamlConfiguration stories = loadYaml("/npc_stories.yml");
        YamlConfiguration config = loadYaml("/config.yml");
        ConfigurationSection actors = config.getConfigurationSection("npc-actors");
        assertNotNull(actors, "config.yml must define npc-actors");
        Set<String> actorKeys = actors.getKeys(false);

        ConfigurationSection root = stories.getConfigurationSection("npc_quests");
        assertNotNull(root, "npc_stories.yml must contain npc_quests");

        int easy = 0, medium = 0, hard = 0;
        for (String id : root.getKeys(false)) {
            ConfigurationSection q = root.getConfigurationSection(id);
            assertNotNull(q, id);
            assertTrue(q.getBoolean("enabled", true), id + " must be enabled");

            String giver = q.getString("npc-key", "");
            assertFalse(giver.isBlank(), id + " missing npc-key");
            validateNpcKey(id, giver, actorKeys);

            String tierStr = q.getString("tier", "");
            Difficulty tier = Difficulty.valueOf(tierStr);
            switch (tier) {
                case EASY -> easy++;
                case MEDIUM -> medium++;
                case HARD -> hard++;
                default -> fail("Unexpected tier " + tier + " for " + id);
            }
            assertTrue(q.getInt("time-limit-minutes", 0) > 0, id + " missing time-limit-minutes");
            assertFalse(q.getString("summary", "").isBlank(), id + " missing summary");

            String initial = q.getString("initial-dialogue", "greeting");
            assertNotNull(q.getConfigurationSection("dialogues." + initial), id + " missing initial dialogue");

            List<?> steps = q.getList("steps");
            assertNotNull(steps, id + " steps");
            assertFalse(steps.isEmpty(), id + " must have steps");
            if (tier == Difficulty.MEDIUM) assertTrue(steps.size() >= 3, id + " medium should have 3+ steps");
            if (tier == Difficulty.HARD) assertTrue(steps.size() >= 5, id + " hard should have 5+ steps");

            for (Object sObj : steps) {
                assertInstanceOf(Map.class, sObj);
                Map<?, ?> step = (Map<?, ?>) sObj;
                NpcQuestStep.Type type = NpcQuestStep.Type.valueOf(String.valueOf(step.get("type")));
                if (type == NpcQuestStep.Type.VISIT_NPC || type == NpcQuestStep.Type.DELIVER_ITEM) {
                    Object target = step.get("target-npc-key");
                    assertNotNull(target, id + " step missing target-npc-key");
                    validateNpcKey(id, target.toString(), actorKeys);
                }
                if (type == NpcQuestStep.Type.CRAFT_ITEM || type == NpcQuestStep.Type.MINE_MATERIAL || type == NpcQuestStep.Type.DELIVER_ITEM) {
                    Material.valueOf(String.valueOf(step.get("material")));
                }
                if (type == NpcQuestStep.Type.KILL_ENTITY) {
                    EntityType.valueOf(String.valueOf(step.get("entity-type")));
                }
            }
            assertTrue(q.getLong("rewards.money", 0) > 0, id + " must have base money reward");
        }

        assertEquals(20, easy, "Expected 20 EASY quests");
        assertEquals(15, medium, "Expected 15 MEDIUM quests");
        assertEquals(10, hard, "Expected 10 HARD quests");
    }

    @Test
    void legacyPoolsAreClearedAndNetherEndIsAbsentFromResources() throws Exception {
        YamlConfiguration contracts = loadYaml("/contracts.yml");
        ConfigurationSection legacy = contracts.getConfigurationSection("contracts");
        assertTrue(legacy == null || legacy.getKeys(false).isEmpty(), "contracts.yml must be empty");

        YamlConfiguration quests = loadYaml("/npc_quests.yml");
        ConfigurationSection oldQuests = quests.getConfigurationSection("npc_quests");
        assertTrue(oldQuests == null || oldQuests.getKeys(false).isEmpty(), "npc_quests.yml placeholders must be removed");

        for (String res : List.of("/npc_stories.yml", "/contracts.yml", "/npc_quests.yml", "/config.yml")) {
            String text = readResource(res).toLowerCase(Locale.ROOT);
            for (String forbidden : FORBIDDEN_NETHER_END) {
                assertFalse(text.contains(forbidden), res + " must not mention Nether/End term: " + forbidden);
            }
        }
    }

    private static void validateNpcKey(String questId, String key, Set<String> actors) {
        if (key.startsWith("@actor:")) {
            assertTrue(actors.contains(key.substring(7)), questId + " references unknown actor " + key);
        } else {
            assertTrue("@contractor".equals(key) || key.startsWith("@brew:"),
                    questId + " uses unexpected NPC key " + key);
        }
    }
}
