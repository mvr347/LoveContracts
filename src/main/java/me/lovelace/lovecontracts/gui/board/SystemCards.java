package me.lovelace.lovecontracts.gui.board;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.condition.ContractCondition;
import me.lovelace.lovecontracts.gui.BoardModes.FilterMode;
import me.lovelace.lovecontracts.gui.BoardModes.SortMode;
import me.lovelace.lovecontracts.gui.ContractLore;
import me.lovelace.lovecontracts.gui.LoreBlocks;
import me.lovelace.lovecontracts.gui.LoreBlocks.Click;
import me.lovelace.lovecontracts.gui.LoreBlocks.Hint;
import me.lovelace.lovecontracts.manager.MessageManager;
import me.lovelace.lovecontracts.model.Contract;
import me.lovelace.lovecontracts.model.Difficulty;
import me.lovelace.lovecontracts.textures.HeadTextures;
import me.lovelace.lovecontracts.util.CoinFormat;
import me.lovelace.lovecontracts.util.HeadUtil;
import me.lovelace.lovecontracts.util.LoreBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cards and list logic of the daily server contracts (the "system" tab). It is the former {@code ContractGUI} card
 * code: the texts are messages.yml {@code gui.*} keys, the click lines come from {@link LoreBlocks}.
 */
final class SystemCards {

    private final LoveContracts plugin;

    SystemCards(LoveContracts plugin) {
        this.plugin = plugin;
    }

    private MessageManager messages() {
        return plugin.getMessageManager();
    }

    private LoreBlocks.Style style() {
        return messages()::getRaw;
    }

    private String text(String key) {
        return messages().text(key);
    }

    private String action(String key) {
        return messages().text(key);
    }

    private static List<Component> components(Player player, List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(CoinFormat.component(player, line));
        return out;
    }

    private ItemStack stack(Player player, String base64, String name, List<String> lines) {
        return HeadUtil.createBase64Head(base64, name, components(player, lines));
    }

    // ------------------------------------------------------------------ list: filter and sort

    List<Contract> apply(List<Contract> contracts, Player player, FilterMode filter, SortMode sort) {
        List<Contract> result = new ArrayList<>(contracts);
        UUID uuid = player.getUniqueId();
        Set<String> activeIds = plugin.getContractManager().getActiveContractIds(uuid);
        Set<String> completedIds = plugin.getContractManager().getCompletedTodayContractIds(uuid);
        Set<String> failedIds = plugin.getContractManager().getFailedContractIds(uuid);

        switch (filter) {
            case EASY -> result.removeIf(c -> c.getDifficulty() != Difficulty.EASY && c.getDifficulty() != Difficulty.STARTER);
            case MEDIUM -> result.removeIf(c -> c.getDifficulty() != Difficulty.MEDIUM);
            case HARD -> result.removeIf(c -> c.getDifficulty() != Difficulty.HARD);
            case AVAILABLE -> result.removeIf(c -> activeIds.contains(c.getId()) || completedIds.contains(c.getId()) || failedIds.contains(c.getId()));
            case ACCEPTED -> result.removeIf(c -> !activeIds.contains(c.getId()));
            case COMPLETED -> result.removeIf(c -> !completedIds.contains(c.getId()));
            case FAILED -> result.removeIf(c -> !failedIds.contains(c.getId()));
            case ALL -> {
            }
        }

        // Status rank: 1 available, 2 taken, 3 completed, 4 failed
        Comparator<Contract> status = Comparator.comparingInt(c -> statusRank(c, activeIds, completedIds, failedIds));
        switch (sort) {
            case ALL -> result.sort(status);
            case REWARD_HIGH -> result.sort(status.thenComparing((c1, c2) -> Double.compare(rewardValue(c2), rewardValue(c1))));
            case REWARD_LOW -> result.sort(status.thenComparing(Comparator.comparingDouble(SystemCards::rewardValue)));
            case DIFFICULTY -> result.sort(status.thenComparingInt(c -> c.getDifficulty().ordinal()));
        }
        return result;
    }

    private static int statusRank(Contract c, Set<String> active, Set<String> completed, Set<String> failed) {
        String id = c.getId();
        if (active.contains(id)) return 2;
        if (completed.contains(id)) return 3;
        if (failed.contains(id)) return 4;
        return 1;
    }

