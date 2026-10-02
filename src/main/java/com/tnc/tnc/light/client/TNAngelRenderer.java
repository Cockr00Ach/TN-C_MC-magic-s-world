package com.tnc.tnc.light.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.light.TNAngelEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 光天使渲染器 ✓ —— 作者 2026-10-01 的两条硬要求都在这里：
 * <ol>
 *   <li><b>半透明</b>：{@code RenderType.entityTranslucentEmissive(...)} ✓（照光翼
 *       {@code TNLightWingsRenderer} 那套已验证的写法 ✓）；</li>
 *   <li><b>自发光</b>：同一个渲染类型就是"发光 + 半透明"✓（不吃场景光照，夜里也是亮的 ✓）。</li>
 * </ol>
 *
 * <p>尺寸：按 {@link TNAngelEntity#scaleFactor()} 缩放 ✓（4 格 / 6 格 / 10 格高，
 * 由法术在 {@code configure(...)} 里定 ✓）—— 缩放放在 {@code scaleModelForRender} 里，
 * 和 {@code YanDarkRenderer} 同一个钩子 ✓（这样包围盒/阴影都跟着一起缩 ✓）。
 */
public class TNAngelRenderer extends GeoEntityRenderer<TNAngelEntity> {

    public TNAngelRenderer(EntityRendererProvider.Context context) {
        super(context, new TNAngelGeoModel());
        this.shadowRadius = 0.0F;                  // 发光体不投影，免得地上出现一团黑 ✓
    }

    /**
     * ★ 同一个"反着做的模型"问题 ✓（2026-10-02 查出，和战斗天使同源）：
     * {@code angel.geo.json} 的眼睛也画在头的 <b>+Z</b> 那一面，而 GeckoLib 把 −Z 当实体朝向
     * ⇒ 不转 180° 的话，法阵中心那尊天使是**背对着你**的 ✗（判据见 {@code tools/face_uv_check.py} ✓）。
     */
    @Override
    public void preRender(PoseStack poseStack, TNAngelEntity animatable, BakedGeoModel model,
                          net.minecraft.client.renderer.MultiBufferSource bufferSource,
                          com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /** ★ 半透明 + 自发光 ✓（这两条就是作者要的观感，别改成 entitySolid ✗）。 */
    @Override
    public RenderType getRenderType(TNAngelEntity animatable, ResourceLocation texture,
                                    net.minecraft.client.renderer.MultiBufferSource bufferSource,
                                    float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    /** 按法术要求的高度缩放 ✓。 */
    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack,
                                    TNAngelEntity animatable, BakedGeoModel model, boolean isReRender,
                                    float partialTick, int packedLight, int packedOverlay) {
        super.scaleModelForRender(widthScale, heightScale, poseStack, animatable, model,
                isReRender, partialTick, packedLight, packedOverlay);
        float s = (float) animatable.scaleFactor();
        if (s != 1.0F) {
            poseStack.scale(s, s, s);
        }
    }
}
