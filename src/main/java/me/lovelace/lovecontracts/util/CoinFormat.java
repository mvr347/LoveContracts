package me.lovelace.lovecontracts.util;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.Denomination;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Формат сумм глифами монет, как в LoveShop и LoveDuels: {@code %img_<tag>% xN} по номиналам LoveEconomy.
 * Награды и штрафы контрактов показываются монетами, а не голым числом.
 *
 * <p>Строка с глифами — MiniMessage; перед {@code MiniMessage.deserialize} её нужно прогнать через
 * {@link #resolveGlyphs(Player, String)} (PlaceholderAPI подставляет шрифтовые символы ItemsAdder).</p>
 */
public final class CoinFormat {

    private static final Pattern GLYPH = Pattern.compile("%img_[A-Za-z0-9_]+%");
    private static final Pattern LEGACY = Pattern.compile("[§&][0-9a-fk-orx]");

    private CoinFormat() {
    }

    public static Optional<LoveEconomy> tryEconomy() {
        if (!Bukkit.getPluginManager().isPluginEnabled("LoveCore")) {
            return Optional.empty();
        }
        try {
            return LoveCore.service(LoveEconomy.class);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /** {@code <white>%img_gold_coin%</white>}: белый цвет до и после глифа, чтобы он не брал цвет соседнего текста. */
    static String glyph(Denomination den) {
        String id = den.itemId();
        int colon = id.indexOf(':');
        String tag = colon >= 0 ? id.substring(colon + 1) : id;
        return "<white>%img_" + tag + "%</white>";
    }

    /** Сумма глифами по номиналам от старшего к младшему; ноль — «0 монет» (без номинала нет глифа для ноля). */
    public static String format(long amount) {
        return format(tryEconomy().orElse(null), amount);
    }

    public static String format(LoveEconomy eco, long amount) {
        if (eco == null) {
            return amount + " монет";
        }
        List<Denomination> dens = new ArrayList<>(eco.denominations());
        dens.sort(Comparator.comparingLong(Denomination::value).reversed());
        if (amount <= 0 || dens.isEmpty()) {
            return "0 " + eco.currencyName();
        }
        StringBuilder sb = new StringBuilder();
        long remaining = amount;
        for (Denomination den : dens) {
            if (den.value() <= 0) continue;
            long count = remaining / den.value();
            if (count > 0) {
                if (sb.length() > 0) sb.append("  ");
                sb.append(glyph(den)).append(" x").append(count);
                remaining %= den.value();
            }
        }
        if (sb.length() == 0) {
            return "0 " + eco.currencyName();
        }
        return sb.toString();
    }

    /** MiniMessage-текст с глифами монет → компонент для конкретного игрока. */
    public static net.kyori.adventure.text.Component component(Player player, String mm) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(resolveGlyphs(player, mm));
    }

    private static String applyPlaceholders(Player player, String text) {
        if (text == null || text.isEmpty() || player == null) return text;
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return text;
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable ignored) {
            return text; // intentional: PlaceholderAPI is optional, the placeholder text stays as is
        }
    }

    /** Подставляет шрифтовые символы вместо {@code %img_*%} (и только их), легаси-коды в результате вырезает. */
    public static String resolveGlyphs(Player player, String mm) {
        if (mm == null || mm.indexOf("%img_") < 0) return mm;
        return GLYPH.matcher(mm).replaceAll(r -> {
            String raw = r.group();
            String out = applyPlaceholders(player, raw);
            if (out == null || out.equals(raw)) return Matcher.quoteReplacement(raw);
            return Matcher.quoteReplacement(LEGACY.matcher(out).replaceAll(""));
        });
    }
}
