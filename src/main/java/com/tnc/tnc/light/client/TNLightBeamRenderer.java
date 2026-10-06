package com.tnc.tnc.light.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.light.TNLightBeamEntity;
import com.tnc.tnc.light.TNLightBeamMechanics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * <b>实体光线的渲染</b> ✓ —— 用**一整根棱柱**画出来（不是粒子 ✗），
 * 颜色沿轴线走彩虹 ✓，外面一层彩色壳 + 里面一根白芯 ✓（作者 2026-10-01："全部加粗"✓）。
 *
 * <h2>★★ 2026-10-01 事故一：{@code RenderType.lightning()} 画不出来（作者："我就看到冒烟，没看到光线"）</h2>
 * 第一版图省事用了原版闪电那层 ✗ —— 结果整根光柱**一个像素都不显示** ✗。原因是它的定义里带着
 * <b>{@code .setOutputState(WEATHER_TARGET)}</b> ✗：原版闪电是画进**天气帧缓冲**的
 * （{@code LevelRenderer} 在天气那一趟才合成它 ✓），而实体渲染这一趟根本不会去合成那个缓冲 ✗
 * ⇒ 顶点提交了、也不报错，就是看不见 ✗。
 * 现在改用**本仓库已经验证过能显示**的那层：{@code entityTranslucentEmissive} ✓
 * （光翼 {@code TNLightWingsRenderer} 和天使 {@code TNAngelRenderer} 用的就是它 ✓），
 * 配一张 16×16 纯白贴图（{@code textures/entity/beam_white.png} ✓，顶点色负责上色 ✓）。
 *
 * <h2>★★ 事故二：忘了乘实体朝向</h2>
 * 渲染器拿到的 {@code pose} 只有"实体在哪" ✓、**没有"实体朝哪"** ✗ ——
 * 原版闪电不用转（它永远竖直 ✓），而光柱是**任意方向**的 ✗ ⇒ 不转的话整根柱子永远沿世界 +Z 躺平 ✗。
 * 现在按原版投射物那套补上：先绕 Y 转 {@code -yRot}、再绕 X 转 {@code xRot} ✓
 * （两者都按 {@code partialTick} 插值 ✓，不然高速转身时会抖 ✗）。
 */
public class TNLightBeamRenderer extends EntityRenderer<TNLightBeamEntity> {

    /** 柱身分几段（越大越圆 ✓；12 段在这个粗细下已经看不出棱 ✓）。 */
    private static final int SIDES = 12;

