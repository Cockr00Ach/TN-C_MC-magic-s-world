package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.VolcanicMortarEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * 抛射火球的占位渲染器。
 *
 * <p>火球本体由 {@link FireBlobFx} 的粒子团绘制，这里不画任何几何。但必须注册：
 * Forge 的 {@code EntityRenderDispatcher} 对未注册实体返回 null，会在渲染时抛空指针。
 * 同时提供落点预警所需的服务端落点数据（由实体自身同步）。
 */
public final class VolcanicMortarRenderer extends EntityRenderer<VolcanicMortarEntity> {

    public VolcanicMortarRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(VolcanicMortarEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(VolcanicMortarEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
