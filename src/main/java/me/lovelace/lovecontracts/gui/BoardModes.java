package me.lovelace.lovecontracts.gui;

import me.lovelace.lovecontracts.model.Difficulty;
import me.lovelace.lovecontracts.npc.model.ChainStatus;
import me.lovelace.lovecontracts.npc.model.ChainView;

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

    public enum FilterMode implements Mode {
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
        @Override public String messageKey() { return "filters." + key; }
        @Override public String fallback() { return fallback; }
        public FilterMode next() { return cycle(values(), this, 1); }
        public FilterMode prev() { return cycle(values(), this, -1); }
    }

    public enum SortMode implements Mode {
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
        @Override public String messageKey() { return "sorts." + key; }
        @Override public String fallback() { return fallback; }
        public SortMode next() { return cycle(values(), this, 1); }
        public SortMode prev() { return cycle(values(), this, -1); }
    }

    /** A filter or sort option of the unified board: its display name lives in messages.yml, the fallback is Russian. */
    public interface Mode {
        String messageKey();
        String fallback();
    }

    /** Filter of the unified quests tab (difficulty and status). */
    public enum ChainFilter implements Mode {
        ALL("all", "Все"),
        EASY("easy", "Лёгкие"),
        MEDIUM("medium", "Средние"),
        HARD("hard", "Сложные"),
        AVAILABLE("available", "Доступные"),
        ACTIVE("active", "Взятые"),
        COOLDOWN("cooldown", "Перерыв"),
        DONE("done", "Выполненные");

        private final String key;
        private final String fallback;

        ChainFilter(String key, String fallback) {
            this.key = key;
            this.fallback = fallback;
        }

        @Override public String messageKey() { return "gui.modes.chain-filter." + key; }
        @Override public String fallback() { return fallback; }
        public ChainFilter next() { return cycle(values(), this, 1); }
        public ChainFilter prev() { return cycle(values(), this, -1); }

        /** Whether a quest in this state shows under the filter. A locked or loading quest shows only under ALL. */
        public boolean accepts(ChainStatus status) {
            return switch (this) {
                case ALL, EASY, MEDIUM, HARD -> true;
                case AVAILABLE -> status == ChainStatus.AVAILABLE;
                case ACTIVE -> status == ChainStatus.ACTIVE;
                case COOLDOWN -> status == ChainStatus.COOLDOWN;
                case DONE -> status == ChainStatus.DONE;
            };
        }

        public boolean accepts(ChainView view) {
            if (view == null || view.quest() == null) return false;
            Difficulty tier = view.quest().getTier();
            return switch (this) {
                case ALL -> true;
                case EASY -> tier == Difficulty.EASY || tier == Difficulty.STARTER;
                case MEDIUM -> tier == Difficulty.MEDIUM;
                case HARD -> tier == Difficulty.HARD;
                default -> accepts(view.status());
            };
        }
    }

    /** Sort of the unified quests tab. */
    public enum ChainSort implements Mode {
        DEFAULT("default", "Как в списке"),
        DIFFICULTY("difficulty", "Сложность"),
        REWARD_HIGH("reward-high", "От Б до М"),
        REWARD_LOW("reward-low", "От М до Б");

        private final String key;
        private final String fallback;

        ChainSort(String key, String fallback) {
            this.key = key;
            this.fallback = fallback;
        }

        @Override public String messageKey() { return "gui.modes.chain-sort." + key; }
        @Override public String fallback() { return fallback; }
        public ChainSort next() { return cycle(values(), this, 1); }
        public ChainSort prev() { return cycle(values(), this, -1); }
    }

    /** Filter of the players' offers tab: by what the offer asks for. */
    public enum OfferFilter implements Mode {
        ALL("all", "Все"),
        KILL("kill", "Охота"),
        DELIVER("deliver", "Доставка"),
        CUSTOM("custom", "Особые");

        private final String key;
        private final String fallback;

        OfferFilter(String key, String fallback) {
            this.key = key;
            this.fallback = fallback;
        }

        @Override public String messageKey() { return "gui.modes.offer-filter." + key; }
        @Override public String fallback() { return fallback; }
        public OfferFilter next() { return cycle(values(), this, 1); }
        public OfferFilter prev() { return cycle(values(), this, -1); }

        public boolean accepts(me.lovelace.lovecontracts.player.model.PlayerContractObjectiveType type) {
            return switch (this) {
                case ALL -> true;
                case KILL -> type == me.lovelace.lovecontracts.player.model.PlayerContractObjectiveType.KILL_ENTITY;
                case DELIVER -> type == me.lovelace.lovecontracts.player.model.PlayerContractObjectiveType.DELIVER_ITEM;
                case CUSTOM -> type == me.lovelace.lovecontracts.player.model.PlayerContractObjectiveType.CUSTOM;
            };
        }
    }

    /** Sort of the players' offers tab. */
    public enum OfferSort implements Mode {
        NEWEST("newest", "Сначала новые"),
        REWARD_HIGH("reward-high", "От Б до М"),
        REWARD_LOW("reward-low", "От М до Б"),
        DEADLINE("deadline", "Скоро истекают");

        private final String key;
        private final String fallback;

        OfferSort(String key, String fallback) {
            this.key = key;
            this.fallback = fallback;
        }

        @Override public String messageKey() { return "gui.modes.offer-sort." + key; }
        @Override public String fallback() { return fallback; }
        public OfferSort next() { return cycle(values(), this, 1); }
        public OfferSort prev() { return cycle(values(), this, -1); }
    }

    /** Filter of "my contracts": my role or only what is still running. */
    public enum MineFilter implements Mode {
        ALL("all", "Все"),
        CREATOR("creator", "Я нанял"),
        EXECUTOR("executor", "Я исполняю"),
        RUNNING("running", "Не завершены");

        private final String key;
        private final String fallback;

        MineFilter(String key, String fallback) {
            this.key = key;
            this.fallback = fallback;
        }

        @Override public String messageKey() { return "gui.modes.mine-filter." + key; }
        @Override public String fallback() { return fallback; }
        public MineFilter next() { return cycle(values(), this, 1); }
        public MineFilter prev() { return cycle(values(), this, -1); }

        /** @param creator whether the viewer is the one who ordered the contract */
        public boolean accepts(boolean creator, me.lovelace.lovecontracts.player.model.PlayerContractStatus status) {
            return switch (this) {
                case ALL -> true;
                case CREATOR -> creator;
                case EXECUTOR -> !creator;
                case RUNNING -> !status.isTerminal();
            };
        }
    }

    /** Sort of "my contracts". */
    public enum MineSort implements Mode {
        NEWEST("newest", "Сначала новые"),
        RUNNING_FIRST("running-first", "Сначала активные");

        private final String key;
        private final String fallback;

        MineSort(String key, String fallback) {
            this.key = key;
            this.fallback = fallback;
        }

        @Override public String messageKey() { return "gui.modes.mine-sort." + key; }
        @Override public String fallback() { return fallback; }
        public MineSort next() { return cycle(values(), this, 1); }
        public MineSort prev() { return cycle(values(), this, -1); }
    }
}
