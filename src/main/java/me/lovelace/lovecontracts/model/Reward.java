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
    private final java.util.function.DoubleSupplier ratio;

    private Reward(double amount, java.util.function.DoubleSupplier ratio) {
        this.amount = amount;
        this.ratio = ratio;
    }

    public static Reward money(double amount) {
        return new Reward(amount, () -> 1.0);
    }

    /** Reward that follows LoveCore's price model: the amount is multiplied by {@code ratio} on every read. */
    public static Reward money(double amount, java.util.function.DoubleSupplier ratio) {
        return new Reward(amount, ratio);
    }

    public double getAmount() { return amount * ratio.getAsDouble(); }

    /** Сумма столбиком: по строке на номинал (MiniMessage с глифами), для лора меню. */
    public java.util.List<String> getDisplayLines() {
        return me.lovelace.lovecontracts.util.CoinFormat.formatLines(Math.round(getAmount()));
    }

    /** MiniMessage с глифами монет; перед показом прогнать через {@code CoinFormat.resolveGlyphs}. */
    public String getDisplay() {
        return me.lovelace.lovecontracts.util.CoinFormat.format(Math.round(getAmount()));
    }
}
