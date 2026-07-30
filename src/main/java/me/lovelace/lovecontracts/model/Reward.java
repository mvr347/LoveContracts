package me.lovelace.lovecontracts.model;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class Reward {

    public enum Type {
        MONEY, ITEMS, EXPERIENCE, REPUTATION
    }

    private final Type type;
    private final double amount;
    private final ItemStack item;
    private final String reputationType;

    public Reward(Type type, double amount) {
        this(type, amount, null, null);
    }

    public Reward(Type type, double amount, ItemStack item, String reputationType) {
        this.type = type;
        this.amount = amount;
        this.item = item;
        this.reputationType = reputationType;
    }

    public static Reward money(double amount) {
        return new Reward(Type.MONEY, amount);
    }

    public static Reward item(ItemStack stack) {
        return new Reward(Type.ITEMS, stack.getAmount(), stack, null);
    }

    public static Reward item(Material material, int amount) {
        return item(new ItemStack(material, amount));
    }

    public static Reward reputation(String type, double amount) {
        return new Reward(Type.REPUTATION, amount, null, type);
    }

    public Type getType() { return type; }
    public double getAmount() { return amount; }
    public ItemStack getItemStack() { return item == null ? null : item.clone(); }
    public String getReputationType() { return reputationType; }

    public String getDisplay() {
        return switch (type) {
            case MONEY -> String.format("%.0f %s", amount, currencyName());
            case ITEMS -> item != null ? item.getAmount() + "x " + item.getType().name() : "items";
            case EXPERIENCE -> (int) amount + " XP";
            case REPUTATION -> (int) amount + " reputation (" + reputationType + ")";
        };
    }

    private static String currencyName() {
        return LoveCore.service(LoveEconomy.class).map(LoveEconomy::currencyName).orElse("coins");
    }
}
