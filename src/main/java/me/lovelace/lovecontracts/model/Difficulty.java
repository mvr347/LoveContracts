package me.lovelace.lovecontracts.model;

public enum Difficulty {
    STARTER(100, "Новичок", "<green>"),
    EASY(10, "Легкий", "<green>"),
    MEDIUM(5, "Средний", "<yellow>"),
    HARD(1, "Сложный", "<red>");

    private final int defaultWeight;
    private final String displayName;
    private final String colorTag;

    Difficulty(int defaultWeight, String displayName, String colorTag) {
        this.defaultWeight = defaultWeight;
        this.displayName = displayName;
        this.colorTag = colorTag;
    }

    public int getDefaultWeight() {
        return defaultWeight;
    }

    public String getDisplayName() {
        me.lovelace.lovecontracts.LoveContracts plugin = me.lovelace.lovecontracts.LoveContracts.getInstance();
        if (plugin != null && plugin.getMessageManager() != null) {
            return plugin.getMessageManager().getRaw("difficulties." + name().toLowerCase(), displayName);
        }
        return displayName;
    }

    public String getColorTag() {
        return colorTag;
    }

    public String getFormattedTag() {
        String tag = colorTag.startsWith("<") && colorTag.endsWith(">") ? colorTag.substring(1, colorTag.length() - 1) : colorTag;
        return colorTag + displayName + "</" + tag + ">";
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
