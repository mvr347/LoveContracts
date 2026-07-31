package me.lovelace.lovecontracts.player.event;

import me.lovelace.lovecontracts.player.model.PlayerContract;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Контракт выполнен и награда выплачена исполнителю.
 *
 * <p>{@link PlayerContract#getReputationHint()} — это только намерение создателя контракта,
 * LoveContracts сам репутацию не трогает (нет прав на запись через {@code ReputationOracle},
 * это read-only прокси к LoveBehavior). Если LoveBehavior хочет реагировать на выполненные
 * контракты — пусть слушает это событие сам и решает, сколько репутации начислить.</p>
 */
public class PlayerContractCompletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PlayerContract contract;

    public PlayerContractCompletedEvent(PlayerContract contract) {
        this.contract = contract;
    }

    public PlayerContract getContract() { return contract; }

    @NotNull @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
