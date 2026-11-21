package me.lovelace.lovecontracts.integration;

import dev.lovelace.lovecore.api.npc.NpcHandle;
import dev.lovelace.lovecore.api.provider.Providers;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Pure parsing of the NPC-related settings: which NPC key is bound.
 * Kept free of any server calls so it can be unit-tested.
 */
public final class NpcSettings {

    /** The only supported NPC provider: LoveEssential. */
    public static final String PROVIDER_ID = Providers.LOVE_ESSENTIAL;
    /**
     * 2026-11-21: the provider switch was removed (LoveEssential is the only provider). The old key is only read
     * to tell an admin that a leftover value is ignored.
     */
    public static final String LEGACY_PROVIDER_PATH = "integrations.npc-provider";
    public static final String KEY_PATH = "npc.key";
    /** Pre-provider config: the numeric NPC id, {@code -1} meaning "not bound". LoveEssential resolves numeric keys. */
    public static final String LEGACY_ID_PATH = "npc.id";

    private NpcSettings() {
    }

    /**
     * The leftover {@code integrations.npc-provider} value that is being ignored, or {@code null} when the key is
     * absent or already names LoveEssential ({@code global} and blank also count as harmless).
     */
    public static String ignoredProviderValue(ConfigurationSection config) {
        Object raw = config.get(LEGACY_PROVIDER_PATH, null);
        if (raw == null) {
            return null;
        }
        String text = raw.toString().trim();
        if (text.isEmpty() || text.equalsIgnoreCase("global") || text.equalsIgnoreCase(PROVIDER_ID)) {
            return null;
        }
        return text;
    }

    /**
     * Normalises an NPC key read from yaml. A number (a legacy numeric id or a LoveEssential NPC number, written as int or text) becomes its
     * canonical decimal text; a negative number is the legacy "not bound" marker. Blank means no key.
     *
     * @return the key, or {@code null} when nothing is bound
     */
    public static String normalizeKey(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.matches("-?\\d+")) {
            try {
                long number = Long.parseLong(text);
                return number < 0 ? null : Long.toString(number);
            } catch (NumberFormatException tooLong) {
                return text.startsWith("-") ? null : text.replaceFirst("^0+(?=\\d)", "");
            }
        }
        return text;
    }

    /** {@code preferred} when it holds a key, otherwise the legacy spelling (for example {@code npc-id}). */
    public static String readKey(Object preferred, Object legacy) {
        String key = normalizeKey(preferred);
        return key != null ? key : normalizeKey(legacy);
    }

    /** The bound contract-board NPC: {@code npc.key}, falling back to the legacy {@code npc.id}. */
    public static String boundKey(ConfigurationSection config) {
        // get(path, null) deliberately ignores the defaults bundled in the jar.
        return readKey(config.get(KEY_PATH, null), config.get(LEGACY_ID_PATH, null));
    }

    /**
     * Moves the legacy numeric {@code npc.id} into {@code npc.key}. An existing {@code npc.key} wins; the legacy
     * key is always removed so the migration is idempotent.
     *
     * @return true when the section was changed (the caller saves it)
     */
    public static boolean migrateLegacyBinding(ConfigurationSection config) {
        if (!config.contains(LEGACY_ID_PATH, true)) {
            return false;
        }
        String legacy = normalizeKey(config.get(LEGACY_ID_PATH, null));
        if (legacy != null && normalizeKey(config.get(KEY_PATH, null)) == null) {
            config.set(KEY_PATH, legacy);
        }
        config.set(LEGACY_ID_PATH, null);
        return true;
    }

    /** Whether a clicked NPC is the bound LoveEssential one. */
    public static boolean isBound(NpcHandle npc, String boundKey) {
        return npc != null && boundKey != null
                && PROVIDER_ID.equalsIgnoreCase(npc.providerId())
                && boundKey.equals(normalizeKey(npc.key()));
    }

    /** Human label for chat: numeric keys read as {@code #5}, others as they are. */
    public static String describe(String key) {
        return key.matches("\\d+") ? "#" + key : key;
    }
}
