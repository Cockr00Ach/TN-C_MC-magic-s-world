package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** 模型的单位已经是 1/16 格（60×44×88 单位 ⇒ 3.75×2.75×5.5 格），所以这里不再放大。 */
public final class VolcanicTortoiseRenderer extends GeoEntityRenderer<VolcanicTortoiseEntity> {
    public VolcanicTortoiseRenderer(EntityRendererProvider.Context context) {
        super(context, new VolcanicTortoiseGeoModel());
        this.shadowRadius = 2.1F;
        this.addRenderLayer(new VolcanicTortoiseEmissiveLayer(this));
    }
}
