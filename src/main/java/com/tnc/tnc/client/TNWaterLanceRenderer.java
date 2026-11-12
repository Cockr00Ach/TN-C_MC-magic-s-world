package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.magic.water.TNWaterLanceEntity;
import com.tnc.tnc.magic.water.WaterGateRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 一道水波（{@link TNWaterLanceEntity}）✓ —— 几何全交给 {@link WaterBeamVisuals}（那根"加粗的闪电"✓）。
 *
 * <p>三个细节：
 * <ul>
 *   <li><b>淡入淡出</b>：第 1 tick 涨起来、最后 3 tick 灭掉 ✓（作者："跟光线一样"✗ ——
 *       光柱是"瞬间出现再淡出"，一闪就没会像掉帧 ✗）；</li>
 *   <li><b>粗细</b>用 {@code WaterGateRules.lanceRadius}（t4 0.9 / t5 2.2 格 ✓），
 *       巨大版 ×2.8 ✓ —— 和判定半径是同一个数 ✗（两边各写一个就会"看着打中了却没伤害"✗）；</li>
 *   <li>近处才画分叉 / 末端爆闪（{@code detail}✓），远处只画主干 ✓。</li>
 * </ul>
 */
public final class TNWaterLanceRenderer extends EntityRenderer<TNWaterLanceEntity> {

    public TNWaterLanceRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(TNWaterLanceEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/water_still.png");
    }

    @Override
    public boolean shouldRender(TNWaterLanceEntity e, Frustum frustum, double x, double y, double z) {
        return e.distanceToSqr(x, y, z) < 144 * 144;
    }

    @Override
    public void render(TNWaterLanceEntity e, float yaw, float partial, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        buffers = WaterTransparencyPass.route(buffers);
        double age = e.tickCount + partial;
        int life = WaterGateRules.lanceTicks(e.tier());
        float fade = (float)Math.min(Math.min(1.0D, (age + 0.5D) / 1.5D),
                Math.max(0.0D, (life - age + 1.0D) / 3.0D));
        if (fade <= 0.004F) {
            return;
        }
        Vec3 dir = e.direction();
        double length = e.length();
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .subtract(e.position());
        boolean detail = camera.lengthSqr() < 64 * 64;
        WaterVisualBuffers.draw(buffers, (body, glow) -> WaterBeamVisuals.render(body, glow,
                stack.last().pose(), e.tier(), dir, length, age, fade, detail, camera, e.radius()));
    }
}
