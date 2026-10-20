package com.tnc.tnc.dungeon.client;

import com.tnc.tnc.dungeon.DungeonSpecies;
import com.tnc.tnc.dungeon.TNDungeonMob;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 12 只小怪**共用一个模型类** —— geo / 贴图 / 动画都按 {@link DungeonSpecies#id} 拼路径。
 *
 * <p>文件名规则（由 {@code tools/gen_dungeon_mobs.py} 生成，别手工改）：
 * <pre>
 *   assets/tnc/geo/entity/&lt;id&gt;.geo.json
 *   assets/tnc/animations/entity/&lt;id&gt;.animation.json
 *   assets/tnc/textures/entity/&lt;id&gt;_bedrock.png      ← "_bedrock" 后缀和暗系召唤物一致
 * </pre>
 */
public class DungeonMobGeoModel extends GeoModel<TNDungeonMob> {

    private static final String FALLBACK = "lantern_boar";

    private static String idOf(TNDungeonMob animatable) {
        DungeonSpecies species = animatable.species();
        return species == null ? FALLBACK : species.id;
    }

    public static ResourceLocation geo(String id) {
        return ResourceLocation.fromNamespaceAndPath("tnc", "geo/entity/" + id + ".geo.json");
    }

    public static ResourceLocation texture(String id) {
        return ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/" + id + "_bedrock.png");
    }

    public static ResourceLocation animation(String id) {
        return ResourceLocation.fromNamespaceAndPath("tnc", "animations/entity/" + id + ".animation.json");
    }

    @Override
    public ResourceLocation getModelResource(TNDungeonMob animatable) {
        return geo(idOf(animatable));
    }

    @Override
    public ResourceLocation getTextureResource(TNDungeonMob animatable) {
        return texture(idOf(animatable));
    }

    @Override
    public ResourceLocation getAnimationResource(TNDungeonMob animatable) {
        return animation(idOf(animatable));
    }

    /** 动画里出现的骨头在 geo 里都有（生成器有校验），缺一根也别让客户端崩 ✗。 */
    @Override
    public boolean crashIfBoneMissing() {
        return false;
    }
}
