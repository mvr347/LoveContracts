package me.lovelace.lovecontracts.player;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.player.gui.PlayerContractBoardGUI;
import me.lovelace.lovecontracts.player.gui.PlayerContractMyGUI;
import me.lovelace.lovecontracts.player.manager.CreateContractRequest;
import me.lovelace.lovecontracts.player.manager.PlayerContractManager;
import me.lovelace.lovecontracts.player.model.PlayerContractObjectiveType;
import me.lovelace.lovecontracts.player.model.PlayerContractVisibility;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * {@code /pcontract} — весь player-to-player контрактный флоу. Гибкость закладывается
 * через свободные флаги в create (withitem, clanonly, rep:N) вместо жёсткого набора
 * обязательных параметров — админ настраивает лимиты в config.yml, а не в коде команды.
 */
public class PlayerContractCommand implements CommandExecutor, TabCompleter {

    private final LoveContracts plugin;
    private final PlayerContractManager manager;
    private final PlayerContractBoardGUI boardGui;
    private final PlayerContractMyGUI myGui;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public PlayerContractCommand(LoveContracts plugin, PlayerContractManager manager,
                                  PlayerContractBoardGUI boardGui, PlayerContractMyGUI myGui) {
        this.plugin = plugin;
        this.manager = manager;
        this.boardGui = boardGui;
        this.myGui = myGui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        if (!player.hasPermission("pcontracts.use")) {
            player.sendMessage(mm.deserialize("<red>Нет прав.</red>"));
            return true;
        }
        if (args.length == 0) {
            boardGui.open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "board" -> boardGui.open(player);
            case "my" -> myGui.open(player);
            case "create" -> handleCreate(player, args);
            case "accept" -> withResolvedId(player, args, 1, id -> manager.accept(player, id).thenAccept(r -> reply(player, r.message())));
            case "turnin" -> withResolvedId(player, args, 1, id -> manager.turnInDelivery(player, id).thenAccept(r -> reply(player, r.message())));
            case "submit" -> withResolvedId(player, args, 1, id -> manager.submitCustom(player, id).thenAccept(r -> reply(player, r.message())));
            case "cancel", "abandon" -> withResolvedId(player, args, 1, id -> manager.abandon(player, id).thenAccept(r -> reply(player, r.message())));
            case "review" -> handleReview(player, args);
            case "info" -> handleInfo(player, args);
            case "help" -> sendHelp(player);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize("<red>Использование: /pcontract create <deliver|kill|custom> ...</red>"));
            return;
        }

        String kind = args[1].toLowerCase();
        try {
            switch (kind) {
                case "deliver" -> createDeliverOrKill(player, args, PlayerContractObjectiveType.DELIVER_ITEM);
                case "kill" -> createDeliverOrKill(player, args, PlayerContractObjectiveType.KILL_ENTITY);
                case "custom" -> createCustom(player, args);
                default -> player.sendMessage(mm.deserialize(
                        "<red>Неизвестный тип. Используйте deliver, kill или custom.</red>"));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(mm.deserialize("<red>Неверное число в аргументах.</red>"));
        } catch (IllegalArgumentException e) {
            player.sendMessage(mm.deserialize("<red>" + e.getMessage() + "</red>"));
        }
    }

    // /pcontract create deliver <material> <amount> <gold> <hours> [flags...] <description...>
    // /pcontract create kill <entity> <amount> <gold> <hours> [flags...] <description...>
    private void createDeliverOrKill(Player player, String[] args, PlayerContractObjectiveType type) {
        if (args.length < 6) {
            player.sendMessage(mm.deserialize(
                    "<red>Использование: /pcontract create " + args[1].toLowerCase()
                            + " <тип> <кол-во> <золото> <часы> [withitem] [clanonly] [rep:N] <описание></red>"));
            return;
        }

        String targetRaw = args[2].toUpperCase();
        String target = type == PlayerContractObjectiveType.DELIVER_ITEM
                ? requireMaterial(targetRaw).name()
                : requireEntityType(targetRaw).name();

        int amount = Integer.parseInt(args[3]);
        long gold = Long.parseLong(args[4]);
        int hours = Integer.parseInt(args[5]);

        submitCreate(player, args, 6, type, target, amount, gold, hours);
    }

    // /pcontract create custom <gold> <hours> [flags...] <description...>
    private void createCustom(Player player, String[] args) {
        if (args.length < 5) {
            player.sendMessage(mm.deserialize(
                    "<red>Использование: /pcontract create custom <золото> <часы> [withitem] [clanonly] [rep:N] <описание></red>"));
            return;
        }
        long gold = Long.parseLong(args[2]);
        int hours = Integer.parseInt(args[3]);
        submitCreate(player, args, 4, PlayerContractObjectiveType.CUSTOM, null, 1, gold, hours);
    }

