package com.tnc.tnc.npc.client;

import com.tnc.tnc.npc.SelfBedrockNpcEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Self（Bedrock 模型版）的 GeckoLib 模型 —— 只回答"用哪个模型/贴图/动画"三个问题 ✓。
 *
 * <p>骨架由 {@code tools/gen_npc_bedrock.py} 生成（原版人形几何，脚底 y=0、头顶 y=32 = 2 格）✓，
 * 皮肤是 128×128 的 {@code self_bedrock.png} ✓，动画文件在
 * {@code assets/tnc/animations/entity/self.animation.json} ✓。
 *
 * <p>与庄鹊让那套的关键区别：咲夜的模型是"旧格式"、靠自带静止姿态站住；
 * 我们这份是**按原版数值生成的标准站姿**（四肢 pivot 就在原位），
 * 所以不播任何动作时他自然站着 ✓ —— 动画只负责"手上的戏" ✓。
 */
public class SelfBedrockGeoModel extends GeoModel<SelfBedrockNpcEntity> {

    @Override
    public ResourceLocation getModelResource(SelfBedrockNpcEntity animatable) {
        return SelfBedrockNpcEntity.modelResource();
    }

    @Override
    public ResourceLocation getTextureResource(SelfBedrockNpcEntity animatable) {
        return SelfBedrockNpcEntity.textureResource();
    }

    @Override
    public ResourceLocation getAnimationResource(SelfBedrockNpcEntity animatable) {
        return SelfBedrockNpcEntity.animationResource();
    }

    /**
     * 骨骼名对不上时**不要崩** ✗ —— 用户在 Blockbench 里改动作时可能写错骨骼名，
     * 那种情况只该"这个动作不生效"，不该把游戏带下水 ✓。
     */
    @Override
    public boolean crashIfBoneMissing() {
        return false;
    }
}
