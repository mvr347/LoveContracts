package me.lovelace.lovecontracts.npc.builder.gui;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.gui.LoreBlocks;
import me.lovelace.lovecontracts.gui.LoreBlocks.Click;
import me.lovelace.lovecontracts.gui.LoreBlocks.Hint;
import me.lovelace.lovecontracts.gui.board.BoardGui;
import me.lovelace.lovecontracts.gui.board.BoardLayout;
import me.lovelace.lovecontracts.gui.board.ContractBoard;
import me.lovelace.lovecontracts.gui.board.DurationText;
import me.lovelace.lovecontracts.gui.ContractLore;
import me.lovelace.lovecontracts.npc.builder.ChainBuilder;
import me.lovelace.lovecontracts.npc.builder.ChainDraft;
import me.lovelace.lovecontracts.npc.model.NpcQuest;
import me.lovelace.lovecontracts.textures.HeadTextures;
import me.lovelace.lovecontracts.util.CoinFormat;
import me.lovelace.lovecontracts.util.HeadUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Entry of the chain builder: every chain on the server. Chains of the builder (chains.yml) can be edited and deleted;
 * chains from the hand-written files are shown for reference only.
 */
public final class ChainListGui extends BoardGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ChainBuilder builder;

    /** One line of the list: a chain of the builder (draft present) or a hand-written one (draft null). */
    private record Entry(String id, ChainDraft draft, NpcQuest loaded) {
        boolean editable() { return draft != null; }
    }

    public ChainListGui(LoveContracts plugin, Player viewer) {
        super(plugin, viewer);
        this.builder = plugin.getChainBuilder();
    }

    @Override
    protected Component title() {
        return MM.deserialize(t("gui.builder-title"));
    }

    public void open() {
        show();
    }

    @Override
    public void render() {
        frame();
        button(0, head(HeadUtil.getHeadTexture("npc-header", HeadTextures.NPC_HEADER), t("gui.builder-head-name"),
                List.of(t("gui.builder-list-subtitle"), "", t("gui.builder-head-line1"), t("gui.builder-head-line2"))), null);
        paged(entries(), this::card, this::click, "gui.builder-empty-hint");
        button(BoardLayout.extraSlot(inventory.getSize()), newButton(), e -> newChain());
        // the list is the root of the builder: no Back
        footer(null);
    }

    private List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        Map<String, ChainDraft> built = builder.chains();
        for (ChainDraft draft : built.values()) {
            entries.add(new Entry(draft.id(), draft, plugin.getNpcQuestManager().getQuest(draft.id())));
        }
        for (NpcQuest quest : plugin.getNpcQuestManager().allQuests()) {
            if (!built.containsKey(quest.getId())) entries.add(new Entry(quest.getId(), null, quest));
        }
        // the builder's own chains first, then the files'; each group by id
        entries.sort(Comparator.comparing((Entry e) -> !e.editable()).thenComparing(Entry::id));
        return entries;
    }

    private ItemStack card(Entry entry) {
        List<String> lines = new ArrayList<>();
        lines.add(t(entry.editable() ? "gui.builder-chain-subtitle" : "gui.builder-file-subtitle"));
        lines.add("");
        lines.add(t("gui.builder-row-id", Map.of("id", MM.escapeTags(entry.id()))));
        String displayName;
        if (entry.editable()) {
            ChainDraft draft = entry.draft();
            displayName = draft.displayName();
            lines.add(t("gui.builder-row-giver", Map.of("giver", MM.escapeTags(String.valueOf(draft.giverKey())))));
            lines.add(t("gui.builder-row-steps", Map.of("count", String.valueOf(draft.steps().size()))));
            lines.add(t("gui.builder-row-repeat", Map.of("value", draft.repeatable()
                    ? t("gui.builder-repeat-yes", Map.of("time", DurationText.format(draft.cooldownMinutes() * 60_000L)))
                    : t("gui.builder-repeat-no"))));
            lines.add(t("gui.builder-row-loaded", Map.of("value",
                    t(entry.loaded() != null ? "gui.builder-loaded-yes" : "gui.builder-loaded-no"))));
            if (draft.reward() > 0) {
                lines.add("");
                lines.add(t("gui.section-reward"));
                lines.addAll(ContractLore.coinRows(CoinFormat.formatLines(draft.reward())));
            }
            lines.addAll(LoreBlocks.clicks(messages()::getRaw,
                    Hint.of(Click.LEFT, t("gui.action-edit-chain")),
                    Hint.of(Click.SHIFT_RIGHT, t("gui.action-delete-chain"))));
        } else {
            NpcQuest quest = entry.loaded();
            displayName = quest.getDisplayName();
            lines.add(t("gui.builder-row-giver", Map.of("giver", MM.escapeTags(String.valueOf(quest.getNpcKey())))));
            lines.add(t("gui.builder-row-steps", Map.of("count", String.valueOf(quest.TotalSteps()))));
            lines.addAll(LoreBlocks.blocked(messages()::getRaw, LoreBlocks.status(messages()::getRaw, "locked"),
                    t("gui.builder-reason-file"), Hint.of(Click.LEFT, t("gui.action-edit-chain"))));
        }
        String base64 = HeadUtil.getHeadTexture(entry.editable() ? "chain" : "locked",
                entry.editable() ? HeadTextures.DIFFICULTY_MEDIUM : HeadTextures.LOCKED);
        String name = entry.editable() ? displayName : LoreBlocks.disabledName(messages()::getRaw, displayName);
        return head(base64, name, lines);
    }

    private void click(Entry entry, InventoryClickEvent event) {
        if (!entry.editable()) return;
        if (event.isShiftClick() && event.isRightClick()) {
            confirmDelete(entry);
        } else if (event.isLeftClick() && !event.isShiftClick()) {
            ChainDraft draft = builder.draftOf(entry.id());
            if (draft != null) new ChainEditorGui(plugin, viewer, draft, false).open();
        }
    }

    private void confirmDelete(Entry entry) {
        List<String> yes = new ArrayList<>();
        yes.add(t("gui.confirm-delete-subtitle"));
        yes.add("  " + entry.draft().displayName());
        yes.add("");
        yes.add(t("gui.section-effects"));
        yes.add(t("gui.effect-delete-chain"));
        yes.addAll(LoreBlocks.clicks(messages()::getRaw, Hint.of(Click.LEFT, t("gui.action-confirm-delete"))));
        plugin.getContractBoard().confirmCustom(viewer, t("gui.confirm-yes-delete"), yes, () -> {
            ChainBuilder.Result result = builder.delete(entry.id());
            viewer.sendMessage(MM.deserialize(result.ok()
                    ? t("gui.builder-deleted", Map.of("id", MM.escapeTags(entry.id())))
                    : t("gui.builder-save-failed", Map.of("reason", MM.escapeTags(result.message())))));
            redraw();
        }, this::redraw);
    }

    private ItemStack newButton() {
        List<String> lines = new ArrayList<>();
        lines.add(t("gui.builder-new-subtitle"));
        lines.add("");
        lines.add(t("gui.builder-new-lore"));
        lines.addAll(LoreBlocks.clicks(messages()::getRaw, Hint.of(Click.LEFT, t("gui.action-new-chain"))));
        return head(HeadUtil.getHeadTexture("create", HeadTextures.CREATE), t("gui.builder-new"), lines);
    }

    private void newChain() {
        askThen("gui.builder-prompt-id", text -> {
            String id = text.trim().toLowerCase(java.util.Locale.ROOT);
            if (!ChainDraft.ID.matcher(id).matches()) {
                viewer.sendMessage(MM.deserialize(t("gui.builder-id-invalid")));
                redraw();
                return;
            }
            if (builder.idTaken(id)) {
                viewer.sendMessage(MM.deserialize(t("gui.builder-id-taken")));
                redraw();
                return;
            }
            new ChainEditorGui(plugin, viewer, new ChainDraft(id), true).open();
        });
    }
}
