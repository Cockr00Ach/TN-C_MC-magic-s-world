package com.tnc.tnc.dialogue.compat;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * 让"我们的对话剧本演完了"能推动 <b>WhisperingQuests</b> 的任务目标。
 *
 * <h2>为什么需要这个桥</h2>
 * WhisperingQuests 自带的 {@code QuestDialogueHandler} 只在两种情况下推进"对话类"目标：
 * <ol>
 *   <li>被右键的实体是原版 {@code Villager}（且玩家不在潜行），或者</li>
 *   <li>那个实体走 {@code p1nero_dl} 对话库（{@code ServerNpcEntityInteractEvent}）。</li>
 * </ol>
 * TN-C 的剧情 NPC <b>两条都不占</b> ✗ —— 它们是自研实体、走自研的 {@code DialogueNetwork}。
 * 结果就是：玩家跟 Self 说完话、任务却永远不会完成（用户 2026-09-29 实测反馈）。
 *
 * <h2>为什么用反射，而不是 compileOnly 依赖</h2>
 * 用真依赖更"正统"（能被编译器检查），但要把第三方 jar 搬进 {@code libs/}、
 * 改 {@code build.gradle} 的 {@code flatDir}/{@code fg.deobf}、并让每个克隆仓库的人重跑
 * {@code fetch-libs.ps1} —— 为了调 <b>一个方法</b>，代价过大。
 * 反射方案对本仓库有额外好处：<b>不改构建、不加依赖、任何人 clone 下来就能编译</b> ✓。
 *
 * <p>实测前提（2026-09-29 用 javap 核对过 {@code whisperingquests-3.2.jar}）：
 * 这个 mod <b>没被混淆</b> —— {@code QuestManager.completeDialogueObjective(ServerPlayer, ResourceLocation)}
 * 就是真实名字，所以反射签名是稳定的，不需要 SRG 中间名。
 *
 * <h2>为什么要单独一个类</h2>
 * 照 {@code npc/compat/MaidNpcSupport} 与 {@code GeoSelfSupport} 那套写法：
 * <b>整个 mod 里只有这里提到 WhisperingQuests</b>。这样"没装这个 mod"时不会因为
 * 类加载失败而整个 TN-C 起不来 ✗（{@link #available()} 只用 {@code ModList}，不碰对方任何类型 ✓）。
 *
 * <p>已实测反证：目标 class / 方法不存在时，反射返回 {@code null}／抛异常都被吃掉并降级为
 * "不做任何事 + 一条 WARN"，<b>不会崩游戏</b> ✓。
 */
public final class WhisperingQuestBridge {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "whisperingquests";
    private static final String QUEST_MANAGER = "com.lirxowo.whisperingquests.quest.QuestManager";

    /**
     * 反射结果缓存：{@code key = 方法名/参数个数}。
     * 查不到的键也会被记下来（值为 {@code null}）—— 所以用普通 HashMap 加同步，
     * 不能用 ConcurrentHashMap（它不接受 null 值 ✗）。
     */
    private static final java.util.Map<String, Method> cache = new java.util.HashMap<>();

    private WhisperingQuestBridge() {
    }

    /** 装了 GeckoLib 吗？（只看 ModList，不碰对方的类 ✓） */
    public static boolean available() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }

    // ------------------------------------------------------------------
    //  任务状态查询（给"按进度选剧本"用，见 DialoguePicker）
    //
    //  为什么要包一层：{@code TeamQuestState} 是对方的类型，直接出现在 NPC 代码里
    //  就等于把整个 mod 绑死在它上面 ✗。这里一律返回纯 Java 的 boolean，调用方无需知道来源。
    // ------------------------------------------------------------------

    /** 这条任务**正在进行中**吗？（没接取 / 已完成都返回 false） */
    public static boolean isQuestActive(net.minecraft.server.level.ServerPlayer player,
                                        net.minecraft.resources.ResourceLocation questId) {
        return queryState(player, questId, "activeQuests");
    }

    /** 这条任务**已经完成**吗？ */
    public static boolean isQuestCompleted(net.minecraft.server.level.ServerPlayer player,
                                           net.minecraft.resources.ResourceLocation questId) {
        return queryState(player, questId, "completedQuests");
    }

    /**
     * 主动接取一条任务。
     *
     * @return 真的接上了才 true（已经接过 / 已完成 / 门槛不满足都是 false）
     */
    public static boolean tryStartQuest(net.minecraft.server.level.ServerPlayer player,
                                        net.minecraft.resources.ResourceLocation questId) {
        if (questId == null) {
            return false;
        }
        if (!available()) {
            LOGGER.warn("TN-C dialogue: script wants to start quest {} but {} is not installed",
                    questId, MOD_ID);
            return false;
        }
        Method method = method("startQuest",
                net.minecraft.server.level.ServerPlayer.class,
                net.minecraft.resources.ResourceLocation.class);
        if (method == null) {
            return false;
        }
        try {
            Object result = method.invoke(null, player, questId);
            boolean started = result instanceof Boolean b && b;
            if (started) {
                LOGGER.info("TN-C dialogue: accepted quest via dialogue -> {}", questId);
            } else {
                LOGGER.info("TN-C dialogue: quest {} was not accepted (already active/done, "
                        + "or a requirement is unmet)", questId);
            }
            return started;
        } catch (ReflectiveOperationException | RuntimeException error) {
            LOGGER.error("TN-C dialogue: could not accept quest {}", questId, error);
            return false;
        }
    }

    /**
     * 走向 {@code TeamQuestState} 问某个集合里有没有这条任务 id。
     *
     * <p>反射链：{@code QuestManager.getTeamState(player)} -> {@code .xxxQuests()} ->
     * {@code .contains(questId)}。任何一段拿不到都记 ERROR 并返回 false ——
     * 不静默、也不崩（对方升级改了签名时必须能一眼看出来）。
     */
    private static boolean queryState(net.minecraft.server.level.ServerPlayer player,
                                      net.minecraft.resources.ResourceLocation questId,
                                      String accessor) {
        if (questId == null || player == null) {
            return false;
        }
        if (!available()) {
            return false;
        }
        try {
            Method getTeamState = method("getTeamState",
                    net.minecraft.server.level.ServerPlayer.class);
            if (getTeamState == null) {
                return false;
            }
            Object state = getTeamState.invoke(null, player);
            if (state == null) {
                return false;
            }
            Method getter = state.getClass().getMethod(accessor);
            Object set = getter.invoke(state);
            if (!(set instanceof java.util.Set<?> ids)) {
                LOGGER.error("TN-C dialogue: {}.{}() did not return a Set", state.getClass(), accessor);
                return false;
            }
            return ids.contains(questId);
        } catch (ReflectiveOperationException | RuntimeException error) {
            LOGGER.error("TN-C dialogue: could not query {} for {}", accessor, questId, error);
            return false;
        }
    }

    /**
     * 把某条对话剧本标记为"演完了"，并顺手推进它绑定的任务目标。
     *
     * @param player  玩家
     * @param questId 剧本声明的任务 id（{@code @quest tnc:main/self_talk}），可空 = 这条剧本不接任务
     * @return 真的推进了任务才 true；没装 mod / 没绑任务 / 任务不在进行中都是 false（都会记日志）
     */
    public static boolean onDialogueFinished(net.minecraft.server.level.ServerPlayer player,
                                             net.minecraft.resources.ResourceLocation questId) {
        if (questId == null) {
            return false;
        }
        if (!available()) {
            // 不是错误：整合包换掉这个 mod 时任务自然失效，但对话本身仍要能播 ✓
            LOGGER.warn("TN-C dialogue: script wants to finish quest {} but {} is not installed",
                    questId, MOD_ID);
            return false;
        }

        Method method = method("completeDialogueObjective",
                net.minecraft.server.level.ServerPlayer.class,
                net.minecraft.resources.ResourceLocation.class);
        if (method == null) {
            return false;
        }

        try {
            Object result = method.invoke(null, player, questId);
            boolean done = result instanceof Boolean b && b;
            if (done) {
                LOGGER.info("TN-C dialogue: finished quest objective via dialogue -> {}", questId);
            } else {
                // 常见但不是故障：任务还没接取 / 目标已经完成过了
                LOGGER.info("TN-C dialogue: quest {} was not advanced by this dialogue "
                        + "(not active yet, or already done)", questId);
            }
            return done;
        } catch (ReflectiveOperationException | RuntimeException error) {
            // 静默失效是这个项目最大的坑，所以这里一定要留下痕迹 ✓
            LOGGER.error("TN-C dialogue: calling {}.completeDialogueObjective for {} failed",
                    QUEST_MANAGER, questId, error);
            return false;
        }
    }

    /** 反射查一个 {@code QuestManager} 的静态方法并缓存（找不到也缓存 null，只查一次）。 */
    private static synchronized Method method(String name, Class<?>... params) {
        String key = name + "/" + params.length;
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        Method resolved = null;
        try {
            Class<?> manager = Class.forName(QUEST_MANAGER);
            resolved = manager.getMethod(name, params);
            LOGGER.info("TN-C dialogue: {} bridge ready ({})", MOD_ID, name);
        } catch (ClassNotFoundException | NoSuchMethodException error) {
            // 对方升级后改了签名就会走到这里 —— 明确报出来，别静默 ✓
            LOGGER.error("TN-C dialogue: could not resolve {}.{}; dialogue-driven quests "
                    + "will not work", QUEST_MANAGER, name, error);
        }
        cache.put(key, resolved);
        return resolved;
    }
}
