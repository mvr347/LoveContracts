package me.lovelace.lovecontracts.npc.builder.gui;

import dev.lovelace.lovecore.api.npc.NpcHandle;
import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.gui.ContractLore;
import me.lovelace.lovecontracts.gui.LoreBlocks;
import me.lovelace.lovecontracts.gui.LoreBlocks.Click;
import me.lovelace.lovecontracts.gui.LoreBlocks.Hint;
import me.lovelace.lovecontracts.gui.board.BoardGui;
import me.lovelace.lovecontracts.gui.board.DurationText;
import me.lovelace.lovecontracts.npc.builder.ChainBuilder;
import me.lovelace.lovecontracts.npc.builder.ChainDraft;
import me.lovelace.lovecontracts.npc.builder.ChainDraftCheck;
import me.lovelace.lovecontracts.npc.builder.StepDraft;
import me.lovelace.lovecontracts.npc.model.NpcQuestStep;
import me.lovelace.lovecontracts.textures.HeadTextures;
import me.lovelace.lovecontracts.util.CoinFormat;
import me.lovelace.lovecontracts.util.HeadUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Edits one chain: the giver, the name, the reward and the repeat rule are the header controls, the steps are the cards of
 * the work zone (the last card adds one). Changes live in the draft until the chain is saved; closing the window drops them.
 */