    private static double rewardValue(Contract c) {
        if (c == null || c.getRewards() == null) return 0.0;
        double total = 0.0;
        for (me.lovelace.lovecontracts.model.Reward r : c.getRewards()) total += r.getAmount();
        return total;
    }

    // ------------------------------------------------------------------ shared lore pieces

    private String description(Contract c) {
        return "<gray>" + c.getDescription() + "</gray>";
    }

    /** Name of a contract with its state: {@code Имя | Состояние}. */
    private String stateName(Contract c, String stateKey) {
        return messages().text("gui.item-name-state", Map.of("name", c.getDisplayName(), "state", text(stateKey)));
    }

    private void addConditions(List<String> lines, Contract c, boolean withDuration) {
        lines.add(text("gui.section-conditions"));
        lines.add(messages().text("gui.contract-difficulty", Map.of("difficulty", c.getDifficulty().getFormattedTag())));
        if (withDuration) {
            lines.add(messages().text("gui.contract-duration", Map.of("duration", c.getFormattedDuration())));
        }
    }

    private static long[] progressOf(Player player, Contract c) {
        ContractCondition condition = c.getCondition();
        if (condition == null) return new long[]{0, 0};
        return new long[]{condition.getProgress(player), condition.getRequiredAmount()};
    }

    /** Lore of a taken contract: progress bar, reward, state, clicks. */
    private List<String> activeLines(Player player, Contract c, boolean done, String subtitleKey) {
        long[] progress = progressOf(player, c);
        List<String> lines = new ArrayList<>();
        lines.add(text(subtitleKey));
        lines.add("");
        lines.add(description(c));
        lines.add("");
        addConditions(lines, c, true);
        lines.add("");
        lines.add(text("gui.section-progress"));
        lines.add(messages().text("gui.contract-progress", Map.of(
                "current", String.valueOf(progress[0]), "required", String.valueOf(progress[1]))));
        lines.add(messages().text("gui.progress-bar", Map.of("bar", LoreBar.bar(progress[0], progress[1]))));
        lines.add("");
        lines.add(text("gui.section-reward"));
        lines.addAll(ContractLore.coinRows(ContractLore.rewardLines(c)));
        lines.add("");
        lines.add(text(done ? "gui.contract-status-ready" : "gui.contract-status-in-progress"));
        if (done) {
            lines.addAll(LoreBlocks.clicks(style(),
                    Hint.of(Click.LEFT, action("gui.action-complete")),
                    Hint.of(Click.RIGHT, action("gui.action-cancel"))));
        } else {
            lines.addAll(LoreBlocks.clicks(style(), Hint.of(Click.RIGHT, action("gui.action-cancel"))));
        }
        return lines;
    }

    // ------------------------------------------------------------------ cards

    /** The footer button of the current contract. */
    ItemStack currentContractButton(Player player, Contract c) {
        String base64 = HeadUtil.getHeadTexture("active-quest", HeadTextures.STARTER_QUEST);
        boolean done = c.getCondition() != null && c.getCondition().isCompleted(player);
        return stack(player, base64,
                stateName(c, done ? "gui.contract-state-ready" : "gui.contract-state-in-progress"),
                activeLines(player, c, done, "gui.current-contract"));
    }

