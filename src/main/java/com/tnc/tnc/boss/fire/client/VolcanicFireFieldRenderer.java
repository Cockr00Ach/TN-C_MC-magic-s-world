package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.VolcanicFireFieldEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * 火焰区域实体本身不画任何几何：粒子由 {@link FireFieldFx} 每 tick 补。
 * 保留一个非空渲染器是为了避免 Forge 的 dispatcher 对未注册实体返回 null。
 */
public final class VolcanicFireFieldRenderer extends EntityRenderer<VolcanicFireFieldEntity> {

    public VolcanicFireFieldRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(VolcanicFireFieldEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(VolcanicFireFieldEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
