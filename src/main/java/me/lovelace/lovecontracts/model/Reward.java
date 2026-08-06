package me.lovelace.lovecontracts.model;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;

/**
 * Contract completion reward — always physical LoveCore currency, granted through
 * {@link LoveEconomy#give}. This used to also support item stacks, XP and reputation, but
 * those were granted directly by this plugin instead of through LoveCore's physical-coin
 * wallet — exactly what the ecosystem's "one currency" rule forbids — so they were removed
 * rather than routed through an economy that has no concept of them.
 */
public class Reward {

    private final double amount;

    private Reward(double amount) {
        this.amount = amount;
    }

    public static Reward money(double amount) {
        return new Reward(amount);
    }

    public double getAmount() { return amount; }

    public String getDisplay() {
        return String.format("%.0f %s", amount, currencyName());
    }

    private static String currencyName() {
        return LoveCore.service(LoveEconomy.class).map(LoveEconomy::currencyName).orElse("coins");
    }
}
