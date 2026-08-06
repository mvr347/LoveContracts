package me.lovelace.lovecontracts.manager;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.model.Contract;
import me.lovelace.lovecontracts.model.Penalty;
import me.lovelace.lovecontracts.model.Reward;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * Gives rewards and applies penalties. Every contract reward is physical LoveCore currency —
 * see {@link Reward} — granted through {@link LoveEconomy}, the same physical-coin wallet the
 * rest of the ecosystem uses. Silently skipped (contract still completes/fails normally) if
 * LoveCore isn't installed.
 */
public class RewardProcessor {

    private final LoveContracts plugin;

    public RewardProcessor(LoveContracts plugin) {
        this.plugin = plugin;
    }

    public void giveRewards(Player player, Contract contract) {
        if (player == null || !player.isOnline() || contract == null) return;

        boolean moneyEnabled = plugin.getConfig().getBoolean("rewards.money.enabled", true);
        if (!moneyEnabled) return;

        long totalAmount = 0;
        for (Reward reward : contract.getRewards()) {
            if (reward.getAmount() > 0) {
                totalAmount += Math.round(reward.getAmount());
            }
        }
        if (totalAmount <= 0) return;

        long amount = totalAmount;
        try {
            LoveCore.service(LoveEconomy.class).ifPresentOrElse(economy -> {
                economy.give(player, amount);
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("<green>Награды:</green> <gold>" + amount + " " + economy.currencyName() + "</gold>"));
            }, () -> plugin.getLogger().fine("Skipping MONEY reward — LoveCore not present: " + amount));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to give reward", e);
        }
    }

    public void applyPenalties(Player player, Contract contract) {
        if (player == null || contract == null) return;
        boolean moneyEnabled = plugin.getConfig().getBoolean("penalties.money.enabled", true);

        double explicitMoneyPenalty = 0.0;
        if (contract.getPenalties() != null) {
            for (Penalty penalty : contract.getPenalties()) {
                if (penalty.getType() == Penalty.Type.MONEY) {
                    explicitMoneyPenalty += Math.abs(penalty.getAmount());
                }
            }
        }

        // Если у контракта не задан прямой штраф монетами — штрафуем на сумму денежной награды
        // (все награды контракта — монеты, см. Reward).
        if (explicitMoneyPenalty <= 0.0 && contract.getRewards() != null) {
            for (Reward r : contract.getRewards()) {
                explicitMoneyPenalty += r.getAmount();
            }
        }

        if (moneyEnabled && explicitMoneyPenalty > 0.0) {
            long fullAmount = Math.round(explicitMoneyPenalty);
            LoveCore.service(LoveEconomy.class).ifPresentOrElse(economy -> {
                long currentBalance = economy.balance(player);
                long immediateCharge = Math.min(fullAmount, currentBalance);
                if (immediateCharge > 0) {
                    economy.charge(player, immediateCharge);
                }
                long remainingDebt = fullAmount - immediateCharge;
                if (remainingDebt > 0 && plugin.getFineManager() != null) {
                    plugin.getFineManager().addDebt(player.getUniqueId(), remainingDebt);
                    plugin.getMessageManager().sendMessage(player, "messages.fine-partial",
                            "<yellow>Списано {PAID} монет. Остаток долга: {REMAINING} монет.</yellow>",
                            java.util.Map.of("PAID", String.valueOf(immediateCharge), "REMAINING", String.valueOf(remainingDebt)));
                } else {
                    plugin.getMessageManager().sendMessage(player, "messages.fine-deducted",
                            "<red>Списан штраф за провал контракта:</red> <gold>{AMOUNT} монет</gold>",
                            java.util.Map.of("AMOUNT", String.valueOf(immediateCharge)));
                }
            }, () -> {
                if (plugin.getFineManager() != null) {
                    plugin.getFineManager().addDebt(player.getUniqueId(), fullAmount);
                }
            });
        }
    }
}
