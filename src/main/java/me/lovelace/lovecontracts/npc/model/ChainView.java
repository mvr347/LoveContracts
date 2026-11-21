package me.lovelace.lovecontracts.npc.model;

/**
 * One chain as a player sees it on the board.
 *
 * @param currentStep         index of the current step of an {@link ChainStatus#ACTIVE} chain, otherwise 0
 * @param stepProgress        counted progress of that step
 * @param cooldownLeftMillis  time until a {@link ChainStatus#COOLDOWN} chain can be taken again, otherwise 0
 */
public record ChainView(NpcQuest quest, ChainStatus status, int currentStep, int stepProgress,
                        long cooldownLeftMillis, LockReason lockReason) {
}
