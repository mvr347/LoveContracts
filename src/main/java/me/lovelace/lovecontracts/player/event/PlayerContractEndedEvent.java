package me.lovelace.lovecontracts.player.event;

import me.lovelace.lovecontracts.player.model.PlayerContract;
import me.lovelace.lovecontracts.player.model.PlayerContractStatus;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Контракт завершился без успеха: отменён создателем, брошен исполнителем или истёк по сроку.
 * Финальный статус — {@link #getContract()}.getStatus() (CANCELLED / ABANDONED / EXPIRED).
 */
public class PlayerContractEndedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PlayerContract contract;
    private final PlayerContractStatus previousStatus;

    public PlayerContractEndedEvent(PlayerContract contract, PlayerContractStatus previousStatus) {
        this.contract = contract;
        this.previousStatus = previousStatus;
    }

    public PlayerContract getContract() { return contract; }
    public PlayerContractStatus getPreviousStatus() { return previousStatus; }

    @NotNull @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
