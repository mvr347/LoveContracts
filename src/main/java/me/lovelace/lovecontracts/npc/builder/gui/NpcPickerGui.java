package me.lovelace.lovecontracts.npc.builder.gui;

import dev.lovelace.lovecore.api.npc.NpcHandle;
import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.gui.LoreBlocks;
import me.lovelace.lovecontracts.gui.LoreBlocks.Click;
import me.lovelace.lovecontracts.gui.LoreBlocks.Hint;
import me.lovelace.lovecontracts.gui.board.BoardGui;
import me.lovelace.lovecontracts.gui.board.BoardLayout;
import me.lovelace.lovecontracts.textures.HeadTextures;
import me.lovelace.lovecontracts.util.HeadUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Picks one of the LoveEssential NPCs for the chain builder: from a list, or the one the admin is looking at. */
final class NpcPickerGui extends BoardGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Runnable back;
    private final Consumer<NpcHandle> onPick;

    NpcPickerGui(LoveContracts plugin, Player viewer, Runnable back, Consumer<NpcHandle> onPick) {
        super(plugin, viewer);
        this.back = back;
        this.onPick = onPick;
    }

    @Override
    protected Component title() {
        return MM.deserialize(t("gui.builder-npc-title"));
    }

    void open() {
        show();
    }

    @Override
    public void render() {
        frame();
        button(0, head(HeadUtil.getHeadTexture("npc-header", HeadTextures.NPC_HEADER), t("gui.builder-head-name"),
                List.of(t("gui.builder-npc-subtitle"), "", t("gui.builder-head-line1"), t("gui.builder-head-line2"))), null);
        paged(npcs(), this::card, (npc, event) -> pick(npc), "gui.builder-npc-empty");
        button(BoardLayout.extraSlot(inventory.getSize()), lookingButton(), e -> pickTargeted());
        footer(back);
    }

    private List<NpcHandle> npcs() {
        List<NpcHandle> all = new ArrayList<>();
        try {
            plugin.getNpcBridge().provider().ifPresent(provider -> all.addAll(provider.all()));
        } catch (RuntimeException unavailable) {
            // an unavailable provider is an empty list; the bridge has already warned about it
        }
        all.sort(Comparator.comparing((NpcHandle npc) -> plainName(npc).toLowerCase()).thenComparing(NpcHandle::key));
        return all;
    }

    private static String plainName(NpcHandle npc) {
        String name = npc.name();
        return name == null || name.isBlank() ? npc.key() : MM.stripTags(name);
    }

    private ItemStack card(NpcHandle npc) {
        List<String> lines = new ArrayList<>();
        lines.add(t("gui.builder-npc-subtitle"));
        lines.add("");
        lines.add(t("gui.builder-npc-key", Map.of("key", MM.escapeTags(npc.key()))));
        String where = whereOf(npc);
        if (!where.isBlank()) lines.add(t("gui.builder-npc-where", Map.of("where", MM.escapeTags(where))));
        lines.addAll(LoreBlocks.clicks(messages()::getRaw, Hint.of(Click.LEFT, t("gui.action-pick-npc"))));
        return head(HeadUtil.getHeadTexture("npc-header", HeadTextures.NPC_HEADER),
                "<gold>" + MM.escapeTags(plainName(npc)) + "</gold>", lines);
    }

    private static String whereOf(NpcHandle npc) {
        try {
            var at = npc.location();
            return at.getWorld().getName() + " " + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ();
        } catch (RuntimeException unavailable) {
            return "";
        }
    }

    private ItemStack lookingButton() {
        List<String> lines = new ArrayList<>();
        lines.add(t("gui.builder-npc-looking-subtitle"));
        lines.add("");
        lines.add(t("gui.builder-npc-looking-lore"));
        lines.addAll(LoreBlocks.clicks(messages()::getRaw, Hint.of(Click.LEFT, t("gui.action-pick-looked-npc"))));
        return head(HeadUtil.getHeadTexture("create", HeadTextures.CREATE), t("gui.builder-npc-looking"), lines);
    }

    private void pickTargeted() {
        Optional<NpcHandle> targeted = Optional.empty();
        try {
            targeted = plugin.getNpcBridge().targeted(viewer);
        } catch (RuntimeException unavailable) {
            // handled below as "nobody in the crosshair"
        }
        if (targeted.isEmpty()) {
            viewer.sendMessage(MM.deserialize(t("gui.builder-npc-none-targeted")));
            return;
        }
        pick(targeted.get());
    }

    private void pick(NpcHandle npc) {
        onPick.accept(npc);
        back.run();
    }
}
