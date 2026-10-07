package com.tnc.tnc.client;

import com.tnc.tnc.magic.fire.TNSolarJudgmentField;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * <b>射线链 t5「太阳の审判」的渲染器</b>（作者 2026-10-05 设计）✓
 *
 * <h2>画什么</h2>
 * <ol>
 *   <li><b>脚下那个巨大法阵</b> —— 同样「由外到里一点点出现」✓（和 t4 一致 ✓）</li>
 *   <li><b>正上方那颗太阳光球</b> —— 作者要「类似太阳的光球」✓
 *       （三片正交发光圆盘 + 一圈日冕 + 一圈耀斑 ✓）</li>
 *   <li><b>大量光线</b> —— 从太阳往法阵里倾泻 ✓（作者要"向法阵范围内发射大量光线"✓）</li>
 * </ol>
 *
 * <p>⚠️ 坐标一律<b>相对实体位置</b> ✗（{@code EntityRenderer} 的 pose 已经平移到实体位置 ✓）
 */
public final class TNSolarJudgmentRenderer extends EntityRenderer<TNSolarJudgmentField> {

    private static final ResourceLocation PLACEHOLDER =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");

    /** 法阵由外到里"描绘"完需要多久（tick ✓）。 */
    private static final double DRAW_TICKS = 40.0D;

    /** 一次最多画几条光线（`beams()` 可能很大，做个上限免得一帧塞几千根管 ✗）。 */
    private static final int MAX_BEAMS = 24;

    /**
     * <b>不管有几个目标，至少撒这么多条</b> ✓
     *
     * <p>⚠️ 作者第 5 条：「向法阵范围内发出大量射线，而不是就一条」✓
     * ⇒ 这就是"大量"的下限 ✓（18 条铺满整个法阵 ✓）
     */
    private static final int MIN_BEAMS = 18;

    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Vec3 AXIS_X = new Vec3(1.0D, 0.0D, 0.0D);
    private static final Vec3 AXIS_Z = new Vec3(0.0D, 0.0D, 1.0D);
    private static final Vec3 ORIGIN = Vec3.ZERO;

    public TNSolarJudgmentRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TNSolarJudgmentField entity) {
        return PLACEHOLDER;
    }

    @Override
    public boolean shouldRender(TNSolarJudgmentField field, Frustum frustum, double x, double y, double z) {
        return field.distanceToSqr(x, y, z) < 200.0D * 200.0D;
    }

    @Override
    public void render(TNSolarJudgmentField field, float yaw, float partial, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        VertexConsumer out = buffers.getBuffer(WaterRenderTypes.geometry());
        Matrix4f pose = stack.last().pose();

        double age = field.tickCount + partial;
        double radius = field.radius();
        if (radius < 0.5D) {
            return;
        }
        // ⚠️ sunPosition() 是世界坐标 ✓ ⇒ 换相对坐标 ✗
        Vec3 sun = field.sunPosition().subtract(field.position());

        // ★ 2026-10-05：作者「t4/t5 中的法阵有问题，不好修就把火球链的法阵拿来用」✓
        //   ⇒ 直接**复用火球链那座**（TNSigilRenderer，照作者参考图画的那版 ✓）
        //     自己手搓的那套 drawSigil 已删除 ✗（两套法阵叠在一起就是"有问题"的来源 ✗）
        // 出场渐显（照 TNSigilRenderer 的写法 ✓，免得"啪"地凭空出现 ✗）
        float fade = (float) Math.min(1.0D, (field.tickCount + partial) / 8.0D);
        TNSigilRenderer.drawSigil(out, pose, Vec3.ZERO, 15.0D, true, age, fade);  // 作者：t4/t5 法阵都变 3 倍 ✗（5 → 15 ✓）
        drawSun(out, pose, sun, age, radius);
        drawBeams(out, pose, sun, radius, age, field.beams());
    }

    // ------------------------------------------------------------------
    //  ① 巨大法阵（由外到里描绘）
    // ------------------------------------------------------------------


    // ------------------------------------------------------------------
    //  ② 正上方那颗太阳
    // ------------------------------------------------------------------

    private static void drawSun(VertexConsumer out, Matrix4f pose, Vec3 sun, double age, double radius) {
        // 太阳比 t4 的球大得多 ✓（作者要"类似太阳的光球"✓）
        double r = Math.max(2.4D, radius * 0.30D);
        float pulse = (float) (0.94D + 0.06D * Math.sin(age * 0.16D));
        // 外层暗红球（三片正交 ✓）
        for (Vec3 axis : new Vec3[]{UP, AXIS_X, AXIS_Z}) {
            WaterGeometry.disk(out, pose, sun, axis, r * 1.5D * pulse, 1.00F, 0.30F, 0.04F, 1.0F);
        }
        // 中层橙
        for (Vec3 axis : new Vec3[]{UP, AXIS_X, AXIS_Z}) {
            WaterGeometry.disk(out, pose, sun, axis, r * 1.05D * pulse, 1.00F, 0.62F, 0.12F, 1.0F);
        }
        // 内层白热核
        for (Vec3 axis : new Vec3[]{UP, AXIS_X, AXIS_Z}) {
            WaterGeometry.disk(out, pose, sun, axis, r * 0.60D * pulse, 1.00F, 0.96F, 0.72F, 1.0F);
        }
        // 日冕 + 耀斑（两圈反向转的环 ✓）
        WaterGeometry.ring(out, pose, sun, UP, r * 2.0D, 0.22D, age * 0.03D, 1.0F);
        WaterGeometry.ring(out, pose, sun, AXIS_X, r * 2.2D, 0.16D, -age * 0.022D, 1.0F);
        WaterGeometry.ring(out, pose, sun, AXIS_Z, r * 2.2D, 0.16D, age * 0.026D, 1.0F);
    }

    // ------------------------------------------------------------------
    //  ③ 大量光线：从太阳往阵内倾泻
    // ------------------------------------------------------------------

    private static void drawBeams(VertexConsumer out, Matrix4f pose, Vec3 sun, double radius,
                                  double age, int beams) {
        // ⚠️ 作者 2026-10-05 第 5 条：「圆球向法阵范围内发出大量射线，而不是就一条」✓
        //    根因：原来光线数 = 阵内敌人数 ✗ ⇒ 只有一只怪时就只画一条 ✗，
        //    看着完全不像"太阳在审判"✗
        //    ⇒ 现在**不管有几个目标，都撒出一整片** ✓（目标多时再多画几条 ✓）
        int n = Math.max(MIN_BEAMS, Math.min(beams, MAX_BEAMS));
        for (int i = 0; i < n; i++) {
            // 落点沿着阵内一条缓慢转动的螺旋铺开 ⇒ 看着像"一直在往下砸"✓
            double angle = age * 0.09D + i * (Math.PI * 2.0D / n) * 1.618D;
            double rr = radius * (0.25D + 0.68D * ((i * 7 % 11) / 10.0D));
            Vec3 to = new Vec3(Math.cos(angle) * rr, 0.15D, Math.sin(angle) * rr);
            // 外焰壳 + 白热芯（和 t4 的激光同一套观感 ✓）
            WaterGeometry.tube(out, pose, sun, to, 0.20D, 1.00F, 0.34F, 0.06F, 1.0F);
            WaterGeometry.tube(out, pose, sun, to, 0.085D, 1.00F, 0.95F, 0.70F, 1.0F);
        }
    }
}
