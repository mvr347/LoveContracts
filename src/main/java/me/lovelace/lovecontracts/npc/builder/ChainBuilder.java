package me.lovelace.lovecontracts.npc.builder;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.npc.manager.NpcQuestManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Saves and deletes the chains of the in-game builder. Everything that can go wrong is turned into a sentence for the
 * admin: an incomplete chain, an id that is taken, a chain that players are on right now, a file that cannot be written.
 * After a successful write the chains are reloaded, so the change is live at once.
 */
public final class ChainBuilder {

    /** @param message what happened, for the admin; the new id of the chain when it was saved */
    public record Result(boolean ok, String message) {
        static Result ok(String message) { return new Result(true, message); }
        static Result fail(String message) { return new Result(false, message); }
    }

    private final LoveContracts plugin;

    public ChainBuilder(LoveContracts plugin) {
        this.plugin = plugin;
    }

    private NpcQuestManager manager() {
        return plugin.getNpcQuestManager();
    }

    /** The chains in chains.yml, read afresh: what the builder may edit. */
    public Map<String, ChainDraft> chains() {
        ChainStore.Loaded loaded = manager().chainStore().read();
        for (String error : loaded.errors()) plugin.getLogger().warning("chains.yml: " + error);
        return loaded.chains();
    }

    /** An independent copy of a stored chain to edit, or null when there is none. */
    public ChainDraft draftOf(String id) {
        ChainDraft stored = chains().get(id);
        return stored == null ? null : stored.copyAs(id);
    }

    /** Whether an id is taken by any loaded chain (hand-written ones included) or by a chain in chains.yml. */
    public boolean idTaken(String id) {
        return manager().getQuest(id) != null || chains().containsKey(id);
    }

    /** Writes a chain. {@code asNew} saves a copy under the next free {@code _vN} id and leaves the original alone. */
    public Result save(ChainDraft draft, boolean asNew) {
        Map<String, ChainDraft> all = chains();
        ChainDraft toSave = draft;
        boolean exists = all.containsKey(draft.id());
        if (asNew) {
            Set<String> taken = new java.util.HashSet<>(all.keySet());
            for (var quest : manager().allQuests()) taken.add(quest.getId());
            toSave = draft.copyAs(ChainDraft.nextVersionId(draft.id(), taken));
        } else if (!exists && manager().getQuest(draft.id()) != null) {
            return Result.fail("Идентификатор «" + draft.id() + "» уже занят цепочкой из другого файла");
        }

        List<String> problems = ChainDraftCheck.problems(toSave);
        if (!problems.isEmpty()) return Result.fail(problems.get(0));

        Map<String, ChainDraft> updated = new LinkedHashMap<>(all);
        updated.put(toSave.id(), toSave);
        return write(updated, toSave.id());
    }

    public Result delete(String id) {
        Map<String, ChainDraft> all = chains();
        if (!all.containsKey(id)) return Result.fail("Цепочки «" + id + "» нет в конструкторе");
        Map<String, ChainDraft> updated = new LinkedHashMap<>(all);
        updated.remove(id);
        return write(updated, id);
    }

    private Result write(Map<String, ChainDraft> updated, String id) {
        // chains.yml is rewritten whole from what parsed; a chain that failed to parse would be erased with it
        ChainStore.Loaded current = manager().chainStore().read();
        if (!current.errors().isEmpty()) {
            return Result.fail("В chains.yml есть цепочка с ошибкой (" + current.errors().get(0)
                    + "): исправьте файл, иначе она пропадёт при записи");
        }
        try {
            manager().validateChainDefinitions(ChainStore.definitions(new ArrayList<>(updated.values())));
        } catch (IllegalArgumentException refused) {
            return Result.fail(refused.getMessage());
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING, "Chain validation failed", error);
            return Result.fail("Проверка не удалась: " + error.getMessage());
        }
        try {
            manager().chainStore().write(updated.values());
        } catch (IOException error) {
            plugin.getLogger().log(Level.WARNING, "Cannot write chains.yml", error);
            return Result.fail("Не удалось записать chains.yml: " + error.getMessage());
        }
        manager().loadFromConfig();
        return Result.ok(id);
    }
}