    /** A contract card in whatever state this player is in for it. */
    ItemStack contractItem(Contract c, Player player) {
        UUID uuid = player.getUniqueId();
        boolean completedToday = plugin.getContractManager().getCompletedTodayContractIds(uuid).contains(c.getId());
        boolean failed = plugin.getContractManager().getFailedContractIds(uuid).contains(c.getId());
        boolean isThisActive = plugin.getContractManager().getActiveContractIds(uuid).contains(c.getId());
        boolean hasActive = plugin.getContractManager().hasActiveContract(uuid);

        if (completedToday) {
            String base64 = HeadUtil.getHeadTexture("completed",
                    HeadUtil.getHeadTexture("completed-contract", HeadTextures.CONTRACT_STATUS_DEFAULT));
            List<String> lines = new ArrayList<>();
            lines.add(text("gui.contract-subtitle"));
            lines.add("");
            lines.add(description(c));
            lines.add("");
            addConditions(lines, c, false);
            lines.add("");
            lines.add(text("gui.section-reward"));
            lines.addAll(ContractLore.coinRows(ContractLore.rewardLines(c)));
            lines.add("");
            lines.add(text("gui.contract-status-completed"));
            return stack(player, base64, stateName(c, "gui.contract-state-completed"), lines);
        }

        if (failed) {
            String base64 = HeadUtil.getHeadTexture("failed",
                    HeadUtil.getHeadTexture("failed-contract", HeadTextures.CONTRACT_STATUS_DEFAULT));
            List<String> lines = new ArrayList<>();
            lines.add(text("gui.contract-subtitle"));
            lines.add("");
            lines.add(description(c));
            lines.add("");
            addConditions(lines, c, false);
            lines.add("");
            lines.add(text("gui.contract-status-failed"));
            return stack(player, base64, stateName(c, "gui.contract-state-failed"), lines);
        }

        if (isThisActive) {
            String base64 = HeadUtil.getHeadTexture("active",
                    HeadUtil.getHeadTexture("active-quest", HeadTextures.STARTER_QUEST));
            boolean done = c.getCondition() != null && c.getCondition().isCompleted(player);
            return stack(player, base64,
                    stateName(c, done ? "gui.contract-state-ready" : "gui.contract-state-in-progress"),
                    activeLines(player, c, done, "gui.contract-subtitle"));
        }

        if (hasActive) {
            String base64 = HeadUtil.getHeadTexture("locked", HeadTextures.LOCKED);
            List<String> lines = new ArrayList<>();
            lines.add(text("gui.contract-subtitle"));
            lines.add("");
            lines.add(description(c));
            lines.add("");
            addConditions(lines, c, false);
            lines.addAll(LoreBlocks.blocked(style(), text("gui.contract-status-locked"),
                    text("gui.contract-reason-locked"), Hint.of(Click.LEFT, action("gui.action-accept"))));
            return stack(player, base64, LoreBlocks.disabledName(style(), c.getDisplayName()), lines);
        }

        String key = switch (c.getDifficulty()) {
            case STARTER -> "starter";
            case EASY -> "easy";
            case MEDIUM -> "medium";
            case HARD -> "hard";
        };
        String fallback = switch (c.getDifficulty()) {
            case STARTER -> HeadTextures.STARTER_QUEST;
            case EASY -> HeadTextures.DIFFICULTY_EASY;
            case MEDIUM -> HeadTextures.DIFFICULTY_MEDIUM;
            case HARD -> HeadTextures.DIFFICULTY_HARD;
        };
        String base64 = HeadUtil.getHeadTexture(key, fallback);

        boolean requirementsMet = true;
        int completedCount = 0;
        if (c.getRequirement() != null) {
            completedCount = plugin.getContractManager().getCompletedContractsCount(uuid);
            requirementsMet = c.getRequirement().isMet(player, completedCount);
        }

        List<String> lines = new ArrayList<>();
        lines.add(text("gui.contract-subtitle"));
        lines.add("");
        lines.add(description(c));
        lines.add("");
        addConditions(lines, c, true);
        if (c.getRequirement() != null) {
            lines.add("");
            lines.add(text("gui.section-requirements"));
            lines.addAll(new ContractLore(messages()).requirementRows(player, c.getRequirement(), completedCount));
        }
        lines.add("");
        lines.add(text("gui.section-reward"));
        lines.addAll(ContractLore.coinRows(ContractLore.rewardLines(c)));
        if (ContractLore.hasPenalty(c)) {
            lines.add("");
            lines.add(text("gui.section-penalty"));
            lines.addAll(ContractLore.coinRows(ContractLore.penaltyLines(c)));
        }

        String name;
        if (requirementsMet) {
            lines.addAll(LoreBlocks.clicks(style(), Hint.of(Click.LEFT, action("gui.action-accept"))));
            name = c.getDisplayName();
        } else {
            lines.addAll(LoreBlocks.blocked(style(), LoreBlocks.status(style(), "unavailable"),
                    text("gui.contract-reason-requirements"), Hint.of(Click.LEFT, action("gui.action-accept"))));
            name = LoreBlocks.disabledName(style(), c.getDisplayName());
        }
        return stack(player, base64, name, lines);
    }
}
