package com.tnc.tnc.boss.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.boss.TNDarkGiantEntity;
import com.tnc.tnc.boss.TNDarkGiantPhase;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * <b>巨兽人领主的"背后虚影"</b>（作者 2026-10-03："二阶段的时候他的背后会出现他自己模型的虚影
 * （他自己的两倍高），虚影跟他做一样的动作" ✓）。
 *
 * <h2>怎么做到"动作一模一样"的（这一层的全部秘诀 ✓）</h2>
 * 虚影<b>不是另一个实体</b> ✗，也不用同步任何东西 ✗ ——
 * GeckoLib 在画之前已经把这个模型的**每一根骨头都摆成了这一帧的动作** ✓（{@code setupAnim} ✓），
 * 而这一层是在**本体画完之后**被调用的 ✓ ⇒ 只要把<b>同一个 BakedGeoModel 再画一遍</b> ✓，
 * 画出来的就必然是同一个动作 ✓（而且永远同步、零延迟 ✓）。
 *
 * <p>所以这一层只干三件事：
 * <ol>
 *   <li>按 {@link TNDarkGiantPhase#PHANTOM_SCALE}（<b>2 倍</b>）放大 ✓；</li>
 *   <li>横向按 {@link TNDarkGiantPhase#PHANTOM_BACK} 挪 ✓（★ 2026-10-03 作者："虚影跟人物的中心没对齐" ✗
 *       ⇒ 这个值现在是 <b>0 = 和本体同心</b> ✓，两边共用同一个中心轴 ✓）；</li>
 *   <li>换一个**半透明暗紫**的渲染层重画一遍 ✓（{@code entityTranslucentEmissive} + 全亮 ✓
 *       ⇒ 不受世界光照影响、自带一点幽光 ✓，正是"虚影"该有的样子 ✓；
 *       作者嫌太透 ⇒ alpha 已经提到 {@link TNDarkGiantPhase#PHANTOM_ALPHA} ✓）。</li>
 * </ol>
 *
 * <p>★ 只在二阶段画 ✓（{@link TNDarkGiantPhase#phantomVisible} ✓）；
 * ★ {@code reRender} 内部带 {@code isReRender = true} ✓ ⇒ 不会再走一遍渲染层 ✗（不会无限递归 ✓）。
 */
public class TNDarkGiantPhantomLayer extends GeoRenderLayer<TNDarkGiantEntity> {

    public TNDarkGiantPhantomLayer(GeoRenderer<TNDarkGiantEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, TNDarkGiantEntity animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        if (!TNDarkGiantPhase.phantomVisible(animatable.phase())) {
            return;   // 一阶段没有虚影 ✓
        }
        RenderType ghost = RenderType.entityTranslucentEmissive(this.getTextureResource(animatable));
        VertexConsumer ghostBuffer = bufferSource.getBuffer(ghost);

        poseStack.pushPose();
        // 背后（模型空间 +Z = 背 ✓），再整体放大 2 倍（原点在脚下 ⇒ 两倍高 ✓）
        poseStack.translate(0.0D, 0.0D, TNDarkGiantPhase.PHANTOM_BACK);
        float scale = (float) TNDarkGiantPhase.PHANTOM_SCALE;
        poseStack.scale(scale, scale, scale);
        this.getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, ghost, ghostBuffer,
                partialTick, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                TNDarkGiantPhase.PHANTOM_RED, TNDarkGiantPhase.PHANTOM_GREEN,
                TNDarkGiantPhase.PHANTOM_BLUE,
                TNDarkGiantPhase.phantomAlpha(animatable.tickCount));
        poseStack.popPose();
    }
}
