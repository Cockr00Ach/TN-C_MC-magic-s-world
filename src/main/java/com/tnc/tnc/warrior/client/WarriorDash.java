package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 冲刺残影（速度线）：Shift 步法把人瞬间甩出去的那一下，在身后留下三道淡光带。
 *
 * <p>为什么不画"人物分身"：复制玩家模型要么重入 {@code PlayerRenderer}（会把所有渲染层、
 * 包括本 mod 的刀光层再跑一遍，很容易递归或糊成一坨），要么改模型矩阵再画一次（拿不到
 * 半透明材质）。速度线是动作片里同样的信息（"这一下很快"），但只是几片加色四边形，
 * 便宜、稳定、任何角度看都成立。
 *
 * <p>触发条件是**水平速度**：普通疾跑约 0.28 格/tick，Shift 短按冲刺会瞬间到 ~1.9，阈值取
 * {@link #TRIGGER_SPEED} 就能把"冲刺"和"跑得快"分开，不会平常走路满屏拉线。
 */
public final class WarriorDash {

    /** 一帧的位置 + 时间戳。 */
    private record Point(Vec3 pos, long at) {
    }

    private static final Map<Integer, Deque<Point>> TRAILS = new HashMap<>();

    /** 每 tick 水平速度超过这个值（格）才算冲刺：疾跑 0.28、冲刺 1.9，中间留足余量。 */
    public static final double TRIGGER_SPEED = 0.55;
    /** 残影寿命（毫秒）。 */
    private static final long LIFE_MS = 240L;
    private static final int MAX_SAMPLES = 14;
    /** 三道速度线的高度（相对脚底）。 */
    private static final double[] HEIGHTS = {0.35, 0.90, 1.45};
    /** 每条线的半宽（格）。 */
    private static final double WIDTH = 0.045;
    /** 峰值透明度。 */
    private static final float ALPHA = 0.30f;

    private WarriorDash() {
    }

    /** 是否算"在冲刺"：纯函数，方便单测。 */
    public static boolean triggered(double speed) {
        return speed >= TRIGGER_SPEED;
    }

    /** 每帧记一次位置（由 {@link WarriorTrailLayer} 调用，和刀光共用一次渲染时机）。 */
    static void sample(AbstractClientPlayer player) {
        int id = player.getId();
        Vec3 movement = player.getDeltaMovement();
        double speed = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        if (!triggered(speed)) {
            // 不采样，但**不清空**：速度掉下来之后旧的残影要自然淡完，不能一刀剪断
            return;
        }
        long now = System.currentTimeMillis();
        Deque<Point> points = TRAILS.computeIfAbsent(id, key -> new ArrayDeque<>());
        Vec3 at = player.position();
        Point last = points.peekLast();
        if (last != null && last.pos().distanceToSqr(at) < 0.0025) {
            return;
        }
        points.addLast(new Point(at, now));
        while (points.size() > MAX_SAMPLES) {
            points.removeFirst();
        }
        while (!points.isEmpty() && now - points.peekFirst().at() > LIFE_MS) {
            points.removeFirst();
        }
    }

    static void render(AbstractClientPlayer player, MultiBufferSource buffer) {
        Deque<Point> points = TRAILS.get(player.getId());
        if (points == null || points.size() < 2) {
            return;
        }
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        long now = System.currentTimeMillis();
        VertexConsumer vc = buffer.getBuffer(RenderType.lightning());
        Point[] list = points.toArray(new Point[0]);
        for (double height : HEIGHTS) {
            // 中间那道最亮，上下两道淡一点，看起来像"人整个拖过去"
            float weight = height > 0.9 ? 0.55f : 1.0f;
            for (int i = 1; i < list.length; i++) {
                float age = Mth.clamp((now - list[i].at()) / (float) LIFE_MS, 0f, 1f);
                float fade = 1f - age;
                if (fade <= 0.03f) {
                    continue;
                }
                Vec3 a = list[i - 1].pos().add(0, height, 0);
                Vec3 b = list[i].pos().add(0, height, 0);
                quad(vc, a, b, cam, fade * weight, width(fade));
            }
        }
    }

    /** 越老越细，尾巴尖是收掉的。 */
    private static double width(float fade) {
        return WIDTH * (0.35 + 0.65 * fade);
    }

    /**
     * 一片面向镜头的四边形：横向取「移动方向 × 视线」，所以从任何角度看都是一条有宽度的线，
     * 不会因为侧看变成一条缝。
     */
    private static void quad(VertexConsumer vc, Vec3 from, Vec3 to, Vec3 cam, float fade, double halfWidth) {
        Vec3 a = from.subtract(cam);
        Vec3 b = to.subtract(cam);
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1.0E-8) {
            return;
        }
        dir = dir.normalize();
        Vec3 view = a.add(b).scale(0.5);
        if (view.lengthSqr() < 1.0E-6) {
            return;
        }
        Vec3 side = dir.cross(view.normalize());
        if (side.lengthSqr() < 1.0E-8) {
            return;
        }
        side = side.normalize().scale(halfWidth);
        float alpha = Mth.clamp(ALPHA * fade, 0f, 1f);
        float tail = alpha * 0.05f;
        // 冷白偏青，和刀光的默认色是一套
        put(vc, a.subtract(side), 0.50f, 0.74f, 1.0f, tail);
        put(vc, a.add(side), 0.50f, 0.74f, 1.0f, tail);
        put(vc, b.add(side), 0.90f, 0.97f, 1.0f, alpha);
        put(vc, b.subtract(side), 0.90f, 0.97f, 1.0f, alpha);
        // 反面
        put(vc, b.subtract(side), 0.90f, 0.97f, 1.0f, alpha);
        put(vc, b.add(side), 0.90f, 0.97f, 1.0f, alpha);
        put(vc, a.add(side), 0.50f, 0.74f, 1.0f, tail);
        put(vc, a.subtract(side), 0.50f, 0.74f, 1.0f, tail);
    }

    private static void put(VertexConsumer vc, Vec3 at, float r, float g, float b, float a) {
        vc.vertex(at.x, at.y, at.z).color(r, g, b, a).endVertex();
    }

    /** 换维度/重生：旧坐标作废。 */
    static void clear() {
        TRAILS.clear();
    }
}
