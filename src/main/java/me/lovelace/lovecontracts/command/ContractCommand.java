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
            sender.sendMessage("Команда только для игроков.");
            return true;
        }

        if (!player.hasPermission("lovecontracts.use")) {
            player.sendMessage(mm.deserialize("<red>Недостаточно прав.</red>"));
            return true;
        }

        if (args.length == 0) {
            plugin.getContractGUI().open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "stats" -> plugin.getContractGUI().open(player);
            case "abandon" -> {
                if (args.length < 2) {
                    player.sendMessage(mm.deserialize("<red>Использование: /contracts abandon <id></red>"));
                    return true;
                }
                player.sendMessage(mm.deserialize("<yellow>Отмена контракта пока не реализована.</yellow>"));
            }
            default -> plugin.getContractGUI().open(player);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("stats", "abandon").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
