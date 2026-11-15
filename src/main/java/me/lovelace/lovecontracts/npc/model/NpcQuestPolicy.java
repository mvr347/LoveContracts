package me.lovelace.lovecontracts.npc.model;

/** Eligibility shared by NPC offers and acceptance; cooldown survives reconnects. */
public final class NpcQuestPolicy {
    private NpcQuestPolicy() {}
    public static boolean available(boolean loaded, boolean active, boolean repeatable, Long lastCompleted, int cooldownMinutes, long now) {
        if (!loaded || active) return false;
        return lastCompleted == null || repeatable && now - lastCompleted >= Math.max(0, cooldownMinutes) * 60_000L;
    }
}
