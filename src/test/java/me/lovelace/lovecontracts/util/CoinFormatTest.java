package me.lovelace.lovecontracts.util;

import dev.lovelace.lovecore.api.economy.Denomination;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoinFormatTest {

    private static final List<Denomination> DENS = List.of(
            new Denomination("ia:copper_coin", 1),
            new Denomination("ia:iron_coin", 100),
            new Denomination("ia:gold_coin", 2000));

    private static final LoveEconomy ECO = (LoveEconomy) Proxy.newProxyInstance(
            LoveEconomy.class.getClassLoader(), new Class<?>[]{LoveEconomy.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "denominations" -> DENS;
                case "currencyName" -> "монет";
                default -> null;
            });

    @Test
    void countAfterTheGlyphIsWhite() {
        assertEquals(List.of("<white>%img_gold_coin%</white> <white>x1</white>",
                        "<white>%img_iron_coin%</white> <white>x2</white>",
                        "<white>%img_copper_coin%</white> <white>x5</white>"),
                CoinFormat.formatLines(ECO, 2205));
    }

    @Test
    void rowFormatKeepsTheSameWhiteCount() {
        assertEquals("<white>%img_iron_coin%</white> <white>x1</white>  <white>%img_copper_coin%</white> <white>x28</white>",
                CoinFormat.format(ECO, 128));
    }
}
