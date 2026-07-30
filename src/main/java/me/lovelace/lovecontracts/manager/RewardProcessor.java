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
                    .deserialize("<green>Rewards:</green> <gold>" + display.toString().trim() + "</gold>"));
        }
    }

    public void applyPenalties(Player player, Contract contract) {
        if (player == null || contract == null) return;
        boolean moneyEnabled = plugin.getConfig().getBoolean("penalties.money.enabled", true);

        for (Penalty penalty : contract.getPenalties()) {
            if (penalty.getType() == Penalty.Type.NONE) continue;
            try {
                switch (penalty.getType()) {
                    case MONEY -> {
                        if (!moneyEnabled) break;
                        long amount = Math.round(Math.abs(penalty.getAmount()));
                        if (amount <= 0) break;
                        // Physical currency has no debt — cap the charge at what the player actually has.
                        LoveCore.service(LoveEconomy.class).ifPresent(economy -> {
                            long charge = Math.min(amount, economy.balance(player));
                            if (charge > 0) economy.charge(player, charge);
                        });
                    }
                    case REPUTATION -> {
                        // LoveBehavior integration point — no shared service exposed for it yet.
                    }
                    default -> {
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to apply penalty", e);
            }
        }
    }
}
