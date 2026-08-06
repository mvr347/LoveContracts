package me.lovelace.lovecontracts.player.integration;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import dev.lovelace.lovecore.api.social.ProfileOracle;
import dev.lovelace.lovecore.api.social.ReputationOracle;
import dev.lovelace.lovecore.api.stats.StatBus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Единственная точка соприкосновения с LoveCore. {@link LoveEconomy} тут — физические монеты
 * в инвентаре игрока (тот же кошелёк, что уже использует {@code /contracts}), а не виртуальный
 * счёт, поэтому все денежные операции требуют живого онлайн-игрока — офлайн эскроу-выплаты
 * ждут в очереди {@code pcontract_payouts} до следующего входа. Награда — исключительно
 * физическая валюта LoveCore, предметы наградой не выдаются.
 */
public class LoveCoreBridge {

    /** Есть ли рабочая экономика для эскроу. Без неё создание контрактов с золотом запрещено. */
    public boolean hasEconomy() {
        return LoveCore.service(LoveEconomy.class).isPresent();
    }

    public boolean hasBalance(Player player, long amount) {
        return LoveCore.service(LoveEconomy.class)
                .map(economy -> economy.has(player, amount))
                .orElse(false);
    }

    /** Списывает золото у живого игрока в эскроу. false = не хватило монет. */
    public boolean charge(Player player, long amount) {
        if (amount <= 0) return true;
        return LoveCore.service(LoveEconomy.class)
                .map(economy -> economy.charge(player, amount))
                .orElse(false);
    }

    /** Выдаёт золото живому игроку. Вызывать только когда player.isOnline(). */
    public void give(Player player, long amount) {
        if (amount <= 0) return;
        LoveCore.service(LoveEconomy.class).ifPresent(economy -> economy.give(player, amount));
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

    /** Выдаёт золото живому игроку через LoveEconomy. */
    public void deliverToLivePlayer(Player player, long gold) {
        give(player, gold);
    }

    public Player onlinePlayer(UUID playerId) {
        return Bukkit.getPlayer(playerId);
    }

    /** Прибавить 1 к накопительной метрике StatBus. Не падает, если LoveCore недоступен. */
    public void recordStat(UUID playerId, String metric) {
        LoveCore.service(StatBus.class).ifPresent(bus -> bus.record(playerId, metric, 1));
    }
}
