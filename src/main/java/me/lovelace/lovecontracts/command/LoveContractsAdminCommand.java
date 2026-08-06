package me.lovelace.lovecontracts.command;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.integration.CitizensIntegration;
import me.lovelace.lovecontracts.model.Contract;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
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
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Единая административная команда плагина: {@code /lovecontractsadmin <subcommand>}.
 * <p>
 * Раньше это была команда {@code /lovecontracts} — она уже была чисто admin-командой (никогда
 * не имела отдельного player-facing назначения), поэтому переименование сюда является простым
 * ребрендингом под единый для экосистемы Love* стиль имени admin-команды, без переноса какой-либо
 * новой логики. Старое имя {@code lovecontracts} (и его короткие алиасы {@code lc}/{@code lca}/
 * {@code lcadmin}) остаётся рабочим алиасом в plugin.yml — команды, привязанные к нему по
 * привычке, продолжают работать один в один, без отдельного redirect-сообщения.
 */
public class LoveContractsAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "reload", "rotate", "create", "npc", "sign", "complete", "fail", "stats", "reset", "toggle", "help");
    private static final List<String> SIGN_SUBCOMMANDS = List.of("bind", "unbind", "list");
    private static final List<String> PLAYER_ARG_SUBCOMMANDS = List.of("complete", "fail", "stats", "reset", "toggle");

    private final LoveContracts plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final CitizensIntegration citizens = new CitizensIntegration();

    public LoveContractsAdminCommand(LoveContracts plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("lovecontracts.admin")) {
            sender.sendMessage(mm.deserialize("<red>✖ Недостаточно прав.</red>"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> reload(sender);
            case "rotate" -> rotate(sender);
            case "create" -> create(sender);
            case "npc" -> npc(sender);
            case "sign" -> sign(sender, args);
            case "complete" -> completeOrFail(sender, args, true);
            case "fail" -> completeOrFail(sender, args, false);
            case "stats" -> stats(sender, args);
            case "reset" -> reset(sender, args);
            case "toggle" -> toggle(sender, args);
            case "help" -> sendHelp(sender);
            default -> {
                sender.sendMessage(mm.deserialize("<red>✖ Неизвестная подкоманда.</red>"));
                sendHelp(sender);
            }
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(mm.deserialize("<dark_gray>========== <aqua>LoveContracts Admin</aqua> ==========</dark_gray>"));
        sender.sendMessage(mm.deserialize(
                "<aqua>/lovecontractsadmin create</aqua> <gray>- Открыть меню создания контракта</gray>"));
        sender.sendMessage(mm.deserialize(
                "<aqua>/lovecontractsadmin sign bind [слот]</aqua> <gray>- Привязать целевую табличку</gray>"));
        sender.sendMessage(mm.deserialize(
                "<aqua>/lovecontractsadmin sign unbind</aqua> <gray>- Отвязать целевую табличку</gray>"));
        sender.sendMessage(mm.deserialize(
                "<aqua>/lovecontractsadmin sign list</aqua> <gray>- Список привязанных табличек</gray>"));
        sender.sendMessage(mm.deserialize(
                "<aqua>/lovecontractsadmin reload</aqua> <gray>- Перезагрузить конфигурацию и heads.yml</gray>"));
        sender.sendMessage(mm.deserialize(
                "<aqua>/lovecontractsadmin rotate</aqua> <gray>- Принудительно ротировать контракты</gray>"));
        sender.sendMessage(mm.deserialize(
                "<aqua>/lovecontractsadmin npc</aqua> <gray>- Привязать NPC Citizens (по взгляду)</gray>"));
        sender.sendMessage(mm.deserialize(
                "<aqua>/lovecontractsadmin complete <игрок> <id></aqua> <gray>- Выполнить контракт</gray>"));
        sender.sendMessage(mm.deserialize(
                "<aqua>/lovecontractsadmin fail <игрок> <id></aqua> <gray>- Провалить контракт</gray>"));
        sender.sendMessage(mm.deserialize(
                "<aqua>/lovecontractsadmin stats <игрок></aqua> <gray>- Открыть статистику игрока</gray>"));
        sender.sendMessage(mm.deserialize(
                "<aqua>/lovecontractsadmin reset <игрок></aqua> <gray>- Сбросить статистику игрока</gray>"));
        sender.sendMessage(mm.deserialize(
                "<aqua>/lovecontractsadmin toggle <игрок></aqua> <gray>- Вкл/Выкл доступ игроку</gray>"));
        sender.sendMessage(mm.deserialize(
                "<dark_gray>(алиасы: /lovecontracts, /lc, /lca, /lcadmin)</dark_gray>"));
        sender.sendMessage(mm.deserialize("<dark_gray>=========================================</dark_gray>"));
    }

    private void create(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>✖ Команда только для игроков.</red>"));
            return;
        }
        plugin.getContractCreateGUI().open(player);
    }

    private void toggle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<red>✖ Использование: /lovecontractsadmin toggle <игрок></red>"));
            return;
        }
        Player target = org.bukkit.Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(mm.deserialize("<red>✖ Игрок не найден.</red>"));
            return;
        }
        boolean enabled = plugin.getContractManager().togglePlayerContracts(target.getUniqueId());
        if (enabled) {
            sender.sendMessage(mm
                    .deserialize("<green>✔ Доступ к контрактам для игрока " + target.getName() + " ВКЛЮЧЕН.</green>"));
        } else {
            sender.sendMessage(
                    mm.deserialize("<red>✔ Доступ к контрактам для игрока " + target.getName() + " ОТКЛЮЧЕН.</red>"));
        }
    }

    private void reload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.loadHeadsConfig();
        plugin.getRegistry().loadFromConfig();
        plugin.getSignManager().load();
        plugin.getSignManager().updateAllSigns();
        sender.sendMessage(mm.deserialize("<green>✔ Конфигурация и heads.yml перезагружены — загружено " +
                plugin.getRegistry().size() + " контрактов.</green>"));
    }

    private void rotate(CommandSender sender) {
        plugin.getContractManager().forceRotate();
        plugin.getSignManager().updateAllSigns();
        sender.sendMessage(mm.deserialize("<green>✔ Контракты обновлены вручную.</green>"));
    }

    private void npc(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>✖ Команда только для игроков.</red>"));
            return;
        }
        if (!sender.hasPermission("lovecontracts.npc")) {
            sender.sendMessage(mm.deserialize("<red>✖ Недостаточно прав.</red>"));
            return;
        }
        if (!citizens.isAvailable()) {
            player.sendMessage(mm.deserialize("<red>✖ Плагин Citizens не установлен или отключен.</red>"));
            return;
        }
        Entity looked = citizens.lookedAtNpc(player, 6.0);
        if (looked == null) {
            player.sendMessage(mm.deserialize("<red>✖ Сначала посмотрите на NPC Citizens.</red>"));
            return;
        }
        Integer id = citizens.npcId(looked);
        if (id == null) {
            player.sendMessage(mm.deserialize("<red>✖ Не удалось определить ID NPC.</red>"));
            return;
        }
        plugin.getConfig().set("npc.id", id);
        plugin.saveConfig();
        player.sendMessage(
                mm.deserialize("<green>✔ Привязан NPC контрактов #" + id + ". ПКМ открывает доску.</green>"));
    }

    private void sign(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>✖ Команда только для игроков.</red>"));
            return;
        }
        if (!sender.hasPermission("lovecontracts.sign")) {
            sender.sendMessage(mm.deserialize("<red>✖ Недостаточно прав.</red>"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<red>✖ Использование: /lovecontractsadmin sign <bind|unbind|list> [слот]</red>"));
            return;
        }

        String action = args[1].toLowerCase();
        Block targetBlock = player.getTargetBlockExact(6);

        switch (action) {
            case "bind" -> {
                if (targetBlock == null || !(targetBlock.getState() instanceof Sign)) {
                    player.sendMessage(
                            mm.deserialize("<red>✖ Вы должны смотреть на табличку (дистанция до 6 блоков).</red>"));
                    return;
                }
                int slot;
                if (args.length >= 3) {
                    try {
                        slot = Integer.parseInt(args[2]);
                        if (slot < 0)
                            throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        player.sendMessage(mm.deserialize(
                                "<red>✖ Номер слота должен быть неотрицательным числом (0, 1, 2...).</red>"));
                        return;
                    }
                } else {
                    slot = plugin.getSignManager().getNextAvailableSlot();
                }
                plugin.getSignManager().bindSign(targetBlock.getLocation(), slot);
                player.sendMessage(mm.deserialize("<green>✔ Табличка успешно привязана к слоту #" + (slot + 1)
                        + " (индекс " + slot + ").</green>"));
            }
            case "unbind" -> {
                if (targetBlock == null || !(targetBlock.getState() instanceof Sign)) {
                    player.sendMessage(mm.deserialize("<red>✖ Вы должны смотреть на табличку.</red>"));
                    return;
                }
                boolean removed = plugin.getSignManager().unbindSign(targetBlock.getLocation());
                if (removed) {
                    player.sendMessage(mm.deserialize("<green>✔ Табличка отвязана.</green>"));
                } else {
                    player.sendMessage(mm.deserialize("<red>✖ Данная табличка не была привязана.</red>"));
                }
            }
            case "list" -> {
                Map<Location, Integer> signs = plugin.getSignManager().getBoundSigns();
                if (signs.isEmpty()) {
                    player.sendMessage(mm.deserialize("<yellow>Нет привязанных табличек.</yellow>"));
                    return;
                }
                player.sendMessage(mm.deserialize("<gold>=== Привязанные таблички (" + signs.size() + ") ===</gold>"));
                for (Map.Entry<Location, Integer> entry : signs.entrySet()) {
                    Location l = entry.getKey();
                    player.sendMessage(mm.deserialize("<gray>• Слот <white>#" + (entry.getValue() + 1) +
                            "</white> [" + l.getWorld().getName() + ", " + l.getBlockX() + ", " + l.getBlockY() + ", "
                            + l.getBlockZ() + "]</gray>"));
                }
            }
            default ->
                player.sendMessage(mm.deserialize("<red>✖ Использование: /lovecontractsadmin sign <bind|unbind|list></red>"));
        }
    }

    private void completeOrFail(CommandSender sender, String[] args, boolean complete) {
        if (args.length < 3) {
            sender.sendMessage(
                    mm.deserialize("<red>✖ Использование: /lovecontractsadmin " + args[0] + " <игрок> <idКонтракта></red>"));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(mm.deserialize("<red>✖ Игрок не найден (должен быть в сети).</red>"));
            return;
        }
        Contract contract = plugin.getRegistry().getContract(args[2]);
        if (contract == null) {
            sender.sendMessage(mm.deserialize("<red>✖ Неизвестный ID контракта.</red>"));
            return;
        }
        if (complete) {
            plugin.getContractManager().completeContract(target, contract);
            sender.sendMessage(mm.deserialize("<green>✔ Контракт " + contract.getId() +
                    " принудительно выполнен для " + target.getName() + "</green>"));
        } else {
            plugin.getContractManager().failContract(target, contract);
            sender.sendMessage(mm.deserialize("<green>✔ Контракт " + contract.getId() +
                    " принудительно провален для " + target.getName() + "</green>"));
        }
    }

    private void stats(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<red>✖ Использование: /lovecontractsadmin stats <игрок></red>"));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(mm.deserialize("<red>✖ Игрок не найден (должен быть в сети).</red>"));
            return;
        }
        plugin.getContractGUI().open(target);
        sender.sendMessage(mm.deserialize("<green>✔ Открыто меню контрактов для " + target.getName() + "</green>"));
    }

    private void reset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<red>✖ Использование: /lovecontractsadmin reset <игрок></red>"));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(mm.deserialize("<red>✖ Игрок не найден (должен быть в сети).</red>"));
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
        sender.sendMessage(mm.deserialize("<green>✔ Ежедневная статистика сброшена для " + name + "</green>"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sign")) {
            return SIGN_SUBCOMMANDS.stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
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
