package com.tnc.tnc.warrior.client;

import com.tnc.tnc.warrior.InstructorEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 教官渲染器：正常吃场景光照（他们站在岛上的演武场里，不是暗影召唤物），
 * 额外挂一层手持物品——见 {@link InstructorHeldItemLayer}。
 */
public final class InstructorRenderer extends GeoEntityRenderer<InstructorEntity> {
    public InstructorRenderer(EntityRendererProvider.Context context) {
        super(context, new InstructorGeoModel());
        this.shadowRadius = 0.45F;
        addRenderLayer(new InstructorHeldItemLayer(this));
    }
}
