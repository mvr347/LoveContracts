package me.lovelace.lovecontracts.model;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;

public class Penalty {

    public enum Type { MONEY, REPUTATION, NONE }

    private final Type type;
    private final double amount;
    private final String reputationType;

    public Penalty(Type type, double amount) {
        this(type, amount, null);
    }

    public Penalty(Type type, double amount, String reputationType) {
        this.type = type;
        this.amount = amount;
        this.reputationType = reputationType;
    }

    public static Penalty none() { return new Penalty(Type.NONE, 0); }
    public static Penalty money(double amount) { return new Penalty(Type.MONEY, amount); }
    public static Penalty reputation(String type, double amount) { return new Penalty(Type.REPUTATION, amount, type); }

    public Type getType() { return type; }
    public double getAmount() { return amount; }
    public String getReputationType() { return reputationType; }

    public String getDisplay() {
        return switch (type) {
            case MONEY -> String.format("-%.0f %s", Math.abs(amount), currencyName());
            case REPUTATION -> (int) amount + " reputation (" + reputationType + ")";
            case NONE -> "none";
        };
    }

    private static String currencyName() {
        return LoveCore.service(LoveEconomy.class).map(LoveEconomy::currencyName).orElse("coins");
    }
}
