package me.lovelace.lovecontracts.gui;

import me.lovelace.lovecontracts.gui.LoreBlocks.Click;
import me.lovelace.lovecontracts.gui.LoreBlocks.Hint;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the GUI lore standard (gui-gen-6, GUI_LORE_STYLE.md) over the gui.* texts of messages.yml, and over the blocks
 * that {@link LoreBlocks} builds from them. The menus assemble their lore from these texts in Java; the blocks they
 * share are checked here on their real output, the texts are checked one by one.
 */
class GuiLoreStyleTest {

    private static final String PREFIX = "gui.";
    private static final String SEPARATOR_KEY = "gui.style.separator";

    /** Names of an item: no icons, no bold, no price, and the state set off with {@code <dark_gray>|}. */
    private static final Set<String> NAME_KEYS = Set.of(
            "gui.filter-title", "gui.sort-title", "gui.cycle-name", "gui.item-name-state", "gui.prev-page",
            "gui.next-page", "gui.close-button", "gui.inactive-create", "gui.confirm-yes", "gui.confirm-no",
            "gui.npc-dialogue-name", "gui.create-preset-name", "gui.create-count-name", "gui.create-reward-name",
            "gui.style.disabled-name");
    /** Name templates whose state is joined with {@code | }. */
    private static final Set<String> STATE_TEMPLATE_KEYS = Set.of(
            "gui.item-name-state", "gui.cycle-name", "gui.create-preset-name", "gui.create-count-name");
    /** Name parts that are a state of a name ("Имя | Состояние"), so they follow the name rules too. */
    private static final String[] NAME_PART_PREFIXES = {"gui.contract-state-", "gui.my-status-"};

