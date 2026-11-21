package me.lovelace.lovecontracts.gui.board;

/** Short Russian wording of a time span for lore: «2 ч 15 мин», «45 мин», «меньше минуты». */
public final class DurationText {

    private DurationText() {
    }

    public static String format(long millis) {
        long minutes = Math.max(0, millis) / 60_000L;
        if (minutes < 1) return "меньше минуты";
        long days = minutes / 1440;
        long hours = (minutes % 1440) / 60;
        long mins = minutes % 60;
        StringBuilder out = new StringBuilder();
        if (days > 0) out.append(days).append(" д");
        if (hours > 0) append(out, hours + " ч");
        // minutes are noise once a duration runs into days
        if (mins > 0 && days == 0) append(out, mins + " мин");
        return out.toString();
    }

    private static void append(StringBuilder out, String part) {
        if (!out.isEmpty()) out.append(' ');
        out.append(part);
    }
}