    /** 纯白贴图（顶点色上色 ✓）。 */
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, "textures/entity/beam_white.png");

    public TNLightBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TNLightBeamEntity entity) {
        return WHITE;
    }

    @Override
    public void render(TNLightBeamEntity beam, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        double radius = beam.radius();
        double length = beam.length();
        if (radius <= 0.0D || length <= 0.0D) {
            return;
        }
        // 出现/消失各 2 tick 的缩放，免得"啪"地一下 ✗（作者一直要"流畅"✓）
        float age = beam.tickCount + partialTick;
        float grow = Mth.clamp(Math.min(age / 2.0F, (beam.life() + 2.0F - age) / 2.0F), 0.0F, 1.0F);
        if (grow <= 0.01F) {
            return;
        }
        boolean descent = beam.style() == TNLightBeamEntity.STYLE_DESCENT;
        // ★ 火系形态（作者 2026-10-05 的「射线链」）：**橙红壳 + 高热黄白芯** ✓
        boolean fire = beam.style() == TNLightBeamEntity.STYLE_FIRE;

        pose.pushPose();
        // ★ 起点用施法者的眼睛（客户端实时位置 ✓）—— 派发器已经平移到"实体插值后的位置"了，
        //   所以这里补一个差值平移 ✓（否则边跑边放会看到光柱从身后追上来 ✗）。
        Vec3 origin = originOf(beam);
        Vec3 dispatched = beam.getPosition(partialTick);
        pose.translate(origin.x - dispatched.x, origin.y - dispatched.y, origin.z - dispatched.z);
        // ★ 朝向用**实体自己那份**（服务端每 tick 限速转向 ✓ ⇒ 看得见它拐向敌人 ✓，这就是"索敌"✓）。
        //   为什么不在客户端直接对准目标：那样会**瞬间贴上去** ✗，看不出在追 ✗
        //   （雷球是引擎投射物的 homing_angle，也是"拐弯"而不是瞬移 ✓）。
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(partialTick, beam.yRotO, beam.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTick, beam.xRotO, beam.getXRot())));

        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        Matrix4f m = pose.last().pose();

        // ① 外层彩色壳（沿轴线彩虹 ✓）
        float shell = (float) (radius * grow);
        tube(vc, m, shell, (float) length, false, age, descent, fire);
        // ② 内层芯（粗光才画 ✓；天降那档芯更粗 ✓）
        //    火系：芯更粗（0.42）+ 更高热 —— 作者要"射线要凝实，能看出是一条射线" ✓
        float coreFraction = fire ? 0.42F : (descent ? 0.45F : 0.30F);
        tube(vc, m, shell * coreFraction, (float) length, true, age, descent, fire);
        // ③ 两端的亮圈
        cap(vc, m, shell, 0.0F, age, fire);
        cap(vc, m, shell * 1.15F, (float) length, age, fire);
        pose.popPose();
    }

    /**
     * 光柱的起点：前射形态用**施法者的眼睛**（客户端实时位置 ✓）。
     *
     * <p>为什么不用实体自己的位置：非生物实体的位置在客户端是**3 tick 插值**的 ✗，
     * 拿它当起点的话，边跑边放会看到"光柱从身后追上来"✗。施法者就在本地时位置是精确的 ✓。
     */
    private static Vec3 originOf(TNLightBeamEntity beam) {
        if (beam.style() == TNLightBeamEntity.STYLE_RAY
                && beam.level().getEntity(beam.casterId()) instanceof LivingEntity caster) {
            return caster.getEyePosition();
        }
        return beam.position();
    }

    /**
     * 一根管：绕 Z 轴 {@link #SIDES} 段、沿 Z 从 0 拉到 {@code length} ✓。
     *
     * <p>每个面**正反都提交** ✓（{@code entityTranslucentEmissive} 开着背面剔除 ✗ ——
     * 光翼那轮就是因为绕序反了整片消失 ✗，这里干脆两面都画，从任何角度看都是实心 ✓）。
     *
     * @param white true = 画白芯（不彩虹 ✓）
     */
    private static void tube(VertexConsumer vc, Matrix4f m, float radius, float length,
                             boolean white, float age, boolean descent, boolean fire) {
        for (int i = 0; i < SIDES; i++) {
            double a0 = Math.PI * 2.0D * i / SIDES;
            double a1 = Math.PI * 2.0D * (i + 1) / SIDES;
            float x0 = (float) Math.cos(a0) * radius;
            float y0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius;
            float y1 = (float) Math.sin(a1) * radius;
            // 颜色：沿轴线走一整圈彩虹 ✓（白芯就纯白 ✓）
            // 火系的芯是**高热黄白**（不是纯白 ✗）；壳是橙红火焰渐变 ✓
            int[] c0 = white ? (fire ? new int[]{255, 241, 186} : new int[]{255, 255, 255})
                    : rainbow(0.0F + age * 0.02F, descent, fire);
            int[] c1 = white ? (fire ? new int[]{255, 250, 225} : new int[]{255, 255, 255})
                    : rainbow(1.0F + age * 0.02F, descent, fire);
            // 火系不透明度更高 ⇒ 看着"凝实"（作者明确要求 ✓）
            int alpha = white ? (fire ? 245 : 235) : (fire ? 230 : 205);
            quad(vc, m, x0, y0, 0.0F, x1, y1, 0.0F, x1, y1, length, x0, y0, length, c0, c1, alpha, true);
            quad(vc, m, x1, y1, 0.0F, x0, y0, 0.0F, x0, y0, length, x1, y1, length, c0, c1, alpha, false);
        }
    }

    /** 两端的一个"亮圈"（把管口堵上，看着像发光的口 ✓）。 */
    private static void cap(VertexConsumer vc, Matrix4f m, float radius, float z, float age, boolean fire) {
        int[] c = rainbow(0.5F + age * 0.02F, false, fire);
        double step = Math.PI * 2.0D / SIDES;
        for (int i = 0; i < SIDES; i++) {
            float x0 = (float) Math.cos(step * i) * radius;
            float y0 = (float) Math.sin(step * i) * radius;
            float x1 = (float) Math.cos(step * (i + 1)) * radius;
            float y1 = (float) Math.sin(step * (i + 1)) * radius;
            quad(vc, m, 0.0F, 0.0F, z, x0, y0, z, x1, y1, z, 0.0F, 0.0F, z, c, c, 190, true);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             int[] near, int[] far, int alpha, boolean flipUv) {
        vertex(vc, m, ax, ay, az, near, alpha, flipUv ? 0.0F : 1.0F);
        vertex(vc, m, bx, by, bz, near, alpha, flipUv ? 1.0F : 0.0F);
        vertex(vc, m, cx, cy, cz, far, alpha, flipUv ? 1.0F : 0.0F);
        vertex(vc, m, dx, dy, dz, far, alpha, flipUv ? 0.0F : 1.0F);
    }

    /** 顶点：{@code entityTranslucentEmissive} 用的是 NEW_ENTITY 格式 ⇒ uv/overlay/light/normal 都要给 ✓。 */
    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z,
                               int[] rgb, int alpha, float u) {
        vc.vertex(m, x, y, z)
                .color(rgb[0], rgb[1], rgb[2], alpha)
                .uv(u, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 0.0F, 1.0F)
                .endVertex();
    }

    /** 彩虹色 ✓ —— 色相直接用机制层那份（{@link TNLightBeamMechanics#rainbow} ✓，一处定义 ✓）；天降偏白金 ✓。 */
    private static int[] rainbow(float t, boolean descent, boolean fire) {
        if (fire) {
            // 火系射线：**橙红 -> 金黄** 的火焰渐变 ✓
            // ⚠️ 调用处传进来的 t 里已经含了 age*0.02 ⇒ 直接拿它做**沿轴抖动**，
            //    看起来像在烧 ✓（不需要额外的时间参数 ✓）
            float f = (float) (0.5D + 0.5D * Math.sin(t * 9.0D));
            return new int[]{
                    255,
                    (int) ((0.44F + 0.34F * f) * 255.0F),   // 0.44 ~ 0.78
                    (int) ((0.06F + 0.20F * f) * 255.0F)};  // 0.06 ~ 0.26
        }
        org.joml.Vector3f c = TNLightBeamMechanics.rainbow(t);
        float r = c.x, g = c.y, b = c.z;
        if (descent) {                            // 天降：往白金色靠 ✓
            r = Math.min(1.0F, r * 0.4F + 0.6F);
            g = Math.min(1.0F, g * 0.4F + 0.55F);
            b = Math.min(1.0F, b * 0.4F + 0.25F);
        }
        return new int[]{(int) (r * 255), (int) (g * 255), (int) (b * 255)};
    }
}
