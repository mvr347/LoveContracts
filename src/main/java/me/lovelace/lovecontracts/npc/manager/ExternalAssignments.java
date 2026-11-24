package me.lovelace.lovecontracts.npc.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Asks LoveHunt whether the player already carries a bounty. Reflection on purpose: neither plugin has a compile
 * dependency on the other, and a missing or changed plugin must never block a quest, so every failure answers "no".
 */
final class ExternalAssignments {

    private ExternalAssignments() {
    }

    static boolean hasBounty(Player player) {
        try {
            Plugin hunt = Bukkit.getPluginManager().getPlugin("LoveHunt");
            if (hunt == null || !hunt.isEnabled()) return false;
            Object service = hunt.getClass().getMethod("bountyService").invoke(hunt);
            if (service == null) return false;
            Object accepted = service.getClass().getMethod("acceptedBy", java.util.UUID.class).invoke(service, player.getUniqueId());
            return accepted instanceof java.util.Collection<?> c && !c.isEmpty();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }
}
