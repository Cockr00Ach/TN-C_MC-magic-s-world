package com.tnc.tnc.client;

import com.tnc.tnc.magic.TNWindSpiritEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 风灵的 GeckoLib 模型（{@code magic/TNWindSpiritEntity}）✓。
 *
 * <p>三个文件都是 {@code tools/gen_wind_sprite_model.py} 程序化生成的 ✓
 * （作者 2026-10-10："抄一下小精灵的模型" —— 但本仓规矩是**不搬别的模组的美术** ✗，
 * 所以这是自己画的小光灵，和小精灵同一个思路：小身子 + 翅膀 + 一点光 ✓）：
 * <ul>
 *   <li>{@code assets/tnc/geo/entity/wind_sprite.geo.json}（5 个方块：body / crest /
 *       wing_left / wing_right / tail ✓）</li>
 *   <li>{@code assets/tnc/animations/entity/wind_sprite.animation.json}（{@code idle}：
 *       悬停上下 + 翅膀快扇 + 头顶呆毛与尾巴慢摆 ✓）</li>
 *   <li>{@code assets/tnc/textures/entity/wind_sprite_bedrock.png}（64×64 ✓）</li>
 * </ul>
 */
public class TNWindSpiritGeoModel extends GeoModel<TNWindSpiritEntity> {

    private static final ResourceLocation GEO =
            ResourceLocation.fromNamespaceAndPath("tnc", "geo/entity/wind_sprite.geo.json");
    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/wind_sprite_bedrock.png");
    private static final ResourceLocation ANIM =
            ResourceLocation.fromNamespaceAndPath("tnc", "animations/entity/wind_sprite.animation.json");

    @Override
    public ResourceLocation getModelResource(TNWindSpiritEntity animatable) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(TNWindSpiritEntity animatable) {
        return TEX;
    }

    @Override
    public ResourceLocation getAnimationResource(TNWindSpiritEntity animatable) {
        return ANIM;
    }

    /** 动画里出现的骨头在 geo 里都有 ✓ —— 但少一根也别崩 ✗（和暗系召唤同一个口径）。 */
    @Override
    public boolean crashIfBoneMissing() {
        return false;
    }
}
