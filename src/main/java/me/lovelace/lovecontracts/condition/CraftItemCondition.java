package me.lovelace.lovecontracts.condition;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.model.Contract;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

public class CraftItemCondition extends ContractCondition {

    private final Material material;
    private final int required;

    public CraftItemCondition(Contract contract, Material material, int required) {
        super(contract);
        this.material = material;
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
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.isOnline() || !isRegistered(player)) {
            return;
        }

        ItemStack result = event.getRecipe().getResult();
        if (result.getType() != material) {
            return;
        }

        int amount = result.getAmount();
        if (event.isShiftClick()) {
            amount = Math.max(1, amount);
        }

        incrementProgress(player, amount);

        if (isCompleted(player)) {
            LoveContracts.getInstance().getContractManager().completeContract(player, contract);
        }
    }
}
