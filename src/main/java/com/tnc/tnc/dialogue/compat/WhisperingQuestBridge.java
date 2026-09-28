package com.tnc.tnc.dialogue.compat;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Optional;

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
    private static final String QUEST_DATA_MANAGER = "com.lirxowo.whisperingquests.data.QuestDataManager";

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
        return engineState(player, questId, "isActiveForPlayer");
    }

    /** 这条任务**已经完成**吗？ */
    public static boolean isQuestCompleted(net.minecraft.server.level.ServerPlayer player,
                                           net.minecraft.resources.ResourceLocation questId) {
        return engineState(player, questId, "isCompletedForPlayer");
    }

    /**
     * 问引擎"这条任务对该玩家算不算 active / completed"。
     *
     * <h2>为什么要绕这么一圈（两次踩坑记录）</h2>
     * <b>第一版</b>用 {@code getTeamState(player).completedQuests()} —— 错的 ✗：
     * 任务分**团队**的和**按人**的，引擎读门槛前会先过 {@code stateFor(questDef,
     * teamState, personalState)} 分流。{@code s1_leave} 是按人存的
     * （存档里 {@code PlayerTrackedQuests} 就是它），团队集合里没有它 ⇒
     * 一律判成"未完成" ⇒ 选段永远挑不到后面那段。
     *
     * <p><b>第二版</b>想直接调 {@code QuestManager.isCompletedForPlayer(player, id)} ——
     * 也是错的 ✗：那个方法是 <b>private</b>（只在引擎同类内部被 lambda 调用），
     * {@code Class.getMethod} 只找 public，运行时报
     * {@code NoSuchMethodException: ...isCompletedForPlayer(...)}，
     * 表现依旧是"永远挑不到后面那段"。
     *
     * <p><b>现在这版</b>走引擎**公开**的入口，两步：
     * <ol>
     *   <li>{@code QuestDataManager.INSTANCE.getQuest(id)} → 拿到 {@code QuestDefinition}
     *       （公开）✓；</li>
     *   <li>{@code QuestManager.getQuestState(player, definition)} → 拿到**已经分流好**的
     *       {@code TeamQuestState}（公开）✓ —— 它内部就是
     *       {@code stateFor(questDef, teamState, personalState)}。</li>
     * </ol>
     * 再从这个 state 上读 {@code activeQuests()} / {@code completedQuests()} 即可，
     * 团队/按人的区别由引擎处理，我们不用懂 ✓。
     */
    private static boolean engineState(net.minecraft.server.level.ServerPlayer player,
                                       net.minecraft.resources.ResourceLocation questId,
                                       String accessor) {
        if (questId == null || player == null) {
            return false;
        }
        if (!available()) {
            return false;
        }
        try {
            // 1) 查任务定义（公开：QuestDataManager.INSTANCE.getQuest(id)）
            //    ⚠️ getQuest 在 QuestDataManager 上，**不在 QuestManager 上** ✗ ——
            //    早先这里用 method() 往 QuestManager 上找，运行时报
            //    NoSuchMethodException: QuestManager.getQuest(...)，gate 一律判 false。
            Method managerGetter = methodOn(QUEST_DATA_MANAGER, "getQuest",
                    new Class<?>[]{net.minecraft.resources.ResourceLocation.class});
            Object dataManager = dataManagerInstance();
            if (managerGetter == null || dataManager == null) {
                return false;
            }
            Object optional = managerGetter.invoke(dataManager, questId);
            if (!(optional instanceof Optional<?> opt) || opt.isEmpty()) {
                return false;               // 定义不存在 => 谈不上完成
            }
            Object definition = opt.get();

            // 2) 走公开的 getQuestState(player, definition) —— 它内部会做按人/团队分流
            Method getQuestState = method("getQuestState",
                    net.minecraft.server.level.ServerPlayer.class,
                    definition.getClass());
            if (getQuestState == null) {
                return false;
            }
            Object state = getQuestState.invoke(null, player, definition);
            if (state == null) {
                return false;
            }

            // 3) 从分流后的 state 读集合
            Object set = state.getClass().getMethod(accessor).invoke(state);
            if (!(set instanceof java.util.Set<?> ids)) {
                LOGGER.error("TN-C dialogue: {}.{}() did not return a Set",
                        state.getClass().getName(), accessor);
                return false;
            }
            return ids.contains(questId);
        } catch (ReflectiveOperationException | RuntimeException error) {
            LOGGER.error("TN-C dialogue: could not read {} for {}", accessor, questId, error);
            return false;
        }
    }

    /** {@code QuestDataManager.INSTANCE}（公开静态字段）。 */
    private static Object dataManagerInstance() {
        if (!available()) {
            return null;
        }
        try {
            Class<?> cls = Class.forName(QUEST_DATA_MANAGER);
            return cls.getField("INSTANCE").get(null);
        } catch (ReflectiveOperationException | RuntimeException error) {
            LOGGER.error("TN-C dialogue: could not read {}.INSTANCE", QUEST_DATA_MANAGER, error);
            return null;
        }
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

    /**
     * 反射查 {@code QuestManager} 上的公开静态方法并缓存（找不到也缓存 null，只查一次）。
     *
     * <p>⚠️ 只有真正在 {@code QuestManager} 上的方法才能用这个 —— 别的方法在别的类上
     * （{@code getQuest} 在 {@code QuestDataManager} 上），用它会得到
     * {@code NoSuchMethodException}。那种情况请直接用
     * {@link #methodOn(String, String, Class[])}。
     */
    private static synchronized Method method(String name, Class<?>... params) {
        return methodOn(QUEST_MANAGER, name, params);
    }

    /**
     * 同上，但<b>显式指定目标类</b>。
     *
     * @param className 目标类的全名（如 {@code com.lirxowo.whisperingquests.data.QuestDataManager}）
     */
    private static synchronized Method methodOn(String className, String name, Class<?>[] params) {
        String key = className + "#" + name + "/" + params.length;
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        Method resolved = null;
        try {
            Class<?> owner = Class.forName(className);
            resolved = owner.getMethod(name, params);
            LOGGER.info("TN-C dialogue: {} bridge ready ({}#{})", MOD_ID, owner.getSimpleName(), name);
        } catch (ClassNotFoundException | NoSuchMethodException error) {
            // 对方升级后改了签名就会走到这里 —— 明确报出来，别静默 ✓
            LOGGER.error("TN-C dialogue: could not resolve {}#{}; dialogue-driven quests "
                    + "will not work", className, name, error);
        }
        cache.put(key, resolved);
        return resolved;
    }
}
