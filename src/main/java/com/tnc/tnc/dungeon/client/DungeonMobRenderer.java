package com.tnc.tnc.dungeon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.dungeon.TNDungeonMob;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 12 只小怪共用的渲染器 —— 唯一的额外工作是按 {@link TNDungeonMob#scale()} 放大精英。
 *
 * <p>影子半径按体型给（0.4 起步），否则蜘蛛那种趴地的怪脚下一团大黑影很出戏。
 */
public class DungeonMobRenderer extends GeoEntityRenderer<TNDungeonMob> {

    public DungeonMobRenderer(EntityRendererProvider.Context context) {
        super(context, new DungeonMobGeoModel());
        this.shadowRadius = 0.45F;
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack,
                                    TNDungeonMob animatable, BakedGeoModel model, boolean isReRender,
                                    float partialTick, int packedLight, int packedOverlay) {
        super.scaleModelForRender(widthScale, heightScale, poseStack, animatable, model,
                isReRender, partialTick, packedLight, packedOverlay);
        double scale = animatable.scale();
        if (scale != 1.0D) {
            poseStack.scale((float) scale, (float) scale, (float) scale);
        }
    }
}
