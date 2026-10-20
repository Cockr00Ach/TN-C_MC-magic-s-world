package com.tnc.tnc.world.ink.client;

import com.tnc.tnc.world.ink.InkAbyssDrakeEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class InkAbyssDrakeRenderer extends GeoEntityRenderer<InkAbyssDrakeEntity> {
    public InkAbyssDrakeRenderer(EntityRendererProvider.Context context) {
        super(context, new InkAbyssDrakeModel());
        shadowRadius = 2.5F;
    }

    @Override
    protected float getDeathMaxRotation(InkAbyssDrakeEntity entity) {
        return 0.0F;
    }
}
