package me.lovelace.lovecontracts.integration;

import dev.lovelace.lovecore.api.npc.NpcHandle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcSettingsTest {

    private static YamlConfiguration yaml(String text) {
        return YamlConfiguration.loadConfiguration(new StringReader(text));
    }

    static NpcHandle handle(String providerId, String key) {
        return (NpcHandle) Proxy.newProxyInstance(NpcHandle.class.getClassLoader(), new Class<?>[]{NpcHandle.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "providerId" -> providerId;
                    case "key" -> key;
                    case "toString" -> providerId + ":" + key;
                    case "hashCode" -> 1;
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    @Test
    void providerIdIsCaseInsensitiveAndFallsBackToCitizens() {
        assertEquals("loveessential", NpcSettings.providerId(" LoveEssential "));
        assertEquals("citizens", NpcSettings.providerId("citizens"));
        assertEquals("citizens", NpcSettings.providerId(null));
        assertEquals("citizens", NpcSettings.providerId(""));
        assertEquals("citizens", NpcSettings.providerId("cmi"));
        assertFalse(NpcSettings.isKnownProvider("cmi"));
        assertTrue(NpcSettings.isKnownProvider("CITIZENS"));
    }

    @Test
    void numbersAndTextBecomeCanonicalKeys() {
        assertEquals("5", NpcSettings.normalizeKey(5));
        assertEquals("5", NpcSettings.normalizeKey(5L));
        assertEquals("5", NpcSettings.normalizeKey(" 005 "));
        assertEquals("0", NpcSettings.normalizeKey(0));
        assertEquals("ext-ab12cd34", NpcSettings.normalizeKey(" ext-ab12cd34 "));
    }

    @Test
    void blankAndLegacyUnboundMeanNoKey() {
        assertNull(NpcSettings.normalizeKey(null));
        assertNull(NpcSettings.normalizeKey(""));
        assertNull(NpcSettings.normalizeKey("   "));
        assertNull(NpcSettings.normalizeKey(-1));
        assertNull(NpcSettings.normalizeKey("-1"));
    }

    @Test
    void readKeyPrefersTheNewSpellingAndFallsBackToTheLegacyOne() {
        assertEquals("ext-1", NpcSettings.readKey("ext-1", 7));
        assertEquals("7", NpcSettings.readKey(null, 7));
        assertEquals("7", NpcSettings.readKey("", "7"));
        assertNull(NpcSettings.readKey(null, null));
    }

    @Test
    void legacyNumericIdIsMigratedToTheKey() {
        YamlConfiguration cfg = yaml("npc:\n  enabled: true\n  id: 12\n");
        assertEquals("12", NpcSettings.boundKey(cfg), "legacy value is readable even before the migration");

        assertTrue(NpcSettings.migrateLegacyBinding(cfg));

        assertEquals("12", cfg.getString("npc.key"));
        assertFalse(cfg.contains("npc.id"));
        assertTrue(cfg.getBoolean("npc.enabled"), "unrelated keys stay");
        assertEquals("12", NpcSettings.boundKey(cfg));
        assertFalse(NpcSettings.migrateLegacyBinding(cfg), "second run changes nothing");
    }

    @Test
    void unboundLegacyIdIsDroppedWithoutInventingAKey() {
        YamlConfiguration cfg = yaml("npc:\n  id: -1\n  key: \"\"\n");

        assertTrue(NpcSettings.migrateLegacyBinding(cfg));

        assertFalse(cfg.contains("npc.id"));
        assertNull(NpcSettings.boundKey(cfg));
    }

    @Test
    void anExistingKeyIsNotOverwrittenByTheLegacyId() {
        YamlConfiguration cfg = yaml("npc:\n  id: 3\n  key: \"ext-9f00aa11\"\n");

        assertTrue(NpcSettings.migrateLegacyBinding(cfg));

        assertEquals("ext-9f00aa11", cfg.getString("npc.key"));
        assertFalse(cfg.contains("npc.id"));
    }

    @Test
    void configWithoutNpcBindingIsUntouched() {
        YamlConfiguration cfg = yaml("npc:\n  enabled: true\n");
        assertFalse(NpcSettings.migrateLegacyBinding(cfg));
        assertNull(NpcSettings.boundKey(cfg));
    }

    @Test
    void bundledDefaultsAreNotMistakenForABinding() {
        YamlConfiguration cfg = yaml("npc:\n  enabled: true\n");
        cfg.setDefaults(yaml("npc:\n  key: \"99\"\n"));
        assertNull(NpcSettings.boundKey(cfg));
    }

    @Test
    void bindingMatchesOnlyTheConfiguredProviderAndKey() {
        assertTrue(NpcSettings.isBound(handle("citizens", "5"), "citizens", "5"));
        assertFalse(NpcSettings.isBound(handle("loveessential", "5"), "citizens", "5"), "other provider's NPC");
        assertFalse(NpcSettings.isBound(handle("citizens", "6"), "citizens", "5"));
        assertFalse(NpcSettings.isBound(handle("citizens", "5"), "citizens", null));
        assertTrue(NpcSettings.isBound(handle("loveessential", "ext-1"), "loveessential", "ext-1"));
    }

    @Test
    void describeKeepsTheOldHashFormatForNumericIds() {
        assertEquals("#5", NpcSettings.describe("5"));
        assertEquals("ext-1", NpcSettings.describe("ext-1"));
    }
}