    private void submitCreate(Player player, String[] args, int fromIndex, PlayerContractObjectiveType type,
                               String target, int amount, long gold, int hours) {
        int i = fromIndex;
        boolean withItem = false;
        PlayerContractVisibility visibility = PlayerContractVisibility.PUBLIC;
        int reputationHint = 0;

        while (i < args.length) {
            String tok = args[i];
            if (tok.equalsIgnoreCase("withitem")) {
                withItem = true;
                i++;
            } else if (tok.equalsIgnoreCase("clanonly")) {
                visibility = PlayerContractVisibility.CLAN_ONLY;
                i++;
            } else if (tok.toLowerCase().startsWith("rep:")) {
                try {
                    reputationHint = Integer.parseInt(tok.substring(4));
                } catch (NumberFormatException ignored) {
                    // игнорируем неверный флаг репутации, не блокируем создание
                }
                i++;
            } else {
                break;
            }
        }

        if (i >= args.length) {
            player.sendMessage(mm.deserialize("<red>Нужно указать описание контракта.</red>"));
            return;
        }
        String description = String.join(" ", Arrays.copyOfRange(args, i, args.length));

        List<ItemStack> itemRewards = List.of();
        if (withItem) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) {
                player.sendMessage(mm.deserialize("<red>Держите предмет в руке, чтобы прикрепить его наградой.</red>"));
                return;
            }
            itemRewards = List.of(hand.clone());
            player.getInventory().setItemInMainHand(null);
        }

        CreateContractRequest req = new CreateContractRequest(
                description, type, target, amount, gold, itemRewards, reputationHint, visibility, hours);
        boolean tookItem = withItem;

        manager.create(player, req).thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!result.success() && tookItem) {
                // создание не удалось после списания предмета в руку — вернуть предмет
                req.itemRewards().forEach(item -> player.getInventory().addItem(item));
            }
            player.sendMessage(mm.deserialize(result.message()));
        }));
    }

    private void handleReview(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(mm.deserialize("<red>Использование: /pcontract review <id> accept|reject</red>"));
            return;
        }
        boolean accept = switch (args[2].toLowerCase()) {
            case "accept", "yes" -> true;
            case "reject", "no" -> false;
            default -> {
                player.sendMessage(mm.deserialize("<red>Укажите accept или reject.</red>"));
                yield false;
            }
        };
        manager.resolveId(args[1]).thenAccept(opt -> {
            if (opt.isEmpty()) {
                reply(player, "<red>Контракт не найден.</red>");
                return;
            }
            manager.review(player, opt.get(), accept).thenAccept(r -> reply(player, r.message()));
        });
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize("<red>Использование: /pcontract info <id></red>"));
            return;
        }
        manager.resolveId(args[1]).thenAccept(opt -> {
            if (opt.isEmpty()) {
                reply(player, "<red>Контракт не найден.</red>");
                return;
            }
            manager.getById(opt.get()).thenAccept(contract -> {
                if (contract.isEmpty()) {
                    reply(player, "<red>Контракт не найден.</red>");
                    return;
                }
                var c = contract.get();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(mm.deserialize("<gold>== " + c.getDescription() + " ==</gold>"));
                    player.sendMessage(mm.deserialize("<gray>Наниматель:</gray> " + c.getCreatorName()));
                    player.sendMessage(mm.deserialize("<gray>Исполнитель:</gray> "
                            + (c.getExecutorName() != null ? c.getExecutorName() : "—")));
                    player.sendMessage(mm.deserialize("<gray>Статус:</gray> " + c.getStatus()));
                    player.sendMessage(mm.deserialize("<gray>Награда:</gray> " + c.getGoldReward() + " монет"));
                    player.sendMessage(mm.deserialize("<gray>id:</gray> " + PlayerContractManager.shortId(c.getId())));
                });
            });
        });
    }

    private void withResolvedId(Player player, String[] args, int idIndex, java.util.function.Consumer<UUID> action) {
        if (args.length <= idIndex) {
            player.sendMessage(mm.deserialize("<red>Укажите id контракта.</red>"));
            return;
        }
        manager.resolveId(args[idIndex]).thenAccept(opt -> {
            if (opt.isEmpty()) {
                reply(player, "<red>Контракт не найден.</red>");
                return;
            }
            action.accept(opt.get());
        });
    }

    private void reply(Player player, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(mm.deserialize(message)));
    }

    private Material requireMaterial(String name) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Неизвестный материал: " + name);
        }
    }

    private EntityType requireEntityType(String name) {
        try {
            return EntityType.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Неизвестный тип существа: " + name);
        }
    }

    private void sendHelp(Player player) {
        List<String> lines = List.of(
                "<gold>== /pcontract ==</gold>",
                "<yellow>/pcontract board</yellow> <gray>— доска открытых контрактов</gray>",
                "<yellow>/pcontract my</yellow> <gray>— мои контракты</gray>",
                "<yellow>/pcontract create deliver <материал> <кол-во> <золото> <часы> [withitem] [clanonly] [rep:N] <описание></yellow>",
                "<yellow>/pcontract create kill <существо> <кол-во> <золото> <часы> [флаги] <описание></yellow>",
                "<yellow>/pcontract create custom <золото> <часы> [флаги] <описание></yellow>",
                "<yellow>/pcontract accept <id></yellow>",
                "<yellow>/pcontract turnin <id></yellow> <gray>— сдать предметы</gray>",
                "<yellow>/pcontract submit <id></yellow> <gray>— сдать произвольную задачу на проверку</gray>",
                "<yellow>/pcontract review <id> accept|reject</yellow>",
                "<yellow>/pcontract cancel <id></yellow>",
                "<yellow>/pcontract info <id></yellow>"
        );
        lines.forEach(l -> player.sendMessage(mm.deserialize(l)));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("board", "my", "create", "accept", "turnin", "submit",
                    "cancel", "review", "info", "help"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            options.addAll(List.of("deliver", "kill", "custom"));
        }
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
        return options.stream().filter(s -> s.toLowerCase().startsWith(prefix)).toList();
    }
}
