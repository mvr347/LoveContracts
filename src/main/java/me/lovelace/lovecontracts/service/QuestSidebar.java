package me.lovelace.lovecontracts.service;

import dev.lovelace.loveessential.api.ScoreboardService;
import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.util.CoinFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import java.time.Duration;
import java.util.*;

/** Owns only an LE API lease, never the player's Bukkit scoreboard or personal settings. */
public final class QuestSidebar implements Listener, AutoCloseable {
    private final LoveContracts plugin;
    private final SidebarLeaseTracker<ScoreboardService.Board> leases = new SidebarLeaseTracker<>();
    private final Map<UUID, String> focused = new HashMap<>();
    private ScoreboardService service;
    private final BukkitTask task;
    private boolean warned;

    public QuestSidebar(LoveContracts plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 1, 5);
    }

    public void focus(UUID player, String id) { focused.put(player, id); }
    public String focusedId(UUID player) { return focused.get(player); }

    private void refresh() {
        ScoreboardService next = plugin.getConfig().getBoolean("quest-scoreboard.enabled", true)
                ? Bukkit.getServicesManager().load(ScoreboardService.class) : null;
        if (service != next) {
            service = next;
            leases.provider(service == null ? null : (player, board) -> {
                var lease = service.show(plugin, player,
                        Math.clamp(plugin.getConfig().getInt("quest-scoreboard.priority", 20), -1000, 1000),
                        Duration.ofSeconds(1), true, board);
                return new SidebarLeaseTracker.Lease<>() {
                    public void update(ScoreboardService.Board content) { lease.update(content); }
                    public void close() { lease.close(); }
                };
            });
            warned = false;
        }
        if (service == null) return;
        long now = System.nanoTime();
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (!plugin.getNpcQuestManager().isQuestboardEnabled(player.getUniqueId())) {
                    leases.remove(player.getUniqueId());
                    continue;
                }
                var objectives = plugin.getNpcQuestManager().objectives(player);
                var objective = QuestSidebarText.select(objectives, focused.get(player.getUniqueId()));
                if (objective == null) {
                    focused.remove(player.getUniqueId());
                    leases.remove(player.getUniqueId());
                } else {
                    var lines = QuestSidebarText.lines(objective, objectives.size() - 1).stream()
                            .map(line -> CoinFormat.component(player, line))
                            .toList();
                    leases.render(player.getUniqueId(),
                            new ScoreboardService.Board(Component.text("Поручение", NamedTextColor.AQUA), lines), now);
                }
            } catch (RuntimeException error) {
                leases.remove(player.getUniqueId());
                if (!warned) { plugin.getLogger().warning("Quest scoreboard unavailable: " + error.getMessage()); warned = true; }
            }
        }
    }

    public static String plain(String text) { return MiniMessage.miniMessage().stripTags(text == null ? "" : text); }
    @EventHandler public void quit(PlayerQuitEvent event) { leases.remove(event.getPlayer().getUniqueId()); focused.remove(event.getPlayer().getUniqueId()); }
    @Override public void close() { task.cancel(); leases.close(); focused.clear(); HandlerList.unregisterAll(this); }
}
