package me.lovelace.lovecontracts.command;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.integration.CitizensIntegration;
import me.lovelace.lovecontracts.model.Contract;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class LoveContractsAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "reload", "rotate", "npc", "sign", "complete", "fail", "stats", "reset", "diag");
    private static final List<String> PLAYER_ARG_SUBCOMMANDS = List.of("complete", "fail", "stats", "reset");

    private final LoveContracts plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final CitizensIntegration citizens = new CitizensIntegration();

    public LoveContractsAdminCommand(LoveContracts plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(mm.deserialize(
                    "<yellow>Usage: /lovecontracts <reload|rotate|npc|sign|complete|fail|stats|reset|diag></yellow>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> reload(sender);
            case "rotate" -> rotate(sender);
            case "npc" -> npc(sender);
            case "sign" -> sign(sender);
            case "complete" -> completeOrFail(sender, args, true);
            case "fail" -> completeOrFail(sender, args, false);
            case "stats" -> stats(sender, args);
            case "reset" -> reset(sender, args);
            case "diag" -> diag(sender);
            default -> sender.sendMessage(mm.deserialize("<red>Unknown subcommand.</red>"));
        }
        return true;
    }

    private void reload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getRegistry().loadFromConfig();
        sender.sendMessage(mm.deserialize("<green>Configs reloaded — " +
                plugin.getRegistry().size() + " contracts loaded.</green>"));
    }

    private void rotate(CommandSender sender) {
        plugin.getContractManager().forceRotate();
        sender.sendMessage(mm.deserialize("<green>Contracts rotated manually.</green>"));
    }

    private void npc(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>Players only.</red>"));
            return;
        }
        if (!sender.hasPermission("lovecontracts.npc")) {
            sender.sendMessage(mm.deserialize("<red>No permission.</red>"));
            return;
        }
        if (!citizens.isAvailable()) {
            player.sendMessage(mm.deserialize("<red>Citizens is not installed or enabled.</red>"));
            return;
        }
        Entity looked = citizens.lookedAtNpc(player, 6.0);
        if (looked == null) {
            player.sendMessage(mm.deserialize("<red>Look at a Citizens NPC first.</red>"));
            return;
        }
        Integer id = citizens.npcId(looked);
        if (id == null) {
            player.sendMessage(mm.deserialize("<red>Could not resolve NPC id.</red>"));
            return;
        }
        plugin.getConfig().set("npc.id", id);
        plugin.saveConfig();
        player.sendMessage(mm.deserialize("<green>Bound Contract NPC #" + id + ". Right-click opens the board.</green>"));
    }

    private void sign(CommandSender sender) {
        if (!sender.hasPermission("lovecontracts.sign")) {
            sender.sendMessage(mm.deserialize("<red>No permission.</red>"));
            return;
        }
        sender.sendMessage(mm.deserialize(
                "<yellow>No binding step needed — place any sign with first line " +
                "<gold>[LoveContracts]</gold> and second line = a contract id from contracts.yml.</yellow>"));
    }

    private void completeOrFail(CommandSender sender, String[] args, boolean complete) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<red>Usage: /lovecontracts " + args[0] + " <player> <contractId></red>"));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(mm.deserialize("<red>Player not found (must be online).</red>"));
            return;
        }
        Contract contract = plugin.getRegistry().getContract(args[2]);
        if (contract == null) {
            sender.sendMessage(mm.deserialize("<red>Unknown contract id.</red>"));
            return;
        }
        if (complete) {
            plugin.getContractManager().completeContract(target, contract);
            sender.sendMessage(mm.deserialize("<green>Force-completed " + contract.getId() +
                    " for " + target.getName() + "</green>"));
        } else {
            plugin.getContractManager().failContract(target, contract);
            sender.sendMessage(mm.deserialize("<green>Force-failed " + contract.getId() +
                    " for " + target.getName() + "</green>"));
        }
    }

    private void stats(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<red>Usage: /lovecontracts stats <player></red>"));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(mm.deserialize("<red>Player not found (must be online).</red>"));
            return;
        }
        plugin.getStatsGUI().open(target);
        sender.sendMessage(mm.deserialize("<green>Opened stats for " + target.getName() + "</green>"));
    }

    private void reset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<red>Usage: /lovecontracts reset <player></red>"));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(mm.deserialize("<red>Player not found (must be online).</red>"));
            return;
        }
        UUID uuid = target.getUniqueId();
        String name = target.getName();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE contract_stats SET daily_completed = 0, daily_failed = 0, daily_accepted = 0, " +
                         "last_daily_reset = datetime('now') WHERE player_uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Reset failed", e);
            }
        });
        sender.sendMessage(mm.deserialize("<green>Reset daily stats for " + name + "</green>"));
    }

    private void diag(CommandSender sender) {
        boolean loveCore = plugin.getServer().getPluginManager().isPluginEnabled("LoveCore");
        boolean placeholderApi = plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        boolean citizensPlugin = citizens.isAvailable();

        sender.sendMessage(mm.deserialize("<gold>=== LoveContracts Diagnostics ===</gold>"));
        sender.sendMessage(mm.deserialize("<gray>Contracts loaded: <white>" + plugin.getRegistry().size() + "</white></gray>"));
        sender.sendMessage(mm.deserialize("<gray>Active now: <white>" +
                plugin.getContractManager().getActiveContracts().size() + "</white></gray>"));
        sender.sendMessage(mm.deserialize("<gray>LoveCore: " + (loveCore ? "<green>found</green>" : "<red>missing (no money rewards/penalties)</red>") + "</gray>"));
        sender.sendMessage(mm.deserialize("<gray>PlaceholderAPI: " + (placeholderApi ? "<green>found</green>" : "<red>missing</red>") + "</gray>"));
        sender.sendMessage(mm.deserialize("<gray>Citizens: " + (citizensPlugin ? "<green>found</green>" : "<red>missing</red>") + "</gray>"));
        int boundNpc = plugin.getConfig().getInt("npc.id", -1);
        sender.sendMessage(mm.deserialize("<gray>Bound NPC: <white>" + (boundNpc < 0 ? "none" : "#" + boundNpc) + "</white></gray>"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && PLAYER_ARG_SUBCOMMANDS.contains(args[0].toLowerCase())) {
            return plugin.getServer().getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("complete") || args[0].equalsIgnoreCase("fail"))) {
            return plugin.getRegistry().getAll().stream().map(Contract::getId)
                    .filter(id -> id.startsWith(args[2].toLowerCase())).toList();
        }
        return List.of();
    }
}
