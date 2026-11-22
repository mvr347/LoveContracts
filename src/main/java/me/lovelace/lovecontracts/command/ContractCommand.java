package me.lovelace.lovecontracts.command;

import me.lovelace.lovecontracts.LoveContracts;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public class ContractCommand implements CommandExecutor, TabCompleter {

    private final LoveContracts plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ContractCommand(LoveContracts plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>Команда доступна только игрокам.</red>"));
            return true;
        }

        if (!player.hasPermission("lovecontracts.use")) {
            return true;
        }

        if (args.length == 0) {
            plugin.getContractBoard().open(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "stats" -> plugin.getContractBoard().open(player);
            case "questboard" -> toggleQuestboard(player, args);
            case "abandon" -> {
                if (args.length < 2) {
                    player.sendMessage(mm.deserialize("<red>Использование: /contracts abandon <id></red>"));
                    return true;
                }
                player.sendMessage(mm.deserialize("<yellow>Отмена поручения доступна на доске контрактов (Shift+ПКМ).</yellow>"));
            }
            default -> plugin.getContractBoard().open(player);
        }
        return true;
    }

    private void toggleQuestboard(Player player, String[] args) {
        var nqm = plugin.getNpcQuestManager();
        boolean next;
        if (args.length >= 2) {
            if (args[1].equalsIgnoreCase("on")) next = true;
            else if (args[1].equalsIgnoreCase("off")) next = false;
            else {
                player.sendMessage(mm.deserialize("<red>Использование: /contracts questboard [on|off]</red>"));
                return;
            }
        } else {
            next = !nqm.isQuestboardEnabled(player.getUniqueId());
        }
        nqm.setQuestboardEnabled(player.getUniqueId(), next);
        player.sendMessage(mm.deserialize(next
                ? "<green>Табло поручений включено.</green>"
                : "<gray>Табло поручений выключено.</gray>"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("lovecontracts.use")) return List.of();

        if (args.length == 1) {
            return List.of("questboard", "stats", "abandon").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("questboard")) {
            return List.of("on", "off").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
