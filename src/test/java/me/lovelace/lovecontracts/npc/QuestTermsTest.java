package me.lovelace.lovecontracts.npc;

import me.lovelace.lovecontracts.npc.model.QuestTerms;
import me.lovelace.lovecontracts.npc.model.QuestTier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestTermsTest {
    private static YamlConfiguration yaml(String text) throws Exception {
        var y = new YamlConfiguration();
        y.loadFromString(text);
        return y;
    }

    @Test
    void aQuestWithoutATierKeepsTheOldBehavior() throws Exception {
        var terms = QuestTerms.read(yaml("display-name: x\nsummary: hi"), null);
        assertFalse(terms.timed());
        assertEquals(0, terms.advancePercent());
        assertEquals("hi", terms.summary());
        // and pays exactly its base: no quality or speed bonus sneaks into legacy chains
        assertEquals(200, terms.rewardTiers().compute(200, 0, 0, 0, true, true).total());
    }

    @Test
    void tierDefaultsComeFromTheConfigSection() throws Exception {
        var defaults = yaml("tiers:\n  medium:\n    time-limit-minutes: 90\n    advance-percent: 25\n    penalty-percent: 12\n");
        var terms = QuestTerms.read(yaml("tier: MEDIUM"), defaults);
        assertEquals(QuestTier.MEDIUM, terms.tier());
        assertEquals(90, terms.timeLimitMinutes());
        assertEquals(25, terms.advancePercent());
        assertEquals(12, terms.penaltyPercent());
    }

    @Test
    void anOlderConfigWithoutTheSectionFallsBackToBuiltInValues() throws Exception {
        var hard = QuestTerms.read(yaml("tier: HARD"), null);
        assertEquals(210, hard.timeLimitMinutes());
        assertEquals(30, hard.advancePercent());
        assertEquals(20, hard.penaltyPercent());
    }

    @Test
    void theQuestOverridesTheDefaultsAndEasyNeverGetsAnAdvance() throws Exception {
        var terms = QuestTerms.read(yaml("tier: EASY\ntime-limit-minutes: 20\nadvance-percent: 50"), null);
        assertEquals(20, terms.timeLimitMinutes());
        assertEquals(0, terms.advancePercent());
        assertTrue(terms.timed());
    }

    @Test
    void unknownTierNameIsEasy() {
        assertEquals(QuestTier.EASY, QuestTier.parse("legendary"));
        assertEquals(QuestTier.HARD, QuestTier.parse(" hard "));
    }
}
