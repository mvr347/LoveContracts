package me.lovelace.lovecontracts.player.manager;

import me.lovelace.lovecontracts.player.model.PlayerContractObjectiveType;
import me.lovelace.lovecontracts.player.model.PlayerContractVisibility;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public record CreateContractRequest(
        String description,
        PlayerContractObjectiveType type,
        String target,
        int amount,
        long goldReward,
        List<ItemStack> itemRewards,
        int reputationHint,
        PlayerContractVisibility visibility,
        int deadlineHours
) {
}
