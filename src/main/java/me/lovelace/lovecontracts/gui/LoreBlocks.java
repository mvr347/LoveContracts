package me.lovelace.lovecontracts.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lore building blocks of the GUI style standard (gui-gen-6, GUI_LORE_STYLE.md): the separator, one click line per
 * action, the status line of a disabled button and the block that follows it. Every result is a MiniMessage string.
 * <p>
 * The texts live in messages.yml under {@code gui.style.*}; {@link #defaults()} repeats them, so a server file that
 * lacks a key still shows the standard look. This class does not touch Bukkit: {@link Style} is the only source.
 */
public final class LoreBlocks {

    /** Mouse click a line belongs to; {@link #key} is the messages key under {@code gui.style.click}. */
    public enum Click {
        LEFT("left", "ЛКМ"),
        RIGHT("right", "ПКМ"),
        SHIFT_LEFT("shift-left", "Shift+ЛКМ"),
        SHIFT_RIGHT("shift-right", "Shift+ПКМ");

        final String key;
        final String label;

        Click(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    /** One click line: which click does what. {@code action} is plain localised text, without tags. */
    public record Hint(Click click, String action) {
        public static Hint of(Click click, String action) {
            return new Hint(click, action);
        }
    }

    /** Source of a messages text: the value at {@code path}, or {@code fallback} when the server file lacks it. */
    @FunctionalInterface
    public interface Style {
        String raw(String path, String fallback);
    }

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("gui.style.separator", "<dark_gray><st>─────────────────────</st></dark_gray>"),
            Map.entry("gui.style.click.left", "<green>ЛКМ <dark_gray>— <gray>{action}"),
            Map.entry("gui.style.click.right", "<gold>ПКМ <dark_gray>— <gray>{action}"),
            Map.entry("gui.style.click.shift-left", "<yellow>Shift+ЛКМ <dark_gray>— <gray>{action}"),
            Map.entry("gui.style.click.shift-right", "<yellow>Shift+ПКМ <dark_gray>— <gray>{action}"),
            Map.entry("gui.style.reason", "<red>Причина: <gray>{reason}"),
            Map.entry("gui.style.blocked-click", "<dark_gray>{click} — {action} (заблокировано)"),
            Map.entry("gui.style.disabled-name", "<gray>{name} <dark_gray>| <red>Недоступно"),
            Map.entry("gui.style.status.unavailable", "<red>✕ Действие недоступно"),
            Map.entry("gui.style.status.locked", "<red>✕ Заблокировано"),
            Map.entry("gui.style.status.cooldown", "<yellow>⌛ Подождите"),
            Map.entry("gui.style.status.max", "<green>✔ Достигнут предел"));

    private LoreBlocks() {
    }

    /** Built-in value of every {@code gui.style.*} key; a test keeps it equal to messages.yml. */
    public static Map<String, String> defaults() {
        return DEFAULTS;
    }

    private static String text(Style style, String path) {
        String value = style.raw(path, DEFAULTS.get(path));
        return value != null ? value : DEFAULTS.get(path);
    }

    public static String separator(Style style) {
        return text(style, "gui.style.separator");
    }

    /** One click line: {@code ЛКМ — action}. The colour comes from the click type. */
    public static String click(Style style, Hint hint) {
        return text(style, "gui.style.click." + hint.click().key).replace("{action}", hint.action());
    }

    /** Separator followed by one line per hint. No hints give no lines, so there is never a dangling separator. */
    public static List<String> clicks(Style style, Hint... hints) {
        if (hints.length == 0) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(hints.length + 1);
        lines.add(separator(style));
        for (Hint hint : hints) {
            lines.add(click(style, hint));
        }
        return lines;
    }

    /** Status line of a disabled button: {@code gui.style.status.<name>} (unavailable, locked, cooldown, max). */
    public static String status(Style style, String name) {
        return text(style, "gui.style.status." + name);
    }

    /**
     * Block of a disabled button, in this order: separator, status line, {@code Причина:} line (only with a reason)
     * and the click that would have worked, shown as blocked (only with a hint).
     */
    public static List<String> blocked(Style style, String statusLine, String reason, Hint blockedHint) {
        List<String> lines = new ArrayList<>(4);
        lines.add(separator(style));
        lines.add(statusLine);
        if (reason != null && !reason.isBlank()) {
            lines.add(text(style, "gui.style.reason").replace("{reason}", reason));
        }
        if (blockedHint != null) {
            lines.add(text(style, "gui.style.blocked-click")
                    .replace("{click}", blockedHint.click().label)
                    .replace("{action}", blockedHint.action()));
        }
        return lines;
    }

    /** Name of a disabled button: {@code Имя | Недоступно}. */
    public static String disabledName(Style style, String name) {
        return text(style, "gui.style.disabled-name").replace("{name}", name);
    }
}
