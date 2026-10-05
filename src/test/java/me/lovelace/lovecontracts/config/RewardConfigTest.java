package me.lovelace.lovecontracts.config;

import dev.lovelace.lovecore.api.economy.MoneyParser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** A typo in a money key must fail the build, not silently zero a reward in production. */
class RewardConfigTest {

    private static YamlConfiguration load(String resource) throws Exception {
        try (Reader r = new InputStreamReader(
                RewardConfigTest.class.getResourceAsStream("/" + resource), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(r);
        }
    }

    private static long money(ConfigurationSection s, String path) {
        Object raw = s.get(path);
        if (raw instanceof Number n) return n.longValue();
        return MoneyParser.parse(String.valueOf(raw), MoneyParser.STANDARD);
    }

    @Test
    void contractRewardsAreOnTheNewScale() throws Exception {
        ConfigurationSection contracts = load("contracts.yml").getConfigurationSection("contracts");
        assertNotNull(contracts);
        for (String id : contracts.getKeys(false)) {
            ConfigurationSection c = contracts.getConfigurationSection(id);
            long reward = money(c, "rewards.money");
            String diff = c.getString("difficulty");
            switch (diff) {
                // 2026-10-04: floor 150 -> 100: bulk mining of 1-2 copper blocks (sand, gravel, dirt) is worth ~128 by the price model
                case "EASY" -> assertTrue(reward >= 100 && reward <= 400, id + " easy reward " + reward);
                case "MEDIUM" -> assertTrue(reward >= 500 && reward <= 900, id + " medium reward " + reward);
                case "HARD" -> assertTrue(reward >= 1200 && reward <= 2200, id + " hard reward " + reward);
                default -> fail(id + ": unknown difficulty " + diff);
            }
            if (c.contains("penalties.money")) {
                long penalty = money(c, "penalties.money");
                assertTrue(penalty > 0 && penalty < reward, id + " penalty " + penalty + " vs reward " + reward);
            }
        }
    }

    @Test
    void npcQuestRewardsParse() throws Exception {
        ConfigurationSection quests = load("npc_quests.yml").getConfigurationSection("npc_quests");
        assertNotNull(quests);
        for (String id : quests.getKeys(false)) {
            assertTrue(money(quests.getConfigurationSection(id), "rewards.money") > 0, id);
        }
    }

    @Test
    void economyKeysInConfigParse() throws Exception {
        YamlConfiguration cfg = load("config.yml");
        assertTrue(cfg.getDouble("economy.reward-scale") > 0);
        assertTrue(cfg.getDouble("economy.penalty-scale") > 0);
        assertTrue(cfg.getDouble("economy.migration.factor") > 0);
        // creation-reward-steps is optional now: without it the coin values are the steps
        if (cfg.getList("economy.creation-reward-steps") != null) {
            for (Object step : cfg.getList("economy.creation-reward-steps")) {
                assertTrue(step instanceof Number || MoneyParser.parse(String.valueOf(step), MoneyParser.STANDARD) > 0);
            }
        }
        // player order bounds: from one copper coin to 100 diamond coins; the old "gold" keys are gone
        assertEquals(1L, money(cfg, "player-contracts.min-reward"));
        assertTrue(money(cfg, "player-contracts.min-reward") < money(cfg, "player-contracts.max-reward"));
        assertTrue(!cfg.contains("player-contracts.min-gold-reward") && !cfg.contains("player-contracts.max-gold-reward"));
    }
}
