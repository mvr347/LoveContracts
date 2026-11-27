package me.lovelace.lovecontracts.npc.dialog;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** What each NPC says in its dialog window and which buttons it has, read from npc_dialogs.yml. */
public final class NpcDialogs {

    /** What the NPC's main button does. */
    public enum Main { BOARD, FUNCTION, NONE }

    public record Entry(String id, String key, Main main, String mainLabel, String game, String gameLabel,
                        List<String> greetings, List<String> smallTalk) {
    }

    public record Labels(String questPrefix, String smallTalk, String close, String gameDefault, String noGamePlugin) {
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Labels labels;
    /** Last line index shown to a player for an NPC and a pool, so that the same line never repeats at once. */
    private final Map<String, Integer> lastLine = new java.util.concurrent.ConcurrentHashMap<>();

    private NpcDialogs(Labels labels) {
        this.labels = labels;
    }

    public static NpcDialogs load(ConfigurationSection root) {
        ConfigurationSection b = root == null ? null : root.getConfigurationSection("buttons");
        Labels labels = new Labels(
                text(b, "quest-prefix", "[ЗАДАНИЕ] "), text(b, "small-talk", "Как дела?"), text(b, "close", "Закрыть"),
                text(b, "game-default", "Сыграем во что-то"), text(b, "no-game-plugin", "Игры сейчас недоступны."));
        NpcDialogs dialogs = new NpcDialogs(labels);
        ConfigurationSection npcs = root == null ? null : root.getConfigurationSection("npcs");
        if (npcs == null) return dialogs;
        for (String id : npcs.getKeys(false)) {
            ConfigurationSection s = npcs.getConfigurationSection(id);
            if (s == null || s.getString("key", "").isBlank()) continue;
            Main main;
            try {
                main = Main.valueOf(s.getString("main", "function").trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                main = Main.FUNCTION;
            }
            String game = s.getString("game", "");
            dialogs.entries.put(id, new Entry(id, s.getString("key").trim(), main,
                    s.getString("main-label", ""), game == null ? "" : game.trim().toUpperCase(Locale.ROOT),
                    s.getString("game-label", ""), List.copyOf(s.getStringList("greetings")),
                    List.copyOf(s.getStringList("small-talk"))));
        }
        return dialogs;
    }

    private static String text(ConfigurationSection s, String key, String fallback) {
        return s == null ? fallback : s.getString(key, fallback);
    }

    public Labels labels() {
        return labels;
    }

    public Collection<Entry> entries() {
        return entries.values();
    }

    /**
     * A random line of the pool, never the one this player got last time from the same pool (when the pool has more
     * than one line). {@code pool} tells the greeting and small-talk pools of one NPC apart.
     */
    public String pick(java.util.UUID player, Entry entry, String pool, List<String> lines) {
        if (lines.isEmpty()) return "";
        if (lines.size() == 1) return lines.get(0);
        String memory = player + ":" + entry.id() + ":" + pool;
        int last = lastLine.getOrDefault(memory, -1);
        int next;
        do {
            next = ThreadLocalRandom.current().nextInt(lines.size());
        } while (next == last);
        lastLine.put(memory, next);
        return lines.get(next);
    }

    /** Forgets what a player was told (they left). */
    public void forget(java.util.UUID player) {
        lastLine.keySet().removeIf(k -> k.startsWith(player + ":"));
    }

    public List<String> ids() {
        return new ArrayList<>(entries.keySet());
    }
}
