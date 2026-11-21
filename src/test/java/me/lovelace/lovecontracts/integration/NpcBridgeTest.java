package me.lovelace.lovecontracts.integration;

import dev.lovelace.lovecore.api.npc.NpcProvider;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcBridgeTest {

    private final List<String> warnings = new ArrayList<>();
    private final Map<String, NpcProvider> registered = new java.util.HashMap<>();
    private YamlConfiguration config = yaml("npc:\n  key: \"5\"\n");

    private static YamlConfiguration yaml(String text) {
        return YamlConfiguration.loadConfiguration(new StringReader(text));
    }

    private static NpcProvider provider(String id) {
        return (NpcProvider) Proxy.newProxyInstance(NpcProvider.class.getClassLoader(), new Class<?>[]{NpcProvider.class},
                (proxy, method, args) -> "providerId".equals(method.getName()) ? id : null);
    }

    private NpcBridge bridge() {
        return new NpcBridge(() -> config, id -> Optional.ofNullable(registered.get(id)), warnings::add);
    }

    @Test
    void usesLoveEssentialOnly() {
        NpcProvider essential = provider("loveessential");
        registered.put("loveessential", essential);
        registered.put("citizens", provider("citizens"));
        NpcBridge bridge = bridge();

        assertEquals("loveessential", bridge.providerId());
        assertSame(essential, bridge.provider().orElseThrow());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void missingProviderWarnsOnceAndDisablesOnlyTheNpcFeature() {
        NpcBridge bridge = bridge();

        assertTrue(bridge.provider().isEmpty());
        assertTrue(bridge.provider().isEmpty());
        assertTrue(bridge.targeted(null).isEmpty());

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("'loveessential'"), warnings.get(0));
    }

    @Test
    void aProviderThatRegistersLateIsPickedUpAndAWarningCanRepeatAfterItVanishes() {
        NpcBridge bridge = bridge();
        assertTrue(bridge.provider().isEmpty());
        assertEquals(1, warnings.size());

        registered.put("loveessential", provider("loveessential"));
        assertTrue(bridge.provider().isPresent());

        registered.clear();
        assertTrue(bridge.provider().isEmpty());
        assertEquals(2, warnings.size());
    }

    @Test
    void leftoverProviderKeyIsIgnoredWithOneWarning() {
        config = yaml("integrations:\n  npc-provider: citizens\nnpc:\n  key: \"5\"\n");
        registered.put("loveessential", provider("loveessential"));
        NpcBridge bridge = bridge();

        assertTrue(bridge.provider().isPresent(), "the old value must not pick another provider");
        assertTrue(bridge.provider().isPresent());

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("'citizens'") && warnings.get(0).contains("ignored"), warnings.get(0));
    }

    @Test
    void harmlessLeftoverValuesDoNotWarn() {
        registered.put("loveessential", provider("loveessential"));
        for (String value : List.of("global", "loveessential", "''")) {
            config = yaml("integrations:\n  npc-provider: " + value + "\nnpc:\n  key: \"5\"\n");
            assertTrue(bridge().provider().isPresent());
        }
        assertTrue(warnings.isEmpty());
    }

    @Test
    void clickFilteringUsesLoveEssentialAndTheBoundKey() {
        NpcBridge bridge = bridge();

        assertTrue(bridge.isBoundNpc(NpcSettingsTest.handle("loveessential", "5")));
        assertFalse(bridge.isBoundNpc(NpcSettingsTest.handle("citizens", "5")));
        assertFalse(bridge.isBoundNpc(NpcSettingsTest.handle("loveessential", "6")));
        assertTrue(warnings.isEmpty(), "ignoring a foreign click must not touch the registry or warn");

        config.set("npc.key", "ext-ab12cd34");
        assertTrue(bridge.isBoundNpc(NpcSettingsTest.handle("loveessential", "ext-ab12cd34")));
        assertFalse(bridge.isBoundNpc(NpcSettingsTest.handle("citizens", "ext-ab12cd34")));
    }

    @Test
    void nothingIsBoundWithoutAKey() {
        config.set("npc.key", "");
        assertFalse(bridge().isBoundNpc(NpcSettingsTest.handle("loveessential", "5")));
    }

    @Test
    void legacyNumericIdStillBinds() {
        config = yaml("npc:\n  id: 5\n");
        assertTrue(bridge().isBoundNpc(NpcSettingsTest.handle("loveessential", "5")));
    }
}
