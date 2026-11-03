package com.tnc.tnc.warrior;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * 龙之剑那条龙的<b>纯几何</b>：没有渲染器、没有世界，只有三角形。
 *
 * <p>这样做有三个好处：
 * <ol>
 *   <li>渲染器只剩"把三角形塞进 buffer"，出了怪形状一眼能看出是数据还是画错；</li>
 *   <li>可以在单测里检查形状（头有没有推进、宽度有没有越界、截面有没有塌、有没有 NaN）；</li>
 *   <li>{@code tools/dragon_preview.py} 能读同一份三角形数据离线出图，不用起客户端。</li>
 * </ol>
 *
 * <p><b>龙身是四棱管，不是一条带子。</b>第一版做的是"沿龙身永远竖直的飘带"，结果在
 * 弧线最高点处切向的 Y 分量穿过 0，{@code up} 会突然翻面，相邻两段直接拧成蝴蝶结
 * （离线预览里看得很清楚：一排梳齿）。现在按<b>平行传输</b>逐段旋转截面（每段只把
 * 上一段的 {@code u} 投影到新切向的垂面上），截面不会翻面，任何视角看都有体积。
 *
 * <p><b>没有翅膀。</b>中途试过给两侧加"膜状翼"，离线预览里是一块块浮在旁边的暗红色块 ——
 * 中式龙本来就是蛇身，靠龙身、龙头、双角认，加翼反而像贴上去的装饰。所以这条龙是
 * 无翼蛇身 + 菱形龙头 + 一对后掠角 + 金色脊线。
 *
 * <p>坐标系就是世界坐标；{@code origin} 是龙尾起点，{@code dir} 是水平朝向（已归一化），
 * {@code side} 是 {@code dir} 左转 90° 的水平向量。
 */
public final class WarriorDragonGeometry {

    /** 从出现到消失的 tick 数：前 {@link #TRAVEL} tick 冲出去，之后整条停住淡出。 */
    public static final int LIFE = 18;
    /** 龙头推进占用的 tick 数。 */
    public static final int TRAVEL = 10;
    /** 沿龙身的采样段数。 */
    public static final int SAMPLES = 28;
    /** 龙长（格）。 */
    public static final double LENGTH = 7.5;
    /** 每段龙身画几个三角形：4 个侧面 × 2。 */
    public static final int TRIS_PER_SEGMENT = 8;
    /** 龙头几个三角形（菱形 2 + 脊线 1 + 双角 2）。 */
    public static final int HEAD_TRIS = 5;

    public static final int SHELL = 0x5E0912;
    public static final int BODY = 0xC92A22;
    public static final int CORE = 0xFFD680;

    /** 一个上色三角形：渲染器三个顶点，预览脚本三个点。 */
    public record Tri(Vec3 a, Vec3 b, Vec3 c, int color, float alpha) {
    }

    private WarriorDragonGeometry() {
    }

    /** 龙身中心线：向前推 + 左右蛇摆 + 先抬后落的弧线。 */
    public static Vec3 path(Vec3 origin, Vec3 dir, Vec3 side, double s) {
        double wave = Math.sin(s * Math.PI * 2.4) * (0.55 + 0.9 * s);
        double rise = Math.sin(s * Math.PI) * 1.15 - 0.35 * s;
        return origin.add(dir.scale(LENGTH * s)).add(side.scale(wave)).add(0, rise, 0);
    }

    /** 局部切向；{@code head} 是龙头的 s，越靠近龙头步长越小，免得把曲线末端抹平。 */
    public static Vec3 tangent(Vec3 origin, Vec3 dir, Vec3 side, double s, double head) {
        double step = Math.min(0.02, Math.max(0.002, (head - s) * 0.5 + 0.01));
        Vec3 back = path(origin, dir, side, Math.max(0, s - step));
        Vec3 ahead = path(origin, dir, side, Math.min(head, s + step));
        Vec3 tangent = ahead.subtract(back);
        return tangent.lengthSqr() < 1.0E-8 ? dir : tangent.normalize();
    }

    /** 半径：尾细、中段最粗、龙头收一点。 */
    public static double radius(double s) {
        double taper = Math.sin(Math.PI * Math.pow(Math.max(0, s), 0.75));
        return 0.035 + 0.30 * Math.max(0, taper);
    }

    /** 龙头进度 0..1。 */
    public static double head(double age) {
        return Math.max(0, Math.min(1, age / TRAVEL));
    }

    /** 整体透明度：最后 8 tick 淡出。 */
    public static float fade(double age) {
        return (float) Math.max(0, Math.min(1, (LIFE - age) / 8.0));
    }

