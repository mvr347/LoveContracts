package me.lovelace.lovecontracts.gui.board;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.gui.LoreBlocks;
import me.lovelace.lovecontracts.gui.LoreBlocks.Click;
import me.lovelace.lovecontracts.gui.LoreBlocks.Hint;
import me.lovelace.lovecontracts.manager.MessageManager;
import me.lovelace.lovecontracts.player.manager.PlayerContractManager;
import me.lovelace.lovecontracts.player.model.PlayerContract;
import me.lovelace.lovecontracts.player.model.PlayerContractObjectiveType;
import me.lovelace.lovecontracts.player.model.PlayerContractStatus;
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

/** Cards of the two player-to-player tabs: the offers of other players and the viewer's own contracts. */
final class PlayerCards {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final LoveContracts plugin;

    PlayerCards(LoveContracts plugin) {
        this.plugin = plugin;
    }

    private MessageManager messages() {
        return plugin.getMessageManager();
    }

    /** Text typed by a player must not be read as MiniMessage. */
    private static String safe(String value) {
        return MM.escapeTags(value == null ? "" : value);
    }

    private static List<Component> components(Player viewer, List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(CoinFormat.component(viewer, line));
        return out;
    }

    /** Card of an open offer: the task and the reward in sections, one click to accept. */
    ItemStack offerCard(Player viewer, PlayerContract c) {
        String base64 = switch (c.getObjectiveType()) {
            case KILL_ENTITY -> HeadUtil.getHeadTexture("offer-kill", HeadTextures.DIFFICULTY_HARD);
            case DELIVER_ITEM -> HeadUtil.getHeadTexture("offer-deliver", HeadTextures.DIFFICULTY_EASY);
            case CUSTOM -> HeadUtil.getHeadTexture("offer-custom", HeadTextures.DIFFICULTY_MEDIUM);
        };
        MessageManager messages = messages();
        List<String> lines = new ArrayList<>();
        lines.add(messages.text("gui.player-board-subtitle"));
        lines.add("");
        lines.add(messages.text("gui.section-task"));
        lines.add(messages.text("gui.player-task-creator", Map.of("creator", safe(String.valueOf(c.getCreatorName())))));
        lines.add(messages.text("gui.player-task-objective", Map.of("objective", safe(objectiveLabel(c)))));
        lines.add(messages.text("gui.player-task-deadline", Map.of("time", timeLeft(c))));
        lines.add(messages.text("gui.player-task-id", Map.of("id", PlayerContractManager.shortId(c.getId()))));
        lines.add("");
        lines.add(messages.text("gui.section-reward"));
        lines.add("  " + CoinFormat.format(c.getGoldReward()));
        lines.addAll(LoreBlocks.clicks(messages::getRaw, Hint.of(Click.LEFT, messages.text("gui.action-accept"))));
        return HeadUtil.createBase64Head(base64, "<gold>" + safe(c.getDescription()) + "</gold>", components(viewer, lines));
    }

    /**
     * Own contract: the state is in the name, the role and the progress in the info section, the commands that move
     * it on in their own section, and the click that cancels or gives it up while it is still open.
     */
    ItemStack mineCard(Player viewer, PlayerContract c) {
        boolean creator = c.getCreatorId().equals(viewer.getUniqueId());
        String base64 = switch (c.getStatus()) {
            case OPEN, IN_PROGRESS, PENDING_REVIEW -> HeadUtil.getHeadTexture("active-quest", HeadTextures.STARTER_QUEST);
            case COMPLETED -> HeadUtil.getHeadTexture("completed", HeadTextures.CONTRACT_STATUS_DEFAULT);
            case ABANDONED, CANCELLED, EXPIRED -> HeadUtil.getHeadTexture("failed", HeadTextures.CONTRACT_STATUS_DEFAULT);
        };
        MessageManager messages = messages();
        String shortId = PlayerContractManager.shortId(c.getId());
        String name = messages.text("gui.item-name-state", Map.of(
                "name", "<gold>" + safe(c.getDescription()) + "</gold>", "state", messages.text(statusKey(c.getStatus()))));

        List<String> lines = new ArrayList<>();
        lines.add(messages.text("gui.my-subtitle"));
        lines.add("");
        lines.add(messages.text("gui.section-info"));
        lines.add(messages.text("gui.my-role", Map.of(
                "role", messages.text(creator ? "gui.my-role-creator" : "gui.my-role-executor"))));
        if (c.getObjectiveType() == PlayerContractObjectiveType.KILL_ENTITY) {
            lines.add(messages.text("gui.my-progress", Map.of(
                    "current", String.valueOf(c.getObjectiveProgress()),
                    "required", String.valueOf(c.getObjectiveAmount()))));
            lines.add(messages.text("gui.progress-bar", Map.of(
                    "bar", LoreBar.bar(c.getObjectiveProgress(), c.getObjectiveAmount()))));
        }
        lines.add(messages.text("gui.my-id", Map.of("id", shortId)));

        List<String> commands = new ArrayList<>();
        boolean running = c.getStatus() == PlayerContractStatus.IN_PROGRESS;
        if (c.getObjectiveType() == PlayerContractObjectiveType.DELIVER_ITEM && !creator && running) {
            commands.add(messages.text("gui.my-command-turnin", Map.of("id", shortId)));
        }
        if (c.getObjectiveType() == PlayerContractObjectiveType.CUSTOM && !creator && running) {
            commands.add(messages.text("gui.my-command-submit", Map.of("id", shortId)));
        }
        if (creator && c.getStatus() == PlayerContractStatus.PENDING_REVIEW) {
            commands.add(messages.text("gui.my-command-review", Map.of("id", shortId)));
        }
        if (!commands.isEmpty()) {
            lines.add("");
            lines.add(messages.text("gui.section-commands"));
            lines.addAll(commands);
        }
        if (!c.getStatus().isTerminal()) {
            lines.addAll(LoreBlocks.clicks(messages::getRaw, Hint.of(Click.LEFT,
                    messages.text(creator ? "gui.action-cancel-own" : "gui.action-abandon"))));
        }
        return HeadUtil.createBase64Head(base64, name, components(viewer, lines));
    }

    /** Message key of a status, which carries its colour: the name shows it after {@code | }. */
    static String statusKey(PlayerContractStatus status) {
        return switch (status) {
            case OPEN -> "gui.my-status-open";
            case IN_PROGRESS -> "gui.my-status-in-progress";
            case PENDING_REVIEW -> "gui.my-status-pending-review";
            case COMPLETED -> "gui.my-status-completed";
            case ABANDONED, CANCELLED -> "gui.my-status-cancelled";
            case EXPIRED -> "gui.my-status-expired";
        };
    }

    private static String objectiveLabel(PlayerContract c) {
        if (c.getObjectiveType() == PlayerContractObjectiveType.CUSTOM) {
            return "Произвольная (сдать на проверку)";
        }
        return c.getObjectiveAmount() + "x " + c.getObjectiveTarget();
    }

    private static String timeLeft(PlayerContract c) {
        long minutes = java.time.Duration.between(java.time.Instant.now(), c.getDeadline()).toMinutes();
        if (minutes <= 0) return "истекает";
        if (minutes < 60) return minutes + " мин";
        return (minutes / 60) + " ч";
    }
}
