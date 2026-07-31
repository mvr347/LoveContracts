package me.lovelace.lovecontracts.player.model;

public enum PlayerContractStatus {
    OPEN,
    IN_PROGRESS,
    PENDING_REVIEW,
    COMPLETED,
    ABANDONED,
    CANCELLED,
    EXPIRED;

    public boolean isTerminal() {
        return this == COMPLETED || this == ABANDONED || this == CANCELLED || this == EXPIRED;
    }
}
