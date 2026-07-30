package me.lovelace.lovecontracts.player.event;

import me.lovelace.lovecontracts.player.model.PlayerContract;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Игрок разместил контракт на доске. */
public class PlayerContractCreatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PlayerContract contract;

    public PlayerContractCreatedEvent(PlayerContract contract) {
        this.contract = contract;
    }

    public PlayerContract getContract() { return contract; }

    @NotNull @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
