package me.lovelace.lovecontracts.command;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.npc.model.NpcQuest;
import me.lovelace.lovecontracts.npc.storage.NpcQuestStorage;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /lovecontractsadmin chain ...}: admin tools for NPC chains of one player. These do not touch the daily
 * contracts (that is what {@code complete}, {@code fail} and {@code reset} are for). Arguments are parsed by
 * {@link #parse}, which knows nothing about the server, so the syntax is covered by plain unit tests.
 */
public final class ChainAdminCommand {

    public static final List<String> OPS = List.of("start", "clear", "complete", "fail", "step", "info");
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private ChainAdminCommand() {
    }

    /** A parsed command; {@code error} is set (and the rest is not) when the arguments are wrong. */
    public record Parsed(String op, String player, String chain, int step, boolean reward, String error) {
        static Parsed error(String message) { return new Parsed(null, null, null, 0, true, message); }
        public boolean ok() { return error == null; }
    }

    /** {@code args} is the whole argument list, {@code args[0]} being "chain". */
    public static Parsed parse(String[] args) {
        if (args.length < 2) return Parsed.error("Использование: /lovecontractsadmin chain <" + String.join("|", OPS) + "> <игрок> ...");
        String op = args[1].toLowerCase(Locale.ROOT);
        if (!OPS.contains(op)) return Parsed.error("Неизвестная операция «" + args[1] + "». Доступно: " + String.join(", ", OPS) + ".");
        if (args.length < 3) return Parsed.error("Укажите игрока: /lovecontractsadmin chain " + op + " <игрок>" + (op.equals("info") ? "" : " <цепочка>"));
        String player = args[2];
        if (op.equals("info")) return new Parsed(op, player, null, 0, true, null);
        if (args.length < 4) {
            return Parsed.error("Укажите цепочку: /lovecontractsadmin chain " + op + " " + player + (op.equals("clear") ? " <цепочка|all>" : " <цепочка>"));
        }
        String chain = args[3];
        switch (op) {
            case "step" -> {
                if (args.length < 5) return Parsed.error("Укажите номер этапа: /lovecontractsadmin chain step " + player + " " + chain + " <номер>");
                int number;
                try {
                    number = Integer.parseInt(args[4]);
                } catch (NumberFormatException notANumber) {
                    return Parsed.error("Номер этапа должен быть числом, а не «" + args[4] + "».");
                }
                if (number < 1) return Parsed.error("Номер этапа начинается с 1.");
                return new Parsed(op, player, chain, number, true, null);
            }
            case "complete" -> {
                boolean reward = true;
                for (int i = 4; i < args.length; i++) {
                    if (args[i].equalsIgnoreCase("--no-reward")) reward = false;
                    else return Parsed.error("Лишний аргумент «" + args[i] + "». Допустим только --no-reward.");
                }
                return new Parsed(op, player, chain, 0, reward, null);
            }
            default -> {
                if (args.length > 4) return Parsed.error("Лишний аргумент «" + args[4] + "».");
                return new Parsed(op, player, chain, 0, true, null);
            }
        }
    }

    public static void run(LoveContracts plugin, CommandSender sender, String[] args) {
        Parsed parsed = parse(args);
        if (!parsed.ok()) {
            sender.sendMessage(MM.deserialize("<red>✖ " + escape(parsed.error()) + "</red>"));
            return;
        }
        OfflinePlayer target = resolve(parsed.player());
        if (target == null) {
            sender.sendMessage(MM.deserialize("<red>✖ Игрок «" + escape(parsed.player()) + "» не найден: он должен быть в сети или уже заходить на сервер.</red>"));
            return;
        }
        UUID uuid = target.getUniqueId();
        String name = target.getName() == null ? parsed.player() : target.getName();
        var manager = plugin.getNpcQuestManager();
        if (manager == null) {
            sender.sendMessage(MM.deserialize("<red>✖ Цепочки поручений сейчас недоступны.</red>"));
            return;
        }
        if (parsed.op().equals("info")) {
            manager.adminInspect(uuid, rows -> sendInfo(plugin, sender, name, rows));
            return;
        }
        boolean clearAll = parsed.op().equals("clear") && parsed.chain().equalsIgnoreCase("all");
        NpcQuest quest = clearAll ? null : manager.getQuest(parsed.chain());
        if (quest == null && !clearAll && !parsed.op().equals("clear")) {
            sender.sendMessage(MM.deserialize("<red>✖ Цепочки «" + escape(parsed.chain()) + "» нет. Список: " + escape(String.join(", ", chainIds(plugin))) + "</red>"));
            return;
        }
        java.util.function.Consumer<me.lovelace.lovecontracts.npc.manager.NpcQuestManager.AdminOutcome> done = outcome ->
                sender.sendMessage(MM.deserialize((outcome.ok() ? "<green>✔ " : "<red>✖ ") + escape(name) + ": " + escape(outcome.message())
                        + (outcome.ok() ? "</green>" : "</red>")));
        switch (parsed.op()) {
            case "start" -> manager.adminStart(uuid, quest, done);
            // an unknown id may still have a row (a chain removed from the config), so clear works on the raw id
            case "clear" -> manager.adminClear(uuid, clearAll ? null : parsed.chain(), done);
            case "complete" -> manager.adminComplete(uuid, quest, parsed.reward(), done);
            case "fail" -> manager.adminFail(uuid, quest, done);
            case "step" -> manager.adminSetStep(uuid, quest, parsed.step(), done);
            default -> sender.sendMessage(MM.deserialize("<red>✖ Неизвестная операция.</red>"));
        }
    }

    private static void sendInfo(LoveContracts plugin, CommandSender sender, String name, List<NpcQuestStorage.Row> rows) {
        sender.sendMessage(MM.deserialize("<dark_gray>===== <aqua>Цепочки: " + escape(name) + "</aqua> =====</dark_gray>"));
        if (rows.isEmpty()) {
            sender.sendMessage(MM.deserialize("<gray>Записей нет.</gray>"));
            return;
        }
        for (NpcQuestStorage.Row row : rows) {
            NpcQuest quest = plugin.getNpcQuestManager().getQuest(row.questId());
            String steps = quest == null ? "?" : String.valueOf(quest.TotalSteps());
            String color = switch (row.status()) {
                case "ACTIVE" -> "yellow";
                case "COMPLETED" -> "green";
                default -> "gray";
            };
            String done = row.lastCompletedMillis() > 0 ? ", завершена " + TIME.format(Instant.ofEpochMilli(row.lastCompletedMillis())) : "";
            sender.sendMessage(MM.deserialize("<aqua>" + escape(row.questId()) + "</aqua> <" + color + ">" + row.status() + "</" + color + ">"
                    + "<gray> этап " + (row.currentStep() + 1) + "/" + steps + ", прогресс " + row.stepProgress()
                    + ", раз: " + row.completionCount() + done + (quest == null ? ", цепочки больше нет в конфиге" : "") + "</gray>"));
        }
    }

    /** Online by exact name first; otherwise only a cached profile, never a blocking name lookup. */
    private static OfflinePlayer resolve(String name) {
        var online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    private static List<String> chainIds(LoveContracts plugin) {
        List<String> ids = new ArrayList<>();
        if (plugin.getNpcQuestManager() != null) plugin.getNpcQuestManager().allQuests().forEach(q -> ids.add(q.getId()));
        return ids;
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("<", "‹").replace(">", "›");
    }

    /** Tab completion for the arguments after "chain" (args[0]), already known to be the chain group. */
    public static List<String> suggest(LoveContracts plugin, String[] args) {
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> options = switch (args.length) {
            case 2 -> OPS;
            case 3 -> Bukkit.getOnlinePlayers().stream().map(org.bukkit.entity.Player::getName).toList();
            case 4 -> {
                if (args[1].equalsIgnoreCase("info")) yield List.of();
                List<String> ids = chainIds(plugin);
                if (args[1].equalsIgnoreCase("clear")) {
                    ids = new ArrayList<>(ids);
                    ids.add("all");
                }
                yield ids;
            }
            case 5 -> {
                if (args[1].equalsIgnoreCase("complete")) yield List.of("--no-reward");
                if (args[1].equalsIgnoreCase("step") && plugin.getNpcQuestManager() != null) {
                    NpcQuest quest = plugin.getNpcQuestManager().getQuest(args[3]);
                    List<String> numbers = new ArrayList<>();
                    if (quest != null) for (int i = 1; i <= quest.TotalSteps(); i++) numbers.add(String.valueOf(i));
                    yield numbers;
                }
                yield List.of();
            }
            default -> List.of();
        };
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }
}
