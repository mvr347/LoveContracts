package me.lovelace.lovecontracts.condition;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.model.Contract;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerFishEvent;

public class CatchFishCondition extends ContractCondition {

    private final int required;

    public CatchFishCondition(Contract contract, int required) {
        super(contract);
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isOnline() || !isRegistered(player)) {
            return;
        }

        incrementProgress(player);

        if (isCompleted(player)) {
            LoveContracts.getInstance().getContractManager().completeContract(player, contract);
        }
    }
}
