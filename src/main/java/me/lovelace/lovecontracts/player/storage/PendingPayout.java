package me.lovelace.lovecontracts.player.storage;

import java.util.UUID;

/**
 * Выплата, которую нужно доставить игроку офлайн — LoveEconomy умеет платить только живому
 * инвентарю, поэтому выплата ждёт следующего входа игрока в сеть.
 */
public record PendingPayout(long payoutId, UUID playerId, long goldAmount, String reason) {
}
