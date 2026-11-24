package me.lovelace.lovecontracts.npc.gui;

import dev.lovelace.lovecore.api.npc.NpcClick;
import dev.lovelace.lovecore.api.npc.NpcClickEvent;
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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * The window shown when a player with an active contract clicks an NPC and the server runs in
 * {@code quests.npc-click-mode: choice}: talk about the contract, or use what the NPC normally does.
 * A root menu (RULE 7): Close only, no Back. The "ordinary" answer replays the click for the other plugins.
 */
public final class NpcChoiceGui extends BoardGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final NpcHandle npc;
    private final Runnable openContract;

    public NpcChoiceGui(LoveContracts plugin, Player viewer, NpcHandle npc, Runnable openContract) {
        super(plugin, viewer);
        this.npc = npc;
        this.openContract = openContract;
    }

    @Override
    protected Component title() {
        return MM.deserialize(t("gui.npc-choice-title"));
    }

    /** One work row (RULE 1): a 27-slot window is enough. */
    @Override
    protected int size() {
        return 27;
    }

    public void open() {
        show();
    }

    @Override
    public void render() {
        frame();
        String name = npc != null && npc.name() != null && !npc.name().isBlank()
                ? MM.escapeTags(MM.stripTags(npc.name())) : t("gui.npc-dialogue-name");
        button(0, head(HeadUtil.getHeadTexture("npc-header", HeadTextures.NPC_HEADER), "<gold>" + name + "</gold>",
                List.of(t("gui.npc-dialogue-subtitle"))), null);

        int[] slots = BoardLayout.rowSlots(9, 2);
        button(slots[0], head(HeadUtil.getHeadTexture("npc-header", HeadTextures.NPC_HEADER), t("gui.npc-choice-contract"),
                lines("gui.npc-choice-contract-sub", "gui.npc-choice-contract-hint", "gui.npc-choice-contract-action")), e -> {
            viewer.closeInventory();
            openContract.run();
        });
        button(slots[1], head(HeadUtil.getHeadTexture("npc-header", HeadTextures.NPC_HEADER), t("gui.npc-choice-ordinary"),
                lines("gui.npc-choice-ordinary-sub", "gui.npc-choice-ordinary-hint", "gui.npc-choice-ordinary-action")), e -> {
            viewer.closeInventory();
            replayClick();
        });
        footer(null);
    }

    private List<String> lines(String subtitle, String hint, String action) {
        List<String> out = new java.util.ArrayList<>();
        out.add(t(subtitle));
        out.add("");
        out.add(t(hint));
        out.addAll(LoreBlocks.clicks(messages()::getRaw, Hint.of(Click.LEFT, t(action))));
        return out;
    }

    /**
     * Fires the click again so the NPC's own plugin (shop, smith, guildmaster...) opens its menu. The contract
     * handler lets this one pass: {@link me.lovelace.lovecontracts.npc.manager.NpcQuestManager#allowOrdinaryClick}.
     */
    private void replayClick() {
        if (npc == null || !viewer.isOnline()) return;
        plugin.getNpcQuestManager().allowOrdinaryClick(viewer);
        NpcClickEvent replay = new NpcClickEvent(viewer, npc, NpcClick.RIGHT);
        Bukkit.getPluginManager().callEvent(replay);
        plugin.getNpcQuestManager().forgetOrdinaryClick(viewer);
    }
}
