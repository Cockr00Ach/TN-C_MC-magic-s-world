package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import com.tnc.tnc.client.lightning.LightningTheme;
import com.tnc.tnc.magic.TNLightningAuraEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * 施法者身上的<b>雷笼</b>渲染器（作者 2026-11-05："自身周围的特效" / "蛛网一样的雷网"）。
 *
 * <p>全部现画，不用模型：
 * <ul>
 *   <li>{@link LightningBoltGeometry#cage} —— 十根竖弧 ＋ 三道横环 ＋ 八条斜拉弧；</li>
 *   <li>{@link LightningBoltGeometry#groundWeb} —— 脚下一张更密的蛛网；</li>
 *   <li>{@link LightningBoltGeometry#arc} —— 从笼子跳到身上的几道短电弧。</li>
 * </ul>
 *
 * <h2>和落雷"同拍"</h2>
 * 雷暴是每 15 tick 劈一轮（{@code STORM_INTERVAL}）✓ —— 雷笼前 5 tick 亮、其余时间暗一半，
 * 看上去就是"天上劈一下，身上这圈也跟着鼓一下" ✓，而不是一盏匀速呼吸的灯 ✗。
 *
 * <h2>为什么形状每 2 tick 换一次</h2>
 * 同一条折线一直不动会像霓虹灯管 ✗。用 {@code getId()*31 + age/2} 当种子 ⇒
 * 每 2 tick 重新抖一批点，观感就是"电流在骨架上乱爬" ✓（一直每帧随机会闪成噪点 ✗）。
 */
public class TNLightningAuraRenderer extends EntityRenderer<TNLightningAuraEntity> {

    public TNLightningAuraRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TNLightningAuraEntity entity) {
        return LightningRenderTypes.WHITE_TEXTURE;
    }

    @Override
    public boolean shouldRender(TNLightningAuraEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        return entity.distanceToSqr(camX, camY, camZ) < 32768.0D;
    }

    @Override
    public void render(TNLightningAuraEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float age = entity.tickCount + partialTick;
        int life = entity.life();
        if (age > life + 1.0F) {
            return;
        }
        // 出场 4 tick 渐亮、最后 12 tick 渐暗（消失不能"啪一下没了" ✗）
        float fade = Math.min(1.0F, age / 4.0F);
        float remain = life - age;
        if (remain < 12.0F) {
            fade *= Math.max(0.0F, remain / 12.0F);
        }
        // 施法那一瞬的爆闪（笼子突然"炸亮"一下，然后落到常驻亮度 ✓）
        float burst = age < 6.0F ? (1.0F - age / 6.0F) * 0.55F : 0.0F;

        float pulse;
        if (entity.style() == TNLightningAuraEntity.STYLE_FIELD) {
            pulse = 0.62F + 0.38F * Math.max(0.0F, Mth.sin(age * 0.42F));
        } else {
            // 15 tick 一轮：前 5 tick 亮（正好盖住刚落下来的那几道雷 ✓）
            float phase = age % 15.0F;
            pulse = phase < 5.0F ? 1.0F : 0.58F;
        }
        float alpha = (0.50F + burst) * fade * pulse;
        if (alpha <= 0.004F) {
            return;
        }

        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 at = entity.getPosition(partialTick);
        double camX = cam.x - at.x;
        double camY = cam.y - at.y;
        double camZ = cam.z - at.z;

        int theme = LightningTheme.clamp(entity.theme());
        double radius = Math.max(1.0D, entity.radius());
        double height = Math.max(1.0D, entity.height());
        // 每 2 tick 换一批形状（见类注释）✓
        long shapeSeed = entity.getId() * 31L + (long) (age / 2.0F);

        Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = buffer.getBuffer(LightningRenderTypes.BOLT);

        // ① 笼子主体（竖弧 + 横环 + 斜拉弧）
        LightningBoltGeometry.cage(vc, m, 0.0D, 0.02D, 0.0D,
                (float) radius, (float) height, shapeSeed, theme, alpha * 0.95F,
                (float) (age * 0.02F), camX, camY, camZ);
        // ② 脚下一张蛛网（和法阵呼应 ✓）
        LightningBoltGeometry.groundWeb(vc, m, 0.0D, 0.05D, 0.0D,
                (float) (radius * 0.98D), shapeSeed + 71L, theme, alpha * 0.75F,
                (float) (age * 0.015F), camX, camY, camZ, false);
        // ③ 从笼子跳到身上的短电弧：每 3 tick 换一组（"电在往身上爬" ✓）
        int arcPhase = (int) (age % 3.0F);
        if (arcPhase == 0 && age > 3.0F) {
            for (int i = 0; i < 4; i++) {
                double a = shapeSeed * 0.001D + i * 1.7D;
                double r = radius * (0.55D + 0.3D * ((i * 37) % 10) / 10.0D);
                double h = height * (0.25D + 0.45D * ((i * 61) % 10) / 10.0D);
                LightningBoltGeometry.arc(vc, m,
                        Math.cos(a) * r, h, Math.sin(a) * r,
                        Math.cos(a + 0.5D) * 0.25D, 1.0D, Math.sin(a + 0.5D) * 0.25D,
                        shapeSeed + i * 13L, 0.035F, alpha * 0.8F, theme, camX, camY, camZ);
            }
        }
    }
}
