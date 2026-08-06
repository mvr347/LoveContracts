package me.lovelace.lovecontracts.condition;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.model.Contract;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;

public class MineMaterialCondition extends ContractCondition {

    private final Material material;
    private final int required;

    public MineMaterialCondition(Contract contract, Material material, int required) {
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
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!player.isOnline() || !isRegistered(player)) {
            return;
        }

        Material broken = event.getBlock().getType();
        if (broken != material && !isDeepslateVariant(broken)) {
            return;
        }

        incrementProgress(player);

        if (isCompleted(player)) {
            LoveContracts.getInstance().getContractManager().completeContract(player, contract);
        }
    }

    private boolean isDeepslateVariant(Material broken) {
        if (material == Material.DIAMOND_ORE && broken == Material.DEEPSLATE_DIAMOND_ORE) return true;
        if (material == Material.IRON_ORE && broken == Material.DEEPSLATE_IRON_ORE) return true;
        if (material == Material.GOLD_ORE && (broken == Material.DEEPSLATE_GOLD_ORE || broken == Material.NETHER_GOLD_ORE)) return true;
        if (material == Material.COAL_ORE && broken == Material.DEEPSLATE_COAL_ORE) return true;
        if (material == Material.LAPIS_ORE && broken == Material.DEEPSLATE_LAPIS_ORE) return true;
        if (material == Material.REDSTONE_ORE && broken == Material.DEEPSLATE_REDSTONE_ORE) return true;
        if (material == Material.COPPER_ORE && broken == Material.DEEPSLATE_COPPER_ORE) return true;
        if (material == Material.EMERALD_ORE && broken == Material.DEEPSLATE_EMERALD_ORE) return true;
        if (material == Material.QUARTZ && broken == Material.NETHER_QUARTZ_ORE) return true;
        if (material == Material.NETHERITE_SCRAP && broken == Material.ANCIENT_DEBRIS) return true;
        return false;
    }
}