final class ChainEditorGui extends BoardGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ChainBuilder builder;
    private final ChainDraft draft;
    /** True while the chain is not in chains.yml yet: there is nothing to delete and "save as new" makes no sense. */
    private final boolean fresh;

    /** A card of the work zone: step number {@code index}, or {@code -1} for the "add a step" card. */
    private record Slot(int index) {
        boolean adds() { return index < 0; }
    }

    ChainEditorGui(LoveContracts plugin, Player viewer, ChainDraft draft, boolean fresh) {
        super(plugin, viewer);
        this.builder = plugin.getChainBuilder();
        this.draft = draft;
        this.fresh = fresh;
    }

    @Override
    protected Component title() {
        return MM.deserialize(t("gui.builder-editor-title", Map.of("id", MM.escapeTags(draft.id()))));
    }

    void open() {
        show();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render() {
        frame();
        button(0, head(HeadUtil.getHeadTexture("npc-header", HeadTextures.NPC_HEADER), t("gui.builder-head-name"),
                List.of(t("gui.builder-chain-subtitle"), "", t("gui.builder-head-line1"), t("gui.builder-head-line2"))), null);

        List<Control> controls = new ArrayList<>();
        controls.add(new Control(giverButton(), this::giverClick));
        controls.add(new Control(nameButton(), e -> askText("gui.builder-prompt-name", text ->
                draft.displayName(text.contains("<") ? text : "<gold>" + text + "</gold>"))));
        controls.add(new Control(rewardButton(), e -> askNumber("gui.builder-prompt-reward", 0, 10_000_000L, draft::reward)));
        controls.add(new Control(repeatButton(), this::repeatClick));
        controls.add(new Control(saveButton(), this::saveClick));
        if (!fresh) controls.add(new Control(deleteButton(), e -> confirmDelete()));
        controls(controls);

        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < draft.steps().size(); i++) slots.add(new Slot(i));
        if (draft.canAddStep()) slots.add(new Slot(-1));
        paged(slots, this::card, this::stepClick, "gui.builder-steps-empty");
        footer(() -> new ChainListGui(plugin, viewer).open());
    }

    // ------------------------------------------------------------------ header controls

    private ItemStack giverButton() {
        String now = draft.giverKey() == null ? t("gui.builder-unset") : MM.escapeTags(draft.giverKey());
        List<String> lines = new ArrayList<>();
        lines.add(t("gui.builder-giver-subtitle"));
        lines.add("");
        lines.add(t("gui.builder-now", Map.of("value", now)));
        lines.addAll(LoreBlocks.clicks(messages()::getRaw,
                Hint.of(Click.LEFT, t("gui.action-pick-npc")),
                Hint.of(Click.RIGHT, t("gui.action-type-key"))));
        return head(HeadUtil.getHeadTexture("tab-chains", HeadTextures.PRESET), t("gui.builder-giver"), lines);
    }

    private void giverClick(InventoryClickEvent event) {
        if (event.isRightClick()) {
            askText("gui.builder-prompt-giver", draft::giverKey);
        } else {
            new NpcPickerGui(plugin, viewer, this::redraw, (NpcHandle npc) -> draft.giverKey(npc.key())).open();
        }
    }

    private ItemStack nameButton() {
        List<String> lines = new ArrayList<>();
        lines.add(t("gui.builder-name-subtitle"));
        lines.add("");
        lines.add(t("gui.builder-now", Map.of("value", draft.displayName())));
        lines.addAll(LoreBlocks.clicks(messages()::getRaw, Hint.of(Click.LEFT, t("gui.action-rename"))));
        return head(HeadUtil.getHeadTexture("sort", HeadTextures.SORT), t("gui.builder-name"), lines);
    }

    private ItemStack rewardButton() {
        List<String> lines = new ArrayList<>();
        lines.add(t("gui.builder-reward-subtitle"));
        lines.add("");
        lines.add(t("gui.builder-reward-units"));
        lines.add(t("gui.builder-reward-cap", Map.of("max", String.valueOf(plugin.getConfig().getLong("economy.contracts-v2.max-reward", 240)))));
        lines.add("");
        lines.add(t("gui.section-reward"));
        if (draft.rewardRaw() != null) {
            lines.add("  <white>" + MM.escapeTags(String.valueOf(draft.rewardRaw())));
        } else if (draft.reward() > 0) {
            lines.addAll(ContractLore.coinRows(CoinFormat.formatLines(draft.reward())));
        } else {
            lines.add("  <white>" + t("gui.builder-reward-none"));
        }
        lines.addAll(LoreBlocks.clicks(messages()::getRaw, Hint.of(Click.LEFT, t("gui.action-set-reward"))));
        return head(HeadUtil.getHeadTexture("reward", HeadTextures.REWARD), t("gui.builder-reward"), lines);
    }

    private ItemStack repeatButton() {
        String value = draft.repeatable()
                ? t("gui.builder-repeat-yes", Map.of("time", DurationText.format(draft.cooldownMinutes() * 60_000L)))
                : t("gui.builder-repeat-no");
        List<String> hints = new ArrayList<>();
        List<Hint> clicks = new ArrayList<>();
        clicks.add(Hint.of(Click.LEFT, t("gui.action-toggle-repeat")));
        if (draft.repeatable()) clicks.add(Hint.of(Click.RIGHT, t("gui.action-set-cooldown")));
        List<String> lines = new ArrayList<>();
        lines.add(t("gui.builder-repeat-subtitle"));
        lines.add("");
        lines.add(t("gui.builder-repeat-now", Map.of("value", value)));
        lines.addAll(LoreBlocks.clicks(messages()::getRaw, clicks.toArray(new Hint[0])));
        return head(HeadUtil.getHeadTexture("count", HeadTextures.COUNT), t("gui.builder-repeat"), lines);
    }

    private void repeatClick(InventoryClickEvent event) {
        if (event.isRightClick() && draft.repeatable()) {
            askNumber("gui.builder-prompt-cooldown", 0, 525_600, v -> draft.cooldownMinutes((int) v));
        } else if (event.isLeftClick()) {
            draft.repeatable(!draft.repeatable());
            render();
        }
    }

    private ItemStack saveButton() {
        List<String> problems = ChainDraftCheck.problems(draft);
        List<String> lines = new ArrayList<>();
        lines.add(t("gui.builder-save-subtitle"));
        lines.add("");
        lines.add(t("gui.builder-save-note"));
        if (!problems.isEmpty()) {
            lines.add("");
            lines.add(t("gui.builder-problems"));
            for (String problem : problems.subList(0, Math.min(problems.size(), 6))) {
                lines.add(t("gui.builder-problem", Map.of("problem", MM.escapeTags(problem))));
            }
            lines.addAll(LoreBlocks.blocked(messages()::getRaw, LoreBlocks.status(messages()::getRaw, "unavailable"),
                    t("gui.builder-reason-incomplete"), Hint.of(Click.LEFT, t("gui.action-save-chain"))));
            return head(HeadUtil.getHeadTexture("confirm", HeadTextures.CONFIRM),
                    LoreBlocks.disabledName(messages()::getRaw, plain(t("gui.builder-save"))), lines);
        }
        List<Hint> clicks = new ArrayList<>();
        clicks.add(Hint.of(Click.LEFT, t("gui.action-save-chain")));
        if (!fresh) clicks.add(Hint.of(Click.SHIFT_LEFT, t("gui.action-save-copy")));
        lines.addAll(LoreBlocks.clicks(messages()::getRaw, clicks.toArray(new Hint[0])));
        return head(HeadUtil.getHeadTexture("confirm", HeadTextures.CONFIRM), t("gui.builder-save"), lines);
    }

    private void saveClick(InventoryClickEvent event) {
        if (!ChainDraftCheck.problems(draft).isEmpty()) return;
        boolean asNew = event.isShiftClick() && !fresh;
        ChainBuilder.Result result = builder.save(draft, asNew);
        if (!result.ok()) {
            viewer.sendMessage(MM.deserialize(t("gui.builder-save-failed", Map.of("reason", MM.escapeTags(result.message())))));
            return;
        }
        viewer.sendMessage(MM.deserialize(t("gui.builder-saved", Map.of("id", MM.escapeTags(result.message())))));
        ChainDraft stored = builder.draftOf(result.message());
        if (stored != null) new ChainEditorGui(plugin, viewer, stored, false).open();
        else new ChainListGui(plugin, viewer).open();
    }

    private ItemStack deleteButton() {
        List<String> lines = new ArrayList<>();
        lines.add(t("gui.builder-delete-subtitle"));
        lines.add("");
        lines.add(t("gui.builder-delete-lore"));
        lines.addAll(LoreBlocks.clicks(messages()::getRaw, Hint.of(Click.LEFT, t("gui.action-delete-chain"))));
        return head(HeadUtil.getHeadTexture("cancel", HeadTextures.CANCEL), t("gui.builder-delete"), lines);
    }

    private void confirmDelete() {
        List<String> yes = new ArrayList<>();
        yes.add(t("gui.confirm-delete-subtitle"));
        yes.add("  " + draft.displayName());
        yes.add("");
        yes.add(t("gui.section-effects"));
        yes.add(t("gui.effect-delete-chain"));
        yes.addAll(LoreBlocks.clicks(messages()::getRaw, Hint.of(Click.LEFT, t("gui.action-confirm-delete"))));
        plugin.getContractBoard().confirmCustom(viewer, t("gui.confirm-yes-delete"), yes, () -> {
            ChainBuilder.Result result = builder.delete(draft.id());
            viewer.sendMessage(MM.deserialize(result.ok()
                    ? t("gui.builder-deleted", Map.of("id", MM.escapeTags(draft.id())))
                    : t("gui.builder-save-failed", Map.of("reason", MM.escapeTags(result.message())))));
            if (result.ok()) new ChainListGui(plugin, viewer).open();
            else redraw();
        }, this::redraw);
    }

    // ------------------------------------------------------------------ steps

    private ItemStack card(Slot slot) {
        if (slot.adds()) {
            List<String> lines = new ArrayList<>();
            lines.add(t("gui.builder-step-add-subtitle"));
            lines.add("");
            lines.add(t("gui.builder-step-add-lore"));
            lines.addAll(LoreBlocks.clicks(messages()::getRaw, Hint.of(Click.LEFT, t("gui.action-add-step"))));
            return head(HeadUtil.getHeadTexture("create", HeadTextures.CREATE), t("gui.builder-step-add"), lines);
        }
        StepDraft step = draft.steps().get(slot.index());
        List<String> lines = new ArrayList<>();
        lines.add(t("gui.builder-step-subtitle"));
        lines.add("");
        lines.add(t("gui.builder-step-description", Map.of("description", MM.escapeTags(step.shownDescription()))));
        lines.addAll(stepRows(step));
        List<String> problems = ChainDraftCheck.stepProblems(step);
        for (String problem : problems) {
            lines.add(t("gui.builder-problem", Map.of("problem", MM.escapeTags(problem))));
        }
        List<Hint> clicks = new ArrayList<>();
        clicks.add(Hint.of(Click.LEFT, t("gui.action-edit-step")));
        if (slot.index() > 0) clicks.add(Hint.of(Click.RIGHT, t("gui.action-move-step-up")));
        clicks.add(Hint.of(Click.SHIFT_RIGHT, t("gui.action-delete-step")));
        lines.addAll(LoreBlocks.clicks(messages()::getRaw, clicks.toArray(new Hint[0])));
        String name = t("gui.item-name-state", Map.of(
                "name", t("gui.builder-step-name", Map.of("number", String.valueOf(slot.index() + 1))),
                "state", ChainStepGui.typeLabel(this, step.type())));
        return head(ChainStepGui.typeHead(step.type()), name, lines);
    }

    /** The parameter rows a step of this type shows: only what the type uses. */
    private List<String> stepRows(StepDraft step) {
        List<String> rows = new ArrayList<>();
        if (StepDraft.usesMaterial(step.type()))
            rows.add(t("gui.builder-step-material", Map.of("value", valueOrUnset(step.material()))));
        if (StepDraft.usesEntity(step.type()))
            rows.add(t("gui.builder-step-entity", Map.of("value", valueOrUnset(step.entityType()))));
        if (StepDraft.usesNpc(step.type()))
            rows.add(t("gui.builder-step-npc", Map.of("value", step.targetNpcKey() == null ? t("gui.builder-unset")
                    : MM.escapeTags(step.targetNpcName().isBlank() ? step.targetNpcKey() : step.targetNpcName()))));
        if (StepDraft.usesCount(step.type()))
            rows.add(t("gui.builder-step-count", Map.of("value", String.valueOf(step.count()))));
        if (StepDraft.usesArea(step.type()) && step.area() != null)
            rows.add(t("gui.builder-step-area", Map.of("value", MM.escapeTags(step.area().address()) + " ±" + (int) step.area().radius())));
        return rows;
    }

    private String valueOrUnset(String value) {
        return value == null ? t("gui.builder-unset") : MM.escapeTags(value);
    }

    private void stepClick(Slot slot, InventoryClickEvent event) {
        if (slot.adds()) {
            StepDraft step = new StepDraft(NpcQuestStep.Type.KILL_ENTITY);
            draft.steps().add(step);
            new ChainStepGui(plugin, viewer, draft, step, this::redraw).open();
            return;
        }
        StepDraft step = draft.steps().get(slot.index());
        if (event.isShiftClick() && event.isRightClick()) {
            draft.steps().remove(slot.index());
            render();
        } else if (event.isRightClick()) {
            if (draft.moveUp(slot.index())) render();
        } else if (event.isLeftClick()) {
            new ChainStepGui(plugin, viewer, draft, step, this::redraw).open();
        }
    }
}
