package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.fire.TNMeteorFallEntity;
import com.tnc.tnc.magic.fire.TNSkyfallEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 火系两座法阵的渲染器 —— <b>画真正的魔法阵，不是一圈火焰粒子</b>。
 *
 * <p>作者 2026-10-05 给了参考图并明确说：「不要就一圈火焰粒子代表法阵」。
 * 所以这里用几何体一笔一笔画出来（照参考图的构成）：
 *
 * <ol>
 *   <li><b>外圈</b>：粗主环 + 外侧细环 + 一圈<b>符文刻痕</b>（短径向刻线，分组、
 *       长短不一 —— 近似"文字"，几何体画不出真字形，但远看一样）</li>
 *   <li><b>内圈群</b>：3~4 层同心细环</li>
 *   <li><b>六芒星</b>：六个顶点连成的两道三角（跨越式连线）</li>
 *   <li><b>三个卫星圆</b>：沿 120° 分布，各自带内环与花纹，缓慢公转</li>
 *   <li><b>中心</b>：圆 + 两圈螺旋</li>
 * </ol>
 *
 * <p>法阵是<b>水平</b>的（法线朝上）—— 地面法阵自上而下看是正圆 ✅，
 * 头顶法阵从下往上看是对称的环 ✅
 *
 * <p>{@code grand = true} 时就是"更复杂"的那一座（{@link TNMeteorFallEntity} 的陨星坠）：
 * 层数更多、六芒星换成双三角叠、卫星圆各有花纹、外圈符文更密 ✓
 *
 * <p>颜色统一是参考图那种<b>金白发光</b>：主环偏金（1.0, 0.86, 0.42），
 * 细节偏白（1.0, 0.95, 0.70）—— 走的还是自发光通道，夜里/洞里都亮。
 */
public final class TNSigilRenderer<T extends Entity> extends EntityRenderer<T> {

    /** 不会被真正贴图（几何体自带颜色），指向一张一定存在的原版贴图只为不触发缺图警告。 */
    private static final ResourceLocation PLACEHOLDER =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/particle/flame.png");

    private final double radius;
    private final boolean grand;

    public TNSigilRenderer(EntityRendererProvider.Context ctx, double radius, boolean grand) {
        super(ctx);
        this.radius = radius;
        this.grand = grand;
        shadowRadius = 0.0F;
    }

    @Override
    public boolean shouldRender(T entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        return entity.distanceToSqr(x, y, z) < 192.0D * 192.0D;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return PLACEHOLDER;
    }

    @Override
    public void render(T entity, float yaw, float partial, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        var out = buffers.getBuffer(WaterRenderTypes.geometry());
        Matrix4f pose = stack.last().pose();
        Vec3 center = entity.position();
        double age = entity.tickCount + partial;

        // 出场渐显：前 8 tick 从 0 涨到 1（免得"啪"地整座法阵凭空出现）
        float fade = (float) Math.min(1.0D, (entity.tickCount + partial) / 8.0D);

        drawSigil(out, pose, center, radius, grand, age, fade);
    }

