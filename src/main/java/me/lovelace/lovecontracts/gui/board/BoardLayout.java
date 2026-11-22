package me.lovelace.lovecontracts.gui.board;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Slot arithmetic of the gui_gen standard (RULE 1-8) for the contract menus (27/36/45/54 slots). Same rules as the
 * market menus of LoveShops, kept here so LoveContracts does not depend on that plugin.
 * <ul>
 *   <li>Header: slots 0-8 (plus Row1 9-17 from 45 slots up, always glass). Slot 0 is the head, slots 1 and 8 are
 *       glass, control buttons live only in 2-7 and are spread by their number.</li>
 *   <li>Work zone: content only, the side walls stay empty (pagination arrows only on 36 and 44 of a 54-slot menu).</li>
 *   <li>Footer: always the last 9 slots: glass, then extra (size-3), back (size-2) and close (size-1).</li>
 * </ul>
 */
public final class BoardLayout {

    /** Pagination arrows of a 54-slot menu: the side walls of the last work row. */
    public static final int PAGE_PREVIOUS = 36;
    public static final int PAGE_NEXT = 44;

    private BoardLayout() {
    }

    /** Slots 2-7 used by {@code count} control buttons, centred with equal gaps (RULE 4). */
    public static int[] controlSlots(int count) {
        return switch (count) {
            case 1 -> new int[]{4};
            case 2 -> new int[]{3, 5};
            case 3 -> new int[]{2, 4, 6};
            case 4 -> new int[]{2, 3, 5, 6};
            case 5 -> new int[]{2, 3, 4, 6, 7};
            case 6 -> new int[]{2, 3, 4, 5, 6, 7};
            default -> throw new IllegalArgumentException("control buttons must be 1..6, got " + count);
        };
    }

    /** First slot of the work zone: 9 up to 36 slots, 18 from 45 slots (Row1 belongs to the header). */
    public static int workStart(int size) {
        return size >= 45 ? 18 : 9;
    }

    /** Content slots: 7 per row (columns 1-7), the side walls (columns 0 and 8) stay empty. */
    public static int[] contentSlots(int size) {
        List<Integer> slots = new ArrayList<>();
        int footerStart = size - 9;
        for (int row = workStart(size); row < footerStart; row += 9) {
            for (int col = 1; col <= 7; col++) slots.add(row + col);
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Slots for {@code count} buttons laid out in one work-zone row starting at {@code rowStart}: columns 1-7,
     * centred with equal gaps (the table of RULE 6: 1 -> centre, 2 -> 3 and 5, ... 7 -> the whole row).
     */
    public static int[] rowSlots(int rowStart, int count) {
        int[] cols = switch (count) {
            case 1 -> new int[]{4};
            case 2 -> new int[]{3, 5};
            case 3 -> new int[]{2, 4, 6};
            case 4 -> new int[]{1, 3, 5, 7};
            case 5 -> new int[]{2, 3, 4, 5, 6};
            case 6 -> new int[]{1, 2, 3, 5, 6, 7};
            case 7 -> new int[]{1, 2, 3, 4, 5, 6, 7};
            default -> throw new IllegalArgumentException("buttons per row must be 1..7, got " + count);
        };
        int[] slots = new int[cols.length];
        for (int i = 0; i < cols.length; i++) slots[i] = rowStart + cols[i];
        return slots;
    }

    /** Middle of the work zone: where the "nothing here yet" card of an empty list goes. */
    public static int centerSlot(int size) {
        int start = workStart(size);
        int rows = Math.max(1, (size - 9 - start) / 9);
        return start + (rows / 2) * 9 + 4;
    }

    public static int extraSlot(int size) { return size - 3; }
    public static int backSlot(int size) { return size - 2; }
    public static int closeSlot(int size) { return size - 1; }

    /** Number of rows between the header and the footer. */
    public static int workRows(int size) {
        return (size - 9 - workStart(size)) / 9;
    }

    /**
     * Clears the inventory and draws the frame: glass in the header (and Row1) and in the footer, nothing in the work
     * zone. Buttons are placed on top afterwards, replacing the glass under them.
     */
    public static void frame(Inventory inv, ItemStack glass) {
        int size = inv.getSize();
        inv.clear();
        int headerEnd = workStart(size);
        for (int i = 0; i < headerEnd; i++) inv.setItem(i, glass);
        for (int i = size - 9; i < size; i++) inv.setItem(i, glass);
    }
}
