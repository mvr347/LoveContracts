package me.lovelace.lovecontracts.gui.board;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.gui.LoreBlocks;
import me.lovelace.lovecontracts.gui.LoreBlocks.Click;
import me.lovelace.lovecontracts.gui.LoreBlocks.Hint;
import me.lovelace.lovecontracts.manager.MessageManager;
import me.lovelace.lovecontracts.model.Difficulty;
import me.lovelace.lovecontracts.model.Reward;
import me.lovelace.lovecontracts.npc.manager.NpcQuestManager;
import me.lovelace.lovecontracts.npc.model.ChainStatus;
import me.lovelace.lovecontracts.npc.model.ChainView;
import me.lovelace.lovecontracts.npc.model.NpcQuest;
import me.lovelace.lovecontracts.npc.model.NpcQuestStep;
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

/** Short, clear cards of the unified quests board (gui-gen-6 compliant). */
final class ChainCards {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int SUMMARY_WRAP = 40;

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
        NpcQuestManager manager = plugin.getNpcQuestManager();

        List<String> lines = new ArrayList<>();
        String tierLabel = tierName(quest.getTier());
        if (quest.getTimeLimitMinutes() > 0) {
            lines.add(text("gui.quest-timed-subtitle", Map.of(
                    "tier", tierLabel,
                    "time", DurationText.format(quest.getTimeLimitMinutes() * 60_000L))));
        } else {
            lines.add(text("gui.quest-subtitle", Map.of("tier", tierLabel)));
        }
        lines.add("");

        String summary = quest.getSummary();
        if (summary.isBlank() && quest.TotalSteps() > 0) {
            summary = quest.getStep(0).getDescription();
        }
        for (String wrapped : wrap(summary, SUMMARY_WRAP)) {
            lines.add(text("gui.quest-summary-line", Map.of("line", safe(wrapped))));
        }

        if (view.status() == ChainStatus.ACTIVE) {
            NpcQuestStep current = quest.getStep(view.currentStep());
            if (current != null) {
                lines.add("");
                lines.add(text("gui.quest-goal", Map.of("goal", safe(current.getDescription()))));
                addProgress(lines, view);
            }
            if (quest.getTimeLimitMinutes() > 0) {
                long left = manager.timeLeftMillis(player, quest);
                lines.add(text("gui.quest-time-left", Map.of("time", DurationText.format(left))));
            }
        }

        lines.add("");
        long baseReward = Math.round(quest.getRewards().stream().mapToDouble(Reward::getAmount).sum());
        double maxMult = plugin.getConfig().getDouble("economy.reward-tiers.max-multiplier", 1.5);
        long maxReward = quest.getRewardTiers().maxReward(baseReward, maxMult);
        if (maxReward > baseReward) {
            lines.add(text("gui.quest-reward-variable", Map.of(
                    "base", CoinFormat.format(baseReward),
                    "max", CoinFormat.format(maxReward))));
        } else {
            lines.add(text("gui.quest-reward-base", Map.of("base", CoinFormat.format(baseReward))));
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
                base64 = tierTexture(quest.getTier());
                if (atLimit) {
                    base64 = HeadUtil.getHeadTexture("locked", HeadTextures.LOCKED);
                    name = LoreBlocks.disabledName(style(), quest.getDisplayName());
                    lines.addAll(LoreBlocks.blocked(style(), LoreBlocks.status(style(), "locked"),
                            text("gui.chain-reason-limit", Map.of("max", String.valueOf(NpcQuestManager.maxActive()))),
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

    private String tierName(Difficulty tier) {
        return switch (tier) {
            case STARTER, EASY -> text("gui.quest-tier-easy");
            case MEDIUM -> text("gui.quest-tier-medium");
            case HARD -> text("gui.quest-tier-hard");
        };
    }

    private String tierTexture(Difficulty tier) {
        return switch (tier) {
            case STARTER, EASY -> HeadUtil.getHeadTexture("easy", HeadTextures.DIFFICULTY_EASY);
            case MEDIUM -> HeadUtil.getHeadTexture("medium", HeadTextures.DIFFICULTY_MEDIUM);
            case HARD -> HeadUtil.getHeadTexture("hard", HeadTextures.DIFFICULTY_HARD);
        };
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
        lines.add(text("gui.contract-progress", Map.of(
                "current", String.valueOf(view.stepProgress()), "required", String.valueOf(step.getCount()))));
        lines.add(text("gui.progress-bar", Map.of("bar", LoreBar.bar(view.stepProgress(), step.getCount()))));
    }

    static List<String> wrap(String text, int maxWidth) {
        if (text == null || text.isBlank()) return List.of();
        String remaining = text.replaceAll("\\s+", " ").trim();
        List<String> out = new ArrayList<>();
        while (!remaining.isEmpty()) {
            int len = remaining.codePointCount(0, remaining.length());
            if (len <= maxWidth) {
                out.add(remaining);
                break;
            }
            int end = remaining.offsetByCodePoints(0, maxWidth);
            int space = remaining.lastIndexOf(' ', end);
            if (space > 8) end = space;
            out.add(remaining.substring(0, end).trim());
            remaining = remaining.substring(end).stripLeading();
        }
        return out;
    }

    private static List<Component> components(Player player, List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(CoinFormat.component(player, line));
        return out;
    }
}
