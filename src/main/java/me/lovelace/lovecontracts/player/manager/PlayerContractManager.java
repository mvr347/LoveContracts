package me.lovelace.lovecontracts.player.manager;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.player.event.PlayerContractAcceptedEvent;
import me.lovelace.lovecontracts.player.event.PlayerContractCompletedEvent;
import me.lovelace.lovecontracts.player.event.PlayerContractCreatedEvent;
import me.lovelace.lovecontracts.player.event.PlayerContractEndedEvent;
import me.lovelace.lovecontracts.player.integration.LoveCoreBridge;
import me.lovelace.lovecontracts.player.model.PlayerContract;
import me.lovelace.lovecontracts.player.model.PlayerContractObjectiveType;
import me.lovelace.lovecontracts.player.model.PlayerContractStatus;
import me.lovelace.lovecontracts.player.model.PlayerContractVisibility;
import me.lovelace.lovecontracts.player.storage.PendingPayout;
import me.lovelace.lovecontracts.player.storage.PlayerContractDatabase;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Вся бизнес-логика player-to-player контрактов: создание с эскроу, приём, сдача задач,
 * ревью произвольных заданий, отмена/истечение с рефандом.
 *
 * <p>Правило потоков: любое обращение к БД — асинхронно ({@link #runAsync}), любое обращение
 * к Bukkit API (инвентарь, сообщения, события) — обратно на главном потоке ({@link #runSync}).
 * Эскроу списывается/выдаётся только когда игрок физически онлайн — офлайн-выплаты уходят
 * в очередь {@code player_contract_payouts} и доставляются при следующем входе.</p>
 */
public class PlayerContractManager {

    private final LoveContracts plugin;
    private final PlayerContractDatabase db;
    private final LoveCoreBridge bridge;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public PlayerContractManager(LoveContracts plugin, PlayerContractDatabase db, LoveCoreBridge bridge) {
        this.plugin = plugin;
        this.db = db;
        this.bridge = bridge;
    }

    public void cleanupPlayer(UUID uuid) {
        // Reserved for per-player caching if added in future
    }

    // ------------------------------------------------------------------
    // Создание
    // ------------------------------------------------------------------

    public CompletableFuture<ContractActionResult> create(Player creator, CreateContractRequest req) {
        CompletableFuture<ContractActionResult> future = new CompletableFuture<>();

        if (!cfg().getBoolean("enabled", true)) {
            future.complete(ContractActionResult.fail("<red>Player-контракты отключены.</red>"));
            return future;
        }

        long minGold = cfg().getLong("min-gold-reward", 10);
        long maxGold = cfg().getLong("max-gold-reward", 100000);
        if (req.goldReward() < minGold || req.goldReward() > maxGold) {
            future.complete(ContractActionResult.fail(
                    "<red>Награда золотом должна быть от " + minGold + " до " + maxGold + ".</red>"));
            return future;
        }

        int minH = cfg().getInt("min-deadline-hours", 1);
        int maxH = cfg().getInt("max-deadline-hours", 168);
        if (req.deadlineHours() < minH || req.deadlineHours() > maxH) {
            future.complete(ContractActionResult.fail(
                    "<red>Срок должен быть от " + minH + " до " + maxH + " часов.</red>"));
            return future;
        }

        int minRep = cfg().getInt("min-reputation-to-create", 0);
        if (!bridge.meetsReputation(creator.getUniqueId(), minRep)) {
            future.complete(ContractActionResult.fail("<red>Недостаточно репутации, чтобы размещать контракты.</red>"));
            return future;
        }

        long tax = Math.round(req.goldReward() * (cfg().getDouble("creation-tax-percent", 0) / 100.0));
        long totalCharge = req.goldReward() + tax;

        if (totalCharge > 0 && !bridge.hasEconomy()) {
            future.complete(ContractActionResult.fail("<red>Экономика недоступна — LoveCore не установлен.</red>"));
            return future;
        }

        if (totalCharge > 0 && !bridge.hasBalance(creator, totalCharge)) {
            future.complete(ContractActionResult.fail(
                    "<red>Не хватает " + bridge.currencyName() + " (нужно " + totalCharge + " с учётом налога).</red>"));
            return future;
        }

        int maxOpen = cfg().getInt("max-open-per-creator", 3);
        UUID creatorId = creator.getUniqueId();
        String creatorName = creator.getName();

        runAsync(() -> {
            try {
                int active = db.countActiveByCreator(creatorId);
                if (active >= maxOpen) {
                    runSync(() -> future.complete(ContractActionResult.fail(
                            "<red>У вас уже " + maxOpen + " открытых контрактов.</red>")));
                    return;
                }

                runSync(() -> {
                    if (!creator.isOnline()) {
                        future.complete(ContractActionResult.fail("<red>Нужно быть онлайн, чтобы разместить контракт.</red>"));
                        return;
                    }
                    // Монеты физические — списываем на главном потоке прямо здесь, а не после
                    // очередного runAsync, иначе игрок между проверкой и списанием мог бы успеть
                    // потратить деньги.
                    if (totalCharge > 0 && !bridge.charge(creator, totalCharge)) {
                        future.complete(ContractActionResult.fail("<red>Не удалось списать эскроу — баланс изменился.</red>"));
                        return;
                    }

                    PlayerContract contract = new PlayerContract(
                            UUID.randomUUID(), creatorId, creatorName,
                            null, null, req.description(), req.type(), req.target(), req.amount(), 0,
                            req.goldReward(), req.reputationHint(), req.visibility(),
                            PlayerContractStatus.OPEN, Instant.now(),
                            Instant.now().plus(req.deadlineHours(), ChronoUnit.HOURS), null, null
                    );

                    runAsync(() -> {
                        try {
                            db.insert(contract);
                            runSync(() -> {
                                Bukkit.getPluginManager().callEvent(new PlayerContractCreatedEvent(contract));
                                bridge.recordStat(creatorId, "contracts.player.posted");
                                future.complete(ContractActionResult.ok(
                                        "<green>Контракт размещён:</green> <gold>" + contract.getDescription() + "</gold> "
                                                + "<gray>(id " + shortId(contract.getId()) + ")</gray>"));
                            });
                        } catch (SQLException e) {
                            plugin.getLogger().log(Level.WARNING, "Failed to insert player contract", e);
                            runSync(() -> {
                                if (totalCharge > 0 && creator.isOnline()) bridge.give(creator, totalCharge);
                                future.complete(ContractActionResult.fail("<red>Ошибка БД — эскроу возвращён.</red>"));
                            });
                        }
                    });
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to count active player contracts", e);
                runSync(() -> future.complete(ContractActionResult.fail("<red>Ошибка БД.</red>")));
            }
        });

        return future;
    }

    // ------------------------------------------------------------------
    // Приём
    // ------------------------------------------------------------------

    public CompletableFuture<ContractActionResult> accept(Player executor, UUID contractId) {
        CompletableFuture<ContractActionResult> future = new CompletableFuture<>();

        int minRep = cfg().getInt("min-reputation-to-accept", 0);
        if (!bridge.meetsReputation(executor.getUniqueId(), minRep)) {
            future.complete(ContractActionResult.fail("<red>Недостаточно репутации, чтобы брать контракты.</red>"));
            return future;
        }

        boolean allowSelf = cfg().getBoolean("allow-self-accept", false);
        int maxAccepted = cfg().getInt("max-accepted-per-executor", 3);

        runAsync(() -> {
            try {
                int active = db.countActiveByExecutor(executor.getUniqueId());
                if (active >= maxAccepted) {
                    runSync(() -> future.complete(ContractActionResult.fail(
                            "<red>У вас уже " + maxAccepted + " активных контрактов.</red>")));
                    return;
                }

                Optional<PlayerContract> opt = db.findById(contractId);
                if (opt.isEmpty()) {
                    runSync(() -> future.complete(ContractActionResult.fail("<red>Контракт не найден.</red>")));
                    return;
                }
                PlayerContract c = opt.get();

                if (c.getStatus() != PlayerContractStatus.OPEN) {
                    runSync(() -> future.complete(ContractActionResult.fail("<red>Контракт больше не доступен.</red>")));
                    return;
                }
                if (!allowSelf && c.getCreatorId().equals(executor.getUniqueId())) {
                    runSync(() -> future.complete(ContractActionResult.fail("<red>Нельзя принять свой же контракт.</red>")));
                    return;
                }
                if (c.getVisibility() == PlayerContractVisibility.CLAN_ONLY
                        && !bridge.areClanmates(c.getCreatorId(), executor.getUniqueId())) {
                    runSync(() -> future.complete(ContractActionResult.fail("<red>Этот контракт только для сокланов.</red>")));
                    return;
                }
                if (c.isExpired()) {
                    runSync(() -> future.complete(ContractActionResult.fail("<red>Срок контракта истёк.</red>")));
                    return;
                }

                Instant now = Instant.now();
                boolean won = db.tryAccept(contractId, executor.getUniqueId(), executor.getName(), now.toEpochMilli());
                if (!won) {
                    runSync(() -> future.complete(ContractActionResult.fail(
                            "<red>Кто-то принял этот контракт прямо перед вами!</red>")));
                    return;
                }

                c.setExecutor(executor.getUniqueId(), executor.getName());
                c.setStatus(PlayerContractStatus.IN_PROGRESS);
                c.setAcceptedAt(now);

                runSync(() -> {
                    Bukkit.getPluginManager().callEvent(new PlayerContractAcceptedEvent(c));
                    future.complete(ContractActionResult.ok(
                            "<green>Контракт принят:</green> <gold>" + c.getDescription() + "</gold>"));
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to accept player contract", e);
                runSync(() -> future.complete(ContractActionResult.fail("<red>Ошибка БД.</red>")));
            }
        });

        return future;
    }

    // ------------------------------------------------------------------
    // Сдача задачи: доставка предметов
    // ------------------------------------------------------------------

    public CompletableFuture<ContractActionResult> turnInDelivery(Player executor, UUID contractId) {
        CompletableFuture<ContractActionResult> future = new CompletableFuture<>();

        runAsync(() -> {
            try {
                Optional<PlayerContract> opt = db.findById(contractId);
                if (opt.isEmpty()) {
                    runSync(() -> future.complete(ContractActionResult.fail("<red>Контракт не найден.</red>")));
                    return;
                }
                PlayerContract c = opt.get();

                if (!executor.getUniqueId().equals(c.getExecutorId())) {
                    runSync(() -> future.complete(ContractActionResult.fail("<red>Это не ваш контракт.</red>")));
                    return;
                }
                if (c.getStatus() != PlayerContractStatus.IN_PROGRESS) {
                    runSync(() -> future.complete(ContractActionResult.fail("<red>Контракт сейчас не активен.</red>")));
                    return;
                }
                if (c.getObjectiveType() != PlayerContractObjectiveType.DELIVER_ITEM) {
                    runSync(() -> future.complete(ContractActionResult.fail(
                            "<red>Этот контракт не принимает сдачу предметов.</red>")));
                    return;
                }

                runSync(() -> {
                    Material material;
                    try {
                        material = Material.valueOf(c.getObjectiveTarget());
                    } catch (IllegalArgumentException ex) {
                        future.complete(ContractActionResult.fail("<red>В контракте указан неверный материал.</red>"));
                        return;
                    }

                    int have = countMaterial(executor, material);
                    if (have < c.getObjectiveAmount()) {
                        future.complete(ContractActionResult.fail(
                                "<red>Нужно " + c.getObjectiveAmount() + "x " + material.name()
                                        + ", у вас " + have + ".</red>"));
                        return;
                    }

                    removeMaterial(executor, material, c.getObjectiveAmount());
                    c.setObjectiveProgress(c.getObjectiveAmount());

                    completeInternal(c).whenComplete((res, err) -> {
                        if (err != null) {
                            future.complete(ContractActionResult.fail("<red>Ошибка БД при завершении.</red>"));
                        } else {
                            future.complete(res);
                        }
                    });
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to turn in player contract", e);
                runSync(() -> future.complete(ContractActionResult.fail("<red>Ошибка БД.</red>")));
            }
        });

        return future;
    }

    // ------------------------------------------------------------------
    // Сдача задачи: убийства (вызывается листенером на EntityDeathEvent)
    // ------------------------------------------------------------------

    public void onEntityKilled(Player killer, EntityType type) {
        runAsync(() -> {
            try {
                List<PlayerContract> candidates = db.findActiveKillContractsForExecutor(killer.getUniqueId());
                for (PlayerContract c : candidates) {
                    if (!type.name().equals(c.getObjectiveTarget())) continue;

                    c.setObjectiveProgress(c.getObjectiveProgress() + 1);
                    db.update(c);

                    if (c.isObjectiveMet()) {
                        completeInternal(c).whenComplete((res, err) -> runSync(() -> {
                            if (err == null && killer.isOnline()) killer.sendMessage(mm.deserialize(res.message()));
                        }));
                    } else {
                        runSync(() -> {
                            if (killer.isOnline()) {
                                killer.sendMessage(mm.deserialize(
                                        "<gray>[Контракт] Прогресс: " + c.getProgressString() + "</gray>"));
                            }
                        });
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to update kill contract progress", e);
            }
        });
    }

    // ------------------------------------------------------------------
    // Произвольные задачи: сдача на ревью
    // ------------------------------------------------------------------

    public CompletableFuture<ContractActionResult> submitCustom(Player executor, UUID contractId) {
        CompletableFuture<ContractActionResult> future = new CompletableFuture<>();

        runAsync(() -> {
            try {
                Optional<PlayerContract> opt = db.findById(contractId);
                if (opt.isEmpty()) {
                    runSync(() -> future.complete(ContractActionResult.fail("<red>Контракт не найден.</red>")));
                    return;
                }
                PlayerContract c = opt.get();

                if (!executor.getUniqueId().equals(c.getExecutorId())) {
                    runSync(() -> future.complete(ContractActionResult.fail("<red>Это не ваш контракт.</red>")));
                    return;
                }
                if (c.getObjectiveType() != PlayerContractObjectiveType.CUSTOM) {
                    runSync(() -> future.complete(ContractActionResult.fail("<red>Этот контракт не требует ревью.</red>")));
                    return;
                }
                if (c.getStatus() != PlayerContractStatus.IN_PROGRESS) {
                    runSync(() -> future.complete(ContractActionResult.fail("<red>Контракт сейчас не активен.</red>")));
                    return;
                }

                c.setStatus(PlayerContractStatus.PENDING_REVIEW);
                db.update(c);

                runSync(() -> {
                    Player creator = bridge.onlinePlayer(c.getCreatorId());
                    if (creator != null) {
                        creator.sendMessage(mm.deserialize(
                                "<yellow>" + executor.getName() + " сдал контракт на проверку: "
                                        + "/pcontract review " + shortId(c.getId()) + " accept|reject</yellow>"));
                    }
                    future.complete(ContractActionResult.ok("<green>Отправлено на проверку нанимателю.</green>"));
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to submit player contract", e);
                runSync(() -> future.complete(ContractActionResult.fail("<red>Ошибка БД.</red>")));
            }
        });

        return future;
    }

    public CompletableFuture<ContractActionResult> review(Player creator, UUID contractId, boolean accept) {
        CompletableFuture<ContractActionResult> future = new CompletableFuture<>();

        runAsync(() -> {
            try {
                Optional<PlayerContract> opt = db.findById(contractId);
                if (opt.isEmpty()) {
                    runSync(() -> future.complete(ContractActionResult.fail("<red>Контракт не найден.</red>")));
                    return;
                }
                PlayerContract c = opt.get();

                if (!creator.getUniqueId().equals(c.getCreatorId())) {
                    runSync(() -> future.complete(ContractActionResult.fail("<red>Это не ваш контракт для проверки.</red>")));
                    return;
                }
                if (c.getStatus() != PlayerContractStatus.PENDING_REVIEW) {
                    runSync(() -> future.complete(ContractActionResult.fail("<red>Контракт не ожидает проверки.</red>")));
                    return;
                }

                if (accept) {
                    c.setObjectiveProgress(c.getObjectiveAmount());
                    completeInternal(c).whenComplete((res, err) -> {
                        if (err != null) {
                            future.complete(ContractActionResult.fail("<red>Ошибка БД при завершении.</red>"));
                        } else {
                            future.complete(res);
                        }
                    });
                } else {
                    c.setStatus(PlayerContractStatus.IN_PROGRESS);
                    db.update(c);
                    runSync(() -> {
                        Player executor = bridge.onlinePlayer(c.getExecutorId());
                        if (executor != null) {
                            executor.sendMessage(mm.deserialize(
                                    "<red>Ваша сдача по контракту " + shortId(c.getId()) + " отклонена. Продолжайте выполнение.</red>"));
                        }
                        future.complete(ContractActionResult.ok("<yellow>Сдача отклонена.</yellow>"));
                    });
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to review player contract", e);
                runSync(() -> future.complete(ContractActionResult.fail("<red>Ошибка БД.</red>")));
            }
        });

        return future;
    }

    // ------------------------------------------------------------------
    // Отмена / отказ
    // ------------------------------------------------------------------

    public CompletableFuture<ContractActionResult> abandon(Player actor, UUID contractId) {
        CompletableFuture<ContractActionResult> future = new CompletableFuture<>();

        runAsync(() -> {
            try {
                Optional<PlayerContract> opt = db.findById(contractId);
                if (opt.isEmpty()) {
                    runSync(() -> future.complete(ContractActionResult.fail("<red>Контракт не найден.</red>")));
                    return;
                }
                PlayerContract c = opt.get();
                UUID uid = actor.getUniqueId();

                if (uid.equals(c.getCreatorId())) {
                    if (c.getStatus().isTerminal()) {
                        runSync(() -> future.complete(ContractActionResult.fail("<red>Контракт уже закрыт.</red>")));
                        return;
                    }
                    PlayerContractStatus prev = c.getStatus();
                    c.setStatus(PlayerContractStatus.CANCELLED);
                    db.update(c);

                    boolean refund = cfg().getBoolean("refund-on-abandon", true);
                    if (refund) {
                        payout(c.getCreatorId(), c.getGoldReward(), "contract-cancel:" + c.getId());
                    }

                    runSync(() -> {
                        Bukkit.getPluginManager().callEvent(new PlayerContractEndedEvent(c, prev));
                        future.complete(ContractActionResult.ok(
                                "<yellow>Контракт отменён" + (refund ? ", эскроу возвращён." : ".") + "</yellow>"));
                    });
                } else if (uid.equals(c.getExecutorId())) {
                    if (c.getStatus() != PlayerContractStatus.IN_PROGRESS && c.getStatus() != PlayerContractStatus.PENDING_REVIEW) {
                        runSync(() -> future.complete(ContractActionResult.fail("<red>У вас нет активного этого контракта.</red>")));
                        return;
                    }
                    PlayerContractStatus prev = c.getStatus();
                    c.clearExecutor();
                    c.setObjectiveProgress(0);
                    c.setStatus(PlayerContractStatus.OPEN);
                    c.setAcceptedAt(null);
                    db.update(c);

                    runSync(() -> {
                        Bukkit.getPluginManager().callEvent(new PlayerContractEndedEvent(c, prev));
                        future.complete(ContractActionResult.ok(
                                "<yellow>Вы отказались от контракта. Он снова на доске.</yellow>"));
                    });
                } else {
                    runSync(() -> future.complete(ContractActionResult.fail("<red>Это не ваш контракт.</red>")));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to abandon player contract", e);
                runSync(() -> future.complete(ContractActionResult.fail("<red>Ошибка БД.</red>")));
            }
        });

        return future;
    }

    // ------------------------------------------------------------------
    // Истечение срока (вызывается из ScheduledTask)
    // ------------------------------------------------------------------

    public void expireOverdue() {
        runAsync(() -> {
            try {
                List<PlayerContract> overdue = db.findOverdue();
                boolean refund = cfg().getBoolean("refund-on-expire", true);

                for (PlayerContract c : overdue) {
                    PlayerContractStatus prev = c.getStatus();
                    c.setStatus(PlayerContractStatus.EXPIRED);
                    db.update(c);

                    if (refund) {
                        payout(c.getCreatorId(), c.getGoldReward(), "contract-expire:" + c.getId());
                    }

                    runSync(() -> {
                        Bukkit.getPluginManager().callEvent(new PlayerContractEndedEvent(c, prev));
                        Player creatorOnline = bridge.onlinePlayer(c.getCreatorId());
                        if (creatorOnline != null) {
                            creatorOnline.sendMessage(mm.deserialize(
                                    "<yellow>Ваш контракт истёк" + (refund ? " — эскроу возвращён." : ".") + "</yellow>"));
                        }
                        if (c.getExecutorId() != null) {
                            Player executorOnline = bridge.onlinePlayer(c.getExecutorId());
                            if (executorOnline != null) {
                                executorOnline.sendMessage(mm.deserialize("<red>Принятый вами контракт истёк.</red>"));
                            }
                        }
                    });
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to expire overdue player contracts", e);
            }
        });
    }

    // ------------------------------------------------------------------
    // Отложенные выплаты офлайн-игрокам
    // ------------------------------------------------------------------

    public void deliverPendingPayouts(Player player) {
        runAsync(() -> {
            try {
                List<PendingPayout> payouts = db.getPendingPayouts(player.getUniqueId());
                if (payouts.isEmpty()) return;

                long totalGold = 0;
                for (PendingPayout p : payouts) {
                    totalGold += p.goldAmount();
                    db.deletePendingPayout(p.payoutId());
                }

                long finalGold = totalGold;
                runSync(() -> {
                    if (!player.isOnline()) return;
                    bridge.deliverToLivePlayer(player, finalGold);
                    player.sendMessage(mm.deserialize(
                            "<green>Вам доставлена отложенная выплата по контракту: " + finalGold + " "
                                    + bridge.currencyName() + ".</green>"));
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to deliver pending contract payouts for " + player.getName(), e);
            }
        });
    }

    // ------------------------------------------------------------------
    // Списки для GUI/команд
    // ------------------------------------------------------------------

    public CompletableFuture<List<PlayerContract>> listOpenFor(Player viewer) {
        CompletableFuture<List<PlayerContract>> future = new CompletableFuture<>();
        runAsync(() -> {
            try {
                List<PlayerContract> open = db.findOpen();
                List<PlayerContract> visible = new ArrayList<>();
                for (PlayerContract c : open) {
                    if (c.getVisibility() == PlayerContractVisibility.CLAN_ONLY
                            && !bridge.areClanmates(c.getCreatorId(), viewer.getUniqueId())) {
                        continue;
                    }
                    visible.add(c);
                }
                future.complete(visible);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to list open player contracts", e);
                future.complete(List.of());
            }
        });
        return future;
    }

    public CompletableFuture<List<PlayerContract>> listMine(Player player) {
        CompletableFuture<List<PlayerContract>> future = new CompletableFuture<>();
        runAsync(() -> {
            try {
                List<PlayerContract> mine = new ArrayList<>(db.findByCreator(player.getUniqueId()));
                for (PlayerContract c : db.findByExecutor(player.getUniqueId())) {
                    if (!mine.contains(c)) mine.add(c);
                }
                future.complete(mine);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to list player's contracts", e);
                future.complete(List.of());
            }
        });
        return future;
    }

    /** Резолвит id, введённый игроком (полный UUID или короткий id из GUI/лога), в UUID контракта. */
    public CompletableFuture<Optional<UUID>> resolveId(String idStr) {
        CompletableFuture<Optional<UUID>> future = new CompletableFuture<>();
        try {
            future.complete(Optional.of(UUID.fromString(idStr)));
            return future;
        } catch (IllegalArgumentException ignored) {
            // не полный UUID — пробуем как короткий префикс ниже
        }
        runAsync(() -> {
            try {
                future.complete(db.findByIdPrefix(idStr).map(PlayerContract::getId));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to resolve player contract id " + idStr, e);
                future.complete(Optional.empty());
            }
        });
        return future;
    }

    public CompletableFuture<Optional<PlayerContract>> getById(UUID id) {
        CompletableFuture<Optional<PlayerContract>> future = new CompletableFuture<>();
        runAsync(() -> {
            try {
                future.complete(db.findById(id));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load player contract " + id, e);
                future.complete(Optional.empty());
            }
        });
        return future;
    }

    // ------------------------------------------------------------------
    // Внутреннее
    // ------------------------------------------------------------------

    private CompletableFuture<ContractActionResult> completeInternal(PlayerContract c) {
        CompletableFuture<ContractActionResult> future = new CompletableFuture<>();

        c.setStatus(PlayerContractStatus.COMPLETED);
        c.setCompletedAt(Instant.now());

        runAsync(() -> {
            try {
                db.update(c);
                payout(c.getExecutorId(), c.getGoldReward(), "contract-complete:" + c.getId());

                runSync(() -> {
                    Bukkit.getPluginManager().callEvent(new PlayerContractCompletedEvent(c));
                    bridge.recordStat(c.getExecutorId(), "contracts.player.fulfilled");
                    future.complete(ContractActionResult.ok(
                            "<green>Контракт выполнен!</green> Награда: " + c.getGoldReward() + " " + bridge.currencyName() + "."));
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to complete player contract", e);
                runSync(() -> future.complete(ContractActionResult.fail("<red>Ошибка БД при завершении контракта.</red>")));
            }
        });

        return future;
    }

    /**
     * Выдаёт золото живому игроку, иначе кладёт его в очередь отложенных выплат — монеты
     * физические (в инвентаре), офлайн-выдачи не существует.
     */
    private void payout(UUID playerId, long gold, String reason) {
        Player online = bridge.onlinePlayer(playerId);
        if (online != null && online.isOnline()) {
            runSync(() -> bridge.deliverToLivePlayer(online, gold));
            return;
        }
        if (gold <= 0) return;
        try {
            db.addPendingPayout(playerId, gold, reason);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to queue pending payout for " + playerId, e);
        }
    }

    private int countMaterial(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) count += item.getAmount();
        }
        return count;
    }

    private void removeMaterial(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material) continue;
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    private org.bukkit.configuration.ConfigurationSection cfg() {
        var section = plugin.getConfig().getConfigurationSection("player-contracts");
        return section != null ? section : plugin.getConfig().createSection("player-contracts");
    }

    public static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private void runAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    private void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
