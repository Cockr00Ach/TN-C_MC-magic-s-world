package com.tnc.tnc.magic.fire;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * <b>火龙术（t3）的粒子龙</b> ✓ —— 作者 2026-10-05：
 * 「根据这张图简要的勾勒出立体的龙头，可以不要细节只表现出大概轮廓，
 * <b>内部用大量粒子填充</b>，<b>不要用光滑的几何体（这是重点）</b>，
 * 身体也不要用几何体<b>改用粒子</b>」✓
 *
 * <h2>怎么做的</h2>
 * <p>不用任何几何体（没有锥台、没有圆环 ✗），全部是**染色粒子** ✓：
 * <ol>
 *   <li><b>轮廓</b>：把龙的侧脸轮廓写成一组归一化剖面点（见 {@link #PROFILE} ✓，
 *       照作者给的参考图勾的 ✓：右上长吻、左上有角、下方是张开的下颌、后面是鬃毛 ✓），
 *       每 tick 沿每条边**等距撒粒子** ✓ ⇒ 用粒子"描"出轮廓 ✓</li>
 *   <li><b>内部填充</b>：在轮廓内部**随机取点**（拒绝采样 ✓）撒粒子 ✓
 *       ⇒ 作者要的"大量粒子填充" ✓</li>
 *   <li><b>立体感</b>：每个点都带一个**横向厚度**（半宽 {@link #halfWidth} ✓）——
 *       在 ±w 之间取一个横向偏移 ✓ ⇒ 出来是一个**有厚度的壳** ✓，不是一个平面片 ✗</li>
 *   <li><b>龙身</b>：沿 -前 方向一条**蛇形曲线**（横向正弦 + 上下余弦 ✓），
 *       越往后越细 ✓ ⇒ 5 格长的火尾 ✓</li>
 * </ol>
 *
 * <p>粒子用 {@link DustParticleOptions} ✓ —— 它**可以指定颜色** ✓，
 * 所以整条龙的配色和游戏内 {@code PixelFlame} 色板一致 ✓
 * （普通 {@code FLAME} 粒子没法染色 ✗，只能出橘黄一种 ✓）
 *
 * <p>⚠️ 纯服务端发出 ✓（所有玩家都看得见 ✓），不依赖渲染器 ✓
 */
public final class TNDragonParticles {

    private TNDragonParticles() {
    }

    /**
     * 龙头剖面（归一化 ✓）：{@code u} = 前后（0 = 颅后、1 = 吻尖 ✓），
     * {@code v} = 上下（+ 向上 ✓）。
     *
     * <p>顺序 = 沿轮廓走一圈 ✓（最后一条边接回第一个点 ⇒ 闭合 ✓）。
     * 勾自作者 2026-10-05 给的参考图 ✓：上颌从吻尖往回到颅顶、
     * 左上一对角、后面几根鬃毛刺、再沿下颌回到吻尖 ✓
     */
    private static final double[] PROFILE = {
            // ⚠️ 2026-10-05：这组剖面点是**从作者给的参考图自动描出来的** ✓
            //    （Moore 邻域边界跟踪 5178 像素 → 抽稀到 45 点 → 归一化 ✓）
            //    ⚠️ 已知局限：参考图是**线条画**（只有描边 ✗），
            //       所以描到的是"线条笔画自己的外沿"✗，不是完整剪影 ✗
            //       ⇒ 形状偏圆、细节（角/牙/鬃毛）被抹掉了 ✗
            //       要更准的话需要一张**实心剪影**（纯黑填充 + 白底 ✓）—— 那样能 1:1 描 ✓
            0.3514, 0.4181, 0.2800, 0.4013, 0.2378, 0.3511, 0.2167, 0.2848,
            0.1548, 0.2578, 0.1133, 0.2746, 0.0426, 0.2571, -0.0222, 0.2309,
            -0.0761, 0.1959, -0.1417, 0.1741, -0.1999, 0.1391, -0.2444, 0.0918,
            -0.3150, 0.0954, -0.3653, 0.0532, -0.4192, 0.0138, -0.4592, -0.0401,
            -0.4964, -0.0947, -0.4869, -0.1661, -0.4345, -0.2032, -0.4024, -0.2607,
            -0.3274, -0.2724, -0.2480, -0.2804, -0.1817, -0.2870, -0.1031, -0.2797,
            -0.0237, -0.2870, 0.0426, -0.3095, 0.1089, -0.2899, 0.1322, -0.2396,
            0.1832, -0.2804, 0.2378, -0.3176, 0.2706, -0.3744, 0.3194, -0.4181,
            0.3908, -0.4064, 0.4359, -0.3598, 0.4658, -0.3001, 0.4840, -0.2302,
            0.4920, -0.1522, 0.5000, -0.0736, 0.5000, 0.0117, 0.4993, 0.0969,
            0.4847, 0.1697, 0.4687, 0.2418, 0.4461, 0.3066, 0.4119, 0.3649,
            0.3653, 0.4093    };

    /** 眼睛的位置与大小（归一化 ✓）——单独一个小环 ✓，让轮廓里有"脸"的感觉 ✓。 */
    private static final double EYE_U = 0.58D;
    private static final double EYE_V = 0.16D;
    private static final double EYE_R = 0.055D;

    /** 龙头周围的**火焰光晕**粒子数 ✓（头壳本身由渲染器画方块模型 ✓）。 */
    private static final int AURA_POINTS = 60;

    /** 轮廓粒子数（越大线越实 ✓）—— 现在只对兜底有用 ✗。 */
    private static final int OUTLINE_POINTS = 46;

    /** 内部填充粒子数（作者："大量粒子填充" ✓）。 */
    private static final int FILL_POINTS = 34;

    /** 龙身粒子数 ✓。 */
    private static final int BODY_POINTS = 40;

    /** 头部整体尺寸（格 ✓）—— 作者要"龙要大"✗ ⇒ 3 格 ✓。 */
    public static final double HEAD_SIZE = 3.0D;

    /**
     * 前后方向的拉伸 ✓
     *
     * <p>参照图里龙头是**明显偏长**的（吻长接近头高 ✗）；只按 1:1 缩放会画成圆脑袋 ✗
     * ⇒ 前后方向乘 1.5 ✓
     */
    private static final double DEPTH_STRETCH = 1.5D;

    /** 龙身半径（格 ✓）—— 尾巴要有体积，不能是一条线 ✗。 */
    private static final double BODY_RADIUS = 0.42D;

    /** 龙身长度（格 ✓）—— 作者："身体大概 5 个方块长" ✓。 */
    public static final double BODY_LENGTH = 5.0D;

    /** 色板（和 {@code PixelFlame} 同一套火系色 ✓）：白热 / 亮黄 / 橙 / 深橙 / 暗红 ✓。 */
    private static final float[][] PALETTE = {
            {1.00F, 0.98F, 0.88F},
            {1.00F, 0.88F, 0.42F},
            {1.00F, 0.66F, 0.16F},
            {0.90F, 0.40F, 0.06F},
            {0.62F, 0.18F, 0.04F},
    };

    /**
     * 这一 tick 把整只粒子龙发出去 ✓。
     *
     * @param origin 龙头的**中心**世界坐标 ✓（一般是实体位置 ✓）
     * @param dir    前方向（飞行方向 ✓，单位向量 ✓）
     * @param right  右方向 ✓
     * @param up     上方向 ✓
     * @param age    实体存活 tick 数（用来让轮廓轻微抖动 ✓）
     */
    public static void emit(ServerLevel level, Vec3 origin, Vec3 dir, Vec3 right, Vec3 up,
                            double age) {
        // ⚠️ 作者 2026-10-05：「身体可以，但头部看不出龙头的感觉，
        //    可不可以把末影龙的头部模型拿来用」✓
        //   ⇒ **龙头改由渲染器画原版 DRAGON_HEAD 方块模型** ✓（见 TNFireBoltRenderer ✓）
        //      所以这里**不再撒头部的轮廓/填充/眼睛粒子** ✗
        //      只留：① 龙头周围一圈**火焰光晕** ✓（让方块头看着"在烧"✓）
        //            ② 龙身（作者说"身体可以"⇒ 保留 ✓）

        // ① 龙头周围的光晕（绕着头壳随机撒 ✓，不描形状 ✗）
        for (int p = 0; p < AURA_POINTS; p++) {
            double au = -0.45D + Math.random() * 1.35D;          // 头的前后范围 ✓
            double av = -0.35D + Math.random() * 0.85D;          // 上下 ✓
            double aw = (Math.random() * 2.0D - 1.0D) * 0.42D;   // 横向（比头壳宽一点 ✓）
            spawn(level, origin, dir, right, up, au, av,
                    Math.abs(aw) + 0.10D, 0.35D + Math.random() * 0.5D, 1, 1);
        }
        // ④ 龙身：沿 -前 的蛇形曲线 ✓，**有体积**（每步撒一小团 ✓，不是一条线 ✗）
        for (int p = 0; p < BODY_POINTS; p++) {
            double t = p / (double) BODY_POINTS;                 // 0 = 颈, 1 = 尾尖 ✓
            double back = 0.25D + t * BODY_LENGTH;               // 以**方块**为单位 ✓
            double sway = Math.sin(t * 3.4D + age * 0.30D) * 0.75D * t;
            double bob = Math.cos(t * 2.6D + age * 0.26D) * 0.45D * t;
            Vec3 at = origin.subtract(dir.scale(back))
                    .add(right.scale(sway)).add(up.scale(bob));
            // 越往后越细、越暗 ✓；每步撒 3 颗、带半径 ⇒ 尾巴是**一根有粗有细的火柱** ✓
            double r = BODY_RADIUS * (1.0D - 0.72D * t);
            level.sendParticles(dust(0.35D + t * 0.55D, 1.45F),
                    at.x, at.y, at.z, 3, r, r, r, 0.0D);
        }
    }

    /** 撒一个粒子：把归一化 (u,v) 换算到世界坐标 ✓ + 加一个横向厚度 ✓。 */
    private static void spawn(ServerLevel level, Vec3 origin, Vec3 dir, Vec3 right, Vec3 up,
                              double u, double v, double halfWidth, double shade, int colorBand,
                              int count) {
        double w = (Math.random() * 2.0D - 1.0D) * halfWidth * HEAD_SIZE;
        Vec3 at = origin.add(dir.scale(u * HEAD_SIZE * DEPTH_STRETCH)).add(up.scale(v * HEAD_SIZE))
                .add(right.scale(w));
        level.sendParticles(dust(shade, 1.55F + colorBand * 0.15F),
                at.x, at.y, at.z, count, 0.05D, 0.05D, 0.05D, 0.0D);
    }

    /** 该处的横向半宽（归一化 ⇒ 乘 HEAD_SIZE 得格 ✓）—— 吻部窄、颅部宽 ✓。 */
    private static double halfWidth(double u) {
        if (u < 0.0D) {
            return 0.16D + 0.06D * (u + 0.7D);          // 颈/鬃毛一带较窄 ✓
        }
        return 0.30D - 0.19D * u * u;                   // 吻尖收窄 ✓
    }

    /** 点 (u,v) 是否在轮廓内部（射线法 ✓）。 */
    private static boolean inside(double u, double v) {
        int n = PROFILE.length / 2;
        boolean in = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double ui = PROFILE[i * 2];
            double vi = PROFILE[i * 2 + 1];
            double uj = PROFILE[j * 2];
            double vj = PROFILE[j * 2 + 1];
            if ((vi > v) != (vj > v) && u < (uj - ui) * (v - vi) / (vj - vi) + ui) {
                in = !in;
            }
        }
        return in;
    }

    /** 按"热 → 冷"取色 ✓（shade 0 = 最热白黄、1 = 最冷暗红 ✓）。 */
    private static DustParticleOptions dust(double shade, float scale) {
        int i = (int) Math.floor(Math.max(0.0D, Math.min(0.9999D, shade)) * PALETTE.length);
        float[] c = PALETTE[Math.min(i, PALETTE.length - 1)];
        return new DustParticleOptions(new Vector3f(c[0], c[1], c[2]), scale);
    }
}
