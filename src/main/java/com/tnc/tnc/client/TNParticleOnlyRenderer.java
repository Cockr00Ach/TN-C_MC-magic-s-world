package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * 「<b>视觉完全由粒子承担</b>」的通用渲染器 —— 火系这几个新实体都用它。
 *
 * <p>为什么是空渲染：火系还没有 {@code WaterGeometry} 那样的几何体工具，
 * 所以火球、熔岩地、两座法阵的样子全部由各自 {@code tick()} 里的客户端粒子画出来。
 * 这样做的额外好处是**判定与表现彻底分离**：改了粒子不会影响任何战斗数值。
 *
 * <p>要升级成真正的几何体时，只需要替换对应实体注册时用的这个类，实体与数值都不用动。
 *
 * <p>（{@link TNFireBoltRenderer} 与 {@link TNLavaFieldRenderer} 写在它之前，
 * 内容等价；将来可以一并折进来，只剩一处实现。）
 */
public final class TNParticleOnlyRenderer<T extends Entity> extends EntityRenderer<T> {

    /** 超过这个距离就不画（火系这些实体的粒子撒得很密，远处没必要）。 */
    private static final double RENDER_DISTANCE = 160.0D;

    /** 不会被真正贴图（render 是空的），指向一张一定存在的原版贴图只为不触发缺图警告。 */
    private static final ResourceLocation PLACEHOLDER =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/particle/flame.png");

    public TNParticleOnlyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.0F;
    }

    @Override
    public boolean shouldRender(T entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        return entity.distanceToSqr(x, y, z) < RENDER_DISTANCE * RENDER_DISTANCE;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return PLACEHOLDER;
    }

    @Override
    public void render(T entity, float yaw, float partial, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        // 故意留空：样子由粒子表达，见类注释。
    }
}
