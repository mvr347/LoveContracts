package me.lovelace.lovecontracts.npc.model;

/** Why a chain is {@link ChainStatus#LOCKED}. */
public enum LockReason {
    NONE,
    /** Contracts are switched off for this player by an admin. */
    DISABLED,
    /** The chain needs an achievement (usually the end of an earlier chain) that the player does not have. */
    ACHIEVEMENT,
    /** Level, permission or finished-contracts requirements are not met. */
    REQUIREMENTS
}
