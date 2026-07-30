package me.lovelace.lovecontracts.player.integration;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.AccountId;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import dev.lovelace.lovecore.api.social.ProfileOracle;
import dev.lovelace.lovecore.api.social.ReputationOracle;
import dev.lovelace.lovecore.api.stats.StatBus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Единственная точка соприкосновения с LoveCore. {@link LoveEconomy} тут — виртуальный счёт
 * ({@link AccountId}), а не физические монеты, поэтому списание/начисление золота работает
 * независимо от того, онлайн игрок или нет. Предметные награды — исключение: инвентарь
 * существует только у живого игрока, их доставка ждёт следующего входа при необходимости.
 */
public class LoveCoreBridge {

    /** Есть ли рабочая экономика для эскроу. Без неё создание контрактов с золотом запрещено. */
    public boolean hasEconomy() {
        return LoveCore.service(LoveEconomy.class).isPresent();
    }

    public long balance(UUID playerId) {
        return LoveCore.service(LoveEconomy.class)
                .map(economy -> economy.balance(AccountId.player(playerId)))
                .orElse(0L);
    }

    /** Списывает золото со счёта игрока в эскроу. Результат {@code false} = не хватило средств. */
    public CompletableFuture<Boolean> withdraw(UUID playerId, long amount, String reason) {
        if (amount <= 0) return CompletableFuture.completedFuture(true);
        return LoveCore.service(LoveEconomy.class)
                .map(economy -> economy.withdraw(AccountId.player(playerId), amount, reason))
                .orElse(CompletableFuture.completedFuture(false));
    }

    /** Зачисляет золото на счёт игрока — работает и для офлайн-игрока. */
    public CompletableFuture<Void> deposit(UUID playerId, long amount, String reason) {
        if (amount <= 0) return CompletableFuture.completedFuture(null);
        return LoveCore.service(LoveEconomy.class)
                .map(economy -> economy.deposit(AccountId.player(playerId), amount, reason))
                .orElse(CompletableFuture.completedFuture(null));
    }

    public String currencyName() {
        return LoveCore.service(LoveEconomy.class).map(LoveEconomy::currencyName).orElse("монет");
    }

    /** Репутация 0..100, либо {@code Integer.MIN_VALUE} если LoveBehavior недоступен (гейт не применяется). */
    public int reputation(UUID playerId) {
        return LoveCore.service(ReputationOracle.class)
                .map(oracle -> oracle.reputation(playerId))
                .orElse(Integer.MIN_VALUE);
    }

    public boolean meetsReputation(UUID playerId, int minRequired) {
        if (minRequired <= 0) return true;
        int rep = reputation(playerId);
        // Оракул недоступен — не блокируем геймплей из-за отсутствующей интеграции.
        return rep == Integer.MIN_VALUE || rep >= minRequired;
    }

    public boolean areClanmates(UUID a, UUID b) {
        return LoveCore.service(ProfileOracle.class)
                .map(oracle -> oracle.areClanmates(a, b))
                .orElse(a.equals(b));
    }

    /** Кладёт предметы в инвентарь живого игрока либо роняет под ноги, если места не хватило. */
    public void deliverItemsToLivePlayer(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            if (item == null) continue;
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            leftover.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
        }
    }

    public Player onlinePlayer(UUID playerId) {
        return Bukkit.getPlayer(playerId);
    }

    /** Прибавить 1 к накопительной метрике StatBus. Не падает, если LoveCore недоступен. */
    public void recordStat(UUID playerId, String metric) {
        LoveCore.service(StatBus.class).ifPresent(bus -> bus.record(playerId, metric, 1));
    }
}
