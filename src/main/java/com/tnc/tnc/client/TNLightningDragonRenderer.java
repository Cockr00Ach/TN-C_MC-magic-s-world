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
        if (entity.coil()) {
            float fade = Math.min(1.0F, age / 8.0F);
            float remain = entity.life() - age;
            if (remain < 20.0F) {
                fade *= Math.max(0.0F, remain / 20.0F);
            }
            if (fade > 0.004F) {
                // ★ 第二批反馈（"太大只了，没有绕在身上的感觉"）：半径收到 ≈1.1 格、
                //   跨度 ≈3 格（头顶略高于人），圈数由几何自己按脊线长度反算（0.55 体型 ≈ 3.5 圈）⇒
                //   贴着身体一圈圈缠上去 ✓（原来是 1.57 格 + 2 圈的大蛇兜圈 ✗）
                double radius = 0.80D + 0.55D * scale;   // 0.30→0.97 / 0.55→1.10
                double height = 1.15D + 3.45D * scale;   // 0.30→2.19 / 0.55→3.05
                LightningDragonGeometry.coil(vc, m, seed, age, scale, theme,
                        0.85F * fade, camX, camY, camZ,
                        radius, height, age * 0.11D);
                // 脚下一圈小雷网（"雷龙给你让路"✓），随龙一起转
                LightningBoltGeometry.groundWeb(vc, m, 0.0D, 0.03D, 0.0D,
                        (float) (1.15D + 1.5D * scale), seed + 3L, theme, 0.32F * fade,
                        (float) (age * 0.07D), camX, camY, camZ, true);
                LightningBoltGeometry.groundFlash(vc, m, 0.0D, 0.03D, 0.0D,
                        (float) (0.9D + 1.2D * scale), theme, 0.20F * fade);
                // 头顶那点白：一眼看出龙首在哪 ✓
                LightningBoltGeometry.billboardFlash(vc, m, 0.0D, height + 0.35D, 0.0D,
                        (float) (0.65D + 0.8D * scale), theme, 0.28F * fade, camX, camY, camZ);
            }
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
}
