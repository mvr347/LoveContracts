package me.lovelace.lovecontracts.model;


public class Penalty {

    public enum Type { MONEY, REPUTATION, NONE }

    private final Type type;
    private final double amount;
    private final String reputationType;
    private final java.util.function.DoubleSupplier ratio;

    public Penalty(Type type, double amount) {
        this(type, amount, null);
    }

    public Penalty(Type type, double amount, String reputationType) {
        this(type, amount, reputationType, () -> 1.0);
    }

    private Penalty(Type type, double amount, String reputationType, java.util.function.DoubleSupplier ratio) {
        this.type = type;
        this.amount = amount;
        this.reputationType = reputationType;
        this.ratio = ratio;
    }

    public static Penalty none() { return new Penalty(Type.NONE, 0); }
    public static Penalty money(double amount) { return new Penalty(Type.MONEY, amount); }
    /** Money penalty that follows the same price-model ratio as the contract's reward. */
    public static Penalty money(double amount, java.util.function.DoubleSupplier ratio) {
        return new Penalty(Type.MONEY, amount, null, ratio);
    }
    public static Penalty reputation(String type, double amount) { return new Penalty(Type.REPUTATION, amount, type); }

    public Type getType() { return type; }
    public double getAmount() { return type == Type.MONEY ? amount * ratio.getAsDouble() : amount; }
    public String getReputationType() { return reputationType; }

    /** Денежный штраф столбиком: по строке на номинал; для остальных типов — одна строка {@link #getDisplay()}. */
    public java.util.List<String> getDisplayLines() {
        if (type != Type.MONEY) return java.util.List.of(getDisplay());
        return me.lovelace.lovecontracts.util.CoinFormat.formatLines(Math.round(Math.abs(getAmount())));
    }

    public String getDisplay() {
        return switch (type) {
            case MONEY -> "-" + me.lovelace.lovecontracts.util.CoinFormat.format(Math.round(Math.abs(getAmount())));
            case REPUTATION -> (int) getAmount() + " reputation (" + reputationType + ")";
            case NONE -> "none";
        };
    }
}
