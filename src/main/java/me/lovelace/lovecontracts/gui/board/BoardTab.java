package me.lovelace.lovecontracts.gui.board;

/**
 * Tabs of the unified contract board, in the order of the header controls. {@code key} is the part of the
 * {@code gui.tab-*} messages the tab uses.
 */
public enum BoardTab {
    /** Unified NPC errands / quests. */
    QUESTS("quests"),
    /** Open offers of other players. */
    PLAYERS("players"),
    /** The viewer's own player contracts. */
    MINE("mine");

    private final String key;

    BoardTab(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
