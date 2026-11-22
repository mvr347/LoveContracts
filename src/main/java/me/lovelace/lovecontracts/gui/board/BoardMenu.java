package me.lovelace.lovecontracts.gui.board;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * A menu of LoveContracts. The menu is its own inventory holder, so {@link BoardGuiListener} recognises it by type
 * (never by title) and cancels every click and drag: nothing moves in or out except through the menu's own handlers.
 */
public interface BoardMenu extends InventoryHolder {

    /** A click inside the top inventory; the event is already cancelled. */
    void handleClick(InventoryClickEvent event);

    /** The viewer closed the menu. */
    default void onClose() {
    }
}
