package com.tnc.tnc.dark.client;

import com.tnc.tnc.light.TNDragonEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 暗龙的 GeckoLib 模型 ✓（作者 2026-10-02："复制一下光龙，生成一个暗龙" ✓）。
 *
 * <p><b>和光龙共用同一套 geo 与动画</b> ✗ —— 只有**贴图**不同 ✓（作者手改的那条东方龙 ✓，
 * 暗龙只是我按他的 UV 画了另一张 {@code dragon_dark_bedrock.png} ✓，
 * 见 {@code tools/retexture_dragon.py --dark} ✓）。
 *
 * <p>所以这里不需要任何新逻辑：实体还是 {@link TNDragonEntity} ✓（暗龙是**另一个实体类型**
 * {@code tnc:dark_dragon} ✓，逻辑一模一样、只有渲染不同 ✓）。
 */
public class TNDarkDragonGeoModel extends GeoModel<TNDragonEntity> {

    @Override
    public ResourceLocation getModelResource(TNDragonEntity animatable) {
        return TNDragonEntity.modelResource();
    }

    @Override
    public ResourceLocation getTextureResource(TNDragonEntity animatable) {
        return TNDragonEntity.DARK_TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(TNDragonEntity animatable) {
        return TNDragonEntity.animationResource();
    }

    /** 动画里出现的骨头在 geo 里都有 ✓ —— 少一根也别崩 ✗。 */
    @Override
    public boolean crashIfBoneMissing() {
        return false;
    }
}
