package me.lovelace.lovecontracts.gui;

import me.lovelace.lovecontracts.manager.MessageManager;
import me.lovelace.lovecontracts.model.Contract;
import me.lovelace.lovecontracts.model.ContractRequirement;
import me.lovelace.lovecontracts.model.Penalty;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lore fragments shared by the contract menus (board and accept confirmation): the requirement rows and the coin rows
 * of rewards and penalties. The texts are messages.yml keys; this class only fills their placeholders.
 */
final class ContractLore {

    private final MessageManager messages;

    ContractLore(MessageManager messages) {
        this.messages = messages;
    }

    /** Rows of the requirement section: ✓ when met, ✕ with the numbers when not. Empty without a requirement. */
    List<String> requirementRows(Player player, ContractRequirement requirement, int completedCount) {
        List<String> rows = new ArrayList<>();
        if (requirement == null) {
            return rows;
        }
        if (requirement.getMinLevel() > 0) {
            rows.add(player.getLevel() >= requirement.getMinLevel()
                    ? messages.text("gui.requirement-level-ok", Map.of("min", num(requirement.getMinLevel())))
                    : messages.text("gui.requirement-level-missing", Map.of(
                            "level", num(player.getLevel()), "min", num(requirement.getMinLevel()))));
        }
        if (requirement.getPermission() != null) {
            rows.add(player.hasPermission(requirement.getPermission())
                    ? messages.text("gui.requirement-permission-ok")
                    : messages.text("gui.requirement-permission-missing"));
        }
        if (requirement.getMinCompletedContracts() > 0) {
            int min = requirement.getMinCompletedContracts();
            rows.add(completedCount >= min
                    ? messages.text("gui.requirement-completed-ok", Map.of("min", num(min)))
                    : messages.text("gui.requirement-completed-missing", Map.of(
                            "done", num(completedCount), "min", num(min))));
        }
        return rows;
    }

    /** Coin lines as section rows: two spaces in front. The coin glyph already starts with {@code <white>}. */
    static List<String> coinRows(List<String> coinLines) {
        List<String> rows = new ArrayList<>(coinLines.size());
        for (String line : coinLines) {
            rows.add("  " + line);
        }
        return rows;
    }

    static List<String> rewardLines(Contract contract) {
        return contract.getRewards().stream().flatMap(r -> r.getDisplayLines().stream()).toList();
    }

    /** Penalty lines; a starter contract shows none, and neither does one without a penalty. */
    static List<String> penaltyLines(Contract contract) {
        if (contract.isStarter()) {
            return List.of();
        }
        return contract.getPenalties().stream()
                .filter(p -> p.getType() != Penalty.Type.NONE)
                .flatMap(p -> p.getDisplayLines().stream())
                .toList();
    }

    static boolean hasPenalty(Contract contract) {
        return !penaltyLines(contract).isEmpty();
    }

    private static String num(long value) {
        return String.valueOf(value);
    }
}