    private static final String ICONS = "✓✕✔✖✗⌛▪▶·⚑⚠✦▸";
    private static final List<String> FORBIDDEN = List.of(
            "Нажмите", "Кликните", "•", "›", "▸", "ЛКМ -", "ПКМ -", "ЛКМ:", "ПКМ:", "✗", "✘", "✖", "🔒", "<b>",
            "<bold>", "§", "·", "⚑", "✦", "⚠");
    private static final Pattern LEGACY_CODE = Pattern.compile("&[0-9a-fk-orA-FK-OR]");
    private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z_][a-zA-Z0-9_]*)");
    private static final Pattern MONEY_AFTER_COLOR = Pattern.compile("<(?!white>)[a-z_]+>(\\s*)%img_");
    private static final Pattern SEPARATOR = Pattern.compile("^<dark_gray><st>─+</st>(</dark_gray>)?$");
    private static final Pattern SECTION = Pattern.compile("^<dark_gray>▪ <gray>[^<]+:$");
    private static final Pattern SUBTITLE = Pattern.compile("^<dark_gray>[^<]+$");
    private static final Pattern STATUS = Pattern.compile("^<(red|yellow|green)>(✕|⌛|✓|✔) [^<]+(</\\w+>)?$");
    private static final Pattern REQUIREMENT_ROW = Pattern.compile("^  <(green|red)>[✓✕] <gray>[^<]+.*$");
    private static final Pattern EFFECT_ROW = Pattern.compile("^  (<green>\\+ |<red>− )?<gray>[^<]+.*$");
    private static final Pattern CLICK_LEFT = Pattern.compile("^<green>ЛКМ <dark_gray>— <gray>\\{action}$");
    private static final Pattern CLICK_RIGHT = Pattern.compile("^<gold>ПКМ <dark_gray>— <gray>\\{action}$");
    private static final Pattern CLICK_SHIFT_LEFT = Pattern.compile("^<yellow>Shift\\+ЛКМ <dark_gray>— <gray>\\{action}$");
    private static final Pattern CLICK_SHIFT_RIGHT = Pattern.compile("^<yellow>Shift\\+ПКМ <dark_gray>— <gray>\\{action}$");
    private static final Pattern BLOCKED_CLICK = Pattern.compile("^<dark_gray>\\{click} — \\{action} \\(заблокировано\\)$");
    private static final Pattern REASON = Pattern.compile("^<red>Причина: <gray>\\{reason}$");
    /** A reason line after the placeholder is filled in. */
    private static final Pattern REASON_LINE = Pattern.compile("^<red>Причина: <gray>.+$");
    /** A blocked click line after the placeholders are filled in. */
    private static final Pattern BLOCKED_LINE = Pattern.compile(
            "^<dark_gray>(ЛКМ|ПКМ|Shift\\+ЛКМ|Shift\\+ПКМ) — .+ \\(заблокировано\\)$");
    private static final Pattern STATUS_LINE = Pattern.compile("^<(red|yellow|green)>(✕|⌛|✓|✔) .+$");

    private static final Map<String, String> CLICK_PATTERNS_KEYS = Map.of(
            "gui.style.click.left", "left", "gui.style.click.right", "right",
            "gui.style.click.shift-left", "shift-left", "gui.style.click.shift-right", "shift-right");

    private static final String[] REQUIRED_STYLE_KEYS = {
            SEPARATOR_KEY, "gui.style.click.left", "gui.style.click.right", "gui.style.click.shift-left",
            "gui.style.click.shift-right", "gui.style.reason", "gui.style.blocked-click", "gui.style.disabled-name",
            "gui.style.status.unavailable", "gui.style.status.locked", "gui.style.status.cooldown",
            "gui.style.status.max"};

    // ---- loading -----------------------------------------------------------------------------------------------

    private static YamlConfiguration messages() throws Exception {
        try (InputStream in = GuiLoreStyleTest.class.getResourceAsStream("/messages.yml")) {
            assertNotNull(in, "messages.yml must be on the classpath");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /** Every gui.* text of messages.yml by its dotted path (a sorted map, so failures read in order). */
    private static Map<String, String> guiTexts() throws Exception {
        YamlConfiguration cfg = messages();
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<String, Object> e : cfg.getValues(true).entrySet()) {
            if (e.getKey().startsWith(PREFIX) && e.getValue() instanceof String text) {
                out.put(e.getKey(), text);
            }
        }
        return out;
    }

    /** The style texts as LoreBlocks reads them: the messages.yml value, with the built-in default as fallback. */
    private static LoreBlocks.Style messagesStyle() throws Exception {
        YamlConfiguration cfg = messages();
        return (path, fallback) -> cfg.getString(path, fallback);
    }

    private static String value(Map<String, String> texts, String key) {
        String value = texts.get(key);
        assertNotNull(value, "messages.yml has no " + key);
        return value;
    }

    private static boolean isNameKey(String key) {
        if (NAME_KEYS.contains(key)) return true;
        for (String prefix : NAME_PART_PREFIXES) {
            if (key.startsWith(prefix)) return true;
        }
        return false;
    }

    private static boolean isStatusKey(String key) {
        return key.startsWith("gui.contract-status-") || key.startsWith("gui.style.status.");
    }

    // ---- rules over the texts -------------------------------------------------------------------------------------

    @Test
    void everyTextParsesAndUsesOnlyStandardTags() throws Exception {
        TagResolver standard = TagResolver.standard();
        List<String> broken = new ArrayList<>();
        for (Map.Entry<String, String> e : guiTexts().entrySet()) {
            Matcher m = TAG.matcher(e.getValue());
            while (m.find()) {
                if (!standard.has(m.group(2).toLowerCase(Locale.ROOT))) {
                    broken.add(e.getKey() + " has unknown tag <" + m.group(2) + ">");
                }
            }
            try {
                MiniMessage.miniMessage().deserialize(e.getValue());
            } catch (RuntimeException ex) {
                broken.add(e.getKey() + " does not parse: " + ex.getMessage());
            }
        }
        assertTrue(broken.isEmpty(), "unparsable or unknown tags: " + broken);
    }

    /** A text is one lore line. The old {@code \n} values drew two lines into one entry, which the client does not wrap. */
    @Test
    void everyTextIsOneLine() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, String> e : guiTexts().entrySet()) {
            if (e.getValue().contains("\n") || e.getValue().contains("\r")) offenders.add(e.getKey());
        }
        assertTrue(offenders.isEmpty(), "multi-line gui texts: " + offenders);
    }

    @Test
    void forbiddenStringsAreGone() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, String> e : guiTexts().entrySet()) {
            String key = e.getKey();
            String v = e.getValue();
            for (String bad : FORBIDDEN) {
                if (v.contains(bad)) offenders.add(key + " contains " + bad + ": " + v);
            }
            if (LEGACY_CODE.matcher(v).find() || v.contains("&")) offenders.add(key + " has a legacy colour code: " + v);
            // «После клика:» is a section title of the standard; the banned forms are the «Клик:» label and «нажмите»
            String lower = v.toLowerCase(Locale.ROOT);
            if (lower.contains("нажм") || lower.contains("кликни") || lower.contains("клик:")) {
                offenders.add(key + " uses a click/press verb: " + v);
            }
            if (v.contains("Причина:") && !key.equals("gui.style.reason")) offenders.add(key + " writes its own Причина: line: " + v);
            if (v.contains("✔") && !key.equals("gui.style.status.max")) offenders.add(key + " uses ✔ outside the max status: " + v);
            if (v.contains("▶") && !key.equals("gui.cycle-option-current")) offenders.add(key + " uses ▶ outside the cycle marker: " + v);
            if (v.contains("⌛") && !isStatusKey(key)) {
                offenders.add(key + " uses ⌛ outside a status: " + v);
            }
        }
        assertTrue(offenders.isEmpty(), "forbidden strings: " + offenders);
    }

    /** Click lines come from gui.style.click.* (the text of an action never carries ЛКМ/ПКМ). */
    @Test
    void clickLinesComeOnlyFromTheStandardStyle() throws Exception {
        Map<String, String> texts = guiTexts();
        List<String> offenders = new ArrayList<>();
        assertTrue(CLICK_PATTERNS_KEYS.keySet().stream().allMatch(texts::containsKey), "click styles present");
        for (Map.Entry<String, String> e : texts.entrySet()) {
            String v = e.getValue();
            boolean clickish = v.contains("ЛКМ") || v.contains("ПКМ") || v.contains("Shift+");
            if (!clickish) continue;
            boolean styleLine = CLICK_PATTERNS_KEYS.containsKey(e.getKey()) || e.getKey().equals("gui.style.blocked-click");
            if (!styleLine) offenders.add(e.getKey() + " writes a click itself: " + v);
        }
        assertEquals(List.of(), offenders, "click text outside gui.style.click*");

        assertTrue(CLICK_LEFT.matcher(value(texts, "gui.style.click.left")).matches(), "left click: green ЛКМ");
        assertTrue(CLICK_RIGHT.matcher(value(texts, "gui.style.click.right")).matches(), "right click: gold ПКМ");
        assertTrue(CLICK_SHIFT_LEFT.matcher(value(texts, "gui.style.click.shift-left")).matches(), "shift-left: yellow");
        assertTrue(CLICK_SHIFT_RIGHT.matcher(value(texts, "gui.style.click.shift-right")).matches(), "shift-right: yellow");
        assertTrue(BLOCKED_CLICK.matcher(value(texts, "gui.style.blocked-click")).matches(), "blocked click shape");
    }

    @Test
    void rightClickIsNeverRed() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, String> e : guiTexts().entrySet()) {
            if (e.getValue().contains("<red>ПКМ") || e.getValue().contains("<red>Shift")) offenders.add(e.getKey());
        }
        assertTrue(offenders.isEmpty(), "red ПКМ/Shift (ПКМ is gold): " + offenders);
    }

    /** An action is the plain phrase the click line ends with: no tags, no placeholders, no dash. */
    @Test
    void actionsArePlainPhrases() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, String> e : guiTexts().entrySet()) {
            if (!e.getKey().startsWith("gui.action-")) continue;
            String v = e.getValue();
            if (v.contains("<") || v.contains("{") || v.contains("—") || v.contains("ЛКМ") || v.contains("ПКМ")) {
                offenders.add(e.getKey() + " = " + v);
            }
        }
        assertTrue(offenders.isEmpty(), "action texts must be plain phrases: " + offenders);
    }

    @Test
    void separatorIsTheSharedOneAndNothingElseDrawsALine() throws Exception {
        Map<String, String> texts = guiTexts();
        String separator = value(texts, SEPARATOR_KEY);
        assertTrue(SEPARATOR.matcher(separator).matches(), "shared separator shape: " + separator);
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, String> e : texts.entrySet()) {
            if (e.getValue().contains("─") && !e.getValue().equals(separator)) offenders.add(e.getKey());
        }
        assertTrue(offenders.isEmpty(), "separator lines must be gui.style.separator: " + offenders);
    }

    @Test
    void namesHaveNoIconsBoldOrPriceAndStateTemplatesSetTheStateOff() throws Exception {
        Map<String, String> texts = guiTexts();
        List<String> offenders = new ArrayList<>();
        Pattern money = Pattern.compile("\\{(money|price|cost|refund|unit)}|%img_");
        for (Map.Entry<String, String> e : texts.entrySet()) {
            if (!isNameKey(e.getKey())) continue;
            String v = e.getValue();
            boolean icon = v.codePoints().anyMatch(cp -> ICONS.indexOf(cp) >= 0 || cp >= 0x1F000);
            if (icon) offenders.add(e.getKey() + " has an icon: " + v);
            if (v.contains("<b>") || v.contains("<bold>")) offenders.add(e.getKey() + " is bold: " + v);
            if (money.matcher(v).find()) offenders.add(e.getKey() + " shows a price: " + v);
        }
        for (String key : STATE_TEMPLATE_KEYS) {
            if (!texts.get(key).contains("<dark_gray>| ")) offenders.add(key + " must set the state off with <dark_gray>| ");
        }
        assertTrue(offenders.isEmpty(), "names: " + offenders);
    }

    @Test
    void subtitlesAreADarkGrayLine() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, String> e : guiTexts().entrySet()) {
            String key = e.getKey();
            if (!key.endsWith("-subtitle") && !key.equals("gui.current-contract")) continue;
            if (!SUBTITLE.matcher(e.getValue()).matches()) offenders.add(key + " = " + e.getValue());
        }
        assertTrue(offenders.isEmpty(), "subtitles must be one dark_gray line: " + offenders);
    }

    @Test
    void sectionsAreDarkGrayHeadersAndRowsAreIndentedByTwo() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, String> e : guiTexts().entrySet()) {
            String key = e.getKey();
            String v = e.getValue();
            if (key.startsWith("gui.section-") || key.equals("gui.cycle-options-title")) {
                if (!SECTION.matcher(v).matches()) offenders.add(key + " is not a ▪ section header: " + v);
            }
            if (v.contains("▪") && !SECTION.matcher(v).matches() && !key.equals("gui.cycle-options-title")) {
                offenders.add(key + " uses ▪ outside a section header: " + v);
            }
            int indent = 0;
            while (indent < v.length() && v.charAt(indent) == ' ') indent++;
            if (indent == 0) continue;
            boolean otherCycleOption = key.equals("gui.cycle-option-other");
            if (indent != 2 && !(otherCycleOption && indent == 4)) {
                offenders.add(key + " indents " + indent + " spaces (rows are 2, other options 4): " + v);
            }
        }
        assertTrue(offenders.isEmpty(), "sections and rows: " + offenders);
    }

    @Test
    void statusLinesCarryTheirStandardGlyph() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, String> e : guiTexts().entrySet()) {
            if (!isStatusKey(e.getKey())) continue;
            if (e.getKey().equals("gui.style.status.max")) {
                if (!e.getValue().startsWith("<green>✔ ")) offenders.add(e.getKey() + " = " + e.getValue());
                continue;
            }
            if (!STATUS.matcher(e.getValue()).matches()) offenders.add(e.getKey() + " = " + e.getValue());
        }
        assertTrue(offenders.isEmpty(), "status lines (✕ failed/blocked, ✓ done, ⌛ time, ✔ only at max): " + offenders);
        assertTrue(STATUS_LINE.matcher(value(guiTexts(), "gui.style.status.unavailable")).matches());
    }

    @Test
    void requirementAndEffectRowsCarryTheirMarker() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, String> e : guiTexts().entrySet()) {
            String key = e.getKey();
            if (key.startsWith("gui.requirement-") && !REQUIREMENT_ROW.matcher(e.getValue()).matches()) {
                offenders.add(key + " = " + e.getValue());
            }
            if (key.startsWith("gui.effect-") && !EFFECT_ROW.matcher(e.getValue()).matches()) {
                offenders.add(key + " = " + e.getValue());
            }
        }
        assertTrue(offenders.isEmpty(), "requirement (✓/✕) and effect (+/−) rows: " + offenders);
    }

    /** The reason is plain text: the block adds {@code Причина:} and its colours. */
    @Test
    void reasonTextsArePlain() throws Exception {
        Map<String, String> texts = guiTexts();
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, String> e : texts.entrySet()) {
            String key = e.getKey();
            if ((key.startsWith("gui.contract-reason-") || key.equals("gui.inactive-create-disabled"))
                    && e.getValue().contains("<")) {
                offenders.add(key + " = " + e.getValue());
            }
        }
        assertTrue(offenders.isEmpty(), "reason texts: " + offenders);
        assertTrue(REASON.matcher(value(texts, "gui.style.reason")).matches(), "reason line shape");
    }

    @Test
    void coinValuesFollowAWhiteTag() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, String> e : guiTexts().entrySet()) {
            if (MONEY_AFTER_COLOR.matcher(e.getValue()).find()) offenders.add(e.getKey() + " = " + e.getValue());
        }
        assertTrue(offenders.isEmpty(), "a coin glyph must be right after <white>: " + offenders);
    }

    @Test
    void everyStyleKeyIsPresentAndTheBuiltInDefaultsMatchTheFile() throws Exception {
        Map<String, String> texts = guiTexts();
        for (String key : REQUIRED_STYLE_KEYS) {
            assertNotNull(texts.get(key), "messages.yml needs " + key);
        }
        for (Map.Entry<String, String> e : LoreBlocks.defaults().entrySet()) {
            assertEquals(e.getValue(), texts.get(e.getKey()), "LoreBlocks default differs from messages.yml: " + e.getKey());
        }
    }

    /** Every gui.* key that the Java menus read literally exists in messages.yml (a missing key shows as [key] in game). */
    @Test
    void everyGuiKeyTheCodeReadsExists() throws Exception {
        Set<String> defined = guiTexts().keySet();
        Pattern literal = Pattern.compile("\"(gui\\.[a-z0-9.\\-]+)\"");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = literal.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (m.find()) {
                    String key = m.group(1);
                    // a prefix ("gui.style.click." + key) is built at runtime; config.yml keys are not messages.yml keys
                    if (key.endsWith(".") || key.equals("gui.title")) continue;
                    if (!defined.contains(key)) missing.add(key + " (" + file.getFileName() + ")");
                }
            }
        }
        assertTrue(missing.isEmpty(), "gui keys read by the code but missing in messages.yml: " + missing);
    }

    // ---- rules over the blocks LoreBlocks builds from the real texts ----------------------------------------------

    @Test
    void separatorComesRightBeforeTheClicks() throws Exception {
        LoreBlocks.Style style = messagesStyle();
        String separator = value(guiTexts(), SEPARATOR_KEY);
        List<String> lines = LoreBlocks.clicks(style, Hint.of(Click.LEFT, "принять контракт"),
                Hint.of(Click.RIGHT, "вернуться на доску"));

        assertEquals(3, lines.size(), "separator plus one line per click: " + lines);
        assertEquals(separator, lines.get(0));
        assertTrue(lines.get(1).startsWith("<green>ЛКМ <dark_gray>— <gray>"), lines.get(1));
        assertTrue(lines.get(2).startsWith("<gold>ПКМ <dark_gray>— <gray>"), lines.get(2));
        assertEquals(List.of(), LoreBlocks.clicks(style), "no hints, no separator");
    }

    @Test
    void blockWithoutReasonOrHintHasOnlySeparatorAndStatus() throws Exception {
        LoreBlocks.Style style = messagesStyle();
        assertEquals(List.of(value(guiTexts(), SEPARATOR_KEY), "<red>✕ Заблокировано"),
                LoreBlocks.blocked(style, LoreBlocks.status(style, "locked"), null, null));
    }

    @Test
    void blockedButtonShowsSeparatorStatusReasonAndBlockedClickInOrder() throws Exception {
        LoreBlocks.Style style = messagesStyle();
        String separator = value(guiTexts(), SEPARATOR_KEY);
        List<String> block = LoreBlocks.blocked(style, LoreBlocks.status(style, "unavailable"),
                "Не выполнены требования контракта", Hint.of(Click.LEFT, "принять контракт"));

        assertEquals(4, block.size(), block.toString());
        assertEquals(separator, block.get(0));
        assertTrue(STATUS_LINE.matcher(block.get(1)).matches(), block.get(1));
        assertTrue(REASON_LINE.matcher(block.get(2)).matches(), block.get(2));
        assertTrue(BLOCKED_LINE.matcher(block.get(3)).matches(), block.get(3));
        assertEquals("<red>Причина: <gray>Не выполнены требования контракта", block.get(2));
        assertEquals("<dark_gray>ЛКМ — принять контракт (заблокировано)", block.get(3));
    }

    @Test
    void disabledNameIsNameAndUnavailableState() throws Exception {
        assertEquals("<gray>Охота <dark_gray>| <red>Недоступно",
                LoreBlocks.disabledName(messagesStyle(), "Охота"));
    }
}
