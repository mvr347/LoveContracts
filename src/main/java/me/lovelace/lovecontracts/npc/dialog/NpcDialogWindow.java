package me.lovelace.lovecontracts.npc.dialog;

import dev.lovelace.lovecore.api.npc.NpcHandle;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.lovelace.lovecontracts.LoveContracts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The native dialog a player sees when clicking an NPC from npc_dialogs.yml: a vertical list of text buttons,
 * the first being the active quest of this NPC when there is one. Small talk reopens the window with a new line
 * instead of writing to the chat, so spamming it cannot flood the chat.
 */
public final class NpcDialogWindow {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern PLAIN_KEY = Pattern.compile("[A-Za-z0-9_\\-.]{1,64}");

    public enum Kind { QUEST, MAIN, GAME, SMALL_TALK, CLOSE }

    public record Option(Kind kind, String label, String payload) {
    }

    /** A quest this NPC is a control point of: its id and the text of the button. */
    public record QuestLine(String questId, String text) {
    }

    private final LoveContracts plugin;
    private final NpcDialogs dialogs;

    public NpcDialogWindow(LoveContracts plugin, NpcDialogs dialogs) {
        this.plugin = plugin;
        this.dialogs = dialogs;
    }

    /**
     * The buttons in order: [QUEST] lines, main, game, small talk, close. The game button is left out when there is
     * no game for the NPC or LoveActivities is missing; the main button when the NPC has none.
     */
    public static List<Option> options(NpcDialogs.Entry entry, NpcDialogs.Labels labels, List<QuestLine> quests,
                                       boolean gamesAvailable) {
        List<Option> out = new ArrayList<>();
        for (QuestLine q : quests) out.add(new Option(Kind.QUEST, labels.questPrefix() + q.text(), q.questId()));
        if (entry.main() != NpcDialogs.Main.NONE && !entry.mainLabel().isBlank()) {
            out.add(new Option(Kind.MAIN, entry.mainLabel(), entry.main().name()));
        }
        if (!entry.game().isBlank() && gamesAvailable) {
            out.add(new Option(Kind.GAME, entry.gameLabel().isBlank() ? labels.gameDefault() : entry.gameLabel(), entry.game()));
        }
        out.add(new Option(Kind.SMALL_TALK, labels.smallTalk(), ""));
        out.add(new Option(Kind.CLOSE, labels.close(), ""));
        return out;
    }

    private boolean gamesAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("LoveActivities");
    }

    /** Opens the window; {@code line} is what the NPC says, or null for a fresh greeting. */
    public void open(Player player, NpcHandle npc, NpcDialogs.Entry entry, List<QuestLine> quests, String line) {
        if (player == null || !player.isOnline() || npc == null) return;
        String speech = line != null ? line : dialogs.pick(player.getUniqueId(), entry, "greeting", entry.greetings());
        String name = npc.name() != null && !npc.name().isBlank() ? MM.escapeTags(MM.stripTags(npc.name())) : entry.id();
        var options = options(entry, dialogs.labels(), quests.size() > 3 ? quests.subList(0, 3) : quests, gamesAvailable());

        var callbackOptions = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(5)).build();
        List<ActionButton> buttons = new ArrayList<>();
        for (Option option : options) {
            buttons.add(ActionButton.create(MM.deserialize(option.kind() == Kind.QUEST
                            ? "<gold>" + MM.escapeTags(option.label()) : "<white>" + MM.escapeTags(option.label())),
                    null, 260, DialogAction.customClick((response, audience) -> {
                        if (!(audience instanceof Player actor) || !actor.getUniqueId().equals(player.getUniqueId())
                                || !plugin.isEnabled()) return;
                        Bukkit.getScheduler().runTask(plugin, () -> act(actor, npc, entry, quests, option));
                    }, callbackOptions)));
        }
        player.showDialog(Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(MM.deserialize("<gold>" + name + "</gold>")).pause(false).canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(Component.text(speech))))
                        .build())
                .type(DialogType.multiAction(buttons, null, 1))));
    }

    private void act(Player player, NpcHandle npc, NpcDialogs.Entry entry, List<QuestLine> quests, Option option) {
        if (!player.isOnline()) return;
        switch (option.kind()) {
            case QUEST -> plugin.getNpcQuestManager().openNpcInteraction(player, option.payload());
            case MAIN -> {
                if (entry.main() == NpcDialogs.Main.BOARD) plugin.getContractBoard().open(player);
                else plugin.getNpcQuestManager().replayOrdinaryClick(player, npc);
            }
            case GAME -> playGame(player, npc, entry);
            case SMALL_TALK -> open(player, npc, entry, quests, dialogs.pick(player.getUniqueId(), entry, "talk", entry.smallTalk()));
            case CLOSE -> {
            }
        }
    }

    /** Starts the game through LoveActivities' /activities npc, allowed only inside this short metadata window. */
    private void playGame(Player player, NpcHandle npc, NpcDialogs.Entry entry) {
        if (!gamesAvailable()) {
            player.sendMessage(MM.deserialize("<yellow>" + MM.escapeTags(dialogs.labels().noGamePlugin()) + "</yellow>"));
            return;
        }
        String key = npc.key();
        if (key == null || !PLAIN_KEY.matcher(key).matches()) return;
        player.setMetadata("npc_play_until", new FixedMetadataValue(plugin, System.currentTimeMillis() + 5_000L));
        try {
            player.performCommand("активности npc " + key + " " + entry.game().toLowerCase(java.util.Locale.ROOT));
        } finally {
            player.removeMetadata("npc_play_until", plugin);
        }
    }
}
