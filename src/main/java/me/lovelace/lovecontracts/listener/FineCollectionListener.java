package me.lovelace.lovecontracts.listener;

import me.lovelace.lovecontracts.LoveContracts;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Реактивный слушатель событий инвентаря игрока.
 * Немедленно взыскивает долги по штрафам при появлении или перемещении монет в инвентаре.
 * Без таймеров и нагружающего поллинга — только O(1) проверка в памяти при событиях.
 */
public class FineCollectionListener implements Listener {

    private final LoveContracts plugin;

    public FineCollectionListener(LoveContracts plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            checkAndCollect(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            checkAndCollect(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            checkAndCollect(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        checkAndCollect(event.getPlayer());
    }

    private void checkAndCollect(Player player) {
        if (player == null || plugin.getFineManager() == null) return;
        if (!plugin.getFineManager().hasDebt(player.getUniqueId())) return;
        plugin.getFineManager().collectDebt(player);
    }
}
