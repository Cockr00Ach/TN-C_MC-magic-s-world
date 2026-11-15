package com.tnc.tnc.warrior;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.dialogue.compat.WhisperingQuestBridge;
import com.tnc.tnc.journey.JourneyData;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Opt-in integration tests: use the real quest engine, resource loader, capability and claim path. */
@GameTestHolder("tncwarriorpractical")
@PrefixGameTestTemplate(false)
public final class WarriorTutorialQuestGameTests {
    private static final ResourceLocation QUEST = WarriorTutorialQuests.QUEST_ID;
    private static final String[] OBJECTIVES = {"meet_mentor", "watch_demo", "use_terminal", "basic_attack",
            "deflect", "break_deflect", "finish_summary"};
    private static final String MANAGER = "com.lirxowo.whisperingquests.quest.QuestManager";

    @GameTest(template = "building_test_empty", timeoutTicks = 40)
    public static void realJBookLoadsAndAcceptsInvitation(GameTestHelper h) throws ReflectiveOperationException {
        if (!enabled(h)) return;
        Object definition = definition(h);
        h.assertTrue(Boolean.FALSE.equals(definition.getClass().getMethod("syncWithTeam").invoke(definition)),
                "Tutorial must use personal quest progress");
        var objectives = (List<?>) definition.getClass().getMethod("objectives").invoke(definition);
        h.assertTrue(objectives.size() == OBJECTIVES.length, "Datapack must contain all seven tutorial objectives");
        for (int i = 0; i < OBJECTIVES.length; i++) {
            Object objective = objectives.get(i);
            h.assertTrue(OBJECTIVES[i].equals(objective.getClass().getMethod("id").invoke(objective)),
                    "Objective order must match the persisted lesson stage: " + i);
            h.assertTrue("tnc:warrior_tutorial".equals(objective.getClass().getMethod("type").invoke(objective)),
                    "The real loader must retain the registered authoritative objective handler");
        }
        var player = player(h);
        try {
            saveStage(player, 0, false);
            WarriorTutorialQuests.sync(player, 0);
            h.assertTrue(WhisperingQuestBridge.isQuestActive(player, QUEST), "Stage zero auto-accepts the normal J quest");
            assertProgress(h, player, definition, 0);
            h.assertTrue(QUEST.equals(trackedQuest(player)),
                    "An empty tracked quest selects the invitation for the J book");
            WarriorTutorialQuests.sync(player, 0);
            h.assertTrue(!WhisperingQuestBridge.isQuestCompleted(player, QUEST), "Replayed invitation cannot finish the quest");
        } finally {
            player.discard();
        }
        h.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 40)
    public static void realInvitationPreservesExistingMainlineTracking(GameTestHelper h) throws ReflectiveOperationException {
        if (!enabled(h)) return;
        definition(h);
        Class<?> type = Class.forName("com.lirxowo.whisperingquests.data.QuestDataManager");
        Object data = type.getField("INSTANCE").get(null);
        Field quests = field(type, "quests");
        @SuppressWarnings("unchecked") var original = (Map<ResourceLocation, Object>) quests.get(data);
        ResourceLocation main = ResourceLocation.fromNamespaceAndPath("tnc", "test/warrior_existing_mainline");
        // A disposable main quest uses the real parser and engine, without touching player saves or pack data.
        JsonObject json = JsonParser.parseString("""
                {"id":"tnc:test/warrior_existing_mainline","enabled":true,"category":"main",
                 "sync_with_team":false,"title":"Existing tracked main quest","icon":"minecraft:book",
                 "objectives":[{"id":"pending","type":"tnc:warrior_tutorial","amount":1}],"rewards":[]}
                """).getAsJsonObject();
        var parse = type.getDeclaredMethod("parseQuest", JsonObject.class);
        parse.setAccessible(true);
        var definitions = new HashMap<>(original);
        definitions.put(main, parse.invoke(null, json));
        var player = player(h);
        try {
            quests.set(data, Map.copyOf(definitions));
            h.assertTrue(Boolean.TRUE.equals(manager("startQuest",
                    new Class<?>[]{ServerPlayer.class, ResourceLocation.class}, player, main)),
                    "The main quest fixture must start through the real engine");
            manager("setTracked", new Class<?>[]{ServerPlayer.class, ResourceLocation.class}, player, main);
            h.assertTrue(main.equals(trackedQuest(player)), "The main quest must already be tracked in actual engine storage");
            saveStage(player, 0, false);
            WarriorTutorialQuests.sync(player, 0);
            h.assertTrue(WhisperingQuestBridge.isQuestActive(player, QUEST), "The tutorial still enters the J quest list");
            h.assertTrue(main.equals(trackedQuest(player)), "Accepting the tutorial preserves existing mainline tracking");
            saveStage(player, 3, false);
            WarriorTutorialQuests.sync(player, 3);
            h.assertTrue(main.equals(trackedQuest(player)), "Tutorial progress also preserves existing mainline tracking");
        } finally {
            quests.set(data, original);
            player.discard();
        }
        h.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 40)
    public static void realQuestReplayRequiresPersistedCompletion(GameTestHelper h) throws ReflectiveOperationException {
        if (!enabled(h)) return;
        Object definition = definition(h);
        var player = player(h);
        var other = player(h);
        try {
            saveStage(player, 0, false);
            saveStage(other, 0, false);
            WarriorTutorialQuests.sync(other, 0);
            WarriorTutorialQuests.sync(player, 7);
            assertProgress(h, player, definition, 0);
            for (int stage = 1; stage <= 6; stage++) {
                saveStage(player, stage, false);
                WarriorTutorialQuests.sync(player, 7);
                assertProgress(h, player, definition, stage);
            }
            WarriorTutorialQuests.sync(player, 0);
            assertProgress(h, player, definition, 6);
            saveStage(player, 7, false);
            WarriorTutorialQuests.sync(player, 7);
            assertProgress(h, player, definition, 6);
            h.assertTrue(!Boolean.TRUE.equals(manager("claimReward",
                    new Class<?>[]{ServerPlayer.class, ResourceLocation.class}, player, QUEST)),
                    "A forged reward claim before Completed cannot finish the summary");
            h.assertTrue(!WhisperingQuestBridge.isQuestCompleted(player, QUEST),
                    "Stage seven alone cannot finish the quest without saved Completed");
            assertProgress(h, other, definition, 0);
            saveStage(player, 7, true);
            WarriorTutorialQuests.sync(player, 7);
            h.assertTrue(WhisperingQuestBridge.isQuestCompleted(player, QUEST),
                    "Saved Completed advances the last objective and uses the real engine claim path");
            h.assertTrue(!WhisperingQuestBridge.isQuestActive(player, QUEST), "A finished lesson leaves the active J list");
            WarriorTutorialQuests.sync(player, 7);
            h.assertTrue(WhisperingQuestBridge.isQuestCompleted(player, QUEST), "Completion replay is idempotent");
            assertProgress(h, other, definition, 0);
        } finally {
            player.discard();
            other.discard();
        }
        h.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 40)
    public static void validSessionClientDoneCannotAdvanceRealQuest(GameTestHelper h) throws ReflectiveOperationException {
        if (!enabled(h)) return;
        Object definition = definition(h);
        var player = player(h);
        var tutor = WarriorEntities.INSTRUCTOR.get().create(h.getLevel());
        var dummy = WarriorEntities.TEST_DUMMY.get().create(h.getLevel());
        h.assertTrue(tutor != null && dummy != null, "Real lesson actors must be registered");
        tutor.setPos(player.position());
        dummy.setPos(player.position().add(0, 0, 3));
        // Fixture only: install an authentic live session, then exercise the public C2S action handler.
        Class<?> sessionType = Class.forName(WarriorTutorial.class.getName() + "$Session");
        var constructor = sessionType.getDeclaredConstructor(InstructorEntity.class, BlockPos.class, long.class);
        constructor.setAccessible(true);
        Object session = constructor.newInstance(tutor, player.blockPosition(), player.level().getGameTime());
        field(sessionType, "dummy").set(session, dummy);
        field(sessionType, "home").set(session, player.position());
        field(sessionType, "learner").set(session, player.position());
        @SuppressWarnings("unchecked")
        var sessions = (Map<UUID, Object>) field(WarriorTutorial.class, "SESSIONS").get(null);
        sessions.put(player.getUUID(), session);
        long token = field(sessionType, "token").getLong(session);
        try {
            h.assertTrue(WarriorTutorial.active(player), "The forged action test must use a live, correctly tokened session");
            for (int stage = 3; stage <= 5; stage++) {
                saveStage(player, stage, false);
                field(sessionType, "stage").setInt(session, stage);
                WarriorTutorialQuests.sync(player, stage);
                for (String action : new String[]{"done", "DONE", "complete", "skip"}) {
                    field(sessionType, "lastAction").setLong(session, Long.MIN_VALUE);
                    WarriorTutorial.action(player, token, action);
                    h.assertTrue(WarriorTutorial.data(player).getInt("Stage") == stage,
                            "Client action cannot certify a practical lesson: " + action);
                    h.assertTrue(!WarriorTutorial.data(player).getBoolean("Completed"),
                            "Client action cannot set authoritative Completed");
                    WarriorTutorialQuests.sync(player, 7);
                    assertProgress(h, player, definition, stage);
                }
            }
        } finally {
            sessions.remove(player.getUUID(), session);
            tutor.discard();
            dummy.discard();
            player.discard();
        }
        h.succeed();
    }

