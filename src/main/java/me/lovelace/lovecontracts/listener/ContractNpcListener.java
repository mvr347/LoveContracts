package me.lovelace.lovecontracts.listener;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.integration.CitizensIntegration;
import me.lovelace.lovecontracts.model.Contract;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class ContractNpcListener implements Listener {

    private final LoveContracts plugin;
    private final CitizensIntegration citizens;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ContractNpcListener(LoveContracts plugin, CitizensIntegration citizens) {
        this.plugin = plugin;
        this.citizens = citizens;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcRightClick(PlayerInteractEntityEvent event) {
        if (!citizens.isAvailable())
            return;

        Integer npcId = citizens.npcId(event.getRightClicked());
        if (npcId == null)
            return;

        int boundId = plugin.getConfig().getInt("npc.id", -1);
        if (boundId < 0 || npcId != boundId)
            return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        Contract active = plugin.getContractManager().getActiveContract(player.getUniqueId());

        if (active != null) {
            boolean isCompleted = active.getCondition() != null && active.getCondition().isCompleted(player);
            if (isCompleted) {
                plugin.getContractManager().completeContract(player, active);
                player.sendMessage(mm.deserialize(
                        "<green>✔ Вы отлично справились! Контракт успешно сдан NPC, награда получена.</green>"));
            } else {
                plugin.getContractManager().cancelContract(player, active);
                player.sendMessage(mm.deserialize("<yellow>Контракт отменен по вашему запросу у NPC.</yellow>"));
            }
        } else {
            plugin.getContractGUI().open(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcLeftClick(EntityDamageByEntityEvent event) {
        if (!citizens.isAvailable())
            return;
        if (!(event.getDamager() instanceof Player player))
            return;

        Integer npcId = citizens.npcId(event.getEntity());
        if (npcId == null)
            return;

        int boundId = plugin.getConfig().getInt("npc.id", -1);
        if (boundId < 0 || npcId != boundId)
            return;

        event.setCancelled(true);
        // Left Click -> Open main contracts board
        plugin.getContractGUI().open(player);
    }
}
