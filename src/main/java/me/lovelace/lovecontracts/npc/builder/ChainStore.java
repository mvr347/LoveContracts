package me.lovelace.lovecontracts.npc.builder;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * chains.yml: the chains made in the in-game builder. Hand-written {@code npc_quests.yml} and {@code npc_stories.yml}
 * (with their Russian comments) are never rewritten; only this file is. Writes go to a temporary file first, so a crash
 * cannot leave a half-written chains.yml.
 */
public final class ChainStore {

    public static final String FILE_NAME = "chains.yml";

    private final File file;

    public ChainStore(File dataFolder) {
        this.file = new File(dataFolder, FILE_NAME);
    }

    public File file() {
        return file;
    }

    /** Result of reading: the chains that parsed and a sentence for each one that did not. */
    public record Loaded(Map<String, ChainDraft> chains, List<String> errors) {
    }

    public synchronized Loaded read() {
        Map<String, ChainDraft> chains = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        if (!file.exists()) return new Loaded(chains, errors);
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("npc_quests");
        if (root == null) return new Loaded(chains, errors);
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            try {
                chains.put(id, ChainDraftYaml.fromSection(id, section));
            } catch (RuntimeException error) {
                errors.add(id + ": " + error.getMessage());
            }
        }
        return new Loaded(chains, errors);
    }

    /** Replaces the whole file with these chains. */
    public synchronized void write(Collection<ChainDraft> chains) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "Цепочки, созданные в игровом конструкторе (/lovecontractsadmin chains).",
                "Файл переписывается конструктором целиком: правьте цепочки в игре.",
                "Реплики, написанные здесь вручную, конструктор сохраняет как есть.",
                "Формат тот же, что у npc_quests.yml; рукописные файлы конструктор не трогает."));
        for (ChainDraft chain : chains) {
            yaml.set("npc_quests." + chain.id(), ChainDraftYaml.toMap(chain));
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
        File temp = new File(file.getPath() + ".tmp");
        yaml.save(temp);
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** The definitions of these chains as plain maps, in the shape the chain loader and the guards read. */
    public static Map<String, Object> definitions(Collection<ChainDraft> chains) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (ChainDraft chain : chains) out.put(chain.id(), ChainDraftYaml.toMap(chain));
        return out;
    }
}