    private static boolean enabled(GameTestHelper h) {
        if (!Boolean.getBoolean("tnc.warriorTutorialRuntimeTests")) {
            h.succeed();
            return false;
        }
        h.assertTrue(ModList.get().isLoaded("whisperingquests"), "Opt-in integration requires the actual WhisperingQuests runtime");
        return true;
    }

    private static ServerPlayer player(GameTestHelper h) throws ReflectiveOperationException {
        var player = AdventureGameTests.player(h);
        player.setNoGravity(true);
        player.setInvulnerable(true);
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(new BlockPos(4, 3, 4))));
        Object optional = manager("getPlayerState", new Class<?>[]{Player.class}, player);
        h.assertTrue(optional instanceof Optional<?> state && state.isPresent(),
                "The actual mod must attach its normal player quest capability");
        return player;
    }

    private static void saveStage(ServerPlayer player, int stage, boolean completed) {
        var lesson = WarriorTutorial.data(player);
        lesson.putInt("Stage", stage);
        lesson.putBoolean("Completed", completed);
        JourneyData.get(player.server).setDirty();
    }

    private static Object definition(GameTestHelper h) throws ReflectiveOperationException {
        Class<?> type = Class.forName("com.lirxowo.whisperingquests.data.QuestDataManager");
        Object data = type.getField("INSTANCE").get(null);
        Object result = type.getMethod("getQuest", ResourceLocation.class).invoke(data, QUEST);
        h.assertTrue(result instanceof Optional<?> quest && quest.isPresent(),
                "Real resource reload must parse the TN-C warrior tutorial quest from its JAR resources");
        return ((Optional<?>) result).orElseThrow();
    }

    private static void assertProgress(GameTestHelper h, ServerPlayer player, Object definition, int completed)
            throws ReflectiveOperationException {
        Object state = manager("getQuestState", new Class<?>[]{ServerPlayer.class, definition.getClass()}, player, definition);
        for (int i = 0; i < OBJECTIVES.length; i++) {
            int progress = (Integer) state.getClass().getMethod("getProgress", ResourceLocation.class, String.class)
                    .invoke(state, QUEST, OBJECTIVES[i]);
            h.assertTrue(progress == (i < completed ? 1 : 0),
                    "Engine progress must follow saved server stages: " + OBJECTIVES[i] + " at " + completed);
        }
    }

    private static Object manager(String name, Class<?>[] types, Object... arguments) throws ReflectiveOperationException {
        return Class.forName(MANAGER).getMethod(name, types).invoke(null, arguments);
    }

    private static Object trackedQuest(ServerPlayer player) throws ReflectiveOperationException {
        Class<?> type = Class.forName("com.lirxowo.whisperingquests.quest.QuestSavedData");
        Object saved = type.getMethod("get", ServerLevel.class).invoke(null, player.serverLevel());
        return type.getMethod("getPlayerTracked", UUID.class).invoke(saved, player.getUUID());
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private WarriorTutorialQuestGameTests() {}
}
