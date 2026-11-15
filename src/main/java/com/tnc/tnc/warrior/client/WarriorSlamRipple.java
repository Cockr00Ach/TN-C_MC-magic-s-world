package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.warrior.WarriorTrailVolume;
import com.tnc.tnc.warrior.WarriorWeaponFamily;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/**
 * 战锤"下砸"的扩散影：锤子砸下去的一瞬间，在砸点铺一圈**向外扩散、越扩越淡**的环。
 *
 * <p>用户的原话是"锤子一旦有下砸类动作，希望它有一种扩散的影的感觉"。这里不用粒子（明确不要），
 * 而是用和刀光同一套贴图/渲染类型画几个平铺在地上的四边形环 —— 环心就是锤尖落点，
 * 半径在 {@link #LIFE_MS} 内从 {@link #START_RADIUS} 扩到 {@link #END_RADIUS}。
 *
 * <p>怎么算"下砸"：只看**几何**，不看动画名 —— 轨迹最后 120 ms 内锤尖高度掉了超过
 * {@link #SLAM_DROP} 格就算。这样所有下砸动作（不管哪个连招、哪把锤）都会出，也不会因为
 * 新加动作而漏掉。每 {@link #COOLDOWN_MS} 最多触发一次，避免连续帧重复铺环。
 */
final class WarriorSlamRipple {
    /** 环的寿命、扩散半径、每帧最多几段、触发阈值与冷却。 */
    static final long LIFE_MS = 380L;
    static final double START_RADIUS = 0.55;
    static final double END_RADIUS = 4.2;
    static final int SEGMENTS = 32;
    static final double SLAM_DROP = 0.45;
    static final long WINDOW_MS = 120L;
    static final long COOLDOWN_MS = 420L;
    /** 环的厚度（格）：外扩时同时变宽，看起来是"荡开"而不是一条细线。 */
    static final double WIDTH_START = 0.16;
    static final double WIDTH_END = 0.62;

    private record Ripple(Vec3 center, long start, int color) {}

    private static final Map<Integer, Ripple> RIPPLES = new HashMap<>();
    private static final Map<Integer, Long> LAST_SLAM = new HashMap<>();

    private WarriorSlamRipple() {}

    /** 扩散半径：{@code t} 是 0（刚砸下）到 1（消失），前半段扩得快、后半段慢下来。 */
    static double radius(double t) {
        double clamped = Math.max(0d, Math.min(1d, t));
        return START_RADIUS + (END_RADIUS - START_RADIUS) * (1d - Math.pow(1d - clamped, 2.2));
    }

    /** 环的厚度：跟着半径一起变宽。 */
    static double width(double t) {
        double clamped = Math.max(0d, Math.min(1d, t));
        return WIDTH_START + (WIDTH_END - WIDTH_START) * clamped;
    }

    /** 透明度：一开始最亮，之后快速收掉（{@code 1-t} 的 1.6 次方）。 */
    static float alpha(double t) {
        double clamped = Math.max(0d, Math.min(1d, t));
        return (float) (0.55 * Math.pow(1d - clamped, 1.6));
    }

