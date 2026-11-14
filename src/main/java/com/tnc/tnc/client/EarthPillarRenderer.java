package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.boss.earth.TNEarthPillarEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 有血量的土柱（2026-11-15 土系 Boss 交互玩法）。
 *
 * <p>六棱石柱 + 岩缝金光：血量越低裂缝越亮越密 —— 玩家不用看数字也知道还剩多少 ✓
 * 走 {@link RenderType#lightning()}（自发光）是为了在昏暗的专属维度里也看得清。
 */
public final class EarthPillarRenderer extends EntityRenderer<TNEarthPillarEntity> {

    private static final int SIDES = 6;

    public EarthPillarRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(TNEarthPillarEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(TNEarthPillarEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        float health = Mth.clamp(entity.health() / TNEarthPillarEntity.PILLAR_HEALTH, 0.0F, 1.0F);
        float hurt = 1.0F - health;
        float time = (entity.tickCount + partialTick) * 0.05F;
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());

        pose.pushPose();
        PoseStack.Pose m = pose.last();

        // 柱身：上窄下宽的六棱柱，分 4 段
        double baseR = 1.15;
        double topR = 0.78;
        double height = 4.0;
        int seg = 4;
        for (int s = 0; s < seg; s++) {
            double t0 = s / (double) seg;
            double t1 = (s + 1) / (double) seg;
            double y0 = height * t0;
            double y1 = height * t1;
            double r0 = baseR + (topR - baseR) * t0;
            double r1 = baseR + (topR - baseR) * t1;
            double tint = 0.42 + 0.10 * s;
            for (int i = 0; i < SIDES; i++) {
                double a0 = Math.PI * 2 * i / SIDES + time * 0.2;
                double a1 = Math.PI * 2 * (i + 1) / SIDES + time * 0.2;
                double[] p0 = {Math.cos(a0) * r0, y0, Math.sin(a0) * r0};
                double[] p1 = {Math.cos(a1) * r0, y0, Math.sin(a1) * r0};
                double[] p2 = {Math.cos(a1) * r1, y1, Math.sin(a1) * r1};
                double[] p3 = {Math.cos(a0) * r1, y1, Math.sin(a0) * r1};
                quad(vc, m, p0, p1, p2, p3, (float) tint, (float) (tint * 0.86), (float) (tint * 0.68), 1.0F);
            }
        }
        // 顶面
        for (int i = 0; i < SIDES; i++) {
            double a0 = Math.PI * 2 * i / SIDES + time * 0.2;
            double a1 = Math.PI * 2 * (i + 1) / SIDES + time * 0.2;
            double[] c = {0, height, 0};
            double[] p1 = {Math.cos(a0) * topR, height, Math.sin(a0) * topR};
            double[] p2 = {Math.cos(a1) * topR, height, Math.sin(a1) * topR};
            tri(vc, m, c, p1, p2, 0.52F, 0.46F, 0.36F, 1.0F);
        }
        // 岩缝：血量越低越亮越多
        int cracks = 3 + (int) (hurt * 7);
        for (int i = 0; i < cracks; i++) {
            double a = Math.PI * 2 * i / cracks + time * 0.7;
            double y0 = 0.3 + (i % 3) * 0.9;
            double y1 = y0 + 0.7 + 0.4 * (i % 2);
            double r = baseR * 0.99;
            double[] from = {Math.cos(a) * r, y0, Math.sin(a) * r};
            double[] to = {Math.cos(a + 0.12) * r * 0.92, y1, Math.sin(a + 0.12) * r * 0.92};
            beam(vc, m, from, to, 0.05 + 0.05 * hurt, 1.0F, 0.72F + 0.2F * hurt, 0.28F);
        }
        pose.popPose();
    }

    private static void tri(VertexConsumer vc, PoseStack.Pose m, double[] a, double[] b, double[] c,
                            float r, float g, float bl, float alpha) {
        vert(vc, m, a, r, g, bl, alpha);
        vert(vc, m, b, r, g, bl, alpha);
        vert(vc, m, c, r, g, bl, alpha);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose m, double[] a, double[] b, double[] c, double[] d,
                             float r, float g, float bl, float alpha) {
        vert(vc, m, a, r, g, bl, alpha);
        vert(vc, m, b, r, g, bl, alpha);
        vert(vc, m, c, r, g, bl, alpha);
        vert(vc, m, d, r, g, bl, alpha);
    }

    private static void beam(VertexConsumer vc, PoseStack.Pose m, double[] a, double[] b, double width,
                             float r, float g, float bl) {
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) {
            return;
        }
        double nx = -dz / len * width, nz = dx / len * width;
        quad(vc, m,
                new double[]{a[0] + nx, a[1] + dy / len * 0, a[2] + nz},
                new double[]{a[0] - nx, a[1], a[2] - nz},
                new double[]{b[0] - nx, b[1], b[2] - nz},
                new double[]{b[0] + nx, b[1], b[2] + nz},
                r, g, bl, 1.0F);
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose m, double[] p, float r, float g, float b, float a) {
        vc.vertex(m.pose(), (float) p[0], (float) p[1], (float) p[2])
                .color((int) (Mth.clamp(r, 0, 1) * 255), (int) (Mth.clamp(g, 0, 1) * 255),
                        (int) (Mth.clamp(b, 0, 1) * 255), (int) (Mth.clamp(a, 0, 1) * 255))
                .endVertex();
    }
}
