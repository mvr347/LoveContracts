package me.lovelace.lovecontracts.gui.board;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.gui.ContractLore;
import me.lovelace.lovecontracts.gui.LoreBlocks;
import me.lovelace.lovecontracts.gui.LoreBlocks.Click;
import me.lovelace.lovecontracts.gui.LoreBlocks.Hint;
import me.lovelace.lovecontracts.manager.MessageManager;
import me.lovelace.lovecontracts.npc.manager.NpcQuestManager;
import me.lovelace.lovecontracts.npc.model.ChainView;
import me.lovelace.lovecontracts.npc.model.NpcQuest;
import me.lovelace.lovecontracts.npc.model.NpcQuestStep;
import me.lovelace.lovecontracts.npc.model.ChainStatus;
import me.lovelace.lovecontracts.npc.model.QuestTerms;
import me.lovelace.lovecontracts.npc.model.QuestTier;
import me.lovelace.lovecontracts.npc.reward.QuestSettlement;
import me.lovelace.lovecontracts.textures.HeadTextures;
import me.lovelace.lovecontracts.util.CoinFormat;
import me.lovelace.lovecontracts.util.HeadUtil;
import me.lovelace.lovecontracts.util.LoreBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Cards of the NPC chains tab: one card per chain, whatever state the player is in for it. */
final class ChainCards {

    /** Width of a summary row in characters; longer text wraps so the tooltip stays narrow. */
    static final int SUMMARY_WIDTH = 38;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final LoveContracts plugin;

