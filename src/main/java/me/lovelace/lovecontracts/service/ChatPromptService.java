package me.lovelace.lovecontracts.service;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.lovelace.lovecontracts.LoveContracts;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * "Type a line in chat" prompts of the chain builder. The answer is swallowed (it never reaches public chat) and handled
 * on the main thread. One prompt per player; it dies on timeout, on quit, or when the player types {@code отмена}.
 */
public final class ChatPromptService implements Listener {

    private static final long TIMEOUT_MILLIS = 120_000L;

    private record Prompt(long expiresAt, Consumer<String> onInput, Runnable onCancel) {
    }

    private final LoveContracts plugin;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public ChatPromptService(LoveContracts plugin) {
        this.plugin = plugin;
    }

    /** Asks the player for one line. A message tells them what to type; an earlier prompt is cancelled. */
    public void ask(Player player, String messageKey, Consumer<String> onInput, Runnable onCancel) {
        player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getMessageManager().text(messageKey)));
        player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getMessageManager().text("gui.prompt-hint")));
        Prompt old = prompts.put(player.getUniqueId(),
                new Prompt(System.currentTimeMillis() + TIMEOUT_MILLIS, onInput, onCancel));
        if (old != null && old.onCancel() != null) old.onCancel().run();
    }

    public boolean has(Player player) {
        return prompts.containsKey(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Prompt prompt = prompts.remove(event.getPlayer().getUniqueId());
        if (prompt == null) return;
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (System.currentTimeMillis() > prompt.expiresAt()) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getMessageManager().text("gui.prompt-timeout")));
                if (prompt.onCancel() != null) prompt.onCancel().run();
                return;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.equals("отмена") || lower.equals("cancel")) {
                if (prompt.onCancel() != null) prompt.onCancel().run();
                return;
            }
            prompt.onInput().accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
    }

    public void clear() {
        prompts.clear();
    }
}
