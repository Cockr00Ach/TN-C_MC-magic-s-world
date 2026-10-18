package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.fire.TNBurnAuraEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * 燃烧链起手时"围着自己的那圈火"（配套实体 {@link TNBurnAuraEntity}）。
 *
 * <p>作者 2026-10-11：「动画从火焰粒子变为出现火焰围绕，高等级法术的火焰规模大于低等级的，
 * <b>采用真实建模</b>」⇒ 这里画的是一圈<b>带贴图的几何体</b>（每簇火苗是两片十字交叉的竖面），
 * 不是 {@code ParticleTypes.FLAME} ✗。
 *
 * <h2>档位表（5 档，规模逐档变大）</h2>
 * <pre>
 *   档   火苗簇   圈半径   火苗高   火苗宽   内圈
 *   1     5      0.56     0.80     0.34     -
 *   2     7      0.67     0.97     0.39     -
 *   3     9      0.79     1.15     0.43     3
 *   4    11      0.90     1.32     0.47     5
 *   5    13      1.02     1.50     0.52     7
 * </pre>
 * 内圈是 t3 起在<b>贴身处</b>再点几簇更矮的火 —— 让人看着"整个人被火包住"，
 * 而不只是脚下一圈火 ✗。
 *
 * <h2>为什么用 {@code entityCutoutNoCull}</h2>
 * 贴图是像素火苗、边缘是硬边（和法术图标同一套调色板）⇒ 用 cutout 保留硬边、不做半透明排序；
 * {@code NoCull} 是因为两片十字面里总有一片背朝镜头，剔除掉就只剩一半火 ✓。
 * 亮度固定 {@link LightTexture#FULL_BRIGHT}（火自发光，洞里/夜里都亮）。
 */
public final class TNBurnAuraRenderer extends EntityRenderer<TNBurnAuraEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/burn_flame.png");

    /** 火苗贴图在竖面上的投影：v=1 是贴图底部（火根），v=0 是火尖。 */
    private static final float A = 0.95F;

    public TNBurnAuraRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TNBurnAuraEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(TNBurnAuraEntity entity, float yaw, float partial, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        Matrix4f pose = stack.last().pose();
        double age = entity.tickCount + partial;
        int tier = Math.max(1, Math.min(5, entity.tier()));

        int lobes = 3 + tier * 2;                 // 5 / 7 / 9 / 11 / 13
        double ringR = 0.44D + tier * 0.115D;     // 0.555 .. 1.015
        double baseH = 0.62D + tier * 0.175D;     // 0.795 .. 1.495
        double baseW = 0.30D + tier * 0.043D;     // 0.343 .. 0.515
        double spin = age * 0.045D;

        for (int k = 0; k < lobes; k++) {
            double a = spin + k * Math.PI * 2.0D / lobes;
            // 每簇各自呼吸：两个不同频率叠加，免得整圈火整齐地一起跳（像呼吸灯一样假）✗
            double flicker = 0.82D + 0.18D * Math.sin(age * 0.55D + k * 1.9D)
                    + 0.06D * Math.sin(age * 1.31D + k * 0.7D);
            double h = baseH * flicker;
            double w = baseW * (0.90D + 0.16D * Math.sin(age * 0.41D + k * 2.3D));
            // 火苗往外歪一点：直上直下像一圈蜡烛 ✗
            double lean = ringR * 0.16D;
            flame(out, pose, a, ringR, w, h, lean);
        }

        if (tier >= 3) {
            int inner = (tier - 1) * 2 - 1;       // 3 / 5 / 7
            double innerR = ringR * 0.42D;
            for (int k = 0; k < inner; k++) {
                double a = -spin * 1.6D + k * Math.PI * 2.0D / inner + 0.6D;
                double flicker = 0.86D + 0.20D * Math.sin(age * 0.67D + k * 2.1D);
                flame(out, pose, a, innerR, baseW * 0.86D, baseH * 0.72D * flicker, 0.0D);
            }
        }
    }

    /**
     * 一簇火 = 两片十字交叉的竖面。
     *
     * @param a     在圈上的方位角
     * @param ringR 距身体中轴的距离
     * @param lean  火尖往外偏移的距离（0 = 笔直向上）
     */
    private static void flame(VertexConsumer out, Matrix4f pose, double a, double ringR,
                              double w, double h, double lean) {
        double cx = Math.cos(a) * ringR;
        double cz = Math.sin(a) * ringR;
        // 径向单位向量（往外）与切向单位向量
        double rx = Math.cos(a);
        double rz = Math.sin(a);
        double tx = -rz;
        double tz = rx;
        double hw = w * 0.5D;

        // ① 切向面：正对圈外（从侧面看是薄薄一片）
        quad(out, pose,
                cx - tx * hw, 0.0D, cz - tz * hw,
                cx + tx * hw, 0.0D, cz + tz * hw,
                cx + tx * hw + rx * lean, h, cz + tz * hw + rz * lean,
                cx - tx * hw + rx * lean, h, cz - tz * hw + rz * lean);

        // ② 径向面：转 90°，从正面看是薄薄一片 —— 两片合起来任何角度都有火 ✓
        quad(out, pose,
                cx - rx * hw, 0.0D, cz - rz * hw,
                cx + rx * hw, 0.0D, cz + rz * hw,
                cx + rx * hw + rx * lean, h, cz + rz * hw + rz * lean,
                cx - rx * hw + rx * lean, h, cz - rz * hw + rz * lean);
    }

    /** 一个竖面：底边两点 + 顶边两点，UV 让火根贴 v=1、火尖贴 v=0。 */
    private static void quad(VertexConsumer out, Matrix4f pose,
                             double x0, double y0, double z0,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             double x3, double y3, double z3) {
        out.vertex(pose, (float) x0, (float) y0, (float) z0)
                .color(1.0F, 1.0F, 1.0F, A).uv(0.0F, 1.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F).endVertex();
        out.vertex(pose, (float) x1, (float) y1, (float) z1)
                .color(1.0F, 1.0F, 1.0F, A).uv(1.0F, 1.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F).endVertex();
        out.vertex(pose, (float) x2, (float) y2, (float) z2)
                .color(1.0F, 1.0F, 1.0F, A).uv(1.0F, 0.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F).endVertex();
        out.vertex(pose, (float) x3, (float) y3, (float) z3)
                .color(1.0F, 1.0F, 1.0F, A).uv(0.0F, 0.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F).endVertex();
    }
}
