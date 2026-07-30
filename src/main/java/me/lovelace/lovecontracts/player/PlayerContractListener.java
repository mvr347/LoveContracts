package me.lovelace.lovecontracts.player;

import me.lovelace.lovecontracts.player.manager.PlayerContractManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Прогресс контрактов типа KILL_ENTITY и доставка отложенных выплат при входе в сеть.
 * DELIVER_ITEM и CUSTOM не слушаются здесь — они сдаются явно через команды
 * {@code /pcontract turnin} / {@code /pcontract submit}.
 */
public class PlayerContractListener implements Listener {

    private final PlayerContractManager manager;

    public PlayerContractListener(PlayerContractManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        manager.onEntityKilled(killer, event.getEntityType());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        manager.deliverPendingPayouts(event.getPlayer());
    }
}
