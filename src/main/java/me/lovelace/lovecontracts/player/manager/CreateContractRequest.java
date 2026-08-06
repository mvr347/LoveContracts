package me.lovelace.lovecontracts.player.manager;

import me.lovelace.lovecontracts.player.model.PlayerContractObjectiveType;
import me.lovelace.lovecontracts.player.model.PlayerContractVisibility;

public record CreateContractRequest(
        String description,
        PlayerContractObjectiveType type,
        String target,
        int amount,
        long goldReward,
        int reputationHint,
        PlayerContractVisibility visibility,
        int deadlineHours
) {
}
