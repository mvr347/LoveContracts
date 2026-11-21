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

    /** Steps listed in a card; a longer chain ends with an «и ещё N» row so the tooltip stays readable. */
    static final int MAX_STEP_ROWS = 8;

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
        NpcQuestManager manager = plugin.getNpcQuestManager();
        NpcQuestManager.Whereabouts spot = manager.whereabouts(player, quest);

        List<String> lines = new ArrayList<>();
        lines.add(text("gui.chain-subtitle"));
        lines.add("");
        lines.add(text("gui.section-conditions"));
        if (!spot.name().isBlank()) {
            lines.add(text(view.status() == me.lovelace.lovecontracts.npc.model.ChainStatus.ACTIVE ? "gui.chain-now-npc" : "gui.chain-giver",
                    Map.of("name", safe(spot.name()))));
        }
        if (!spot.where().isBlank()) {
            lines.add(text("gui.chain-where", Map.of("where", safe(spot.where()))));
        }
        lines.add(text("gui.chain-steps-count", Map.of("count", String.valueOf(quest.TotalSteps()))));
        lines.add(quest.isRepeatable()
                ? text("gui.chain-repeat", Map.of("time", DurationText.format(quest.getCooldownMinutes() * 60_000L)))
                : text("gui.chain-once"));

        lines.add("");
        lines.add(text("gui.section-steps"));
        addSteps(lines, view);

        lines.add("");
        lines.add(text("gui.section-reward"));
        lines.addAll(ContractLore.coinRows(ContractLore.rewardLines(quest.getRewards())));

        String name = quest.getDisplayName();
        String base64;
        switch (view.status()) {
            case ACTIVE -> {
                base64 = HeadUtil.getHeadTexture("active-quest", HeadTextures.STARTER_QUEST);
                name = text("gui.item-name-state", Map.of("name", quest.getDisplayName(), "state", text("gui.chain-state-active")));
                addProgress(lines, view);
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

    private String lockReason(ChainView view) {
        return switch (view.lockReason()) {
            case DISABLED -> text("gui.chain-reason-disabled");
            case ACHIEVEMENT -> text("gui.chain-reason-achievement");
            case REQUIREMENTS -> text("gui.chain-reason-requirements");
            case NONE -> text("gui.chain-reason-loading");
        };
    }

    /** Step rows: done ones green with a check, the current one highlighted, the rest dimmed. */
    private void addSteps(List<String> lines, ChainView view) {
        NpcQuest quest = view.quest();
        boolean active = view.status() == me.lovelace.lovecontracts.npc.model.ChainStatus.ACTIVE;
        int total = quest.TotalSteps();
        int shown = Math.min(total, MAX_STEP_ROWS);
        // keep the current step in view when a long chain is partly done
        int from = active ? Math.max(0, Math.min(view.currentStep() - 1, total - shown)) : 0;
        if (from > 0) lines.add(text("gui.chain-step-more", Map.of("count", String.valueOf(from))));
        for (int i = from; i < from + shown; i++) {
            NpcQuestStep step = quest.getStep(i);
            String description = safe(step.getDescription().isBlank() ? step.getType().name() : step.getDescription());
            String key = !active ? "gui.chain-step-pending"
                    : i < view.currentStep() ? "gui.chain-step-done"
                    : i == view.currentStep() ? "gui.chain-step-current" : "gui.chain-step-pending";
            lines.add(text(key, Map.of("description", description)));
        }
        int hidden = total - from - shown;
        if (hidden > 0) lines.add(text("gui.chain-step-more", Map.of("count", String.valueOf(hidden))));
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
