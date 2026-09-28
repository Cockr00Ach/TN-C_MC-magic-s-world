package com.tnc.tnc.npc.client;

import com.tnc.tnc.npc.ZuowangBedrockNpcEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 周坐望（Bedrock 模型版）的 GeckoLib 模型 —— 只回答"用哪个模型/贴图/动画"三个问题 ✓。
 *
 * <p>模型与贴图由 {@code tools/gen_zuowang_model.py} 生成（原版人形骨架 + 21 根须发骨骼，
 * 脚底 y=0、头顶 y≈33 = 约 2 格）✓。
 *
 * <p>与 {@code self} 那套同一套写法：不播任何动作时保持模型自带的静止站姿 ✓，
 * 动画只负责"戏"（点头/抬手/看远处…），由剧本的 {@code @act} 点播 ✓。
 */
public class ZuowangBedrockGeoModel extends GeoModel<ZuowangBedrockNpcEntity> {

    @Override
    public ResourceLocation getModelResource(ZuowangBedrockNpcEntity animatable) {
        return ZuowangBedrockNpcEntity.modelResource();
    }

    @Override
    public ResourceLocation getTextureResource(ZuowangBedrockNpcEntity animatable) {
        return ZuowangBedrockNpcEntity.textureResource();
    }

    @Override
    public ResourceLocation getAnimationResource(ZuowangBedrockNpcEntity animatable) {
        return ZuowangBedrockNpcEntity.animationResource();
    }

    /**
     * ⚠️ 这个 NPC 的动画文件**现在还不存在** ✗ —— 找不到动画文件时 GeckoLib 会去查这个开关。
     * 返回 false ⇒ 只是"没有动作可播"，模型照常渲染 ✓，不会把游戏带下水。
     * （与 {@code SelfBedrockGeoModel} 同样的理由：用户在 Blockbench 里改动作时可能写错骨骼名，
     * 那种情况只该"这个动作不生效" ✗，不该崩 ✓。）
     */
    @Override
    public boolean crashIfBoneMissing() {
        return false;
    }
}