    /**
     * 画一座法阵。<b>水平</b>放置（法线朝上），所有半径都是 {@code radius} 的比例。
     *
     * @param grand 更复杂的那一座（陨星坠）
     * @param age   用于缓慢自转的相位
     * @param fade  整体透明度（出场渐显）
     */
    public static void drawSigil(VertexConsumer out, Matrix4f pose, Vec3 center, double radius,
                                 boolean grand, double age, float fade) {
        // 旋转相位：外圈正转、内圈反转 —— 参考图那种"机械感"
        double spin = age * 0.010D;
        double spinIn = -age * 0.016D;

        // ---------------- 1) 外圈 ----------------
        ring(out, pose, center, radius * 1.000D, radius * 0.030D, 1.00F, 0.86F, 0.42F, 0.90F * fade);
        ring(out, pose, center, radius * 0.940D, radius * 0.012D, 1.00F, 0.95F, 0.70F, 0.70F * fade);
        ring(out, pose, center, radius * 1.045D, radius * 0.008D, 1.00F, 0.80F, 0.36F, 0.55F * fade);

        // 外圈的"扇贝边"：一圈小弧段（参考图外缘那种缺口感）
        int scallop = grand ? 24 : 16;
        for (int k = 0; k < scallop; k++) {
            double a0 = spin + k * Math.PI * 2.0D / scallop;
            double a1 = a0 + (Math.PI * 2.0D / scallop) * 0.55D;
            arc(out, pose, center, radius * 1.075D, radius * 0.022D, a0, a1, 1.00F, 0.88F, 0.48F, 0.60F * fade);
        }

        // ---------------- 2) 符文刻痕 ----------------
        // 几何体画不出真字形，所以用"长短不一、成组"的径向刻线近似 —— 远看就是一圈文字
        int runes = grand ? 28 : 18;
        for (int k = 0; k < runes; k++) {
            double a = spin + k * Math.PI * 2.0D / runes;
            double longTick = (k % 3 == 0) ? 1.0D : 0.55D;
            double r0 = radius * 0.960D;
            double r1 = radius * (0.960D + 0.055D * longTick);
            tube(out, pose, polar(center, a, r0), polar(center, a, r1),
                    radius * 0.006D, 1.00F, 0.93F, 0.62F, 0.75F * fade);
        }

        // ---------------- 3) 内圈群 ----------------
        ring(out, pose, center, radius * 0.820D, radius * 0.020D, 1.00F, 0.90F, 0.55F, 0.85F * fade);
        ring(out, pose, center, radius * 0.780D, radius * 0.008D, 1.00F, 0.95F, 0.70F, 0.65F * fade);
        ring(out, pose, center, radius * 0.700D, radius * 0.014D, 1.00F, 0.88F, 0.50F, 0.75F * fade);
        if (grand) {
            ring(out, pose, center, radius * 0.640D, radius * 0.008D, 1.00F, 0.95F, 0.72F, 0.60F * fade);
            ring(out, pose, center, radius * 0.560D, radius * 0.010D, 1.00F, 0.90F, 0.58F, 0.60F * fade);
        }

        // ---------------- 4) 六芒星 ----------------
        // 六个顶点，按"隔两点连一次"画两道三角 —— 就是六芒星
        int nodes = 6;
        double starR = radius * 0.820D;
        for (int k = 0; k < nodes; k++) {
            double a0 = spin * 0.6D + k * Math.PI * 2.0D / nodes;
            double a1 = spin * 0.6D + ((k + 2) % nodes) * Math.PI * 2.0D / nodes;
            tube(out, pose, polar(center, a0, starR), polar(center, a1, starR),
                    radius * 0.007D, 1.00F, 0.92F, 0.60F, 0.60F * fade);
        }
        if (grand) {
            // 更复杂的那座：再叠一道反向三角 + 顶点小环
            double star2 = radius * 0.700D;
            for (int k = 0; k < nodes; k++) {
                double a0 = spinIn * 0.6D + k * Math.PI * 2.0D / nodes;
                double a1 = spinIn * 0.6D + ((k + 2) % nodes) * Math.PI * 2.0D / nodes;
                tube(out, pose, polar(center, a0, star2), polar(center, a1, star2),
                        radius * 0.006D, 1.00F, 0.86F, 0.45F, 0.50F * fade);
            }
            for (int k = 0; k < nodes; k++) {
                double a = spin * 0.6D + k * Math.PI * 2.0D / nodes;
                ringAt(out, pose, polar(center, a, starR), radius * 0.045D, radius * 0.008D,
                        1.00F, 0.95F, 0.70F, 0.65F * fade);
            }
        }

        // ---------------- 5) 三个卫星圆 ----------------
        // 参考图里最显眼的三颗"小世界"：沿 120° 分布、各自缓慢公转
        double orbit = radius * 0.560D;
        for (int k = 0; k < 3; k++) {
            double a = age * 0.006D + k * Math.PI * 2.0D / 3.0D;
            Vec3 c = polar(center, a, orbit);
            ringAt(out, pose, c, radius * 0.150D, radius * 0.016D, 1.00F, 0.90F, 0.55F, 0.90F * fade);
            ringAt(out, pose, c, radius * 0.130D, radius * 0.006D, 1.00F, 0.97F, 0.78F, 0.65F * fade);
            // 卫星圆里的"花纹"：几条不规则短线（参考图里像大陆轮廓）
            for (int m = 0; m < 5; m++) {
                double b0 = spinIn + m * Math.PI * 2.0D / 5.0D + k;
                double b1 = b0 + 0.55D;
                tube(out, pose, polar(c, b0, radius * 0.045D), polar(c, b1, radius * 0.075D),
                        radius * 0.005D, 1.00F, 0.94F, 0.66F, 0.55F * fade);
            }
        }

        // ---------------- 6) 中心 ----------------
        ring(out, pose, center, radius * 0.320D, radius * 0.014D, 1.00F, 0.88F, 0.52F, 0.85F * fade);
        ring(out, pose, center, radius * 0.250D, radius * 0.008D, 1.00F, 0.96F, 0.74F, 0.70F * fade);
        // 中心螺旋：两段反向的弧，慢慢转
        int spiral = grand ? 3 : 2;
        for (int k = 0; k < spiral; k++) {
            for (int seg = 0; seg < 10; seg++) {
                double t0 = seg / 10.0D;
                double t1 = (seg + 1) / 10.0D;
                double r0 = radius * (0.070D + 0.170D * t0);
                double r1 = radius * (0.070D + 0.170D * t1);
                double a0 = age * 0.030D + t0 * 3.4D + k * Math.PI * 2.0D / spiral;
                double a1 = age * 0.030D + t1 * 3.4D + k * Math.PI * 2.0D / spiral;
                tube(out, pose, polar(center, a0, r0), polar(center, a1, r1),
                        radius * 0.006D, 1.00F, 0.92F, 0.60F, 0.75F * fade);
            }
        }
    }

