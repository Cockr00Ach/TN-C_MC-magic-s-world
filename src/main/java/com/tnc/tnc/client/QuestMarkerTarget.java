package com.tnc.tnc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * 「跟着任务找目标人物」—— 纯逻辑部分（不碰渲染，可单测）。
 *
 * <h2>为什么不用现成的任务指路功能</h2>
 * {@code whisperingquests} 自带路点：它把目标点丢给 Xaero 小地图/世界地图
 * （{@code compat/XaeroMinimapIntegration}）。但**它只认 {@code location} 类型的
 * 任务目标，而且坐标是写在任务 JSON 里的固定值** ✗。
 *
 * <p>我们的主线恰恰**故意不用固定坐标** —— 天空岛与地面传送阵每个存档位置都不同，
 * 写死的坐标只在量它的那个存档里对得上（这条已经踩过一次，见
 * {@code docs/任务系统_交接.md} §十 1c）。而我们的目标是**会走动的 NPC**，
 * 坐标本来就不该是常量 ✓。
 *
 * <p>所以这里走另一条路：<b>任务里只记"要找哪一类 NPC"
 * （{@code npc.entity_type}），目标位置在客户端每帧从世界里现查</b> ✓ ——
 * 换来的是"换存档、NPC 被挪动、玩家把它推走"都不会失准 ✓。
 *
 * <h2>找人的顺序</h2>
 * <ol>
 *   <li>取当前**正在追踪**的任务（{@code ClientQuestData.trackedEntry()}）；
 *       没追踪就什么都不画 —— 这正是"我开始追踪那个任务"才出现标记 ✓；</li>
 *   <li>任务里写了 {@code npc.entity_type} ⇒ 在客户端已加载的实体里找**最近的一个**；</li>
 *   <li>任务目标是 {@code location} 且带固定坐标 ⇒ 退回用那个坐标（引擎自带路点也管这条）✓。</li>
 * </ol>
 *
 * <p>⚠️ 客户端只找**已加载**的实体：目标远在天边时找不到，这是对的 ——
 * 那时应该显示"方向 + 距离"而不是假装知道它在哪 ✗。
 */
public final class QuestMarkerTarget {

    private QuestMarkerTarget() {
    }

    /** 一个要标记的目标。 */
    public record Marker(Vec3 position, String label, double distance, boolean sameDimension) {
    }

    /**
     * 解析当前该标记的目标；没有就不画。
     *
     * @param mc 客户端实例
     * @return 目标位置 + 显示名；没有追踪任务、或找不到目标时为空
     */
    public static Optional<Marker> resolve(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return Optional.empty();
        }

        Optional<Entry> tracked = trackedEntry();
        if (tracked.isEmpty()) {
            return Optional.empty();
        }
        Entry entry = tracked.get();

        // 1) 优先按"要找哪一类 NPC"现查最近的一个
        ResourceLocation npcType = entry.npcEntityTypeId();
        if (npcType != null) {
            Entity nearest = nearestOfType(mc, npcType, player);
            if (nearest != null) {
                String label = entry.npcDisplayName() != null && !entry.npcDisplayName().isBlank()
                        ? entry.npcDisplayName()
                        : entry.title();
                return Optional.of(new Marker(nearest.position(), label,
                        player.position().distanceTo(nearest.position()), true));
            }
        }

        // 2) 退回固定坐标（location 目标）
        Vec3 fixed = entry.objectivePosition();
        if (fixed != null) {
            return Optional.of(new Marker(fixed, entry.title(),
                    player.position().distanceTo(fixed), true));
        }

