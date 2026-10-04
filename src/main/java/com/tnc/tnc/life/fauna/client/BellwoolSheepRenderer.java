package com.tnc.tnc.life.fauna.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.life.fauna.BellwoolSheepEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class BellwoolSheepRenderer extends MobRenderer<BellwoolSheepEntity, BellwoolSheepModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "textures/entity/bellwool_sheep.png");

    public BellwoolSheepRenderer(EntityRendererProvider.Context context) {
        super(context, new BellwoolSheepModel(context.bakeLayer(BellwoolSheepModel.LAYER)), 0.6F);
    }

    @Override public ResourceLocation getTextureLocation(BellwoolSheepEntity entity) { return TEXTURE; }
}