    // ------------------------------------------------------------------
    //  小工具（都画在**水平面**上：法阵躺平，法线朝上）
    // ------------------------------------------------------------------

    /** 水平面上、距中心 {@code r}、方位角 {@code a} 的一点。 */
    private static Vec3 polar(Vec3 center, double a, double r) {
        return center.add(Math.cos(a) * r, 0.0D, Math.sin(a) * r);
    }

    /** 一圈正环（同心圆）。 */
    private static void ring(VertexConsumer out, Matrix4f pose, Vec3 center, double r, double width,
                             float red, float green, float blue, float alpha) {
        ringAt(out, pose, center, r, width, red, green, blue, alpha);
    }

    /** 以任意点为心的一圈环 —— 卫星圆、顶点小环都用它。 */
    private static void ringAt(VertexConsumer out, Matrix4f pose, Vec3 center, double r, double width,
                               float red, float green, float blue, float alpha) {
        WaterGeometry.ringColor(out, pose, center, UP, r, width, red, green, blue, alpha);
    }

    /** 一小段圆弧（用若干小段管子拼）—— 外圈那种"扇贝"缺口感。 */
    private static void arc(VertexConsumer out, Matrix4f pose, Vec3 center, double r, double width,
                            double a0, double a1, float red, float green, float blue, float alpha) {
        int seg = 4;
        for (int i = 0; i < seg; i++) {
            double t0 = a0 + (a1 - a0) * i / seg;
            double t1 = a0 + (a1 - a0) * (i + 1) / seg;
            tube(out, pose, polar(center, t0, r), polar(center, t1, r), width, red, green, blue, alpha);
        }
    }

    private static void tube(VertexConsumer out, Matrix4f pose, Vec3 from, Vec3 to, double width,
                             float red, float green, float blue, float alpha) {
        WaterGeometry.tube(out, pose, from, to, width, red, green, blue, alpha);
    }

    /** 法阵的法线：永远朝上（法阵是躺平的）。 */
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

    /** 给两个法阵实体类型用的两个实例（一个普通、一个"更复杂"）。 */
    public static TNSigilRenderer<TNSkyfallEntity> skyfall(EntityRendererProvider.Context ctx) {
        return new TNSigilRenderer<>(ctx, TNSkyfallEntity.SIGIL_RADIUS, false);
    }

    public static TNSigilRenderer<TNMeteorFallEntity> meteorFall(EntityRendererProvider.Context ctx) {
        return new TNSigilRenderer<>(ctx, TNMeteorFallEntity.SIGIL_RADIUS, true);
    }
}
