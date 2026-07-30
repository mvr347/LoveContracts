package me.lovelace.lovecontracts.model;

public enum Difficulty {
    EASY(10),
    MEDIUM(5),
    HARD(1);

    private final int defaultWeight;

    Difficulty(int defaultWeight) {
        this.defaultWeight = defaultWeight;
    }

    public int getDefaultWeight() {
        return defaultWeight;
    }

    public static Difficulty fromString(String value) {
        if (value == null) return EASY;
        try {
            return Difficulty.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return EASY;
        }
    }
}
