package me.lovelace.lovecontracts.manager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Menu texts of messages.yml reach an existing server only through {@code data-version}: copyDefaults never updates a
 * key that is already in the file. An older file gets the {@code gui} section taken again from the jar (the caller
 * keeps the previous file as a backup). Chat messages in the other sections stay as the admin left them.
 */
public final class MessagesMigrator {

    public static final String VERSION_KEY = "data-version";

    /** Sections overwritten when migrating to version N (index = N - 2). */
    private static final List<List<String>> SECTIONS_BY_VERSION = List.of(
            // v2: GUI lore and names in the gui-gen-6 standard (style.*, gui.*)
            List.of("gui"));

    private MessagesMigrator() {
    }

    public static int latestVersion() {
        return SECTIONS_BY_VERSION.size() + 1;
    }

    /** Changes {@code server} in place and returns true when it was older than the jar's messages.yml. */
    public static boolean migrate(FileConfiguration server, FileConfiguration bundled) {
        int current = server.getInt(VERSION_KEY, 1);
        int target = Math.min(bundled.getInt(VERSION_KEY, 1), latestVersion());
        if (current >= target) {
            return false;
        }
        // the first version with a migration is 2, so a file with version 0 or 1 starts there
        for (int version = Math.max(current + 1, 2); version <= target; version++) {
            for (String section : SECTIONS_BY_VERSION.get(version - 2)) {
                overwriteSection(server, bundled, section);
            }
        }
        server.set(VERSION_KEY, target);
        return true;
    }

    private static void overwriteSection(FileConfiguration server, FileConfiguration bundled, String path) {
        server.set(path, null);
        ConfigurationSection jarSection = bundled.getConfigurationSection(path);
        if (jarSection == null) {
            return;
        }
        for (String key : jarSection.getKeys(true)) {
            Object leaf = jarSection.get(key);
            if (!(leaf instanceof ConfigurationSection)) {
                server.set(path + "." + key, leaf);
            }
        }
    }
}
