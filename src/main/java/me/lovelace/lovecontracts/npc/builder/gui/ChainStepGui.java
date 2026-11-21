package me.lovelace.lovecontracts.npc.builder.gui;

import dev.lovelace.lovecore.api.npc.NpcHandle;
import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.gui.LoreBlocks;
import me.lovelace.lovecontracts.gui.LoreBlocks.Click;
import me.lovelace.lovecontracts.gui.LoreBlocks.Hint;
import me.lovelace.lovecontracts.gui.board.BoardGui;
import me.lovelace.lovecontracts.gui.board.BoardLayout;
import me.lovelace.lovecontracts.npc.builder.ChainDraft;
import me.lovelace.lovecontracts.npc.builder.StepDraft;
import me.lovelace.lovecontracts.npc.model.NpcQuestStep;
import me.lovelace.lovecontracts.npc.model.QuestLocation;
import me.lovelace.lovecontracts.textures.HeadTextures;
import me.lovelace.lovecontracts.util.HeadUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One step of a chain: the type in the first work row, the parameters that type uses in the second (gui_gen RULE 8 for
 * 36 slots: header, two work rows, footer). Each button changes the step and shows the menu again.
 */
final class ChainStepGui extends BoardGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int TYPE_ROW = 9;
    private static final int PARAM_ROW = 18;

    /** The step types in the order of the type row. */
    private static final NpcQuestStep.Type[] TYPES = {
            NpcQuestStep.Type.KILL_ENTITY, NpcQuestStep.Type.MINE_MATERIAL, NpcQuestStep.Type.CRAFT_ITEM,
            NpcQuestStep.Type.CATCH_FISH, NpcQuestStep.Type.DELIVER_ITEM, NpcQuestStep.Type.VISIT_NPC,
            NpcQuestStep.Type.VISIT_LOCATION};

    private final ChainDraft chain;
    private final StepDraft step;
    private final Runnable back;

    ChainStepGui(LoveContracts plugin, Player viewer, ChainDraft chain, StepDraft step, Runnable back) {
        super(plugin, viewer);
        this.chain = chain;
        this.step = step;
        this.back = back;
    }

    @Override
    protected int size() {
        return 36;
    }

    @Override
    protected Component title() {
        return MM.deserialize(t("gui.builder-step-title", Map.of("number", String.valueOf(chain.steps().indexOf(step) + 1))));
    }

    void open() {
        show();
    }

    // ------------------------------------------------------------------ type labels and heads (shared with the editor)

    /** Name of a step type, in the colour of a state (the step card shows it after the bar). */
    static String typeLabel(BoardGui gui, NpcQuestStep.Type type) {
        return gui.messagesText(typeKey(type));
    }

    private static String typeKey(NpcQuestStep.Type type) {
        return switch (type) {
            case KILL_ENTITY -> "gui.builder-type-kill";
            case MINE_MATERIAL -> "gui.builder-type-mine";
            case CRAFT_ITEM -> "gui.builder-type-craft";
            case CATCH_FISH -> "gui.builder-type-fish";
            case DELIVER_ITEM -> "gui.builder-type-deliver";
            case VISIT_NPC -> "gui.builder-type-visit-npc";
            case VISIT_LOCATION -> "gui.builder-type-visit-location";
        };
    }

    static String typeHead(NpcQuestStep.Type type) {
        return switch (type) {
            case KILL_ENTITY -> HeadUtil.getHeadTexture("offer-kill", HeadTextures.DIFFICULTY_HARD);
            case MINE_MATERIAL, CRAFT_ITEM -> HeadUtil.getHeadTexture("offer-deliver", HeadTextures.DIFFICULTY_EASY);
            case CATCH_FISH -> HeadUtil.getHeadTexture("tab-players", HeadTextures.COUNT);
            case DELIVER_ITEM -> HeadUtil.getHeadTexture("tab-mine", HeadTextures.REWARD);
            case VISIT_NPC -> HeadUtil.getHeadTexture("tab-chains", HeadTextures.PRESET);
            case VISIT_LOCATION -> HeadUtil.getHeadTexture("tab-system", HeadTextures.STARTER_QUEST);
        };
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render() {
        frame();
        button(0, head(HeadUtil.getHeadTexture("npc-header", HeadTextures.NPC_HEADER), t("gui.builder-head-name"),
                List.of(t("gui.builder-step-subtitle"), "", t("gui.builder-head-line1"), t("gui.builder-head-line2"))), null);

        int[] typeSlots = BoardLayout.rowSlots(TYPE_ROW, TYPES.length);
        for (int i = 0; i < TYPES.length; i++) {
            NpcQuestStep.Type type = TYPES[i];
            button(typeSlots[i], typeButton(type), e -> {
                step.type(type);
                render();
            });
        }

        List<Control> params = params();
        int[] paramSlots = BoardLayout.rowSlots(PARAM_ROW, params.size());
        for (int i = 0; i < params.size(); i++) {
            button(paramSlots[i], params.get(i).item(), params.get(i).action());
        }

        // Д: remove this step; the menu then goes back to the chain
        button(BoardLayout.extraSlot(inventory.getSize()), deleteButton(), e -> {
            chain.steps().remove(step);
            back.run();
        });
        footer(back);
    }

    private ItemStack typeButton(NpcQuestStep.Type type) {
        boolean selected = type == step.type();
        String name = selected
                ? t("gui.item-name-state", Map.of("name", t(typeKey(type)), "state", t("gui.builder-type-selected")))
                : t(typeKey(type));
        List<String> lines = new ArrayList<>();
        lines.add(t("gui.builder-type-subtitle"));
        lines.add("");
        lines.add(t("gui.builder-type-lore"));
        if (!selected) lines.addAll(LoreBlocks.clicks(messages()::getRaw, Hint.of(Click.LEFT, t("gui.action-pick-type"))));
        ItemStack item = head(typeHead(type), name, lines);
        if (selected) item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        return item;
    }

    private ItemStack deleteButton() {
        List<String> lines = new ArrayList<>();
        lines.add(t("gui.builder-step-delete-subtitle"));
        lines.add("");
        lines.add(t("gui.builder-step-delete-lore"));
        lines.addAll(LoreBlocks.clicks(messages()::getRaw, Hint.of(Click.LEFT, t("gui.action-delete-step"))));
        return head(HeadUtil.getHeadTexture("cancel", HeadTextures.CANCEL), t("gui.builder-step-delete"), lines);
    }

    // ------------------------------------------------------------------ parameters

    private List<Control> params() {
        NpcQuestStep.Type type = step.type();
        List<Control> params = new ArrayList<>();
        if (StepDraft.usesEntity(type)) params.add(new Control(entityButton(), e -> askEntity()));
        if (StepDraft.usesMaterial(type)) params.add(new Control(materialButton(), this::materialClick));
        if (StepDraft.usesNpc(type)) params.add(new Control(npcButton(), e -> pickNpc()));
        if (StepDraft.usesCount(type)) params.add(new Control(countButton(), e ->
                askNumber("gui.builder-prompt-count", 1, 100_000, v -> step.count((int) v))));
        if (StepDraft.usesArea(type)) params.add(new Control(areaButton(), this::areaClick));
        params.add(new Control(descriptionButton(), e -> askText("gui.builder-prompt-description", step::description)));
        return params;
    }

    private ItemStack paramButton(String nameKey, String value, Hint... clicks) {
        List<String> lines = new ArrayList<>();
        lines.add(t("gui.builder-param-subtitle"));
        lines.add("");
        lines.add(t("gui.builder-param-now", Map.of("value", value)));
        lines.addAll(LoreBlocks.clicks(messages()::getRaw, clicks));
        return head(HeadUtil.getHeadTexture("count", HeadTextures.COUNT), t(nameKey), lines);
    }

    private String shown(String value) {
        return value == null || value.isBlank() ? t("gui.builder-unset") : MM.escapeTags(value);
    }

    private ItemStack entityButton() {
        return paramButton("gui.builder-param-entity", shown(step.entityType()),
                Hint.of(Click.LEFT, t("gui.action-type-entity")));
    }

    private void askEntity() {
        askText("gui.builder-prompt-entity", text -> {
            EntityType type = entityOf(text);
            if (type == null) {
                viewer.sendMessage(MM.deserialize(t("gui.builder-unknown-entity", Map.of("value", MM.escapeTags(text)))));
                return;
            }
            step.entityType(type.name());
        });
    }

    /** The entity a name stands for ({@code zombie}, {@code ZOMBIE}, {@code minecraft:zombie}); null when there is none. */
    static EntityType entityOf(String text) {
        String name = text.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(name.contains(":") ? name : "minecraft:" + name);
        if (key == null) return null;
        try {
            return org.bukkit.Registry.ENTITY_TYPE.get(key);
        } catch (RuntimeException unknown) {
            return null;
        }
    }

    private ItemStack materialButton() {
        return paramButton("gui.builder-param-material", shown(step.material()),
                Hint.of(Click.LEFT, t("gui.action-take-hand-item")),
                Hint.of(Click.RIGHT, t("gui.action-type-material")));
    }

    private void materialClick(InventoryClickEvent event) {
        if (event.isRightClick()) {
            askText("gui.builder-prompt-material", text -> {
                Material material = Material.matchMaterial(text.trim());
                if (material == null || !material.isItem()) {
                    viewer.sendMessage(MM.deserialize(t("gui.builder-unknown-material", Map.of("value", MM.escapeTags(text)))));
                    return;
                }
                step.material(material.name());
            });
            return;
        }
        ItemStack inHand = viewer.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()) {
            viewer.sendMessage(MM.deserialize(t("gui.builder-empty-hand")));
            return;
        }
        step.material(inHand.getType().name());
        render();
    }

    private ItemStack npcButton() {
        String value = step.targetNpcKey() == null ? t("gui.builder-unset")
                : MM.escapeTags(step.targetNpcName().isBlank() ? step.targetNpcKey() : step.targetNpcName());
        return paramButton("gui.builder-param-npc", value, Hint.of(Click.LEFT, t("gui.action-pick-npc")));
    }

    private void pickNpc() {
        new NpcPickerGui(plugin, viewer, this::redraw, (NpcHandle npc) -> {
            String name = npc.name() == null ? "" : MM.stripTags(npc.name());
            step.targetNpc(npc.key(), name);
        }).open();
    }

    private ItemStack countButton() {
        return paramButton("gui.builder-param-count", String.valueOf(step.count()),
                Hint.of(Click.LEFT, t("gui.action-type-count")));
    }

    private ItemStack areaButton() {
        QuestLocation area = step.area();
        String value = area == null ? t("gui.builder-unset")
                : MM.escapeTags(area.address()) + " ±" + (int) area.radius();
        List<Hint> clicks = new ArrayList<>();
        clicks.add(Hint.of(Click.LEFT, t("gui.action-set-area-here")));
        if (area != null && step.type() != NpcQuestStep.Type.VISIT_LOCATION) {
            clicks.add(Hint.of(Click.SHIFT_RIGHT, t("gui.action-clear-area")));
        }
        return paramButton("gui.builder-param-area", value, clicks.toArray(new Hint[0]));
    }

    private void areaClick(InventoryClickEvent event) {
        if (event.isShiftClick() && event.isRightClick()) {
            if (step.type() != NpcQuestStep.Type.VISIT_LOCATION) {
                step.area(null);
                render();
            }
            return;
        }
        // the place is where the admin stands NOW; the radius is asked afterwards, the window is closed meanwhile
        Location here = viewer.getLocation().clone();
        askNumber("gui.builder-prompt-radius", 1, 256, radius ->
                step.area(new QuestLocation(here.getWorld().getName(), here.getX(), here.getY(), here.getZ(), radius)));
    }

    private ItemStack descriptionButton() {
        String value = step.description().isBlank() ? t("gui.builder-description-auto") : MM.escapeTags(step.description());
        return paramButton("gui.builder-param-description", value, Hint.of(Click.LEFT, t("gui.action-type-description")));
    }
}
