package me.lovelace.lovecontracts.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles NPC clicks and chat lines: a player who spams the click on an NPC must not open the window again and
 * again or flood the chat, and a line that is the same as the previous one is not repeated within a few seconds.
 */
public final class ClickGate {
    private static final int PRUNE_AT = 512;

    private final Map<String, Long> lastClick = new ConcurrentHashMap<>();
    private final Map<UUID, Line> lastLine = new ConcurrentHashMap<>();

    private record Line(String text, long at) {
    }

    /** True when this click may go on: the same player has not clicked the same NPC within {@code windowMillis}. */
    public boolean allowClick(UUID player, String npcKey, long now, long windowMillis) {
        Long last = lastClick.put(player + ":" + npcKey, now);
        if (lastClick.size() > PRUNE_AT) lastClick.values().removeIf(t -> now - t > 60_000L);
        return last == null || now - last >= windowMillis;
    }

    /** True when the line is not an exact repeat of the player's previous line within {@code windowMillis}. */
    public boolean allowLine(UUID player, String text, long now, long windowMillis) {
        Line previous = lastLine.put(player, new Line(text, now));
        if (previous != null && previous.text().equals(text) && now - previous.at() < windowMillis) {
            // a repeat does not extend the window, otherwise holding the click would mute the line for good
            lastLine.put(player, previous);
            return false;
        }
        return true;
    }

    public void forget(UUID player) {
        lastLine.remove(player);
        lastClick.keySet().removeIf(k -> k.startsWith(player + ":"));
    }
}
