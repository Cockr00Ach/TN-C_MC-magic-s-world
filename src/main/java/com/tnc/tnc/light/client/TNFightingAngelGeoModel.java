package com.tnc.tnc.light.client;

import com.tnc.tnc.light.TNFightingAngelEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 战斗天使的 GeckoLib 模型（作者的 fightingangel geo ＋ 贴图 ＋ 五个动作 ✓） */
public class TNFightingAngelGeoModel extends GeoModel<TNFightingAngelEntity> {
    @Override
    public ResourceLocation getModelResource(TNFightingAngelEntity a) { return TNFightingAngelEntity.modelResource(); }
    @Override
    public ResourceLocation getTextureResource(TNFightingAngelEntity a) { return TNFightingAngelEntity.textureResource(); }
    @Override
    public ResourceLocation getAnimationResource(TNFightingAngelEntity a) { return TNFightingAngelEntity.animationResource(); }
    @Override
    public boolean crashIfBoneMissing() { return false; }
}