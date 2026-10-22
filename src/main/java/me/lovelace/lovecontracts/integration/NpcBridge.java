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
 * The only place that talks to the NPC provider chosen by {@code integrations.npc-provider}. The provider is
 * resolved on every use because providers register late (LoveCore, LoveEssential load around this plugin).
 * A missing provider is reported with one warning and only disables the NPC feature.
 */
public final class NpcBridge {

    private final Supplier<ConfigurationSection> config;
    private final Function<String, Optional<NpcProvider>> lookup;
    private final Consumer<String> warn;

    private String warnedAbout;
    private boolean warnedUnknownValue;

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

    /** The configured provider id; an unknown value is reported once and treated as {@code citizens}. */
    public synchronized String providerId() {
        Object raw = config.get().get(NpcSettings.PROVIDER_PATH, null);
        if (raw != null && !raw.toString().isBlank() && !NpcSettings.isKnownProvider(raw)) {
            if (!warnedUnknownValue) {
                warnedUnknownValue = true;
                warn.accept(NpcSettings.PROVIDER_PATH + ": unknown value '" + raw + "' (expected citizens or "
                        + "loveessential), using citizens");
            }
        } else {
            warnedUnknownValue = false;
        }
        return NpcSettings.providerId(raw);
    }

    /** The configured provider, or empty (with one warning naming it) when it is not registered. */
    public synchronized Optional<NpcProvider> provider() {
        String id = providerId();
        Optional<NpcProvider> found = lookup.apply(id);
        if (found.isPresent()) {
            warnedAbout = null;
        } else if (!id.equals(warnedAbout)) {
            warnedAbout = id;
            warn.accept("NPC provider '" + id + "' is not available (is LoveCore"
                    + (NpcSettings.LOVEESSENTIAL.equals(id) ? " / LoveEssential" : " / Citizens") + " installed and enabled?);"
                    + " the contract-board NPC is disabled until it appears");
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

    /** True when the click event concerns the bound contract-board NPC of the configured provider. */
    public boolean isBoundNpc(NpcHandle npc) {
        // NpcClickEvent comes from every provider at once: others are ignored without touching the registry.
        String key = boundKey();
        return key != null && npc != null && NpcSettings.isBound(npc, providerIdQuiet(), key);
    }

    private String providerIdQuiet() {
        return NpcSettings.providerId(config.get().get(NpcSettings.PROVIDER_PATH, null));
    }
}
