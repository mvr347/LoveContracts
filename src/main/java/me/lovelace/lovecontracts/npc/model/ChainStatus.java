package me.lovelace.lovecontracts.npc.model;

/** Where an NPC chain stands for one player; the board shows it and decides what a click does. */
public enum ChainStatus {
    /** The player's chain history is still being read from the database. */
    LOADING,
    /** Taken and in progress. */
    ACTIVE,
    /** Can be taken now. */
    AVAILABLE,
    /** A repeatable chain that was finished recently: it can be taken again after the cooldown. */
    COOLDOWN,
    /** A one-time chain that was finished. */
    DONE,
    /** Cannot be taken: see {@link LockReason}. */
    LOCKED
}