    /**
     * 把一条龙变成三角形列表。{@code head} 是龙头进度（0..1），{@code fade} 是整体透明度。
     */
    public static List<Tri> build(Vec3 origin, Vec3 dir, Vec3 side, double head, float fade) {
        List<Tri> out = new ArrayList<>(SAMPLES * TRIS_PER_SEGMENT * 3 + 8);
        if (head <= 0.01) {
            return out;
        }
        Vec3[] points = new Vec3[SAMPLES + 1];
        Vec3[] tangents = new Vec3[SAMPLES + 1];
        Vec3[] us = new Vec3[SAMPLES + 1];
        Vec3[] vs = new Vec3[SAMPLES + 1];
        double[] radius = new double[SAMPLES + 1];
        for (int i = 0; i <= SAMPLES; i++) {
            double s = head * i / (double) SAMPLES;
            points[i] = path(origin, dir, side, s);
            tangents[i] = tangent(origin, dir, side, s, head);
            radius[i] = radius(s);
        }
        // 平行传输：只把上一段的 u 投影到新切向的垂面，截面就不会在弧线顶点翻面
        Vec3 carried = new Vec3(0, 1, 0);
        for (int i = 0; i <= SAMPLES; i++) {
            Vec3 t = tangents[i];
            Vec3 u = carried.subtract(t.scale(carried.dot(t)));
            if (u.lengthSqr() < 1.0E-6) {
                u = side.subtract(t.scale(side.dot(t)));
            }
            if (u.lengthSqr() < 1.0E-6) {
                u = dir.cross(t);
            }
            u = u.normalize();
            carried = u;
            us[i] = u;
            vs[i] = t.cross(u).normalize();
        }
        tube(out, points, us, vs, radius, 1.0, SHELL, fade * 0.42f);
        tube(out, points, us, vs, radius, 0.62, BODY, fade * 0.72f);
        tube(out, points, us, vs, radius, 0.20, CORE, fade * 0.95f);
        head(out, origin, dir, side, head, fade);
        return out;
    }

    /** 四棱管：每段 4 个侧面，尾淡头亮。 */
    private static void tube(List<Tri> out, Vec3[] points, Vec3[] us, Vec3[] vs, double[] radius,
                             double scale, int color, float alpha) {
        int last = points.length - 1;
        for (int i = 0; i < last; i++) {
            double t0 = i / (double) last, t1 = (i + 1) / (double) last;
            float a0 = alpha * (float) (0.24 + 0.76 * t0);
            float a1 = alpha * (float) (0.24 + 0.76 * t1);
            float sideAlpha = (a0 + a1) * 0.5f;
            double r0 = radius[i] * scale, r1 = radius[i + 1] * scale;
            Vec3 a = points[i], b = points[i + 1];
            Vec3 au = us[i], bu = us[i + 1], av = vs[i], bv = vs[i + 1];
            Vec3[] aCorners = {
                a.add(au.scale(r0)).add(av.scale(r0)), a.add(au.scale(r0)).subtract(av.scale(r0)),
                a.subtract(au.scale(r0)).subtract(av.scale(r0)), a.subtract(au.scale(r0)).add(av.scale(r0))
            };
            Vec3[] bCorners = {
                b.add(bu.scale(r1)).add(bv.scale(r1)), b.add(bu.scale(r1)).subtract(bv.scale(r1)),
                b.subtract(bu.scale(r1)).subtract(bv.scale(r1)), b.subtract(bu.scale(r1)).add(bv.scale(r1))
            };
            for (int corner = 0; corner < 4; corner++) {
                Vec3 a0p = aCorners[corner], a1p = aCorners[(corner + 1) % 4];
                Vec3 b0p = bCorners[corner], b1p = bCorners[(corner + 1) % 4];
                out.add(new Tri(a0p, b0p, b1p, color, sideAlpha));
                out.add(new Tri(a0p, b1p, a1p, color, sideAlpha));
            }
        }
    }

    /** 龙头：菱形 + 金色脊线 + 一对向后掠的角。 */
    private static void head(List<Tri> out, Vec3 origin, Vec3 dir, Vec3 side, double head, float fade) {
        Vec3 tip = path(origin, dir, side, head);
        Vec3 forward = tangent(origin, dir, side, head, head);
        Vec3 up = new Vec3(0, 1, 0).subtract(forward.scale(forward.y));
        up = up.lengthSqr() < 1.0E-4 ? side : up.normalize();
        double size = 0.40;
        Vec3 snout = tip.add(forward.scale(1.75 * size));
        Vec3 left = tip.add(up.scale(size)).subtract(forward.scale(0.30));
        Vec3 right = tip.subtract(up.scale(size)).subtract(forward.scale(0.30));
        Vec3 back = tip.subtract(forward.scale(1.70 * size));
        out.add(new Tri(left, snout, right, BODY, fade * 0.88f));
        out.add(new Tri(left, right, back, SHELL, fade * 0.62f));
        Vec3 core = tip.subtract(forward.scale(0.06));
        out.add(new Tri(core.add(up.scale(0.15)), core.add(forward.scale(0.45)),
                core.subtract(up.scale(0.15)), CORE, fade));
        // 角：短、向后掠，让远端轮廓一眼能认出是"头"而不是"箭头"
        for (int sign = -1; sign <= 1; sign += 2) {
            Vec3 base = tip.subtract(forward.scale(0.30)).add(up.scale(size * 0.60));
            Vec3 horn = tip.subtract(forward.scale(0.85)).add(up.scale(size * 1.55))
                    .add(side.scale(sign * 0.16));
            out.add(new Tri(base, horn, tip.subtract(forward.scale(0.95)).add(up.scale(0.10)),
                    SHELL, fade * 0.8f));
        }
    }
}
