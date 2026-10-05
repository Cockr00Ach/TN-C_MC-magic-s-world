package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.fire.FireSpellRules;
import com.tnc.tnc.magic.fire.TNFireBoltEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 火球的渲染器 —— 一颗<b>拖长焰尾的彗星</b>（作者 2026-10-05 参照图定稿）。
 *
 * <h2>形态要求（照作者给的参考图）</h2>
 * <pre>
 *   尾部（细、暗、飘散）  ←────────  焰体（渐宽再收细）  ←──  白热核心（最亮、在最前）
 * </pre>
 * 参考图里是一颗**被拉长的火流星**：
 * <ul>
 *   <li>最前面是一团<b>白热核</b>（白黄、最亮、几乎不透）</li>
 *   <li>焰体<b>不是球</b>，是从核心向后拖出去的锥体：先略微涨到最宽，再一路收细到看不见</li>
 *   <li>焰体外面有几条<b>螺旋缠绕的火舌</b>，越往后越飘散</li>
 *   <li>颜色沿长度走：白黄 → 亮橙 → 橙 → 深橙红，透明度同步衰减</li>
 * </ul>
 *
 * <h2>为什么上一版不对</h2>
 * 上一版是"一颗球 + 三条细管 + 一圈粒子"，看着仍然像<b>球外面挂了点东西</b> ✗。
 * 关键差别是：参考图的<b>主体是那条焰尾</b>，球只是它的头部。
 * 所以这一版把焰尾做成**正片主几何**（{@link #flameBody}），核心只是收口的那一团
 * （{@link #coreBlob}），粒子退成点缀。
 *
 * <h2>为什么能直接用水系那两个类</h2>
 * {@code WaterGeometry} / {@code WaterRenderTypes} 是<b>包内可见</b>的通用工具
 * （{@code quad} / {@code radial} 都带颜色参数，只因当年为水法而写才沿用这个名字），
 * 本渲染器与它们同在 {@code com.tnc.tnc.client} 包，直接复用 ✓
 * 渲染通道用的是自发光着色器（{@code RENDERTYPE_LIGHTNING_SHADER}），洞里也亮。
 */
public final class TNFireBoltRenderer extends EntityRenderer<TNFireBoltEntity> {

    /** 不会被真正贴图（几何体自带颜色），指向一张一定存在的原版贴图只为不触发缺图警告。 */
    private static final ResourceLocation PLACEHOLDER =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/particle/flame.png");

    /** 焰尾有多长（= 核心半径的多少倍）。参考图里尾巴远长于头部，所以给得大。 */
    private static final double TAIL_LENGTH_FACTOR = 8.5D;

    /** 焰体沿长度切多少段（越大越平滑，代价是顶点数）。 */
    private static final int BODY_SLICES = 18;
    /** 焰体一圈切多少边。 */
    private static final int BODY_SIDES = 16;
    /** 几条螺旋火舌。 */
    private static final int WISPS = 5;
    /** 火舌沿长度切多少段 / 一圈几边（比主体省，因为它们本来就细碎）。 */
    private static final int WISP_SLICES = 14;
    private static final int WISP_SIDES = 7;

    public TNFireBoltRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.0F;
    }

    @Override
    public boolean shouldRender(TNFireBoltEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        return entity.distanceToSqr(x, y, z) < 192.0D * 192.0D;
    }

    @Override
    public ResourceLocation getTextureLocation(TNFireBoltEntity entity) {
        return PLACEHOLDER;
    }

    @Override
    public void render(TNFireBoltEntity entity, float yaw, float partial, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        var out = buffers.getBuffer(WaterRenderTypes.geometry());
        Matrix4f pose = stack.last().pose();

        Vec3 dir = entity.safeDirection();
        Vec3 right = FireSpellRules.right(dir);
        Vec3 up = right.cross(dir).normalize();

        // 成形阶段整体长大（作者要求"先形成一个球形再发射出去"）；
        // 飞过射程 65% 之后逐渐消失（作者要求"离开一定距离后逐渐消失"）。
        // 两件事都作用在"这颗球现在多大 + 多亮"上，所以在这里一次算完。
        float fade = entity.fade(partial);
        if (fade <= 0.02F) {
            return;                                        // 已经飞到头，整颗收掉
        }
        double radius = entity.radius() * entity.formProgress(partial) * (0.35D + 0.65D * fade);
        if (radius < 0.02D) {
            return;
        }
        double age = entity.tickCount + partial;
        double length = radius * TAIL_LENGTH_FACTOR;

        // 画法顺序 = 从"最外层/最暗"到"最亮"，让白热核最后压上去。
        // （渲染通道只写颜色不写深度，所以后画的会盖在前面的上面）
        for (int w = 0; w < WISPS; w++) {
            wisp(out, pose, dir, right, up, radius, length, age, w, fade);
        }
        flameBody(out, pose, dir, right, up, radius, length, age, fade);
        coreBlob(out, pose, dir, right, up, radius, age, fade);
    }

    // ------------------------------------------------------------------
    //  焰体：从核心往后拖出去的渐变锥（参考图的主体）
    // ------------------------------------------------------------------

    private static void flameBody(VertexConsumer out, Matrix4f pose, Vec3 dir, Vec3 right, Vec3 up,
                                  double radius, double length, double age, float fade) {
        float[] color = new float[4];
        Vec3 prevCenter = null;
        double prevR = 0.0D;
        for (int s = 0; s <= BODY_SLICES; s++) {
            double t = s / (double) BODY_SLICES;
            Vec3 center = axisPoint(dir, right, up, t, length, radius, age, 1.0D);
            double r = profile(t) * radius;
            if (prevCenter != null && prevR > 1.0E-4D && r > 1.0E-4D) {
                flameColor(t, 0.80F * fade, color);
                ring(out, pose, right, up, prevCenter, prevR, center, r, color, BODY_SIDES);
            }
            prevCenter = center;
            prevR = r;
        }
    }

    // ------------------------------------------------------------------
    //  螺旋火舌：缠绕在焰体外、越往后越飘散
    // ------------------------------------------------------------------

    private static void wisp(VertexConsumer out, Matrix4f pose, Vec3 dir, Vec3 right, Vec3 up,
                             double radius, double length, double age, int index, float fade) {
        float[] color = new float[4];
        Vec3 prevCenter = null;
        double prevR = 0.0D;
        double phase = index * (Math.PI * 2.0D / WISPS);
        for (int s = 0; s <= WISP_SLICES; s++) {
            double t = 0.06D + (s / (double) WISP_SLICES) * 0.94D;
            // 沿轴的位置 + 绕轴的螺旋偏移：角度随 t 与时间转，形成"火舌在缠"
            double angle = age * 0.30D + phase + t * 2.4D + index * 0.7D;
            double off = profile(t) * radius * (0.55D + 0.25D * Math.sin(age * 0.5D + index));
            Vec3 center = axisPoint(dir, right, up, t, length, radius, age, 1.35D)
                    .add(WaterGeometry.radial(right, up, angle, off));
            double r = profile(t) * radius * 0.40D * (1.0D - 0.35D * Math.sin(age * 0.8D + index * 1.3D));

            if (prevCenter != null && prevR > 1.0E-4D && r > 1.0E-4D) {
                // 火舌比主体更亮更透 —— 它是"蹿起来的火苗"
                flameColor(t * 0.85D, 0.55F * fade, color);
                color[0] = Math.min(1.0F, color[0] * 1.05F);
                color[2] = Math.min(1.0F, color[2] * 1.10F);
                ring(out, pose, right, up, prevCenter, prevR, center, r, color, WISP_SIDES);
            }
            prevCenter = center;
            prevR = r;
        }
    }

    // ------------------------------------------------------------------
    //  核心：白热的三层球（焰体从它后面接出去）
    // ------------------------------------------------------------------

    private static void coreBlob(VertexConsumer out, Matrix4f pose, Vec3 dir, Vec3 right, Vec3 up,
                                 double radius, double age, float fade) {
        // 燃烧的呼吸感
        float flicker = (float) (0.85D + 0.15D * Math.sin(age * 0.6D));
        sphere(out, pose, dir, right, up, radius * 1.00D, 1.00F, 0.66F, 0.20F, 0.55F * flicker * fade);
        sphere(out, pose, dir, right, up, radius * 0.74D, 1.00F, 0.86F, 0.42F, 0.78F * flicker * fade);
        sphere(out, pose, dir, right, up, radius * 0.44D, 1.00F, 0.97F, 0.82F, 0.95F * fade);
    }

    // ------------------------------------------------------------------
    //  几何与颜色工具
    // ------------------------------------------------------------------

    /**
     * 焰体在某处的<b>中心点</b>：沿 -dir 后退，并叠加一点横向摆动
     * （越靠尾越晃 —— 火焰不是一根直棍子）。
     *
     * @param wobble 摆动幅度倍率（火舌用 > 1 让它更飘）
     */
    private static Vec3 axisPoint(Vec3 dir, Vec3 right, Vec3 up, double t, double length,
                                  double radius, double age, double wobble) {
        double w1 = Math.sin(t * 5.0D + age * 0.35D) * radius * 0.30D * t * wobble;
        double w2 = Math.cos(t * 4.0D + age * 0.28D) * radius * 0.22D * t * wobble;
        return dir.scale(-length * t).add(right.scale(w1)).add(up.scale(w2));
    }

    /**
     * 焰体沿长度的粗细（1.0 = 与核心半径相同）。
     *
     * <p>参考图的形态：从核心往后<b>先略微涨宽</b>（约 15% 处最宽），
     * 然后一路<b>收细到 0</b> —— 这是"火流星"和"球"最大的区别所在。
     */
    private static double profile(double t) {
        if (t <= 0.15D) {
            return 1.00D + 0.20D * (t / 0.15D);
        }
        double k = Math.max(0.0D, (1.0D - t) / 0.85D);
        return 1.20D * Math.pow(k, 0.72D);
    }

    /**
     * 沿长度取色：白黄 → 亮橙 → 橙 → 深橙红，透明度同步衰减。
     *
     * <p>结果写进 {@code dst}（复用数组，避免每段 new 一次 —— 渲染里不该分配）。
     *
     * @param t          0 = 最热处，1 = 尾尖
     * @param alphaScale 这一层的整体透明度倍率
     */
    private static void flameColor(double t, float alphaScale, float[] dst) {
        double k;
        if (t < 0.30D) {                 // 白黄 → 亮橙
            k = t / 0.30D;
            dst[0] = 1.00F;
            dst[1] = (float) (1.00D - 0.32D * k);
            dst[2] = (float) (0.82D - 0.72D * k);
        } else if (t < 0.65D) {          // 亮橙 → 橙
            k = (t - 0.30D) / 0.35D;
            dst[0] = 1.00F;
            dst[1] = (float) (0.68D - 0.30D * k);
            dst[2] = (float) (0.10D - 0.06D * k);
        } else {                         // 橙 → 深橙红
            k = (t - 0.65D) / 0.35D;
            dst[0] = (float) (1.00D - 0.16D * k);
            dst[1] = (float) (0.38D - 0.24D * k);
            dst[2] = 0.04F;
        }
        dst[3] = (float) ((1.0D - 0.88D * t) * alphaScale);
    }

    /** 连接两圈、每段一个颜色的锥台（焰体的基本积木）。 */
    private static void ring(VertexConsumer out, Matrix4f pose, Vec3 right, Vec3 up,
                             Vec3 c0, double r0, Vec3 c1, double r1, float[] color, int sides) {
        float a = color[0], b = color[1], c = color[2], d = color[3];
        for (int i = 0; i < sides; i++) {
            double a0 = i * Math.PI * 2.0D / sides;
            double a1 = (i + 1) * Math.PI * 2.0D / sides;
            Vec3 p0 = c0.add(WaterGeometry.radial(right, up, a0, r0));
            Vec3 p1 = c0.add(WaterGeometry.radial(right, up, a1, r0));
            Vec3 q1 = c1.add(WaterGeometry.radial(right, up, a1, r1));
            Vec3 q0 = c1.add(WaterGeometry.radial(right, up, a0, r1));
            WaterGeometry.quad(out, pose, p0, p1, q1, q0, a, b, c, d);
        }
    }

    /** 画一颗球：沿飞行方向切 10 圈、每圈 20 段（参数化照抄水系那颗水球）。 */
    private static void sphere(VertexConsumer out, Matrix4f pose, Vec3 dir, Vec3 right, Vec3 up,
                               double radius, float r, float g, float b, float alpha) {
        for (int j = 0; j < 10; j++) {
            double a = -Math.PI / 2.0D + j * Math.PI / 10.0D;
            double c = a + Math.PI / 10.0D;
            for (int i = 0; i < 20; i++) {
                double u = i * Math.PI / 10.0D;
                double v = (i + 1) * Math.PI / 10.0D;
                Vec3 p = dir.scale(Math.sin(a) * radius).add(WaterGeometry.radial(right, up, u, Math.cos(a) * radius));
                Vec3 q = dir.scale(Math.sin(a) * radius).add(WaterGeometry.radial(right, up, v, Math.cos(a) * radius));
                Vec3 s = dir.scale(Math.sin(c) * radius).add(WaterGeometry.radial(right, up, v, Math.cos(c) * radius));
                Vec3 t = dir.scale(Math.sin(c) * radius).add(WaterGeometry.radial(right, up, u, Math.cos(c) * radius));
                WaterGeometry.quad(out, pose, p, q, s, t, r, g, b, alpha);
            }
        }
    }
}
