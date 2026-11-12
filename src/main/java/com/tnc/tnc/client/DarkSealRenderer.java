package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import com.tnc.tnc.client.lightning.LightningTheme;
import com.tnc.tnc.magic.TNMagicCircleEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * ★ <b>暗系仪式阵</b>（2026-11-05 第四批：作者「优化一下衍 boss 的施法 … 衍是有黑暗元素的」）。
 *
 * <h2>和雷系 / 光系那两套的区别</h2>
 * 光系是"日轮"（白金光、放射、亮核），雷系是"霆狱"（真雷、滋滋、白芯）；
 * 暗系要的是<b>"地上开了一个口子"</b>：
 * <ul>
 *   <li>阵心是一潭<b>黑</b>（{@code black_circle.png} 压暗，越靠近中心越黑 ✓）；</li>
 *   <li>环与辐条不是直的，是<b>往里勾的爪子</b>（每道两折，尖端偏紫 ✓）；</li>
 *   <li>整张阵在<b>慢慢收</b>：辐条末端每隔几 tick 往中心拉一截（{@code pull}）⇒ 像在把东西吸进去 ✓；</li>
 *   <li>配色只用两种：暗紫（{@link LightningTheme#DARK}）＋ 一点亮紫（{@link LightningTheme#VIOLET}），
 *       不做金色/蓝色 —— 那是雷法和光法的记号 ✗。</li>
 * </ul>
 *
 * <h2>样式</h2>
 * <ul>
 *   <li>{@code STYLE_DARK_MINOR}（13）：一圈主环 ＋ 6 道勾爪 ＋ 黑心，小技能的前戏；</li>
 *   <li>{@code STYLE_DARK_GRAND}（14）：加外环 ＋ 20 道刻痕 ＋ 12 道勾爪 ＋ 8 道内拉弧，大招的前戏。</li>
 * </ul>
 */
final class DarkSealRenderer {

    private static final int STYLE_MINOR = TNMagicCircleEntity.STYLE_DARK_MINOR;
    private static final int STYLE_GRAND = TNMagicCircleEntity.STYLE_DARK_GRAND;

    private static final ResourceLocation DARK_CIRCLE = ResourceLocation.fromNamespaceAndPath(
            com.tnc.tnc.TNMod.MODID, "textures/entity/dark_circle.png");
    private static final ResourceLocation BLACK_CIRCLE = ResourceLocation.fromNamespaceAndPath(
            com.tnc.tnc.TNMod.MODID, "textures/entity/black_circle.png");

    private DarkSealRenderer() {
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
        if (!grand && !minor) {
            return;
        }
        double radius = Math.max(0.2D, entity.radius());

        // ---- 底图：暗紫符文盘 ----
        poseStack.pushPose();
        if (entity.upright()) {
            poseStack.mulPose(Axis.YP.rotationDegrees(entity.facingDegrees()));
            poseStack.mulPose(Axis.ZP.rotationDegrees(age * 1.1F));
        } else {
            poseStack.translate(0.0D, 0.03D, 0.0D);
            poseStack.mulPose(Axis.YP.rotationDegrees(-age * 1.1F));
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        }
        Matrix4f quadMatrix = poseStack.last().pose();
        float qr = (float) radius;
        drawQuad(buffers.getBuffer(RenderType.entityTranslucentEmissive(DARK_CIRCLE)), quadMatrix, qr,
                alpha * 0.65F, false);
        drawQuad(buffers.getBuffer(RenderType.entityTranslucentEmissive(DARK_CIRCLE)), quadMatrix, qr,
                alpha * 0.65F, true);
        // 阵心压黑：一潭不反光的黑（只在平铺时画，竖着的门环不需要 ✗）
        if (!entity.upright()) {
            drawQuad(buffers.getBuffer(RenderType.entityTranslucentEmissive(BLACK_CIRCLE)), quadMatrix,
                    (float) (radius * 0.62D), alpha * 0.55F, false);
        }
        poseStack.popPose();

        // 竖着的门环：贴图就够（几何是地平面那套，立起来会翻 ✗）
        if (entity.upright()) {
            return;
        }

        Vec3 at = entity.getPosition(partialTick);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double cx = cam.x - at.x;
        double cy = cam.y - at.y;
        double cz = cam.z - at.z;
        VertexConsumer vc = buffers.getBuffer(LightningRenderTypes.BOLT);
        Matrix4f m = poseStack.last().pose();
        long seed = entity.getId() * 2654435761L + entity.tickCount / 3L;
        float a = alpha;
        double spin = -age * 0.028D;                 // 暗阵是**逆**着转的（和光/雷反着来 ✓）
        // "往里收"的节拍：0..1 一涨一落，辐条尖端跟着拉近 ⇒ 阵在吸东西 ✓
        double pull = 0.5D + 0.5D * Math.sin(age * 0.18D);

        // 内环（反向转）＋ 主环
        ring(vc, m, radius * 0.58D, 18, seed + 11L, 0.045F, 0.55F * a, LightningTheme.DARK,
                -spin * 1.6D, cx, cy, cz);
        ring(vc, m, radius * 0.99D, minor ? 24 : 30, seed + 12L, minor ? 0.052F : 0.060F,
                0.88F * a, LightningTheme.DARK, spin, cx, cy, cz);

        // 高阶：外环 ＋ 刻痕
        if (grand) {
            ring(vc, m, radius * 1.14D, 34, seed + 13L, 0.048F, 0.62F * a, LightningTheme.DARK,
                    spin * 0.5D, cx, cy, cz);
            for (int i = 0; i < 20; i++) {
                double ang = i * (Math.PI * 2.0D / 20.0D) + spin * 0.5D;
                double r0 = radius * 1.14D;
                double r1 = radius * (i % 4 == 0 ? 1.27D : 1.20D);
                LightningBoltGeometry.bolt(vc, m,
                        Math.cos(ang) * r0, 0.05D, Math.sin(ang) * r0,
                        Math.cos(ang) * r1, 0.05D, Math.sin(ang) * r1,
                        seed + 300L + i, 0.042F, 0.55F * a, LightningTheme.DARK, 0, cx, cy, cz);
            }
        }

        // 勾爪：从外圈往里卷（两折），尖端亮紫 ⇒ "影子伸进来了" ✓
        int claws = grand ? 12 : 6;
        double clawOuter = radius * (grand ? 1.10D : 0.96D);
        double clawMid = radius * (0.80D - 0.06D * pull);
        double clawTip = radius * (0.42D - 0.10D * pull);
        for (int i = 0; i < claws; i++) {
            double ang = i * (Math.PI * 2.0D / claws) + spin * 0.9D;
            double curl = 0.42D;                                  // 每折偏多少弧度
            double midAng = ang + curl;
            double tipAng = ang + curl * 2.1D;
            // 外段：暗紫，粗
            LightningBoltGeometry.bolt(vc, m,
                    Math.cos(ang) * clawOuter, 0.06D, Math.sin(ang) * clawOuter,
                    Math.cos(midAng) * clawMid, 0.05D, Math.sin(midAng) * clawMid,
                    seed + 500L + i, 0.048F, 0.62F * a, LightningTheme.DARK, 1, cx, cy, cz);
            // 内段：亮紫，细（尖端）
            LightningBoltGeometry.bolt(vc, m,
                    Math.cos(midAng) * clawMid, 0.05D, Math.sin(midAng) * clawMid,
                    Math.cos(tipAng) * clawTip, 0.04D, Math.sin(tipAng) * clawTip,
                    seed + 800L + i, 0.036F, 0.70F * a, LightningTheme.VIOLET, 0, cx, cy, cz);
        }

        // 高阶：8 道从主环往中心"拉"的弧（和 pull 同拍）
        if (grand) {
            for (int i = 0; i < 8; i++) {
                double ang = i * (Math.PI * 2.0D / 8.0D) - spin * 0.8D;
                double r0 = radius * 0.99D;
                double r1 = radius * (0.30D + 0.22D * pull);
                LightningBoltGeometry.arc(vc, m,
                        Math.cos(ang) * r0, 0.08D, Math.sin(ang) * r0,
                        Math.cos(ang + 0.5D) * r1, 0.10D, Math.sin(ang + 0.5D) * r1,
                        seed + 1100L + i, 0.040F, 0.48F * a, LightningTheme.VIOLET, cx, cy, cz);
            }
        }

        // ★ 横着走的黑线/血线（作者 2026-11-05："闪电要是横着来是不是就是黑线和血线了"）：
        //   低阶 3 条、高阶 5 条，从阵心附近甩到环外，一边转一边起伏 —— 阵上方像有几根"线"在游 ✓
        VertexConsumer dvc = buffers.getBuffer(LightningRenderTypes.DARK);
        int threads = grand ? 5 : 3;
        for (int i = 0; i < threads; i++) {
            double ang = i * (Math.PI * 2.0D / threads) + age * 0.012D;
            double h0 = 0.55D + 0.28D * Math.sin(age * 0.05D + i * 1.7D);
            double h1 = h0 + 0.25D * Math.sin(age * 0.041D + i * 2.3D);
            double r0 = radius * 0.12D;
            double r1 = radius * (0.92D + 0.06D * Math.sin(age * 0.03D + i));
            double mid = (r0 + r1) * 0.5D;
            // 两段折线 ⇒ 不是直棍，而是会拐的"线" ✓
            LightningBoltGeometry.darkBolt(dvc, m,
                    Math.cos(ang) * r0, h0, Math.sin(ang) * r0,
                    Math.cos(ang + 0.55D) * mid, h0 + 0.18D, Math.sin(ang + 0.55D) * mid,
                    seed + 1700L + i, 0.030F, 0.85F * a, cx, cy, cz);
            LightningBoltGeometry.darkBolt(dvc, m,
                    Math.cos(ang + 0.55D) * mid, h0 + 0.18D, Math.sin(ang + 0.55D) * mid,
                    Math.cos(ang + 0.95D) * r1, h1, Math.sin(ang + 0.95D) * r1,
                    seed + 1900L + i, 0.026F, 0.75F * a, cx, cy, cz);
        }

        // 阵心：一暗一亮两只"眼"（暗核吸光 ＋ 一点紫芯）
        LightningBoltGeometry.billboardFlash(vc, m, 0.0D, 0.10D, 0.0D,
                (float) (radius * (grand ? 0.20D : 0.14D)), LightningTheme.DARK, 0.62F * a, cx, cy, cz);
        LightningBoltGeometry.billboardFlash(vc, m, 0.0D, 0.14D, 0.0D,
                (float) (radius * 0.05D), LightningTheme.VIOLET, 0.85F * a, cx, cy, cz);
    }

    /** 一圈短雷首尾相接的环（暗阵也借这套几何，只是配色换成暗紫 ✓）。 */
    private static void ring(VertexConsumer vc, Matrix4f m, double radius, int segments,
                             long seed, float width, float alpha, int theme, double spin,
                             double camX, double camY, double camZ) {
        for (int i = 0; i < segments; i++) {
            double a0 = spin + i * (Math.PI * 2.0D / segments);
            double a1 = a0 + Math.PI * 2.0D / segments;
            LightningBoltGeometry.bolt(vc, m,
                    Math.cos(a0) * radius, 0.05D, Math.sin(a0) * radius,
                    Math.cos(a1) * radius, 0.05D, Math.sin(a1) * radius,
                    seed + i, width, alpha, theme, 0, camX, camY, camZ);
        }
    }

    /** 和另外两套阵同一套淡入淡出：先满亮，再 smoothstep 淡出 ✓ */
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