        return Optional.empty();
    }

    /**
     * 在**客户端已加载**的实体里找这一类型中离玩家最近的一个。
     *
     * <p>用遍历而不是 {@code level.getEntities} 的 AABB 版本：超视距时本来也找不到，
     * 而这样写不需要猜一个搜索半径 ✓。
     */
    private static Entity nearestOfType(Minecraft mc, ResourceLocation typeId, LocalPlayer player) {
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == player || !entity.isAlive()) {
                continue;
            }
            ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                    .getKey(entity.getType());
            if (!typeId.equals(id)) {
                continue;
            }
            double distance = player.distanceToSqr(entity);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    //  对 whisperingquests 客户端的**只读**访问
    //
    //  全程反射：那个 mod 是第三方、我们不加编译依赖（与 WhisperingQuestBridge 同一套理由）。
    //  拿不到就当"没有追踪任务"处理 —— 少画一个标记，不是故障 ✓。
    // ------------------------------------------------------------------

    /** 追踪中的任务：id / 标题 / 要找的 NPC 类型 / 固定坐标。 */
    public record Entry(String id, String title, String npcDisplayName,
                        ResourceLocation npcEntityTypeId, Vec3 objectivePosition) {
    }

    private static final String CLIENT_DATA = "com.lirxowo.whisperingquests.client.ClientQuestData";

    private static volatile boolean lookedUp;
    private static java.lang.reflect.Method trackedEntry;
    private static java.lang.reflect.Method entryTitle;
    private static java.lang.reflect.Method entryNpcType;
    private static java.lang.reflect.Method entryNpcName;
    private static java.lang.reflect.Method entryObjectives;
    private static java.lang.reflect.Method objectivePosition;

    private static Optional<Entry> trackedEntry() {
        if (!lookedUp) {
            resolveApi();
        }
        if (trackedEntry == null) {
            return Optional.empty();
        }
        try {
            Object optional = trackedEntry.invoke(null);
            if (!(optional instanceof Optional<?> opt) || opt.isEmpty()) {
                return Optional.empty();
            }
            Object e = opt.get();
            String id = String.valueOf(e.getClass().getMethod("id").invoke(e));
            String title = asString(invoke(e, entryTitle));
            String npcName = asString(invoke(e, entryNpcName));
            ResourceLocation npcType = (ResourceLocation) invoke(e, entryNpcType);
            Vec3 pos = firstObjectivePosition(e);
            return Optional.of(new Entry(id, title, npcName, npcType, pos));
        } catch (ReflectiveOperationException | RuntimeException error) {
            return Optional.empty();     // 对方改了签名 ⇒ 少画标记，不报错刷屏
        }
    }

    /** 取第一个带坐标的目标（location 目标走这条）。 */
    private static Vec3 firstObjectivePosition(Object entry) throws ReflectiveOperationException {
        if (entryObjectives == null || objectivePosition == null) {
            return null;
        }
        Object objectives = entryObjectives.invoke(entry);
        if (!(objectives instanceof java.util.List<?> list)) {
            return null;
        }
        for (Object objective : list) {
            Object pos = objectivePosition.invoke(objective);
            if (pos instanceof net.minecraft.core.BlockPos bp) {
                return new Vec3(bp.getX() + 0.5D, bp.getY(), bp.getZ() + 0.5D);
            }
        }
        return null;
    }

    private static Object invoke(Object target, java.lang.reflect.Method m)
            throws ReflectiveOperationException {
        return m == null ? null : m.invoke(target);
    }

    private static String asString(Object o) {
        return o instanceof String s ? s : null;
    }

    private static synchronized void resolveApi() {
        if (lookedUp) {
            return;
        }
        try {
            Class<?> data = Class.forName(CLIENT_DATA);
            trackedEntry = data.getMethod("trackedEntry");

            Class<?> entryClass = Class.forName(CLIENT_DATA + "$Entry");
            entryTitle = entryClass.getMethod("title");
            entryNpcType = entryClass.getMethod("npcEntityTypeId");
            entryNpcName = entryClass.getMethod("npcDisplayName");
            entryObjectives = entryClass.getMethod("objectives");

            Class<?> objectiveClass = Class.forName(CLIENT_DATA + "$ObjectiveEntry");
            objectivePosition = objectiveClass.getMethod("position");

            com.mojang.logging.LogUtils.getLogger()
                    .info("TN-C quest marker: whisperingquests client API resolved");
        } catch (ReflectiveOperationException | RuntimeException error) {
            // 不带 @Redirect 之类的兜底：拿不到就不画标记，记一条 WARN 便于排查 ✓
            com.mojang.logging.LogUtils.getLogger().warn(
                    "TN-C quest marker: whisperingquests client API unavailable ({}); "
                            + "no quest markers will be drawn", error.toString());
            trackedEntry = null;
        }
        lookedUp = true;
    }
}
