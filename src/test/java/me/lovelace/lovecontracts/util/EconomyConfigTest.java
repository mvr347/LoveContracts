package me.lovelace.lovecontracts.util;

import dev.lovelace.lovecore.api.economy.Denomination;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyConfigTest {

    private static LoveEconomy economy(List<Denomination> dens) {
        return (LoveEconomy) Proxy.newProxyInstance(LoveEconomy.class.getClassLoader(), new Class<?>[]{LoveEconomy.class},
                (proxy, method, args) -> "denominations".equals(method.getName()) ? dens : null);
    }

    @Test
    void defaultStepsAreTheCoinValues() {
        LoveEconomy eco = economy(List.of(
                new Denomination("gold_coin", 2000), new Denomination("copper_coin", 1),
                new Denomination("diamond_coin", 20000), new Denomination("iron_coin", 100)));
        assertEquals(List.of(1L, 100L, 2000L, 20000L), EconomyConfig.denominationSteps(eco));
    }

    @Test
    void withoutEconomyTheShippedLadderIsUsed() {
        assertEquals(List.of(1L, 100L, 2000L, 20000L), EconomyConfig.denominationSteps(null));
    }
}
