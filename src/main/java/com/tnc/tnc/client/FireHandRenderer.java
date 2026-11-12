package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.fire.TNFlameHandEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 火手链：燃烧的巨人手掌（几何全在这里生成，不用模型文件）。
 *
 * <h2>为什么手要"虚影 + 发光"</h2>
 * 手比玩家大得多，做实心会挡住视野、还会和方块穿插得很脏 ✗。所以：
 * <ul>
 *   <li><b>虚影</b>：整体 alpha 约 0.55，内部比边缘更暗 —— 看起来是"一团热浪凝成的手" ✓</li>
 *   <li><b>发光</b>：走 {@link RenderType#lightning()}（POSITION_COLOR、加色、不吃光照），
 *       裂纹和火舌是自发光，黑壳部分是暗红 —— 夜里也看得见 ✓</li>
 *   <li><b>地边不露</b>：<b>越靠近地面 alpha 越低</b>（{@link #GROUND_FADE}），
 *       手臂是"从火里长出来"而不是被一刀切断 ✓</li>
 * </ul>
 *
 * <h2>骨骼</h2>
 * 前臂 / 腕 / 掌 / 四指各 3 节 / 拇指 2 节 —— 一共 17 段。每段都是一个锥管，
 * 关节靠<b>管子自己的半径剖面</b>鼓出来（不另加球，所以不会有"一串糖葫芦"）。
 * 相位决定每根骨头的角度：破土(半握) → 张爪 → 依次收拢 → 三下捏 → 炸开。
 */
public final class FireHandRenderer extends EntityRenderer<TNFlameHandEntity> {

    /** 整体透明度：虚影。 */
    private static final float GHOST = 0.92F;

    /** 离地多高开始完全不透明（格）。 */
    private static final double GROUND_FADE = 0.75D;

    private static final int SIDES = 12;

    public FireHandRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(TNFlameHandEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(TNFlameHandEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        float p = Mth.clamp(entity.phase() + partialTick / TNFlameHandEntity.LIFE, 0F, 1F);
        int tier = Mth.clamp(entity.tier(), 1, 5);
        float scale = 0.70F + 0.30F * tier;      // t1 小、t5 巨大

        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());

        pose.pushPose();
        // 手在实体脚下 / 空中的位置；整体放大
        pose.translate(0.0F, 0.0F, 0.0F);
        pose.scale(scale, scale, scale);

        float curl = curlOf(p);
        float spread = spreadOf(p);
        float tremble = p > 0.46F && p < 0.82F ? 0.012F : 0.0F;

        PoseStack.Pose m = pose.last();

        // ---- 前臂（从地里往上升）
        double rise = riseOf(p);
        forearm(vc, m, rise, tremble);
        // ---- 掌 + 五指
        palm(vc, m, rise, curl);
        for (int i = 0; i < 4; i++) {
            finger(vc, m, i, rise, curl * (1.0F + 0.05F * i), spread);
        }
        thumb(vc, m, rise, curl);
        // ---- 火：手上的火舌 + 空中的火柱
        float rage = 1.0F + 0.55F * crushPulse(p) + 0.10F * tier;   // 捏的那一下整体炸亮
        flames(vc, m, p, rise, tier, rage);
        if (entity.aerial()) {
            pillar(vc, m, p, rise, tier);
        }

        pose.popPose();
    }

    // ------------------------------------------------------------ 相位 → 姿势

    /** 0 张开 → 1 握死。三下捏在 0.46/0.58/0.70 处各紧一次。 */
    private static float curlOf(float p) {
        if (p < 0.18F) {
            return 0.45F;                                  // 破土时半握
        }
        if (p < 0.32F) {
            return Mth.lerp((p - 0.18F) / 0.14F, 0.45F, 0.06F);   // 张爪
        }
        if (p < 0.46F) {
            return Mth.lerp((p - 0.32F) / 0.14F, 0.06F, 0.86F);   // 合拢
        }
        if (p < 0.80F) {
            float t = (p - 0.46F) / 0.34F;
            float pulse = (float) (Math.sin(t * Math.PI * 6.0) * 0.5 + 0.5);  // 三下收紧
            return 0.86F + 0.14F * pulse;
        }
        return Mth.lerp(Math.min(1F, (p - 0.80F) / 0.20F), 1.0F, 0.10F);      // 炸开
    }

    /** 三下捏的发力包络：每一拍的峰值 = 1，用来让火光和抖动跟上节奏。 */
    private static float crushPulse(float p) {
        if (p < 0.44F || p > 0.84F) {
            return 0.0F;
        }
        float t = (p - 0.44F) / 0.40F;
        return (float) Math.pow(Math.max(0.0, Math.sin(t * Math.PI * 3.0)), 3.0);
    }

    /** 手指张开程度（0 并拢 → 1 大张）。 */
    private static float spreadOf(float p) {
        if (p < 0.18F) {
            return 0.15F;
        }
        if (p < 0.32F) {
            return Mth.lerp((p - 0.18F) / 0.14F, 0.15F, 1.0F);
        }
        if (p < 0.46F) {
            return Mth.lerp((p - 0.32F) / 0.14F, 1.0F, 0.1F);
        }
        return 0.1F;
    }

    /** 手掌升到多高（0 = 全在地下，1 = 完全升起）。 */
    private static double riseOf(float p) {
        if (p < 0.56F) {
            return Mth.clamp(p / 0.18F, 0F, 1F);
        }
        return 1.0F;
    }

    private static void forearm(VertexConsumer vc, PoseStack.Pose m, double rise, float tremble) {
        double base = -1.35 + 1.20 * rise;
        double ex = tremble * Math.sin(base * 40.0);
        double[][] pts = {
                {ex, base, 0.0},
                {ex * 0.6, base + 0.34, 0.0},
                {0.0, base + 0.70, 0.0},
                {0.0, base + 1.02, 0.0},
        };
        double[] radii = {0.34, 0.385, 0.40, 0.36};
        tube(vc, m, pts, radii, 0.10F, 0.16F, 0.13F, 0.34F, 1.0F);
    }

    private static void palm(VertexConsumer vc, PoseStack.Pose m, double rise, float curl) {
        double y0 = -0.35 + 1.20 * rise;
        double y1 = y0 + 1.05;
        int layers = 7;
        for (int i = 0; i < layers; i++) {
            double t0 = i / (double) layers;
            double t1 = (i + 1) / (double) layers;
            double a0 = y0 + (y1 - y0) * t0;
            double a1 = y0 + (y1 - y0) * t1;
            double w0 = 0.44 * (0.72 + 0.34 * Math.sin(Math.PI * Math.min(1.0, t0 * 1.35)) + 0.17 * t0);
            double w1 = 0.44 * (0.72 + 0.34 * Math.sin(Math.PI * Math.min(1.0, t1 * 1.35)) + 0.17 * t1);
            double d0 = 0.235 * (0.88 + 0.28 * Math.sin(Math.PI * t0));
            double d1 = 0.235 * (0.88 + 0.28 * Math.sin(Math.PI * t1));
            ring(vc, m, a0, a1, w0, d0, w1, d1, 0.055F, 0.085F, 0.070F, 0.36F);
        }
    }

    /** 一根手指：3 节，按 curl 弯下去；关节靠半径剖面鼓出来。 */
    private static void finger(VertexConsumer vc, PoseStack.Pose m, int index, double rise,
                              float curl, float spread) {
        double[] roots = {-0.40, -0.14, 0.14, 0.38};
        double[] lens = {0.62, 0.66, 0.62, 0.52};
        double[] rads = {0.125, 0.132, 0.125, 0.108};
        double rootX = roots[index] * (1.0 + 0.22 * spread);
        double y0 = 0.70 + 1.20 * rise;
        double[] segLen = {0.44, 0.30, 0.20};
        double[] bends = {Math.toRadians(90) * curl, Math.toRadians(102) * curl, Math.toRadians(56) * curl};

        double x = rootX, y = y0, z = 0.0;
        double ang = Math.toRadians((index - 1.5) * 7.0 * (1 + spread));
        double dx = Math.sin(ang) * 0.4, dy = 1.0, dz = 0.05;
        double[][] pts = new double[5][3];
        pts[0] = new double[]{x, y, z};
        for (int s = 0; s < 3; s++) {
            double a = bends[s];
            double nx = dx * Math.cos(a) - dy * Math.sin(a);
            double ny = dx * Math.sin(a) + dy * Math.cos(a);
            dx = nx;
            dy = ny;
            double len = lens[index] * segLen[s];
            x += dx * len;
            y += dy * len;
            z += dz * len;
            pts[s + 1] = new double[]{x, y, z};
        }
        double r = rads[index];
        double[] radii = {r * 1.06, r * 1.18, r * 0.92, r * 1.10, r * 0.55};
        tube(vc, m, pts, radii, 0.06F, 0.10F, 0.078F, 0.40F, 1.15F);
    }

    private static void thumb(VertexConsumer vc, PoseStack.Pose m, double rise, float curl) {
        double y0 = 0.34 + 1.20 * rise;
        double bx = -0.40;
        double[][] pts = {
                {bx, y0, 0.10},
                {bx - 0.30, y0 + 0.30, 0.22},
                {bx - 0.28, y0 + 0.58, 0.30},
                {bx - 0.16, y0 + 0.76, 0.34},
        };
        double bend = Math.toRadians(40) * curl;
        for (int i = 1; i < 4; i++) {
            double dx = pts[i][0] - pts[i - 1][0];
            double dy = pts[i][1] - pts[i - 1][1];
            double ca = Math.cos(bend), sa = Math.sin(bend);
            pts[i][0] = pts[i - 1][0] + dx * ca - dy * sa;
            pts[i][1] = pts[i - 1][1] + dx * sa + dy * ca;
        }
        double[] radii = {0.24, 0.215, 0.185, 0.135};
        tube(vc, m, pts, radii, 0.07F, 0.11F, 0.082F, 0.42F, 1.2F);
    }

    // ------------------------------------------------------------ 火

    /** 手上的火舌：沿手掌外缘窜出去的细锥。 */
    private static void flames(VertexConsumer vc, PoseStack.Pose m, float p, double rise, int tier,
                               float rage) {
        int count = (int) ((12 + 4 * tier) * Math.min(2.0F, rage));
        for (int i = 0; i < count; i++) {
            double a = (i / (double) count) * Math.PI * 2.0 + p * 3.0;
            double y0 = 0.1 + 1.5 * rise + 0.5 * Math.sin(a * 3.0 + p * 9.0);
            double r0 = 0.42 + 0.06 * Math.sin(a * 5.0 + p * 7.0);
            double x0 = Math.cos(a) * r0;
            double z0 = Math.sin(a) * r0 * 0.55;
            double h = (0.30 + 0.35 * (i % 3)) * (0.7 + 0.5 * Math.sin(p * 11.0 + i))
                    * (0.85 + 0.55 * rage);
            double sway = 0.12 * Math.sin(p * 13.0 + i * 2.1);
            double[][] pts = {
                    {x0, y0, z0},
                    {x0 * 1.15 + sway, y0 + h * 0.55, z0 * 1.15},
                    {x0 * 1.25 + sway * 2, y0 + h, z0 * 1.25},
            };
            double[] radii = {0.075 * rage, 0.045 * rage, 0.004};
            tube(vc, m, pts, radii, 1.0F, 0.72F, 0.22F,
                    Math.min(0.98F, 0.80F * rage), 1.6F);
        }
    }

    /** 空中目标：从地面一直到手掌，一整根疯烧的火柱。 */
    private static void pillar(VertexConsumer vc, PoseStack.Pose m, float p, double rise, int tier) {
        double top = 0.9 + 1.2 * rise;
        double bottom = -2.5;
        int rings = 12;
        double phaseShift = p * 9.0;
        for (int i = 0; i < rings; i++) {
            double t0 = i / (double) rings;
            double t1 = (i + 1) / (double) rings;
            double y0 = bottom + (top - bottom) * t0;
            double y1 = bottom + (top - bottom) * t1;
            double w0 = 0.34 + 0.10 * Math.sin(phaseShift + t0 * 9.0) + 0.12 * t0;
            double w1 = 0.34 + 0.10 * Math.sin(phaseShift + t1 * 9.0) + 0.12 * t1;
            ring(vc, m, y0, y1, w0, w0, w1, w1, 0.85F, 0.30F, 0.05F, 0.55F * (1.0F - 0.5F * (float) t0));
        }
    }

    // ------------------------------------------------------------ 几何工具

    /** 一个锥管：沿折线扫掠，半径逐个变化（关节就是靠这里的半径鼓出来的）。 */
    private static void tube(VertexConsumer vc, PoseStack.Pose m, double[][] pts, double[] radii,
                             float r, float g, float b, float alpha, float emissive) {
        double[] prevRight = null, prevUp = null;
        for (int i = 0; i < pts.length - 1; i++) {
            double[] d = sub(pts[i + 1], pts[i]);
            d = norm(d);
            double[] ref = Math.abs(d[1]) > 0.85 ? new double[]{1, 0, 0} : new double[]{0, 1, 0};
            double[] right = norm(cross(ref, d));
            double[] up = cross(d, right);
            if (prevRight == null) {
                prevRight = right;
                prevUp = up;
            } else {
                // 平行传输：避免管子自己扭起来
                right = prevRight;
                up = prevUp;
            }
            for (int s = 0; s < SIDES; s++) {
                double a0 = Math.PI * 2 * s / SIDES;
                double a1 = Math.PI * 2 * (s + 1) / SIDES;
                double[] q0 = ringPoint(pts[i], prevRight, prevUp, a0, radii[i]);
                double[] q1 = ringPoint(pts[i], prevRight, prevUp, a1, radii[i]);
                double[] q2 = ringPoint(pts[i + 1], right, up, a1, radii[i + 1]);
                double[] q3 = ringPoint(pts[i + 1], right, up, a0, radii[i + 1]);
                float t = i / (float) Math.max(1, pts.length - 2);
                // 越往上越亮：底部焦黑，指节/指尖发白
                float hot = Mth.clamp(t * 0.9f + emissive * 0.15f, 0f, 1f);
                // 底色也要亮：光影/加色下暗色会被吃掉，所以从"暗红炭"直接提到"烧红的铁"
                quad(vc, m, q0, q1, q2, q3,
                        Mth.lerp(hot, 0.72F, 1.0F), Mth.lerp(hot, 0.26F, 0.86F),
                        Mth.lerp(hot, 0.08F, 0.42F),
                        alpha * ghostFade(q0[1]));
            }
            prevRight = right;
            prevUp = up;
        }
    }

    /** 手掌的一段：超椭圆环（方而不尖），上下两层接到一起。 */
    private static void ring(VertexConsumer vc, PoseStack.Pose m, double y0, double y1,
                             double w0, double d0, double w1, double d1,
                             float r, float g, float b, float alpha) {
        int n = 16;
        for (int s = 0; s < n; s++) {
            double a0 = Math.PI * 2 * s / n;
            double a1 = Math.PI * 2 * (s + 1) / n;
            double[] p0 = superEllipse(a0, y0, w0, d0);
            double[] p1 = superEllipse(a1, y0, w0, d0);
            double[] p2 = superEllipse(a1, y1, w1, d1);
            double[] p3 = superEllipse(a0, y1, w1, d1);
            quad(vc, m, p0, p1, p2, p3, 0.82F, 0.32F, 0.10F, alpha * ghostFade(y0));
        }
    }

    private static double[] superEllipse(double a, double y, double w, double d) {
        double ca = Math.cos(a), sa = Math.sin(a);
        double n = 2.6;
        double px = Math.signum(ca) * Math.pow(Math.abs(ca), 2.0 / n) * w;
        double pz = Math.signum(sa) * Math.pow(Math.abs(sa), 2.0 / n) * d;
        return new double[]{px, y, pz};
    }

    /** 地面淡出：越贴近地面越透明 —— 手臂不会有一条看得见的断口。 */
    private static float ghostFade(double y) {
        if (y >= GROUND_FADE) {
            return GHOST;
        }
        return GHOST * (float) Mth.clamp((y + 0.35) / (GROUND_FADE + 0.35), 0.18, 1.0);
    }

    private static double[] ringPoint(double[] p, double[] right, double[] up, double a, double radius) {
        return new double[]{
                p[0] + right[0] * Math.cos(a) * radius + up[0] * Math.sin(a) * radius,
                p[1] + right[1] * Math.cos(a) * radius + up[1] * Math.sin(a) * radius,
                p[2] + right[2] * Math.cos(a) * radius + up[2] * Math.sin(a) * radius,
        };
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose m,
                             double[] a, double[] b, double[] c, double[] d,
                             float r, float g, float bl, float alpha) {
        if (alpha <= 0.01F) {
            return;
        }
        vert(vc, m, a, r, g, bl, alpha);
        vert(vc, m, b, r, g, bl, alpha);
        vert(vc, m, c, r, g, bl, alpha);
        vert(vc, m, d, r, g, bl, alpha);
        // 背面（虚影要两面都看得到）
        vert(vc, m, d, r, g, bl, alpha * 0.85F);
        vert(vc, m, c, r, g, bl, alpha * 0.85F);
        vert(vc, m, b, r, g, bl, alpha * 0.85F);
        vert(vc, m, a, r, g, bl, alpha * 0.85F);
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose m, double[] p, float r, float g, float b, float a) {
        vc.vertex(m.pose(), (float) p[0], (float) p[1], (float) p[2])
                .color((int) (r * 255), (int) (g * 255), (int) (b * 255), (int) (a * 255))
                .endVertex();
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] norm(double[] a) {
        double n = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        if (n < 1e-9) {
            return new double[]{0, 1, 0};
        }
        return new double[]{a[0] / n, a[1] / n, a[2] / n};
    }
}