    ChainCards(LoveContracts plugin) {
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

    private String text(String key, Map<String, String> placeholders) {
        return messages().text(key, placeholders);
    }

    /** Dynamic text (names, descriptions from the yml files) must not be read as MiniMessage. */
    private static String safe(String value) {
        return MM.escapeTags(value == null ? "" : value);
    }

    /** What a click on this chain's card does; the board turns it into the real action. */
    enum Kind { TAKE, ACTIVE, BLOCKED }

    static Kind kindOf(ChainView view, boolean atLimit) {
        return switch (view.status()) {
            case AVAILABLE -> atLimit ? Kind.BLOCKED : Kind.TAKE;
            case ACTIVE -> Kind.ACTIVE;
            default -> Kind.BLOCKED;
        };
    }

    ItemStack card(Player player, ChainView view, boolean atLimit) {
        NpcQuest quest = view.quest();
        QuestTerms terms = quest.terms();
        boolean active = view.status() == ChainStatus.ACTIVE;

        // Short card: what it is, how hard, the story in a few words, the time and the money. No step list, no giver.
        List<String> lines = new ArrayList<>();
        lines.add(text("gui.quest-subtitle"));
        lines.add("");
        lines.add(text("gui.quest-tier", Map.of("tier", tierName(terms.tier()))));
        if (!terms.summary().isBlank()) {
            lines.add("");
            for (String row : wrap(terms.summary(), SUMMARY_WIDTH)) lines.add(text("gui.quest-summary", Map.of("text", safe(row))));
        }
        lines.add("");
        if (active) {
            addCurrentGoal(lines, player, view);
        }
        if (terms.timed()) {
            if (active && view.timeLeftMillis() >= 0) {
                lines.add(text("gui.quest-time-left", Map.of("time", DurationText.format(view.timeLeftMillis()))));
            } else if (!active) {
                lines.add(text("gui.quest-time", Map.of("time", DurationText.format(terms.timeLimitMillis()))));
            }
        }
        lines.add(text("gui.section-reward"));
        lines.addAll(ContractLore.coinRows(ContractLore.rewardLines(quest.getRewards())));
        long base = quest.baseReward();
        long maximum = terms.rewardTiers().maximum(base, terms.timed());
        if (!active && maximum > base) {
            lines.add(text("gui.quest-reward-max", Map.of("amount", CoinFormat.format(maximum))));
        }
        long advance = active ? view.advancePaid() : QuestSettlement.advance(base, terms.advancePercent());
        if (advance > 0) {
            lines.add(text(active ? "gui.quest-advance-paid" : "gui.quest-advance", Map.of("amount", CoinFormat.format(advance))));
        }

        String name = quest.getDisplayName();
        String base64;
        switch (view.status()) {
            case ACTIVE -> {
                base64 = HeadUtil.getHeadTexture("active-quest", HeadTextures.STARTER_QUEST);
                name = text("gui.item-name-state", Map.of("name", quest.getDisplayName(), "state", text("gui.chain-state-active")));
                lines.add("");
                lines.add(text("gui.contract-status-in-progress"));
                lines.addAll(LoreBlocks.clicks(style(),
                        Hint.of(Click.LEFT, text("gui.action-chain-continue")),
                        Hint.of(Click.SHIFT_RIGHT, text("gui.action-abandon-chain"))));
            }
            case AVAILABLE -> {
                base64 = HeadUtil.getHeadTexture("chain", HeadTextures.DIFFICULTY_MEDIUM);
                if (atLimit) {
                    base64 = HeadUtil.getHeadTexture("locked", HeadTextures.LOCKED);
                    name = LoreBlocks.disabledName(style(), quest.getDisplayName());
                    lines.addAll(LoreBlocks.blocked(style(), LoreBlocks.status(style(), "locked"),
                            text("gui.chain-reason-limit", Map.of("max", String.valueOf(NpcQuestManager.MAX_ACTIVE_CHAINS))),
                            Hint.of(Click.LEFT, text("gui.action-take-chain"))));
                } else {
                    lines.addAll(LoreBlocks.clicks(style(),
                            Hint.of(Click.LEFT, text("gui.action-take-chain")),
                            Hint.of(Click.RIGHT, text("gui.action-chain-talk"))));
                }
            }
            case COOLDOWN -> {
                base64 = HeadUtil.getHeadTexture("completed", HeadTextures.CONTRACT_STATUS_DEFAULT);
                name = text("gui.item-name-state", Map.of("name", quest.getDisplayName(), "state", text("gui.chain-state-cooldown")));
                lines.addAll(LoreBlocks.blocked(style(), LoreBlocks.status(style(), "cooldown"),
                        text("gui.chain-reason-cooldown", Map.of("time", DurationText.format(view.cooldownLeftMillis()))),
                        Hint.of(Click.LEFT, text("gui.action-take-chain"))));
            }
            case DONE -> {
                base64 = HeadUtil.getHeadTexture("completed", HeadTextures.CONTRACT_STATUS_DEFAULT);
                name = text("gui.item-name-state", Map.of("name", quest.getDisplayName(), "state", text("gui.chain-state-done")));
                lines.add("");
                lines.add(text("gui.contract-status-completed"));
            }
            default -> {
                base64 = HeadUtil.getHeadTexture("locked", HeadTextures.LOCKED);
                name = LoreBlocks.disabledName(style(), quest.getDisplayName());
                lines.addAll(LoreBlocks.blocked(style(), LoreBlocks.status(style(), "locked"), lockReason(view),
                        Hint.of(Click.LEFT, text("gui.action-take-chain"))));
            }
        }
        return HeadUtil.createBase64Head(base64, name, components(player, lines));
    }

    private String tierName(QuestTier tier) {
        return text(switch (tier) {
            case EASY -> "gui.tier-easy";
            case MEDIUM -> "gui.tier-medium";
            case HARD -> "gui.tier-hard";
        });
    }

    /** The current step of a running quest: where to go and what to do, with a counter for counted steps. */
    private void addCurrentGoal(List<String> lines, Player player, ChainView view) {
        NpcQuestStep step = view.quest().getStep(view.currentStep());
        if (step == null) return;
        String goal = step.getDescription().isBlank() ? step.getType().name() : step.getDescription();
        lines.add(text("gui.quest-goal", Map.of("goal", safe(goal))));
        NpcQuestManager.Whereabouts spot = plugin.getNpcQuestManager().whereabouts(player, view.quest());
        if (!spot.name().isBlank()) lines.add(text("gui.chain-now-npc", Map.of("name", safe(spot.name()))));
        if (!spot.where().isBlank()) lines.add(text("gui.chain-where", Map.of("where", safe(spot.where()))));
        addProgress(lines, view);
        lines.add("");
    }

    /** Word wrap by characters; a card row must stay short enough for a tooltip. */
    static List<String> wrap(String text, int width) {
        List<String> rows = new ArrayList<>();
        StringBuilder row = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            if (row.length() > 0 && row.length() + 1 + word.length() > width) {
                rows.add(row.toString());
                row.setLength(0);
            }
            if (row.length() > 0) row.append(' ');
            row.append(word);
        }
        if (row.length() > 0) rows.add(row.toString());
        return rows;
    }

    private String lockReason(ChainView view) {
        return switch (view.lockReason()) {
            case DISABLED -> text("gui.chain-reason-disabled");
            case ACHIEVEMENT -> text("gui.chain-reason-achievement");
            case REQUIREMENTS -> text("gui.chain-reason-requirements");
            case NONE -> text("gui.chain-reason-loading");
        };
    }

    /** Counter and bar of a counted step (kill 15, craft 16); a talk or visit step has nothing to count. */
    private void addProgress(List<String> lines, ChainView view) {
        NpcQuestStep step = view.quest().getStep(view.currentStep());
        if (step == null || step.getCount() <= 1) return;
        lines.add("");
        lines.add(text("gui.section-progress"));
        lines.add(text("gui.contract-progress", Map.of(
                "current", String.valueOf(view.stepProgress()), "required", String.valueOf(step.getCount()))));
        lines.add(text("gui.progress-bar", Map.of("bar", LoreBar.bar(view.stepProgress(), step.getCount()))));
    }

    private static List<Component> components(Player player, List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(CoinFormat.component(player, line));
        return out;
    }
}
