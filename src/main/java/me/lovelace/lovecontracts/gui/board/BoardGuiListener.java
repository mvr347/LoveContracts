package me.lovelace.lovecontracts.gui.board;

import me.lovelace.lovecontracts.LoveContracts;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One listener for every contract menu. Every click and drag is cancelled in the top inventory and in the player's own
 * one (shift-click, number keys and double-click gathering move items across the two): the menus take no items, so
 * only their explicit handlers act. Menus are recognised by their holder type, never by title.
 */
public final class BoardGuiListener implements Listener {

    private static final long DEBOUNCE_MILLIS = 300L;

    private final LoveContracts plugin;
    /** The last click of a player and the menu it landed in: a new window right after a click must not eat the next one. */
    private record Last(BoardMenu menu, long at) {
    }

    private final Map<UUID, Last> lastClick = new ConcurrentHashMap<>();

    public BoardGuiListener(LoveContracts plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BoardMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;

        long now = System.currentTimeMillis();
        Last last = lastClick.put(player.getUniqueId(), new Last(menu, now));
        if (last != null && last.menu() == menu && now - last.at() < DEBOUNCE_MILLIS) return;
        menu.handleClick(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BoardMenu) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BoardMenu menu) {
            menu.onClose();
        }
    }

    public void cleanupPlayer(UUID uuid) {
        if (uuid != null) lastClick.remove(uuid);
    }
}
