package me.lovelace.lovecontracts.util;

/**
 * Progress bar of the GUI lore standard: 16 cells of {@code █} in dark-gray brackets, then the percentage. The result
 * is a MiniMessage fragment, meant for a lore placeholder such as {@code {bar}}.
 */
public final class LoreBar {

    public static final int CELLS = 16;

    private LoreBar() {
    }

    /** Gold while it fills, green once complete. */
    public static String bar(long current, long max) {
        int percent = percent(current, max);
        String color = percent >= 100 ? "green" : "gold";
        // Floor, not round: 99 % must not look full, only 100 % fills every cell.
        int filled = percent * CELLS / 100;
        return "<dark_gray>[<" + color + ">" + "█".repeat(filled) + "</" + color + ">"
                + "<dark_gray>" + "█".repeat(CELLS - filled) + "]</dark_gray> "
                + "<dark_gray>(<" + color + ">" + percent + "%</" + color + "><dark_gray>)";
    }

    /** Whole percent clamped to 0..100; an empty or negative maximum counts as 0. */
    public static int percent(long current, long max) {
        if (max <= 0 || current <= 0) {
            return 0;
        }
        return (int) Math.min(100, current * 100 / max);
    }
}
