package me.lovelace.lovecontracts.manager;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.model.Contract;
import me.lovelace.lovecontracts.model.Penalty;
import me.lovelace.lovecontracts.model.Reward;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.logging.Level;

/**
 * Gives rewards and applies penalties.
 * No economy plugin dependency — MONEY type is ignored (items / XP / reputation only).
 */
public class RewardProcessor {

    private final LoveContracts plugin;

    public RewardProcessor(LoveContracts plugin) {
        this.plugin = plugin;
    }

    public void giveRewards(Player player, Contract contract) {
        if (player == null || !player.isOnline() || contract == null) return;

        StringBuilder display = new StringBuilder();

        for (Reward reward : contract.getRewards()) {
            try {
                switch (reward.getType()) {
                    case MONEY -> {
                        // Economy removed — skip money rewards
                        plugin.getLogger().fine("Skipping MONEY reward (no economy): " + reward.getAmount());
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

        for (Penalty penalty : contract.getPenalties()) {
            if (penalty.getType() == Penalty.Type.NONE) continue;
            try {
                switch (penalty.getType()) {
                    case MONEY -> {
                        // Economy removed — skip money penalties
                        plugin.getLogger().fine("Skipping MONEY penalty (no economy): " + penalty.getAmount());
                    }
                    case REPUTATION -> {
                        // integration point
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
