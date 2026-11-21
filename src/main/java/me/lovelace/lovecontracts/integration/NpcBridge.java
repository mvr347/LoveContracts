package me.lovelace.lovecontracts.integration;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.npc.NpcHandle;
import dev.lovelace.lovecore.api.npc.NpcProvider;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The only place that talks to the NPC provider (LoveEssential). The provider is resolved on every use because
 * it registers late (LoveCore, LoveEssential load around this plugin). A missing provider is reported with one
 * warning and only disables the NPC feature.
 */
public final class NpcBridge {

    private final Supplier<ConfigurationSection> config;
    private final Function<String, Optional<NpcProvider>> lookup;
    private final Consumer<String> warn;

    private String warnedAbout;
    private String warnedIgnoredValue;

    public NpcBridge(Supplier<ConfigurationSection> config, Consumer<String> warn) {
        this(config, NpcBridge::lookupViaLoveCore, warn);
    }

    public NpcBridge(Supplier<ConfigurationSection> config, Function<String, Optional<NpcProvider>> lookup,
                     Consumer<String> warn) {
        this.config = config;
        this.lookup = lookup;
        this.warn = warn;
    }

    private static Optional<NpcProvider> lookupViaLoveCore(String id) {
        try {
            return LoveCore.provider(NpcProvider.class, id);
        } catch (LinkageError loveCoreMissing) {
            return Optional.empty();
        }
    }

    /** The provider id: always LoveEssential. */
    public String providerId() {
        return NpcSettings.PROVIDER_ID;
    }

    /** The NPC provider, or empty (with one warning) when LoveEssential is not registered. */
    public synchronized Optional<NpcProvider> provider() {
        String ignored = NpcSettings.ignoredProviderValue(config.get());
        if (ignored != null && !ignored.equals(warnedIgnoredValue)) {
            warnedIgnoredValue = ignored;
            warn.accept(NpcSettings.LEGACY_PROVIDER_PATH + ": value '" + ignored + "' is ignored, NPCs come from "
                    + "LoveEssential only; remove the key from config.yml");
        }
        String id = providerId();
        Optional<NpcProvider> found = lookup.apply(id);
        if (found.isPresent()) {
            warnedAbout = null;
        } else if (!id.equals(warnedAbout)) {
            warnedAbout = id;
            warn.accept("NPC provider '" + id + "' is not available (are LoveCore and LoveEssential installed and "
                    + "enabled?); the contract-board NPC is disabled until it appears");
        }
        return found;
    }

    /** The NPC the player points at or has selected, per the configured provider. */
    public Optional<NpcHandle> targeted(org.bukkit.entity.Player player) {
        return provider().flatMap(provider -> provider.targeted(player));
    }

    public String boundKey() {
        return NpcSettings.boundKey(config.get());
    }

    /** True when the click event concerns the bound contract-board NPC. */
    public boolean isBoundNpc(NpcHandle npc) {
        String key = boundKey();
        return key != null && npc != null && NpcSettings.isBound(npc, key);
    }
}
