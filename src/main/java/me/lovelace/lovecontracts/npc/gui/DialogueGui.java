package me.lovelace.lovecontracts.npc.gui;

import dev.lovelace.lovecore.api.npc.NpcHandle;
import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.gui.LoreBlocks;
import me.lovelace.lovecontracts.gui.LoreBlocks.Click;
import me.lovelace.lovecontracts.gui.LoreBlocks.Hint;
import me.lovelace.lovecontracts.gui.board.BoardGui;
import me.lovelace.lovecontracts.gui.board.BoardLayout;
import me.lovelace.lovecontracts.npc.model.NpcDialogueNode;
import me.lovelace.lovecontracts.npc.model.NpcDialogueOption;
import me.lovelace.lovecontracts.npc.model.NpcQuest;
import me.lovelace.lovecontracts.textures.HeadTextures;
import me.lovelace.lovecontracts.util.CoinFormat;
import me.lovelace.lovecontracts.util.HeadUtil;
import me.lovelace.lovecontracts.util.ItemText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * One line of an NPC conversation as a 54-slot menu (gui_gen RULE 8): slot 0 the NPC head, Row1 glass, the text in the
 * middle of the work zone and the answers in the row below, centred by their number; the footer is Close only (an NPC
 * conversation is a root menu). Accepting a chain asks for a confirmation first.
 */
final class DialogueGui extends BoardGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int TEXT_SLOT = 22;
    private static final int ANSWER_ROW = 27;

    private final NpcQuest quest;
    private final NpcDialogueNode node;
    private final NpcHandle speaker;
    /** Index of the step this conversation is for; {@code -1} when it offers a chain that is not taken yet. */
    private final int expected;

    DialogueGui(LoveContracts plugin, Player viewer, NpcQuest quest, NpcDialogueNode node, NpcHandle speaker, int expected) {
        super(plugin, viewer);
        this.quest = quest;
        this.node = node;
        this.speaker = speaker;
        this.expected = expected;
    }

    @Override
    protected Component title() {
        return MM.deserialize("<gold>" + quest.getDisplayName() + "</gold>");
    }

    void open() {
        show();
    }

    @Override
    public void render() {
        frame();
        button(0, speakerHead(), null);
        button(TEXT_SLOT, textBook(), null);

        List<NpcDialogueOption> options = node.getOptions();
        int count = Math.min(options.size(), 7);
        int[] slots = count == 0 ? new int[0] : BoardLayout.rowSlots(ANSWER_ROW, count);
        for (int i = 0; i < count; i++) {
            NpcDialogueOption option = options.get(i);
            button(slots[i], optionItem(option), e -> answer(option));
        }
        footer(null);
    }

    private ItemStack speakerHead() {
        String base64 = HeadUtil.getHeadTexture("npc-header", HeadTextures.NPC_HEADER);
        String name = speaker != null && speaker.name() != null && !speaker.name().isBlank()
                ? MM.escapeTags(MM.stripTags(speaker.name())) : t("gui.npc-dialogue-name");
        return head(base64, "<gold>" + name + "</gold>", List.of(t("gui.npc-dialogue-subtitle")));
    }

    private ItemStack textBook() {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ItemText.upright(MM.deserialize(t("gui.npc-dialogue-name"))));
        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize(t("gui.npc-dialogue-subtitle")));
        lore.add(Component.empty());
        for (String line : node.getTextLines()) lore.add(MM.deserialize(line));
        lore.add(Component.empty());
        lore.add(MM.deserialize(t("gui.npc-dialogue-reward")));
        for (var reward : quest.getRewards()) {
            for (String line : reward.getDisplayLines()) lore.add(MM.deserialize(CoinFormat.resolveGlyphs(viewer, line)));
        }
        meta.lore(ItemText.upright(lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack optionItem(NpcDialogueOption option) {
        Material material = switch (option.getAction()) {
            case ACCEPT_QUEST -> Material.LIME_DYE;
            case NEXT_NODE -> Material.CYAN_DYE;
            case COMPLETE_STEP -> Material.ORANGE_DYE;
            case CLOSE -> Material.RED_DYE;
        };
        List<String> lines = new ArrayList<>();
        lines.add(t("gui.npc-option-subtitle"));
        lines.addAll(LoreBlocks.clicks(messages()::getRaw, Hint.of(Click.LEFT, t("gui.action-npc-option"))));
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ItemText.upright(MM.deserialize(option.getText())));
        meta.lore(ItemText.upright(lore(lines)));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        // the NPC walked away or the conversation is stale: nothing more can be answered
        if (!plugin.getNpcQuestManager().canInteract(viewer, speaker)) {
            if (event.getRawSlot() != BoardLayout.closeSlot(54)) {
                viewer.closeInventory();
                return;
            }
        }
        super.handleClick(event);
    }

    private void answer(NpcDialogueOption option) {
        if (option.isPolite()) plugin.getNpcQuestManager().recordPoliteChoice(viewer, quest);
        switch (option.getAction()) {
            case ACCEPT_QUEST -> {
                if (expected != -1) {
                    viewer.closeInventory();
                    return;
                }
                plugin.getContractBoard().confirmTakeChain(viewer, quest, this::reopen, () -> {
                    plugin.getNpcQuestManager().startQuest(viewer, quest);
                });
            }
            case NEXT_NODE -> {
                NpcDialogueNode next = option.getNextNode() == null ? null : quest.getDialogueNode(option.getNextNode());
                if (next == null) {
                    viewer.closeInventory();
                    return;
                }
                new DialogueGui(plugin, viewer, quest, next, speaker, expected).open();
            }
            case COMPLETE_STEP -> {
                viewer.closeInventory();
                plugin.getNpcQuestManager().confirmStep(viewer, quest, expected, speaker);
            }
            case CLOSE -> viewer.closeInventory();
        }
    }

    /** Back from the confirmation window to this very line of the conversation. */
    private void reopen() {
        viewer.openInventory(inventory);
    }
}
