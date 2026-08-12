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
        if (isAuthenticated(event.getPlayer())) {
            manager.deliverPendingPayouts(event.getPlayer());
        }
    }

    @EventHandler
    public void onAuthenticated(dev.lovelace.lovecore.api.auth.PlayerAuthenticatedEvent event) {
        manager.deliverPendingPayouts(event.player());
    }

    /**
     * Не кэшируем Optional<AuthOracle> — сосед может зарегистрировать реализацию позже,
     * см. LoveCore.service(...) javadoc в LoveCore. Если LoveAuth не установлен, выплаты
     * доставляются сразу на join, как и раньше.
     */
    private boolean isAuthenticated(Player player) {
        return dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.auth.AuthOracle.class)
                .map(oracle -> oracle.isAuthenticated(player.getUniqueId()))
                .orElse(true);
    }
}
