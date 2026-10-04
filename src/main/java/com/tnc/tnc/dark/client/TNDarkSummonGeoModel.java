package com.tnc.tnc.dark.client;

import com.tnc.tnc.dark.TNDarkSummonEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 暗系五个召唤物**共用一个模型类** ✓ —— geo / 贴图 / 动画都按 {@link TNDarkSummonEntity#modelName()}
 * 挑（档位由实体类型决定 ✓，见 {@code dark/TNDarkSummonEntity#tier()} ✓）。
 *
 * <p>这样五档不需要五个模型类 ✗（它们的区别只有"读哪个文件"✓）。
 */
public class TNDarkSummonGeoModel extends GeoModel<TNDarkSummonEntity> {

    @Override
    public ResourceLocation getModelResource(TNDarkSummonEntity animatable) {
        return TNDarkSummonEntity.geoOf(animatable.modelName());
    }

    @Override
    public ResourceLocation getTextureResource(TNDarkSummonEntity animatable) {
        return TNDarkSummonEntity.textureOf(animatable.modelName());
    }

    @Override
    public ResourceLocation getAnimationResource(TNDarkSummonEntity animatable) {
        return TNDarkSummonEntity.animationOf(animatable.modelName());
    }

    /** 动画里出现的骨头在 geo 里都有 ✓ —— 少一根也别崩 ✗。 */
    @Override
    public boolean crashIfBoneMissing() {
        return false;
    }
}
