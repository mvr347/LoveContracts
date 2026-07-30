package me.lovelace.lovecontracts.condition;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.model.Contract;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;

public class KillEntityCondition extends ContractCondition {

    private final EntityType entityType;
    private final int required;

    public KillEntityCondition(Contract contract, EntityType entityType, int required) {
        super(contract);
        this.entityType = entityType;
        this.required = Math.max(1, required);
    }

    @Override
    public boolean isCompleted(Player player) {
        return getProgress(player) >= required;
    }

    @Override
    public String getProgressString(Player player) {
        return getProgress(player) + "/" + required;
    }

    @Override
    public int getRequiredAmount() {
        return required;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !killer.isOnline()) {
            return;
        }

        if (!isRegistered(killer)) {
            return;
        }

        if (event.getEntityType() != entityType) {
            return;
        }

        incrementProgress(killer);

        if (isCompleted(killer)) {
            LoveContracts.getInstance().getContractManager().completeContract(killer, contract);
        }
    }
}
