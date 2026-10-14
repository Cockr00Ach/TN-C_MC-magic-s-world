package com.tnc.tnc.boss.client;

import com.tnc.tnc.boss.TNWaterLeviathanEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Huge silhouette, broad shadow, and a separate full-bright blue-gold rune layer. */
public final class TNWaterLeviathanRenderer extends GeoEntityRenderer<TNWaterLeviathanEntity> {
    public TNWaterLeviathanRenderer(EntityRendererProvider.Context context) {
        super(context, new TNWaterLeviathanGeoModel());
        this.shadowRadius = 3.2F;
        this.addRenderLayer(new TNWaterLeviathanEmissiveLayer(this));
    }
}
