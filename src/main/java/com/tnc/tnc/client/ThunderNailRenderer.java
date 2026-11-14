package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.boss.TNThunderNailEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 雷锲：一枚<b>插在地上的雷楔</b>（2026-11-15 衍的交互玩法）。
 *
 * <p>几何现场生成，不用模型文件：底部一圈雷印 → 三角楔身 → 顶端电弧。
 * 血量越低越亮、裂纹越明显 —— 玩家不用看数字也知道快打碎了 ✓
 * 走 {@link RenderType#lightning()}（自发光、加色），雷系在暗处也看得清。
 */
public final class ThunderNailRenderer extends EntityRenderer<TNThunderNailEntity> {

    public ThunderNailRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(TNThunderNailEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(TNThunderNailEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        float health = Mth.clamp(entity.health() / TNThunderNailEntity.NAIL_HEALTH, 0.0F, 1.0F);
        float hurt = 1.0F - health;                       // 0 = 满血，1 = 快碎
        float time = (entity.tickCount + partialTick) * 0.12F;
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());

        pose.pushPose();
        pose.translate(0.0F, 0.0F, 0.0F);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(time * 40.0F));
        PoseStack.Pose m = pose.last();

        // 地面雷印（八边形薄环）
        ring(vc, m, 0.0, 0.62F, 0.30F, 0.55F, 0.85F, 0.45F, 0.9F);
        // 契身：四棱锥（底宽 0.34，高 1.55）
        double base = 0.34;
        double top = 1.55;
        double[][] square = {{-base, 0.02, -base}, {base, 0.02, -base}, {base, 0.02, base}, {-base, 0.02, base}};
        for (int i = 0; i < 4; i++) {
            double[] a = square[i];
            double[] b = square[(i + 1) % 4];
            tri(vc, m, a, b, new double[]{0.0, top, 0.0}, 1.0F, 0.72F + 0.28F * hurt, 0.22F);
        }
        // 裂纹：血量越低越亮越多
        int cracks = 2 + (int) (hurt * 5);
        for (int i = 0; i < cracks; i++) {
            double angle = Math.PI * 2 * i / cracks + time * 0.4;
            double r = base * (0.5 + 0.15 * Math.sin(time + i));
            double[] from = {Math.cos(angle) * r, 0.15 + 0.22 * i, Math.sin(angle) * r};
            double[] to = {Math.cos(angle) * r * 0.4, 0.5 + 0.30 * i, Math.sin(angle) * r * 0.4};
            beam(vc, m, from, to, 0.02 + 0.02 * hurt, 0.75F, 0.95F, 1.0F);
        }
        // 顶端电弧
        for (int i = 0; i < 3; i++) {
            double a0 = time * 2.2 + i * 2.1;
            double[] from = {0.0, top, 0.0};
            double[] to = {Math.cos(a0) * 0.42, top + 0.30 + 0.12 * Math.sin(time * 3 + i),
                    Math.sin(a0) * 0.42};
            beam(vc, m, from, to, 0.025, 0.85F, 0.95F, 1.0F);
        }
        pose.popPose();
    }

    /** 地面一圈：把环拆成 8 段四边形。 */
    private static void ring(VertexConsumer vc, PoseStack.Pose m, double y, double r, double w,
                             float red, float green, float blue, float alpha) {
        int n = 12;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n;
            double a1 = Math.PI * 2 * (i + 1) / n;
            double[] p0 = {Math.cos(a0) * r, y, Math.sin(a0) * r};
            double[] p1 = {Math.cos(a1) * r, y, Math.sin(a1) * r};
            double[] p2 = {Math.cos(a1) * (r + w), y, Math.sin(a1) * (r + w)};
            double[] p3 = {Math.cos(a0) * (r + w), y, Math.sin(a0) * (r + w)};
            quad(vc, m, p0, p1, p2, p3, red, green, blue, alpha * 0.5F);
        }
    }

    private static void tri(VertexConsumer vc, PoseStack.Pose m, double[] a, double[] b, double[] c,
                            float red, float green, float blue) {
        vert(vc, m, a, red, green, blue, 0.55F);
        vert(vc, m, b, red, green, blue, 0.55F);
        vert(vc, m, c, red * 1.2F, green, blue, 0.75F);
        // 背面
        vert(vc, m, c, red, green, blue, 0.45F);
        vert(vc, m, b, red, green, blue, 0.45F);
        vert(vc, m, a, red, green, blue, 0.45F);
    }

    private static void beam(VertexConsumer vc, PoseStack.Pose m, double[] a, double[] b, double width,
                             float red, float green, float blue) {
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) {
            return;
        }
        double nx = -dy / len * width, ny = dx / len * width, nz = 0.0;
        double[] a0 = {a[0] + nx, a[1] + ny, a[2] + nz};
        double[] a1 = {a[0] - nx, a[1] - ny, a[2] - nz};
        double[] b0 = {b[0] + nx, b[1] + ny, b[2] + nz};
        double[] b1 = {b[0] - nx, b[1] - ny, b[2] - nz};
        quad(vc, m, a0, a1, b1, b0, red, green, blue, 0.95F);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose m, double[] a, double[] b, double[] c, double[] d,
                             float red, float green, float blue, float alpha) {
        vert(vc, m, a, red, green, blue, alpha);
        vert(vc, m, b, red, green, blue, alpha);
        vert(vc, m, c, red, green, blue, alpha);
        vert(vc, m, d, red, green, blue, alpha);
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose m, double[] p, float r, float g, float b, float a) {
        vc.vertex(m.pose(), (float) p[0], (float) p[1], (float) p[2])
                .color((int) (Mth.clamp(r, 0, 1) * 255), (int) (Mth.clamp(g, 0, 1) * 255),
                        (int) (Mth.clamp(b, 0, 1) * 255), (int) (Mth.clamp(a, 0, 1) * 255))
                .endVertex();
    }
}
