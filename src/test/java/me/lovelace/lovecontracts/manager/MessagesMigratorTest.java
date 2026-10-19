package me.lovelace.lovecontracts.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesMigratorTest {

    private static YamlConfiguration yaml(String text) {
        return YamlConfiguration.loadConfiguration(new StringReader(text));
    }

    @Test
    void olderFileGetsTheBundledGuiSectionAndKeepsChatMessages() {
        YamlConfiguration server = yaml("""
                gui:
                  title: "<red>Старый"
                  old-only: "устаревший ключ"
                messages:
                  contract-accepted: "<green>Текст админа</green>"
                """);
        YamlConfiguration bundled = yaml("""
                data-version: 2
                gui:
                  title: "<gold>Доска"
                  style:
                    separator: "<dark_gray>-"
                messages:
                  contract-accepted: "<green>Из jar</green>"
                """);

        assertTrue(MessagesMigrator.migrate(server, bundled));

        assertEquals("<gold>Доска", server.getString("gui.title"));
        assertEquals("<dark_gray>-", server.getString("gui.style.separator"));
        assertFalse(server.contains("gui.old-only"), "keys the jar no longer has are dropped from gui");
        assertEquals("<green>Текст админа</green>", server.getString("messages.contract-accepted"));
        assertEquals(2, server.getInt(MessagesMigrator.VERSION_KEY));
    }

    @Test
    void currentFileIsLeftAlone() {
        YamlConfiguration server = yaml("""
                data-version: 2
                gui:
                  title: "<red>Своё</red>"
                """);
        YamlConfiguration bundled = yaml("""
                data-version: 2
                gui:
                  title: "<gold>Доска</gold>"
                """);

        assertFalse(MessagesMigrator.migrate(server, bundled));
        assertEquals("<red>Своё</red>", server.getString("gui.title"));
    }

    @Test
    void versionZeroAndMissingVersionBothStartFromTheFirstMigration() {
        YamlConfiguration zero = yaml("data-version: 0\ngui:\n  title: \"старый\"\n");
        YamlConfiguration bundled = yaml("data-version: 2\ngui:\n  title: \"новый\"\n");

        assertTrue(MessagesMigrator.migrate(zero, bundled));
        assertEquals("новый", zero.getString("gui.title"));
    }

    @Test
    void jarVersionAboveWhatTheCodeKnowsIsCappedAtTheLatestMigration() {
        YamlConfiguration server = yaml("gui:\n  title: \"старый\"\n");
        YamlConfiguration bundled = yaml("data-version: 9\ngui:\n  title: \"новый\"\n");

        assertTrue(MessagesMigrator.migrate(server, bundled));
        assertEquals(MessagesMigrator.latestVersion(), server.getInt(MessagesMigrator.VERSION_KEY));
    }

    @Test
    void bundledMessagesDeclareTheVersionTheCodeExpects() throws Exception {
        try (InputStream in = MessagesMigratorTest.class.getResourceAsStream("/messages.yml")) {
            assertTrue(in != null, "messages.yml must be on the classpath");
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            assertEquals(MessagesMigrator.latestVersion(), bundled.getInt(MessagesMigrator.VERSION_KEY));
        }
    }
}
