package me.lovelace.lovecontracts.player.storage;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Выплата, которую нужно доставить игроку офлайн — LoveEconomy умеет платить только живому
 * инвентарю, поэтому выплата ждёт следующего входа игрока в сеть.
 */
public record PendingPayout(long payoutId, UUID playerId, long goldAmount, List<ItemStack> items, String reason) {
}
