package me.lovelace.lovecontracts.npc.model;

import java.util.Locale;

/** How hard a quest is: drives the board filter, the default time limit and whether the giver pays an advance. */
public enum QuestTier {
    EASY, MEDIUM, HARD;

    /** Reads a tier name from a config value; anything unknown is EASY so a typo never hides a quest. */
    public static QuestTier parse(String value) {
        if (value == null) return EASY;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return EASY;
        }
    }

    /** Only medium and hard quests come with an advance (owner decision, 2026-11-22). */
    public boolean hasAdvance() {
        return this != EASY;
    }
}
