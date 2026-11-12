package com.tnc.tnc.boss.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.boss.TNDarkMageEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 黑魔法师的渲染器 ✓ —— 模型 / 贴图 / 动作都在 {@code tools/gen_dark_mage.py} 生成的资源里 ✓。
 *
 * <p>影子半径给 0.5 ✓（他脚下那圈破布离地 0.6 格 ✓，影子小一点才像"浮着"✓，
 * 给大了会像"站在一个黑盘子上"✗）。
 *
 * <p>★ 注册位置：{@code TNNpcClientEvents} 里，<b>必须在任何 if/else 外面</b> ✗ ——
 * 少注册一个渲染器 ⇒ 客户端在 Oculus 影子 pass 里 NPE（不是"画不出来"，是<b>崩游戏</b>✗）。
 */
public class TNDarkMageRenderer extends GeoEntityRenderer<TNDarkMageEntity> {
    public TNDarkMageRenderer(EntityRendererProvider.Context context) {
        super(context, new TNDarkMageGeoModel());
        this.shadowRadius = 0.5F;
        // 眼缝 / 胸前符文 / 杖心：全亮画一遍（自发光 ✓）
        this.addRenderLayer(new TNDarkMageEmissiveLayer(this));
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack,
                                    TNDarkMageEntity animatable, BakedGeoModel model, boolean isReRender,
                                    float partialTick, int packedLight, int packedOverlay) {
        super.scaleModelForRender(widthScale, heightScale, poseStack, animatable, model,
                isReRender, partialTick, packedLight, packedOverlay);
        float s = (float) TNDarkMageEntity.MODEL_SCALE;
        if (s != 1.0F) {
            poseStack.scale(s, s, s);
        }
    }
}
