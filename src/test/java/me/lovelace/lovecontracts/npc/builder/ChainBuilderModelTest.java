package me.lovelace.lovecontracts.npc.builder;

import me.lovelace.lovecontracts.npc.model.NpcQuestStep;
import me.lovelace.lovecontracts.npc.model.QuestLocation;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ChainBuilderModelTest {

    private static ChainDraft sample() {
        ChainDraft chain = new ChainDraft("smith_order");
        chain.displayName("<gold>Заказ кузнеца</gold>");
        chain.giverKey("ext-1a2b3c4d");
        chain.repeatable(true);
        chain.cooldownMinutes(30);
        chain.reward(300);

        StepDraft kill = new StepDraft(NpcQuestStep.Type.KILL_ENTITY);
        kill.entityType("zombie");
        kill.count(15);
        kill.area(new QuestLocation("world", 120.5, 64, -80, 12));
        chain.steps().add(kill);

        StepDraft craft = new StepDraft(NpcQuestStep.Type.CRAFT_ITEM);
        craft.material("IRON_INGOT");
        craft.count(16);
        craft.description("Скрафтите слитки");
        chain.steps().add(craft);

        StepDraft visit = new StepDraft(NpcQuestStep.Type.VISIT_NPC);
        visit.targetNpc("ext-5e6f7a8b", "Оружейник");
        chain.steps().add(visit);

        StepDraft deliver = new StepDraft(NpcQuestStep.Type.DELIVER_ITEM);
        deliver.material("BREAD");
        deliver.count(24);
        deliver.targetNpc("ext-5e6f7a8b", "Трактирщик");
        chain.steps().add(deliver);

        StepDraft go = new StepDraft(NpcQuestStep.Type.VISIT_LOCATION);
        go.area(new QuestLocation("world_nether", 10, 70, 10, 8));
        chain.steps().add(go);
        return chain;
    }

    // ---- yaml form -------------------------------------------------------------------------------------------

    @Test
    void theYamlFormCarriesTheKeysTheChainLoaderReads() {
        Map<String, Object> map = ChainDraftYaml.toMap(sample());
        for (String key : List.of("npc-key", "display-name", "repeatable", "cooldown-minutes", "initial-dialogue",
                "dialogues", "steps", "rewards")) {
            assertTrue(map.containsKey(key), key);
        }
        assertEquals("greeting", map.get("initial-dialogue"));
        assertEquals(Map.of("money", 300L), map.get("rewards"));
        @SuppressWarnings("unchecked")
        Map<String, Object> dialogues = (Map<String, Object>) map.get("dialogues");
        assertTrue(dialogues.containsKey("greeting"), "the offer the loader requires");
        assertEquals(5, ((List<?>) map.get("steps")).size());
    }

    @Test
    void aChainSurvivesWritingAndReadingTheFile(@TempDir File dir) throws Exception {
        ChainStore store = new ChainStore(dir);
        ChainDraft chain = sample();
        store.write(List.of(chain));

        ChainStore.Loaded loaded = store.read();
        assertEquals(List.of(), loaded.errors());
        ChainDraft back = loaded.chains().get("smith_order");
        assertNotNull(back);
        assertEquals("<gold>Заказ кузнеца</gold>", back.displayName());
        assertEquals("ext-1a2b3c4d", back.giverKey());
        assertTrue(back.repeatable());
        assertEquals(30, back.cooldownMinutes());
        assertEquals(300, back.reward());
        assertEquals(5, back.steps().size());

        StepDraft kill = back.steps().get(0);
        assertEquals(NpcQuestStep.Type.KILL_ENTITY, kill.type());
        assertEquals("ZOMBIE", kill.entityType());
        assertEquals(15, kill.count());
        assertEquals(new QuestLocation("world", 120.5, 64, -80, 12), kill.area());

        StepDraft craft = back.steps().get(1);
        assertEquals("IRON_INGOT", craft.material());
        assertEquals("Скрафтите слитки", craft.description());

        StepDraft deliver = back.steps().get(3);
        assertEquals("BREAD", deliver.material());
        assertEquals("ext-5e6f7a8b", deliver.targetNpcKey());
        assertEquals("Трактирщик", deliver.targetNpcName());

        assertEquals(new QuestLocation("world_nether", 10, 70, 10, 8), back.steps().get(4).area());
    }

    @Test
    void writingTwiceGivesTheSameFile(@TempDir File dir) throws Exception {
        ChainStore store = new ChainStore(dir);
        store.write(List.of(sample()));
        String first = java.nio.file.Files.readString(store.file().toPath());
        store.write(store.read().chains().values());
        assertEquals(first, java.nio.file.Files.readString(store.file().toPath()),
                "re-saving what was read must not change the file (auto descriptions stay automatic)");
    }

    @Test
    void handWrittenDialoguesAreKeptWhenTheBuilderSavesAgain(@TempDir File dir) throws Exception {
        ChainStore store = new ChainStore(dir);
        store.write(List.of(sample()));
        // an admin edits the offer text by hand
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(store.file());
        yaml.set("npc_quests.smith_order.dialogues.greeting.text", List.of("<yellow>Брон:</yellow> Нужны доспехи!"));
        yaml.save(store.file());

        ChainDraft chain = store.read().chains().get("smith_order");
        chain.reward(500);
        store.write(List.of(chain));

        YamlConfiguration after = YamlConfiguration.loadConfiguration(store.file());
        assertEquals(List.of("<yellow>Брон:</yellow> Нужны доспехи!"),
                after.getStringList("npc_quests.smith_order.dialogues.greeting.text"));
        assertEquals(500, after.getLong("npc_quests.smith_order.rewards.money"));
    }

    @Test
    void aRewardWrittenInAnotherFormIsKeptUntilItIsChanged(@TempDir File dir) throws Exception {
        ChainStore store = new ChainStore(dir);
        store.write(List.of(sample()));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(store.file());
        yaml.set("npc_quests.smith_order.rewards.money", "3i");
        yaml.save(store.file());

        ChainDraft chain = store.read().chains().get("smith_order");
        store.write(List.of(chain));
        assertEquals("3i", YamlConfiguration.loadConfiguration(store.file()).getString("npc_quests.smith_order.rewards.money"));

        chain.reward(120);
        store.write(List.of(chain));
        assertEquals(120, YamlConfiguration.loadConfiguration(store.file()).getLong("npc_quests.smith_order.rewards.money"));
    }

    @Test
    void aBrokenChainIsReportedAndTheOthersStillLoad(@TempDir File dir) throws Exception {
        ChainStore store = new ChainStore(dir);
        store.write(List.of(sample()));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(store.file());
        yaml.set("npc_quests.broken.display-name", "x");
        yaml.set("npc_quests.broken.steps", List.of(Map.of("type", "TELEPORT_HOME")));
        yaml.save(store.file());

        ChainStore.Loaded loaded = store.read();
        assertTrue(loaded.chains().containsKey("smith_order"));
        assertFalse(loaded.chains().containsKey("broken"));
        assertEquals(1, loaded.errors().size());
        assertTrue(loaded.errors().get(0).startsWith("broken: "), loaded.errors().get(0));
    }

    @Test
    void aMissingFileIsAnEmptyStore(@TempDir File dir) {
        ChainStore.Loaded loaded = new ChainStore(dir).read();
        assertTrue(loaded.chains().isEmpty());
        assertTrue(loaded.errors().isEmpty());
    }

    @Test
    void definitionsAreKeyedByChainId() {
        assertEquals(Set.of("smith_order"), ChainStore.definitions(List.of(sample())).keySet());
    }

    // ---- validation ------------------------------------------------------------------------------------------

    @Test
    void aCompleteChainHasNoProblems() {
        assertEquals(List.of(), ChainDraftCheck.problems(sample()));
    }

    @Test
    void anEmptyChainListsWhatIsMissing() {
        ChainDraft chain = new ChainDraft("Bad Id!");
        chain.displayName("");
        List<String> problems = ChainDraftCheck.problems(chain);
        assertEquals(4, problems.size(), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("Идентификатор")));
        assertTrue(problems.contains("Не задано название"));
        assertTrue(problems.contains("Не выбран заказчик"));
        assertTrue(problems.contains("Нет ни одного этапа"));
    }

    @Test
    void everyStepTypeNamesTheFieldItLacks() {
        assertEquals(List.of("не выбрано существо"), ChainDraftCheck.stepProblems(new StepDraft(NpcQuestStep.Type.KILL_ENTITY)));
        assertEquals(List.of("не выбран предмет"), ChainDraftCheck.stepProblems(new StepDraft(NpcQuestStep.Type.MINE_MATERIAL)));
        assertEquals(List.of("не выбран предмет"), ChainDraftCheck.stepProblems(new StepDraft(NpcQuestStep.Type.CRAFT_ITEM)));
        assertEquals(List.of("не выбран NPC"), ChainDraftCheck.stepProblems(new StepDraft(NpcQuestStep.Type.VISIT_NPC)));
        assertEquals(List.of("не выбран предмет", "не выбран NPC"), ChainDraftCheck.stepProblems(new StepDraft(NpcQuestStep.Type.DELIVER_ITEM)));
        assertEquals(List.of("не задано место"), ChainDraftCheck.stepProblems(new StepDraft(NpcQuestStep.Type.VISIT_LOCATION)));
        assertEquals(List.of(), ChainDraftCheck.stepProblems(new StepDraft(NpcQuestStep.Type.CATCH_FISH)));
    }

    @Test
    void problemsOfAStepAreNumberedFromOne() {
        ChainDraft chain = sample();
        chain.steps().add(new StepDraft(NpcQuestStep.Type.KILL_ENTITY));
        assertEquals(List.of("Этап 6: не выбрано существо"), ChainDraftCheck.problems(chain));
    }

    // ---- step model ------------------------------------------------------------------------------------------

    @Test
    void changingTheTypeDropsWhatTheNewTypeDoesNotUse() {
        StepDraft step = new StepDraft(NpcQuestStep.Type.DELIVER_ITEM);
        step.material("BREAD");
        step.count(24);
        step.targetNpc("ext-1", "Трактирщик");

        step.type(NpcQuestStep.Type.KILL_ENTITY);
        assertNull(step.material());
        assertNull(step.targetNpcKey());
        assertEquals(24, step.count(), "a counted type keeps its count");

        step.type(NpcQuestStep.Type.VISIT_NPC);
        assertEquals(1, step.count(), "a talk step has nothing to count");
    }

    @Test
    void countNeverDropsBelowOne() {
        StepDraft step = new StepDraft(NpcQuestStep.Type.MINE_MATERIAL);
        step.count(-5);
        assertEquals(1, step.count());
    }

    @Test
    void anEmptyDescriptionIsGeneratedFromTheStep() {
        StepDraft kill = new StepDraft(NpcQuestStep.Type.KILL_ENTITY);
        kill.entityType("skeleton");
        kill.count(10);
        assertEquals("Убейте: SKELETON × 10", kill.shownDescription());
        kill.description("Зачистите кладбище");
        assertEquals("Зачистите кладбище", kill.shownDescription());
    }

    // ---- chain model -----------------------------------------------------------------------------------------

    @Test
    void stepsMoveUpAndTheFirstOneStaysPut() {
        ChainDraft chain = sample();
        StepDraft second = chain.steps().get(1);
        assertTrue(chain.moveUp(1));
        assertSame(second, chain.steps().get(0));
        assertFalse(chain.moveUp(0));
        assertFalse(chain.moveUp(99));
    }

    @Test
    void aChainHoldsAtMostTwentySteps() {
        ChainDraft chain = new ChainDraft("long_chain");
        for (int i = 0; i < ChainDraft.MAX_STEPS; i++) {
            assertTrue(chain.canAddStep());
            chain.steps().add(new StepDraft());
        }
        assertFalse(chain.canAddStep());
    }

    @Test
    void saveAsNewTakesTheNextFreeVersion() {
        assertEquals("smith_v2", ChainDraft.nextVersionId("smith", Set.of("smith")));
        assertEquals("smith_v3", ChainDraft.nextVersionId("smith", Set.of("smith", "smith_v2")));
        assertEquals("smith_v3", ChainDraft.nextVersionId("smith_v2", Set.of("smith", "smith_v2")));
    }

    @Test
    void aCopyIsIndependentOfTheOriginal() {
        ChainDraft original = sample();
        ChainDraft copy = original.copyAs("smith_v2");
        copy.steps().get(0).count(99);
        copy.displayName("другое");
        assertEquals(15, original.steps().get(0).count());
        assertEquals("<gold>Заказ кузнеца</gold>", original.displayName());
        assertEquals("smith_v2", copy.id());
    }

    @Test
    void idsFollowTheFileKeyRule() {
        assertTrue(ChainDraft.ID.matcher("smith_order").matches());
        assertTrue(ChainDraft.ID.matcher("a1b").matches());
        assertFalse(ChainDraft.ID.matcher("ab").matches());
        assertFalse(ChainDraft.ID.matcher("1abc").matches());
        assertFalse(ChainDraft.ID.matcher("Smith").matches());
        assertFalse(ChainDraft.ID.matcher("smith order").matches());
    }
}
