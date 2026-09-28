package com.tnc.tnc.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.npc.ZuowangBedrockNpcEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 周坐望（Bedrock 模型版）的渲染器 —— GeckoLib 画 {@code zuowang.geo.json} ✓。
 *
 * <p>与 NPC 专题那套（{@link TnNpcRenderer} = 原版人形 + 64×64 皮肤）并存：
 * 装了 GeckoLib 走本渲染器（**能播 Blockbench 关键帧** ✓），没装就走那边的 ✓
 * （分流见 {@code com.tnc.tnc.npc.compat.GeoSelfSupport}）。
 *
 * <h2>尺寸</h2>
 * 模型是 1 单位 = 1/16 格的 bedrock 坐标，脚底 y=0、头顶 y≈33 单位 =
 * <b>约 2.06 格</b>（多出来的 1 单位是那根呆毛 {@code hairAhoge}）✓ ——
 * 原版玩家是 1.8 格碰撞箱 + 约 2 格高的模型，所以默认<b>不缩放</b> ✓。
 * 万一在游戏里看着偏大/偏小/悬空，<b>只改下面两个常量</b>即可
 * （与 self / 庄鹊让那边同一套旋钮）。
 */
public class ZuowangBedrockRenderer extends GeoEntityRenderer<ZuowangBedrockNpcEntity> {

    /** 整体缩放。1.0 = 模型原尺寸（约 2 格高）。 */
    private static final float MODEL_SCALE = 1.0F;

    /** 垂直微调（单位：格）。0 = 脚底贴地。 */
    private static final float MODEL_Y_OFFSET = 0.0F;

    public ZuowangBedrockRenderer(EntityRendererProvider.Context context) {
        super(context, new ZuowangBedrockGeoModel());
        this.shadowRadius = 0.5F;
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack,
                                    ZuowangBedrockNpcEntity animatable, BakedGeoModel model,
                                    boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        super.scaleModelForRender(widthScale, heightScale, poseStack, animatable, model,
                isReRender, partialTick, packedLight, packedOverlay);
        if (MODEL_SCALE != 1.0F) {
            poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        }
        if (MODEL_Y_OFFSET != 0.0F) {
            poseStack.translate(0.0D, MODEL_Y_OFFSET, 0.0D);
        }
    }
}
