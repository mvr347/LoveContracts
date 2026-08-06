package me.lovelace.lovecontracts.manager;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.model.Contract;
import me.lovelace.lovecontracts.model.Penalty;
import me.lovelace.lovecontracts.model.Reward;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.logging.Level;

/**
 * Gives rewards and applies penalties. Money goes through LoveCore's LoveEconomy — the same
 * physical-coin wallet the rest of the ecosystem uses — and is silently skipped if LoveCore
 * isn't installed (contracts still work for items/XP/reputation).
 */
public class RewardProcessor {

    private final LoveContracts plugin;

    public RewardProcessor(LoveContracts plugin) {
        this.plugin = plugin;
    }

    public void giveRewards(Player player, Contract contract) {
        if (player == null || !player.isOnline() || contract == null) return;

        boolean moneyEnabled = plugin.getConfig().getBoolean("rewards.money.enabled", true);
        StringBuilder display = new StringBuilder();

        for (Reward reward : contract.getRewards()) {
            try {
                switch (reward.getType()) {
                    case MONEY -> {
                        if (moneyEnabled && reward.getAmount() > 0) {
                            LoveCore.service(LoveEconomy.class).ifPresentOrElse(economy -> {
                                economy.give(player, Math.round(reward.getAmount()));
                                display.append(String.format("%.0f %s", reward.getAmount(), economy.currencyName())).append(" ");
                            }, () -> plugin.getLogger().fine(
                                    "Skipping MONEY reward — LoveCore not present: " + reward.getAmount()));
                        }
                    }
                    case ITEMS -> {
                        ItemStack item = reward.getItemStack();
                        if (item != null && item.getAmount() > 0 && item.getType().isItem()) {
                            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                            leftover.values().forEach(drop ->
                                    player.getWorld().dropItemNaturally(player.getLocation(), drop));
                            display.append(reward.getDisplay()).append(" ");
                        }
                    }
                    case EXPERIENCE -> {
                        player.giveExp((int) reward.getAmount());
                        display.append(reward.getDisplay()).append(" ");
                    }
                    case REPUTATION -> {
                        // LoveBehavior / custom integration point
                        display.append(reward.getDisplay()).append(" ");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to give reward " + reward.getType(), e);
            }
        }

        if (!display.isEmpty()) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<green>Награды:</green> <gold>" + display.toString().trim() + "</gold>"));
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
        if (explicitMoneyPenalty <= 0.0 && contract.getRewards() != null) {
            for (Reward r : contract.getRewards()) {
                if (r.getType() == Reward.Type.MONEY) {
                    explicitMoneyPenalty += r.getAmount();
                }
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