    /**
     * 每帧调用（在刀光采样之后）：判断是不是下砸，是就在落点起一个环。
     *
     * @param curve 这一帧已经重采样好的轨迹（老的在前）
     */
    static void sample(int playerId, String weapon, WarriorWeaponFamily family,
                       List<WarriorTrailVolume.Point> curve, long now, int color) {
        if (!isHammer(weapon, family) || curve.size() < 2) {
            return;                     // 不是锤：不新起环，但已有的环继续淡出（换武器不会"啪"一下消失）
        }
        Ripple current = RIPPLES.get(playerId);
        if (current != null && now - current.start() > LIFE_MS) {
            RIPPLES.remove(playerId);
            current = null;
        }
        Long last = LAST_SLAM.get(playerId);
        if (last != null && now - last < COOLDOWN_MS) {
            return;                     // 冷却中：不重复铺环，但已有的环继续画
        }
        WarriorTrailVolume.Point newest = curve.get(curve.size() - 1);
        double newestY = newest.tip().y;
        // 找 120 ms 之前那个点：它是这一段的"起点"
        double oldestY = newestY;
        for (int i = curve.size() - 1; i >= 0; i--) {
            WarriorTrailVolume.Point p = curve.get(i);
            if (newest.at() - p.at() > WINDOW_MS) {
                oldestY = p.tip().y;
                break;
            }
            oldestY = p.tip().y;
        }
        if (newestY - oldestY > -SLAM_DROP) {
            return;                     // 没往下砸
        }
        Vec3 center = new Vec3(newest.tip().x, newestY - 0.08, newest.tip().z);
        RIPPLES.put(playerId, new Ripple(center, now, color));
        LAST_SLAM.put(playerId, now);
    }

    /** 把还活着的环画出来：平铺在地面上的一圈四边形，向外扩散并淡出。 */
    static void render(int playerId, MultiBufferSource buffer, Vec3 camera, long now) {
        Ripple ripple = RIPPLES.get(playerId);
        if (ripple == null) {
            return;
        }
        long age = now - ripple.start();
        if (age > LIFE_MS) {
            RIPPLES.remove(playerId);
            return;
        }
        double t = age / (double) LIFE_MS;
        float a = alpha(t);
        if (a <= 0.01f) {
            return;
        }
        double inner = radius(t);
        double outer = inner + width(t);
        VertexConsumer out = WarriorTrailRenderType.plume(buffer, 0.9, WarriorTrail.PLUME_CORE);
        int color = ripple.color();
        float r = (color >> 16 & 255) / 255f;
        float g = (color >> 8 & 255) / 255f;
        float b = (color & 255) / 255f;
        Vec3 c = ripple.center().subtract(camera);
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / SEGMENTS;
            double a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
            Vec3 p0 = new Vec3(c.x + Math.cos(a0) * inner, c.y, c.z + Math.sin(a0) * inner);
            Vec3 p1 = new Vec3(c.x + Math.cos(a1) * inner, c.y, c.z + Math.sin(a1) * inner);
            Vec3 p2 = new Vec3(c.x + Math.cos(a1) * outer, c.y, c.z + Math.sin(a1) * outer);
            Vec3 p3 = new Vec3(c.x + Math.cos(a0) * outer, c.y, c.z + Math.sin(a0) * outer);
            // 内沿亮、外沿透明：一眼能看出是"从砸点荡开"的
            quad(out, p0, p1, p2, p3, r, g, b, a, a, 0f, 0f);
        }
    }

    /** 一个四边形（正反两面各写一遍，双面都看得见）。 */
    private static void quad(VertexConsumer out, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             float r, float g, float bl, float aa, float ab, float ac, float ad) {
        put(out, a, r, g, bl, aa);
        put(out, b, r, g, bl, ab);
        put(out, c, r, g, bl, ac);
        put(out, d, r, g, bl, ad);
        put(out, d, r, g, bl, ad);
        put(out, c, r, g, bl, ac);
        put(out, b, r, g, bl, ab);
        put(out, a, r, g, bl, aa);
    }

    private static void put(VertexConsumer out, Vec3 p, float r, float g, float b, float a) {
        out.vertex(p.x, p.y, p.z).color(r, g, b, a).endVertex();
    }

    /** 战锤家族，或者 id 里带 hammer 的武器（家族表以后变了也不会漏）。 */
    static boolean isHammer(String weapon, WarriorWeaponFamily family) {
        return family == WarriorWeaponFamily.HAMMER
                || (weapon != null && (weapon.contains("hammer") || weapon.contains("maul")));
    }

    static void clear() {
        RIPPLES.clear();
        LAST_SLAM.clear();
    }
}
