package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import com.tnc.tnc.magic.TNMagicCircleEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * ★ <b>雷系法阵</b>（2026-11-05 第三批：作者「参考一下光法的施法前戏和法阵，照猫画虎一下」）。
 *
 * <h2>照的是哪只猫</h2>
 * 光系那一套（{@code ElementSealRenderer} ＋ {@code LightRitualSequence}）已经把
 * "低阶是符、高阶是仪式"讲清楚了：
 * <ul>
 *   <li>低阶：<b>一圈环 ＋ 几道光芒 ＋ 中心亮核</b>，没有外环/刻痕/压暗 ✓（{@link #STYLE_MINOR}）；</li>
 *   <li>高阶：外环 ＋ 24 道刻痕 ＋ 12 道髻须 ＋ 更强亮核 ✓（{@link #STYLE_GRAND}）。</li>
 * </ul>
 * 这里把"画"换成"雷"：所有环、辐条、刻痕、髻须都是
 * {@link LightningBoltGeometry} 的<b>真雷</b>（四层加色、白芯），
 * 每 2 tick 换一次种子 ⇒ 阵在"滋滋"地爬 ✓（不是一张静止贴图）。
 *
 * <h2>和 {@code STYLE_LIGHTNING}（7 号）的分工</h2>
 * 7 号是"老雷阵 ＋ 地面雷网"（作者最初要的蛛网感）；11/12 是<b>按档位分级的仪式阵</b>：
 * 低阶干脆不铺网 ✗，高阶靠外环与刻痕撑"重"，而不是靠一张网。
 * 两套并存，谁用哪套由铺阵方按档位决定 ✓。
 */
final class LightningSealRenderer {

    /** 低阶（t1/t2 主阵 ＋ 所有卫星小阵） */
    private static final int STYLE_MINOR = TNMagicCircleEntity.STYLE_LIGHTNING_MINOR;
    /** 高阶（t4/t5 主阵） */
    private static final int STYLE_GRAND = TNMagicCircleEntity.STYLE_LIGHTNING_GRAND;

    private LightningSealRenderer() {
    }

    static void render(TNMagicCircleEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffers) {
        float age = entity.tickCount + partialTick;
        float life = Math.max(1.0F, entity.life());
        float alpha = fade(age, life);
        if (alpha <= 0.01F) {
            return;
        }
        boolean grand = entity.style() == STYLE_GRAND;
        boolean minor = entity.style() == STYLE_MINOR;
        double radius = Math.max(0.2D, entity.radius());
        int theme = entity.theme();
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                com.tnc.tnc.TNMod.MODID, "textures/entity/magic_circle.png");

        // ---- 底图：那张雷法阵贴图（雷几何盖在它上面，一起读成"阵"） ----
        poseStack.pushPose();
        if (entity.upright()) {
            poseStack.mulPose(Axis.YP.rotationDegrees(entity.facingDegrees()));
            poseStack.mulPose(Axis.ZP.rotationDegrees(age * 1.6F));
        } else {
            poseStack.translate(0.0D, 0.03D, 0.0D);
            poseStack.mulPose(Axis.YP.rotationDegrees(age * 1.6F));
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        }
        Matrix4f quadMatrix = poseStack.last().pose();
        float qr = (float) radius;
        drawQuad(buffers.getBuffer(RenderType.entityTranslucentEmissive(texture)), quadMatrix, qr, alpha * 0.7F, false);
        drawQuad(buffers.getBuffer(RenderType.entityTranslucentEmissive(texture)), quadMatrix, qr, alpha * 0.7F, true);
        poseStack.popPose();

        // 竖着的阵（门环）到这里就够了：雷几何是地平面那套，竖着转不划算 ✗
        if (entity.upright()) {
            return;
        }

        // ---- 雷几何（地平面、实体原点坐标） ----
        Vec3 at = entity.getPosition(partialTick);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double cx = cam.x - at.x;
        double cy = cam.y - at.y;
        double cz = cam.z - at.z;
        VertexConsumer vc = buffers.getBuffer(LightningRenderTypes.BOLT);
        Matrix4f m = poseStack.last().pose();
        long seed = entity.getId() * 1315423911L + entity.tickCount / 2L;
        float a = alpha;
        double spin = age * 0.035D;

        // 内环：所有档位都有（低阶就靠它 ＋ 辐条 ＋ 亮核）
        ring(vc, m, radius * 0.62D, 0.05D, 18, seed + 11L, 0.040F, 0.60F * a, theme, -spin * 1.5D, cx, cy, cz);
        // 主环
        ring(vc, m, radius * 0.99D, 0.06D, minor ? 20 : 28, seed + 12L, minor ? 0.050F : 0.058F,
                0.85F * a, theme, spin, cx, cy, cz);
        // 高阶：外环 ＋ 24 道外侧刻痕 ＋ 12 道向外抬起的电须
        if (grand) {
            ring(vc, m, radius * 1.12D, 0.05D, 32, seed + 13L, 0.052F, 0.75F * a, theme, spin * 0.6D, cx, cy, cz);
            for (int i = 0; i < 24; i++) {
                double ang = i * (Math.PI * 2.0D / 24.0D) + spin * 0.6D;
                double r0 = radius * 1.12D;
                double r1 = radius * (i % 3 == 0 ? 1.24D : 1.18D);
                tick(vc, m, ang, r0, r1, 0.045F, 0.7F * a, theme, seed + 200L + i, cx, cy, cz);
            }
            for (int i = 0; i < 12; i++) {
                double ang = i * (Math.PI * 2.0D / 12.0D) - spin * 0.7D;
                double r = radius * 1.2D;
                LightningBoltGeometry.bolt(vc, m,
                        Math.cos(ang) * r, 0.06D, Math.sin(ang) * r,
                        Math.cos(ang) * (r + 0.35D), 0.55D + 0.35D * Math.sin(i * 1.9D),
                        Math.sin(ang) * (r + 0.35D),
                        seed + 400L + i, 0.05F, 0.55F * a, theme, 1, cx, cy, cz);
            }
        }
        // 辐条：低阶 6 道、高阶 12 道（从内环打到主环，稍微拱起来一点）
        int spokes = grand ? 12 : 6;
        for (int i = 0; i < spokes; i++) {
            double ang = i * (Math.PI * 2.0D / spokes) + spin * 0.4D;
            LightningBoltGeometry.bolt(vc, m,
                    Math.cos(ang) * radius * 0.30D, 0.10D, Math.sin(ang) * radius * 0.30D,
                    Math.cos(ang) * radius * 0.97D, 0.06D, Math.sin(ang) * radius * 0.97D,
                    seed + 500L + i, 0.045F, 0.50F * a, theme, 0, cx, cy, cz);
        }
        // 中心核：低阶小一点、高阶大一点
        LightningBoltGeometry.billboardFlash(vc, m, 0.0D, 0.12D, 0.0D,
                (float) (radius * (grand ? 0.26D : 0.18D)), theme, 0.55F * a, cx, cy, cz);
    }

    /** 一圈由短雷首尾相接的环 ✓ */
    private static void ring(VertexConsumer vc, Matrix4f m, double radius, double y, int segments,
                             long seed, float width, float alpha, int theme, double spin,
                             double camX, double camY, double camZ) {
        for (int i = 0; i < segments; i++) {
            double a0 = spin + i * (Math.PI * 2.0D / segments);
            double a1 = a0 + Math.PI * 2.0D / segments;
            LightningBoltGeometry.bolt(vc, m,
                    Math.cos(a0) * radius, y, Math.sin(a0) * radius,
                    Math.cos(a1) * radius, y, Math.sin(a1) * radius,
                    seed + i, width, alpha, theme, 0, camX, camY, camZ);
        }
    }

    /** 一道径向刻痕（外面那圈小齿） */
    private static void tick(VertexConsumer vc, Matrix4f m, double ang, double r0, double r1,
                             float width, float alpha, int theme, long seed,
                             double camX, double camY, double camZ) {
        LightningBoltGeometry.bolt(vc, m,
                Math.cos(ang) * r0, 0.05D, Math.sin(ang) * r0,
                Math.cos(ang) * r1, 0.05D, Math.sin(ang) * r1,
                seed, width, alpha, theme, 0, camX, camY, camZ);
    }

    /** 和 {@code TNMagicCircleRenderer} 同一套淡入淡出：先满亮，再 smoothstep 淡出 ✓ */
    private static float fade(float age, float life) {
        float hold = Math.max(15.0F, Math.min(life * 0.25F, 40.0F));
        hold = Math.min(hold, life * 0.5F);
        if (age <= hold) {
            return 1.0F;
        }
        float u = 1.0F - (age - hold) / Math.max(1.0F, life - hold);
        u = Math.max(0.0F, Math.min(1.0F, u));
        return u * u * (3.0F - 2.0F * u);
    }

    private static void drawQuad(VertexConsumer consumer, Matrix4f matrix, float r, float alpha, boolean flip) {
        float[][] corners = flip
                ? new float[][]{{-r, -r}, {-r, r}, {r, r}, {r, -r}}
                : new float[][]{{-r, -r}, {r, -r}, {r, r}, {-r, r}};
        float[][] uvs = {{0.0F, 0.0F}, {1.0F, 0.0F}, {1.0F, 1.0F}, {0.0F, 1.0F}};
        for (int i = 0; i < 4; i++) {
            consumer.vertex(matrix, corners[i][0], corners[i][1], 0.0F)
                    .color(1.0F, 1.0F, 1.0F, alpha)
                    .uv(uvs[i][0], uvs[i][1])
                    .overlayCoords(0)
                    .uv2(0xF000F0)
                    .normal(0.0F, 0.0F, flip ? -1.0F : 1.0F)
                    .endVertex();
        }
    }
}
