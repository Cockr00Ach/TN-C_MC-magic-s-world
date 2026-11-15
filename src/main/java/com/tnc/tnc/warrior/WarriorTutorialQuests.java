package com.tnc.tnc.warrior;

import com.mojang.logging.LogUtils;
import com.tnc.tnc.dialogue.compat.WhisperingQuestBridge;
import com.tnc.tnc.journey.JourneyData;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/** Mirrors the authoritative lesson stages into the normal J quest book. */
@Mod.EventBusSubscriber(modid = "tnc", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WarriorTutorialQuests {
    public static final ResourceLocation QUEST_ID = ResourceLocation.fromNamespaceAndPath("tnc", "side/warrior_initiation");
    public static final String MENTOR_NAME = "莉娅 · 战士入门";
    private static final String OBJECTIVE_TYPE = "tnc:warrior_tutorial";
    private static final String MOD_ID = "whisperingquests";
    private static final String[] OBJECTIVES = {
            "meet_mentor", "watch_demo", "use_terminal", "basic_attack",
            "deflect", "break_deflect", "finish_summary"
    };
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Api api;
    private static boolean lookupAttempted;
    private static boolean reportedFailure;

    private WarriorTutorialQuests() {}

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        // The objective handler must exist before server datapacks are parsed.
        event.enqueueWork(() -> {
            resolveApi();
        });
    }

    /**
     * Called after persisting a lesson stage, and periodically after login for safe replay.
     * Stage 0 is an invitation, stages 1-6 finish consecutive lesson objectives, and the
     * saved Completed flag is required before finishing the summary objective.
     */
    public static void sync(ServerPlayer player, int step) {
        if (player == null || player.server == null || !player.isAlive()) return;
        var saved = JourneyData.get(player.server).player(player.getUUID());
        if (!saved.contains("WarriorTutorial", Tag.TAG_COMPOUND)) return;
        var lesson = saved.getCompound("WarriorTutorial");
        int completedSteps = lesson.getBoolean("Completed") ? OBJECTIVES.length
                : Math.max(0, Math.min(OBJECTIVES.length - 1, Math.min(step, lesson.getInt("Stage"))));
        Api bridge = resolveApi();
        if (bridge == null || WhisperingQuestBridge.isQuestCompleted(player, QUEST_ID)) return;
        try {
            boolean active = WhisperingQuestBridge.isQuestActive(player, QUEST_ID);
            if (!active) {
                if (!Boolean.TRUE.equals(bridge.start.invoke(null, player, QUEST_ID))) return;
                trackIfEmpty(player, bridge);
            }
            for (int index = 0; index < completedSteps; index++) {
                // The engine clamps and saves progress, chooses personal/team state, and synchronizes changes.
                bridge.setProgress.invoke(null, player, QUEST_ID, OBJECTIVES[index], 1);
            }
            if (completedSteps == OBJECTIVES.length) {
                bridge.claim.invoke(null, player, QUEST_ID);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            reportFailure(error);
        }
    }

    private static void trackIfEmpty(ServerPlayer player, Api bridge) throws ReflectiveOperationException {
        // WQ 3.2 stores current tracking in global saved data; the capability field is legacy state.
        Object saved = bridge.savedData.invoke(null, player.serverLevel());
        Object tracked = bridge.trackedQuest.invoke(saved, player.getUUID());
        if (tracked == null || tracked.equals(bridge.emptyId)) {
            bridge.track.invoke(null, player, QUEST_ID);
        }
    }

    private static synchronized Api resolveApi() {
        if (api != null || lookupAttempted) return api;
        lookupAttempted = true;
        if (ModList.get() == null || !ModList.get().isLoaded(MOD_ID)) return null;
        try {
            Class<?> manager = Class.forName("com.lirxowo.whisperingquests.quest.QuestManager");
            Class<?> facade = Class.forName("com.lirxowo.whisperingquests.api.WhisperingQuestsApi");
            Class<?> handlerType = Class.forName("com.lirxowo.whisperingquests.quest.objective.ObjectiveHandler");
            Class<?> savedData = Class.forName("com.lirxowo.whisperingquests.quest.QuestSavedData");
            Object handler = Proxy.newProxyInstance(handlerType.getClassLoader(), new Class<?>[]{handlerType},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "toString" -> "TN-C authoritative warrior tutorial objectives";
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> args != null && args.length == 1 && proxy == args[0];
                                default -> null;
                            };
                        }
                        // All ObjectiveHandler callbacks are boolean. Actual lesson actions alone advance progress.
                        return false;
                    });
            facade.getMethod("registerObjectiveHandler", String.class, handlerType).invoke(null, OBJECTIVE_TYPE, handler);
            api = new Api(
                    manager.getMethod("startQuest", ServerPlayer.class, ResourceLocation.class),
                    manager.getMethod("setObjectiveProgress", ServerPlayer.class, ResourceLocation.class, String.class, int.class),
                    manager.getMethod("claimReward", ServerPlayer.class, ResourceLocation.class),
                    savedData.getMethod("get", ServerLevel.class),
                    savedData.getMethod("getPlayerTracked", UUID.class),
                    manager.getMethod("setTracked", ServerPlayer.class, ResourceLocation.class),
                    Class.forName("com.lirxowo.whisperingquests.Whisperingquests").getField("EMPTY_ID").get(null));
        } catch (ReflectiveOperationException | RuntimeException error) {
            reportFailure(error);
        }
        return api;
    }

    private static void reportFailure(Exception error) {
        if (reportedFailure) return;
        reportedFailure = true;
        LOGGER.warn("TN-C warrior tutorial could not synchronize the J quest book", error);
    }

    private record Api(Method start, Method setProgress, Method claim, Method savedData,
                       Method trackedQuest, Method track, Object emptyId) {}
}
