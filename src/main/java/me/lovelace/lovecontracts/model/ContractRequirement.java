package me.lovelace.lovecontracts.model;

import org.bukkit.entity.Player;

public class ContractRequirement {

    private final int minLevel;
    private final String permission;
    private final int minCompletedContracts;

    public ContractRequirement(int minLevel, String permission, int minCompletedContracts) {
        this.minLevel = Math.max(0, minLevel);
        this.permission = permission != null && !permission.isBlank() ? permission : null;
        this.minCompletedContracts = Math.max(0, minCompletedContracts);
    }

    public int getMinLevel() { return minLevel; }
    public String getPermission() { return permission; }
    public int getMinCompletedContracts() { return minCompletedContracts; }

    public boolean isMet(Player player, int playerCompletedCount) {
        if (player == null) return false;
        if (minLevel > 0 && player.getLevel() < minLevel) return false;
        if (permission != null && !player.hasPermission(permission)) return false;
        if (minCompletedContracts > 0 && playerCompletedCount < minCompletedContracts) return false;
        return true;
    }

}
