package com.tnc.tnc.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.npc.ZhuangquerangMaidNpcEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 庄鹊让（女仆模型版）的渲染器 —— GeckoLib 画 bedrock 女仆模型 ✓。
 *
 * <p>与 NPC 专题那套（{@link TnNpcRenderer} = 原版人形 + 64×64 皮肤）并存：
 * 装了 GeckoLib 走本渲染器，没装就走他们的 ✓（分流见 {@code com.tnc.tnc.npc.compat.MaidModelSupport}）。
 *
 * <h2>尺寸</h2>
 * 咲夜的模型是 <b>1 单位 = 1/16 格</b> 的 bedrock 坐标，脚底在 y=0、头顶约 26 单位 ≈ 1.6 格 ✓ ——
 * 和我们的实体（0.6×1.8）基本吻合，所以默认<b>不缩放</b> ✓。
 * 万一在游戏里看着偏大/偏小/悬空，<b>只改下面两个常量</b>即可（见 {@link #MODEL_SCALE} / {@link #MODEL_Y_OFFSET}）。
 */
public class ZhuangquerangMaidRenderer extends GeoEntityRenderer<ZhuangquerangMaidNpcEntity> {

    /**
     * 整体缩放。<b>1.0 = 模型原尺寸</b>（约 1.6 格高）。
     * 游戏里觉得太大就往下调（如 0.9），太小就往上调 ✓。
     */
    private static final float MODEL_SCALE = 1.0F;

    /**
     * 垂直微调（单位：格）。0 = 脚底贴地 ✓；
     * 悬空就填负数往下压，陷进地里就填正数往上提 ✓。
     */
    private static final float MODEL_Y_OFFSET = 0.0F;

    public ZhuangquerangMaidRenderer(EntityRendererProvider.Context context) {
        super(context, new ZhuangquerangMaidGeoModel());
        this.shadowRadius = 0.5F;
    }

    /**
     * GeckoLib 的缩放钩子：默认是"不缩放"。
     * 这里把可调的两个值集中过来，改数值不用碰别的地方 ✓。
     */
    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack,
                                    ZhuangquerangMaidNpcEntity animatable, BakedGeoModel model,
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
