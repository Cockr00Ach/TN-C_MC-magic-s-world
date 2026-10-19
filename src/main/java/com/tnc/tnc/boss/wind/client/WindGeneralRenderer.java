package com.tnc.tnc.boss.wind.client;

import com.tnc.tnc.boss.wind.WindGeneralEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class WindGeneralRenderer extends GeoEntityRenderer<WindGeneralEntity> {
    public WindGeneralRenderer(EntityRendererProvider.Context context){super(context,new WindGeneralModel());withScale(1.15F);shadowRadius=.55F;}
}
