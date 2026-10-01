package com.tnc.tnc.light.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.light.TNLightBeamEntity;
import com.tnc.tnc.light.TNLightBeamMechanics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * <b>实体光线的渲染</b> ✓ —— 用**一整根棱柱**画出来（不是粒子 ✗），
 * 颜色沿轴线走彩虹 ✓，外面一层彩色壳 + 里面一根白芯 ✓（作者 2026-10-01："全部加粗"✓）。
 *
 * <h2>为什么用 {@code RenderType.lightning()}</h2>
 * 它是原版闪电用的那层：<b>顶点色 + 半透明 + 不吃光照</b> ✓，正好是"发光的光柱"要的效果 ✓，
 * 而且**不需要贴图** ✓（只要 {@code POSITION_COLOR} 顶点 ✓）—— 省一张素材、也省去 UV 的麻烦 ✓。
 *
 * <h2>几何</h2>
 * 本地空间里光柱从 {@code (0,0,0)} 沿着 <b>+Z</b> 长 {@code length} 格 ✓，
 * 实体自己的 {@code yRot/xRot} 已经把朝向摆好了 ✓（{@code pose} 进来时就是本地空间 ✓）。
 * 柱身绕 Z 轴分 {@link #SIDES} 段 ⇒ 侧面看是圆的 ✓，段数固定，半径只影响缩放 ✓。
 * 两端各画一个亮圈（{@code cap}）当"口" ✓。
 */
public class TNLightBeamRenderer extends EntityRenderer<TNLightBeamEntity> {

    /** 柱身分几段（越大越圆 ✓；12 段在这个粗细下已经看不出棱 ✓）。 */
    private static final int SIDES = 12;

    public TNLightBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    /** 不用贴图（顶点色 ✓）—— 但父类要求一个，随便给个图集位置 ✓。 */
    @Override
    public ResourceLocation getTextureLocation(TNLightBeamEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
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

        pose.pushPose();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();

        // ① 外层彩色壳（沿轴线彩虹 ✓）
        float shell = (float) (radius * grow);
        tube(vc, m, shell, (float) length, false, age, descent);
        // ② 内层白芯（粗光才画 ✓；天降那档芯更粗 ✓）
        float coreFraction = descent ? 0.45F : 0.30F;
        tube(vc, m, shell * coreFraction, (float) length, true, age, descent);
        // ③ 两端的亮圈
        cap(vc, m, shell, 0.0F, age);
        cap(vc, m, shell * 1.15F, (float) length, age);
        pose.popPose();
    }

    /**
     * 一根管：绕 Z 轴 {@link #SIDES} 段、沿 Z 从 0 拉到 {@code length} ✓。
     *
     * @param white true = 画白芯（不彩虹 ✓）
     */
    private static void tube(VertexConsumer vc, Matrix4f m, float radius, float length,
                             boolean white, float age, boolean descent) {
        for (int i = 0; i < SIDES; i++) {
            double a0 = Math.PI * 2.0D * i / SIDES;
            double a1 = Math.PI * 2.0D * (i + 1) / SIDES;
            float x0 = (float) Math.cos(a0) * radius;
            float y0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius;
            float y1 = (float) Math.sin(a1) * radius;
            // 颜色：沿轴线走一整圈彩虹 ✓（白芯就纯白 ✓）
            int[] c0 = white ? new int[]{255, 255, 255} : rainbow(0.0F + age * 0.02F, descent);
            int[] c1 = white ? new int[]{255, 255, 255} : rainbow(1.0F + age * 0.02F, descent);
            int a0i = white ? 235 : 200;
            // 侧面（正反两面都提交 ✓ —— 从任何角度看都实心 ✓）
            quad(vc, m, x0, y0, 0.0F, x1, y1, 0.0F, x1, y1, length, x0, y0, length, c0, c1, a0i);
            quad(vc, m, x1, y1, 0.0F, x0, y0, 0.0F, x0, y0, length, x1, y1, length, c0, c1, a0i);
        }
    }

    /** 两端的一个"亮圈"（把管口堵上，看着像发光的口 ✓）。 */
    private static void cap(VertexConsumer vc, Matrix4f m, float radius, float z, float age) {
        int[] c = rainbow(0.5F + age * 0.02F, false);
        double step = Math.PI * 2.0D / SIDES;
        for (int i = 0; i < SIDES; i++) {
            float x0 = (float) Math.cos(step * i) * radius;
            float y0 = (float) Math.sin(step * i) * radius;
            float x1 = (float) Math.cos(step * (i + 1)) * radius;
            float y1 = (float) Math.sin(step * (i + 1)) * radius;
            quad(vc, m, 0.0F, 0.0F, z, x0, y0, z, x1, y1, z, 0.0F, 0.0F, z, c, c, 190);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             int[] near, int[] far, int alpha) {
        vertex(vc, m, ax, ay, az, near, alpha);
        vertex(vc, m, bx, by, bz, near, alpha);
        vertex(vc, m, cx, cy, cz, far, alpha);
        vertex(vc, m, dx, dy, dz, far, alpha);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, int[] rgb, int alpha) {
        vc.vertex(m, x, y, z).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
    }

    /** 彩虹色 ✓ —— 色相直接用机制层那份（{@link TNLightBeamMechanics#rainbow} ✓，一处定义 ✓）；天降偏白金 ✓。 */
    private static int[] rainbow(float t, boolean descent) {
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
