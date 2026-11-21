package me.lovelace.lovecontracts.gui.board;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.manager.MessageManager;
import me.lovelace.lovecontracts.textures.HeadTextures;
import me.lovelace.lovecontracts.util.CoinFormat;
import me.lovelace.lovecontracts.util.HeadUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base of the 54-slot contract menus (gui_gen RULE 8): the glass frame, header controls spread over slots 2-7, the
 * footer with Back (only when the menu has a parent) and Close, and the pagination arrows. Click handlers are
 * registered while rendering and run by {@link #handleClick}.
 */
public abstract class BoardGui implements BoardMenu {

    protected final LoveContracts plugin;
    protected final Player viewer;
    protected Inventory inventory;
    /** Click handlers by raw slot, filled while rendering. */
    protected final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    /** Current page of a paged menu (0-based). */
    protected int page = 0;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** A header control button together with what it does. */
    public record Control(ItemStack item, Consumer<InventoryClickEvent> action) {
    }

    protected BoardGui(LoveContracts plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    public Player viewer() {
        return viewer;
    }

    /** Title of the window; the standard does not touch it. */
    protected abstract Component title();

    /** (Re)draws the whole menu into {@link #inventory}. */
    public abstract void render();

    @Override
    public void handleClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action != null) action.accept(event);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /** Creates the inventory, draws the menu and opens it. */
    protected void show() {
        this.inventory = Bukkit.createInventory(this, 54, title());
        render();
        viewer.openInventory(inventory);
    }

    // ------------------------------------------------------------------ texts

    protected MessageManager messages() {
        return plugin.getMessageManager();
    }

    protected String t(String key) {
        return messages().text(key);
    }

    protected String t(String key, Map<String, String> placeholders) {
        return messages().text(key, placeholders);
    }

    /** Lore lines as components, with coin glyphs resolved for the viewer. */
    protected List<Component> lore(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(CoinFormat.component(viewer, line));
        return out;
    }

    /** A textured head; name and lore are MiniMessage, coin glyphs in the lore resolve for the viewer. */
    protected ItemStack head(String base64, String name, List<String> lines) {
        return HeadUtil.createBase64Head(base64, name, lore(lines));
    }

    /** Glass of the frame: a gray pane with an empty name. */
    protected ItemStack glass() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }

    // ------------------------------------------------------------------ layout helpers

    /** Clears the menu and redraws the glass frame; handlers registered earlier are dropped. */
    protected void frame() {
        BoardLayout.frame(inventory, glass());
        actions.clear();
    }

    protected void button(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action);
    }

    /** Puts the control buttons into header slots 2-7, spread by their number (RULE 4). */
    protected void controls(List<Control> controls) {
        int[] slots = BoardLayout.controlSlots(controls.size());
        for (int i = 0; i < slots.length; i++) {
            button(slots[i], controls.get(i).item(), controls.get(i).action());
        }
    }

    /** Slot 0 of every NPC menu: the common NPC head with a short description of the board. */
    protected ItemStack npcHeader() {
        String base64 = HeadUtil.getHeadTexture("npc-header", HeadTextures.NPC_HEADER);
        return head(base64, t("gui.npc-head-name"),
                List.of(t("gui.npc-head-sub"), "", t("gui.npc-head-line1"), t("gui.npc-head-line2")));
    }

    /** Navigation has no lore (gui-gen-6, section 14): the name only. */
    protected ItemStack closeButton() {
        return HeadUtil.createBase64Head(HeadUtil.getHeadTexture("close", HeadTextures.CLOSE), t("gui.close-button"), List.of());
    }

    protected ItemStack backButton() {
        return HeadUtil.createBase64Head(HeadUtil.getHeadTexture("prev", HeadTextures.PAGINATION_PREVIOUS), t("gui.back-button"), List.of());
    }

    /** Footer: Back only when {@code back} is given (a menu with a parent), otherwise the glass stays; Close always. */
    protected void footer(Runnable back) {
        int size = inventory.getSize();
        if (back != null) button(BoardLayout.backSlot(size), backButton(), e -> back.run());
        button(BoardLayout.closeSlot(size), closeButton(), e -> viewer.closeInventory());
    }

    /**
     * Pagination arrows on the side walls of the last work row (slots 36 and 44, RULE 6); a wall without an arrow
     * stays empty. Returns the page, clamped to the range.
     */
    protected int pager(int totalPages) {
        int pages = Math.max(1, totalPages);
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;
        if (page > 0) {
            button(BoardLayout.PAGE_PREVIOUS, HeadUtil.createBase64Head(
                    HeadUtil.getHeadTexture("prev", HeadTextures.PAGINATION_PREVIOUS), t("gui.prev-page"), List.of()),
                    e -> { page--; render(); });
        }
        if (page < pages - 1) {
            button(BoardLayout.PAGE_NEXT, HeadUtil.createBase64Head(
                    HeadUtil.getHeadTexture("next", HeadTextures.PAGINATION_NEXT), t("gui.next-page"), List.of()),
                    e -> { page++; render(); });
        }
        return page;
    }

    /** The "nothing here yet" card in the middle of the work zone of an empty list. */
    protected void emptyCard(String hintKey) {
        List<String> lines = new ArrayList<>();
        lines.add(t("gui.empty-subtitle"));
        if (hintKey != null) {
            lines.add("");
            lines.add(t(hintKey));
        }
        inventory.setItem(BoardLayout.centerSlot(inventory.getSize()),
                head(HeadUtil.getHeadTexture("completed", HeadTextures.CONTRACT_STATUS_DEFAULT), t("gui.empty-title"), lines));
    }

    /** MiniMessage without tags, for plain text such as a button name. */
    protected static String plain(String text) {
        return MM.stripTags(text == null ? "" : text);
    }
}
