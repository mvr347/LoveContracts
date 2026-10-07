package me.lovelace.lovecontracts.gui;

/**
 * Filter and sort modes of the contract board plus their cycling logic.
 * Pure (no Bukkit access) so the wrap-around behaviour is unit-testable.
 * Display names live in messages.yml under {@code filters.<key>} / {@code sorts.<key>};
 * the fallback here is only used when the key is missing.
 */
public final class BoardModes {

    private BoardModes() {}

    /** Steps {@code delta} positions through {@code values}, wrapping around both ends. */
    static <E extends Enum<E>> E cycle(E[] values, E current, int delta) {
        int n = values.length;
        return values[Math.floorMod(current.ordinal() + delta, n)];
    }

    public enum FilterMode {
        ALL("all", "Все"),
        EASY("easy", "Легкие"),
        MEDIUM("medium", "Средние"),
        HARD("hard", "Сложные"),
        AVAILABLE("available", "Доступные"),
        ACCEPTED("accepted", "Взятые"),
        COMPLETED("completed", "Выполненные"),
        FAILED("failed", "Проваленные");

        private final String key;
        private final String fallback;

        FilterMode(String key, String fallback) {
            this.key = key;
            this.fallback = fallback;
        }

        /** messages.yml path of this mode's display name. */
        public String messageKey() { return "filters." + key; }
        public String fallback() { return fallback; }
        public FilterMode next() { return cycle(values(), this, 1); }
        public FilterMode prev() { return cycle(values(), this, -1); }
    }

    public enum SortMode {
        ALL("all", "Все"),
        REWARD_HIGH("reward-high", "От Б до М"),
        REWARD_LOW("reward-low", "От М до Б"),
        DIFFICULTY("difficulty", "Сложность");

        private final String key;
        private final String fallback;

        SortMode(String key, String fallback) {
            this.key = key;
            this.fallback = fallback;
        }

        /** messages.yml path of this mode's display name. */
        public String messageKey() { return "sorts." + key; }
        public String fallback() { return fallback; }
        public SortMode next() { return cycle(values(), this, 1); }
        public SortMode prev() { return cycle(values(), this, -1); }
    }
}
