package me.lovelace.lovecontracts.integration;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.npc.NpcHandle;
import dev.lovelace.lovecore.api.provider.ProviderKind;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Set;

/**
 * Pure parsing of the NPC-related settings: which provider is configured and which NPC key is bound.
 * Kept free of any server calls so it can be unit-tested.
 */
public final class NpcSettings {

    public static final String PROVIDER_PATH = "integrations.npc-provider";
    public static final String KEY_PATH = "npc.key";
    /** Pre-provider config: the numeric Citizens id, {@code -1} meaning "not bound". */
    public static final String LEGACY_ID_PATH = "npc.id";

    public static final String CITIZENS = "citizens";
    public static final String LOVEESSENTIAL = "loveessential";
    public static final Set<String> KNOWN_PROVIDERS = Set.of(CITIZENS, LOVEESSENTIAL);

    private NpcSettings() {
    }

    /** True when the raw config value names a provider this plugin knows (case-insensitive). */
    public static boolean isKnownProvider(Object raw) {
        return raw != null && KNOWN_PROVIDERS.contains(raw.toString().trim().toLowerCase(Locale.ROOT));
    }

    /** Config value that follows {@code integrations.provider} of LoveCore. */
    public static final String GLOBAL = "global";

    /** True for an absent, blank or {@code global} value: the provider is then the global choice of LoveCore. */
    public static boolean followsGlobal(Object raw) {
        return raw == null || raw.toString().isBlank() || GLOBAL.equalsIgnoreCase(raw.toString().trim());
    }

    /**
     * Provider id from the config value. {@code global}, absent or blank follows {@code integrations.provider}
     * of LoveCore (without LoveCore: {@code citizens}); a known id wins; an unknown value falls back to
     * {@code citizens}.
     */
    public static String providerId(Object raw) {
        if (followsGlobal(raw)) {
            try {
                return LoveCore.providerId(ProviderKind.NPC, GLOBAL);
            } catch (LinkageError coreMissingOrOld) {
                return CITIZENS;
            }
        }
        return isKnownProvider(raw) ? raw.toString().trim().toLowerCase(Locale.ROOT) : CITIZENS;
    }

    /**
     * Normalises an NPC key read from yaml. A number (legacy Citizens id, written as int or text) becomes its
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

    /** Whether a clicked NPC is the bound one of the configured provider. */
    public static boolean isBound(NpcHandle npc, String providerId, String boundKey) {
        return npc != null && boundKey != null
                && providerId.equalsIgnoreCase(npc.providerId())
                && boundKey.equals(normalizeKey(npc.key()));
    }

    /** Human label for chat: numeric keys read as {@code #5}, others as they are. */
    public static String describe(String key) {
        return key.matches("\\d+") ? "#" + key : key;
    }
}
