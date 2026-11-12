package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.client.dragon.LightningDragonGeometry;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import com.tnc.tnc.client.lightning.LightningTheme;
import com.tnc.tnc.magic.TNLightningDragonEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * <b>雷龙渲染器</b>（作者 2026-11-05："砸中的地面有龙直冲云霄"）。
 *
 * <p>时间轴（{@code age = tickCount - DATA_DELAY}）：
 * <ul>
 *   <li><b>age &lt; 0</b> —— 蓄力：地上只有一圈雷网 ＋ 越来越亮的地光（"地下有东西"✓）；</li>
 *   <li><b>age 0~78</b> —— 破土直冲：龙头沿螺旋窜到 50 多格高，龙身从地里一节节拖出来 ✓；</li>
 *   <li><b>age &gt; 78</b> —— 在云里盘住、慢慢淡出（最后 25 tick）✓。</li>
 * </ul>
 *
 * <p>所有部件都由 {@link LightningDragonGeometry} 现画：它返回龙头的位置，
 * 这里顺手在龙头上方点一团光斑 —— 云被龙照亮的那一下 ✓（只有加色光画得出来）。
 */
public class TNLightningDragonRenderer extends EntityRenderer<TNLightningDragonEntity> {

    public TNLightningDragonRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TNLightningDragonEntity entity) {
        return LightningRenderTypes.WHITE_TEXTURE;
    }

    @Override
    public boolean shouldRender(TNLightningDragonEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        return entity.distanceToSqr(camX, camY, camZ) < 131072.0D;
    }

    @Override
    public void render(TNLightningDragonEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        double scale = Math.max(0.4D, entity.scale());
        int theme = LightningTheme.clamp(entity.theme());
        int delay = entity.delay();
        float age = entity.tickCount + partialTick - delay;
        int visibleLife = Math.max(20, entity.life() - delay);

        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 at = entity.getPosition(partialTick);
        double camX = cam.x - at.x;
        double camY = cam.y - at.y;
        double camZ = cam.z - at.z;

        Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = buffer.getBuffer(LightningRenderTypes.BOLT);
        long seed = entity.seed();

        // ---- ★ 缠绕形态（登神链 t5）：绕着人一条螺旋，一直在游 ✓ ----
        //   ★ 2026-11-06：几何搬去 {@link CoilDragonOverlay}（{@code AFTER_ENTITIES} 里画）——
        //     作者：「缠绕着玩家的龙模型加载怎么在玩家模型之后，这样从侧面看龙始终在玩家模型背后
        //     没有绕着的感觉」。加色层不写深度，在实体层里先画就会被玩家盖掉 ✗；
        //     改成"所有实体画完之后再补画"，深度测试才会自己把后半圈挡掉 ✓
        if (entity.coil()) {
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }

        // ---- ★ 2026-11-05 第四批：俯冲形态（雷球链 t4/t5）----
        //   作者："一条龙从天上飞进地下，身后跟着的是我们的巨大雷球" ✓
        //   实体位置 = 落点；龙身被 poseStack 抬到 diveHeight 高处、整条倒过来（头朝下），
        //   再按 age 一路降到 0 ⇒ 最后正好"扎进地里" ✓
        if (entity.dive()) {
            double height = entity.diveHeight();
            float total = Math.max(1.0F, entity.life());
            float p = Math.min(1.0F, age / total);
            double y = height * (1.0D - p);
            float fade = Math.min(1.0F, age / 4.0F);
            float remain = entity.life() - age;
            if (remain < 10.0F) {
                fade *= Math.max(0.0F, remain / 10.0F);
            }
            if (fade <= 0.004F) {
                super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
                return;
            }
            double dcamY = camY - y;   // 龙身被抬起来了 ⇒ 相机相对向量也要跟着抬 ✓

            poseStack.pushPose();
            poseStack.translate(0.0D, y, 0.0D);
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180.0F));   // 头朝下
            Matrix4f diveMatrix = poseStack.last().pose();
            LightningDragonGeometry.dragon(vc, diveMatrix, seed, Math.max(0.0F, age),
                    scale, theme, 0.92F * fade, camX, dcamY, camZ);
            poseStack.popPose();

            // 身后那一串巨大雷球：沿同一条下落线，一颗比一颗高、比龙慢半拍 ✓
            int balls = scale >= 1.0D ? 5 : 4;
            for (int i = 0; i < balls; i++) {
                double lag = 0.09D * (i + 1);
                double bp = Math.max(0.0D, p - lag);
                double by = height * (1.0D - bp) + 1.2D * (i + 1);
                double wobble = 0.55D * scale * (i + 1) * 0.35D;
                double bx = Math.sin(age * 0.06D + i * 1.7D) * wobble;
                double bz = Math.cos(age * 0.05D + i * 1.3D) * wobble;
                lightningBall(vc, m, bx, by, bz, (float) (1.5D * scale + 0.3D), theme,
                        fade * (1.0F - 0.10F * i), seed + 900L + i, camX, camY, camZ);
            }

            // 落点：被"吸"起来的一圈电 ＋ 地光（越接近落地越亮 ✓）
            float suck = Math.min(1.0F, p * 2.5F);
            LightningBoltGeometry.groundWeb(vc, m, 0.0D, 0.03D, 0.0D,
                    (float) (2.4D + 2.4D * scale) * (0.5F + 0.5F * suck), seed + 7L, theme,
                    0.55F * fade * suck, (float) (age * 0.06D), camX, camY, camZ, true);
            LightningBoltGeometry.groundFlash(vc, m, 0.0D, 0.03D, 0.0D,
                    (float) (1.8D + 2.2D * scale) * suck, theme, 0.40F * fade * suck);

            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }

        // ---- 蓄力：地上那圈雷网 + 地光（age < 0）----
        if (age < 0.0F) {
            float charge = Math.min(1.0F, (entity.tickCount + partialTick) / Math.max(1.0F, delay));
            float a = 0.25F + 0.55F * charge;
            LightningBoltGeometry.groundWeb(vc, m, 0.0D, 0.02D, 0.0D,
                    (float) (2.0D + 3.2D * charge) * (float) scale, seed, theme, a,
                    (float) (age * 0.05F), camX, camY, camZ, true);
            LightningBoltGeometry.groundFlash(vc, m, 0.0D, 0.02D, 0.0D,
                    (float) (1.0D + 3.0D * charge) * (float) scale, theme, a * 0.9F);
            // 几道从地底往上"顶"的电弧（"要出来了"✓）
            for (int i = 0; i < 5; i++) {
                double ang = i * 1.256D + age * 0.3D;
                double r = 1.2D * scale;
                LightningBoltGeometry.arc(vc, m,
                        Math.cos(ang) * r, 0.05D, Math.sin(ang) * r,
                        Math.cos(ang + 0.4D) * r * 0.5D, (1.5D + 2.5D * charge) * scale,
                        Math.sin(ang + 0.4D) * r * 0.5D,
                        seed + i * 41L, 0.06F * (float) scale, a * 0.7F, theme, camX, camY, camZ);
            }
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }

        // ---- 淡入淡出 ----
        float fade = Math.min(1.0F, age / 5.0F);
        float remain = visibleLife - age;
        if (remain < 25.0F) {
            fade *= Math.max(0.0F, remain / 25.0F);
        }
        // 刚破土那几 tick 额外爆亮
        float burst = age < 7.0F ? (1.0F - age / 7.0F) * 0.5F : 0.0F;
        float alpha = Math.min(1.0F, 0.80F * fade + burst);

        // 破土那一瞬：地上一圈白光把龙"喷"出来 ✓
        // ★ 2026-11-05：从 14 tick 拉长到 26 tick —— 龙 1 秒内就窜到二十几格，
        //   地上那圈电要是立刻消失，观感是"龙跑了、地面干干净净" ✗；留一会儿才像被劈开过 ✓
        if (age < 26.0F) {
            float k = 1.0F - age / 26.0F;
            LightningBoltGeometry.groundWeb(vc, m, 0.0D, 0.02D, 0.0D,
                    (float) (4.5D + age * 0.35D) * (float) scale, seed + 5L, theme,
                    k * 0.9F, (float) (age * 0.06F), camX, camY, camZ, true);
            LightningBoltGeometry.groundFlash(vc, m, 0.0D, 0.02D, 0.0D,
                    (float) (2.5D + age * 0.32D) * (float) scale, theme, k * 0.85F);
            // 内圈再来一层慢一点的余电（叠出"雷池还没散"的层次 ✓）
            if (age > 8.0F) {
                float k2 = Math.max(0.0F, 1.0F - (age - 8.0F) / 18.0F);
                LightningBoltGeometry.groundWeb(vc, m, 0.0D, 0.03D, 0.0D,
                        (float) 2.4D * (float) scale, seed + 77L, theme,
                        k2 * 0.45F, (float) (-age * 0.09F), camX, camY, camZ, false);
            }
        }

        // ---- 龙本体 ----
        Vector3f head = LightningDragonGeometry.dragon(vc, m, seed, Math.max(0.0F, age),
                scale, theme, alpha, camX, camY, camZ);

        // ---- 龙头上方那团被照亮的云（只在前 40 tick 明显）----
        float cloud = Math.max(0.0F, 1.0F - age / 55.0F) * 0.20F * fade;
        if (cloud > 0.004F) {
            LightningBoltGeometry.billboardFlash(vc, m,
                    head.x, head.y + 3.0D * scale, head.z, (float) (4.0D + 3.0D * scale),
                    theme, cloud, camX, camY, camZ);
        }
        // 龙眼位置的一点点白（远看也认得出"头在哪"✓）
        LightningBoltGeometry.billboardFlash(vc, m, head.x, head.y + 0.4D * scale, head.z,
                (float) (0.8D * scale), theme, 0.35F * fade, camX, camY, camZ);

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * ★ 一颗<b>巨大雷球</b>（俯冲形态身后拖着的那串）。
     *
     * <p>用纯几何画，不加载 {@code lightingball_2} 模型：两层朝向镜头的加色光斑
     * （外层主题色壳 ＋ 内层白核）＋ 8 道沿球面爬的电须 ⇒ 远看就是一颗"滋滋响的电球" ✓。
     * 这样也绕开了引擎 {@code CustomModels} 那套 {@code translate(-0.5,-0.5,-0.5)} 的坑 ✗。
     */
    private static void lightningBall(VertexConsumer vc, Matrix4f m, double x, double y, double z, float r,
                                      int theme, float alpha, long seed,
                                      double camX, double camY, double camZ) {
        if (r <= 0.05F || alpha <= 0.004F) {
            return;
        }
        // 外壳（大、淡）＋ 中层（主题色、实）＋ 白核（小、最亮）
        LightningBoltGeometry.billboardFlash(vc, m, x, y, z, r * 1.45F, theme, 0.42F * alpha, camX, camY, camZ);
        LightningBoltGeometry.billboardFlash(vc, m, x, y, z, r * 1.02F, theme, 0.80F * alpha, camX, camY, camZ);
        LightningBoltGeometry.billboardFlash(vc, m, x, y, z, r * 0.48F, theme, alpha, camX, camY, camZ);
        // 球面上爬的电须：从球心附近打到球面，随机偏转 ⇒ "电在球里滚"
        for (int i = 0; i < 8; i++) {
            double a0 = seed * 0.017D + i * 0.7854D;
            double a1 = a0 + 0.7D + (i % 3) * 0.25D;
            double rr0 = r * 0.30D;
            double rr1 = r * (0.72D + (i % 2) * 0.22D);
            LightningBoltGeometry.arc(vc, m,
                    x + Math.cos(a0) * rr0, y + Math.sin(a0 * 1.7D) * rr0, z + Math.sin(a0) * rr0,
                    x + Math.cos(a1) * rr1, y + Math.sin(a1 * 1.7D) * rr1, z + Math.sin(a1) * rr1,
                    seed + i * 97L, 0.10F * r * 0.35F, 0.70F * alpha, theme, camX, camY, camZ);
        }
    }
}
