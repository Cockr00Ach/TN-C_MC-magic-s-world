package com.tnc.tnc.boss.client;

import com.tnc.tnc.boss.TNApolloEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 阿波罗的 GeckoLib 模型（哥特大祭司 geo ＋ 作者的两个施法动画 ✓） */
public class TNApolloGeoModel extends GeoModel<TNApolloEntity> {
    @Override
    public ResourceLocation getModelResource(TNApolloEntity a) { return TNApolloEntity.modelResource(); }
    @Override
    public ResourceLocation getTextureResource(TNApolloEntity a) { return TNApolloEntity.textureResource(); }
    @Override
    public ResourceLocation getAnimationResource(TNApolloEntity a) { return TNApolloEntity.animationResource(); }
    @Override
    public boolean crashIfBoneMissing() { return false; }
}