package com.tnc.tnc.light.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.light.TNDragonEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 光明龙渲染器 ✓ —— 和光天使同一套"半透明 + 自发光"（作者那条龙是"光明龙"✓，
 * 贴图里的亮金/白热像素就是要它发光 ✓）。
 *
 * <p>缩放由法术写进实体（{@code scale() ✓}），放在 {@code scaleModelForRender} 里 ✓。
 *
 * <p>★ 这条龙长 23 格 ✗ ⇒ 剔除盒必须在**实体**里按体长撑大
 * （{@link TNDragonEntity#getBoundingBoxForCulling} ✓），不然抬头只看一段时整条消失 ✓。
 *
 * <h2>★★ 2026-10-04 修：这条龙"倒着飞"（作者："黑龙和光龙都是倒着飞" ✗）</h2>
 * 根因在 GeckoLib 的 {@code applyRotations} ✓ —— 反汇编 {@code geckolib-4.8.4.jar} 看得很清楚：
 * <pre>
 *   float yaw = animatable instanceof LivingEntity ? lerp(pt, yRotO, yRot) : 0.0F;   // ← 就是这里
 *   applyRotations(animatable, poseStack, 0.0F, yaw, partialTick);
 *   ...
 *   poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
 * </pre>
 * <b>非生物实体（{@code TNDragonEntity extends Entity} ✗，是投射物 ✓）拿到的 yaw 恒等于 0</b> ✗
 * ⇒ 模型被钉死在"绕 Y 转 180°"这一个姿态上 ✓：龙身朝 −Z、而它每 tick 往 {@code chargeDir} 走 ✗
 * ⇒ 看着就是**尾巴朝前、倒着飞** ✗。光龙/暗龙都是同一个类 ⇒ 两条一起中招 ✓。
 *
 * <p>所以这里自己补两个旋转 ✓（就这一处，实体里的 {@code yRot} 保持标准值，
 * 免得以后再拿它做别的计算时对不上 ✗）：
 * <ol>
 *   <li><b>+180°（Y）</b> —— 把 GeckoLib 那一下抵消掉 ✓；</li>
 *   <li><b>{@code -xRot}（X）</b> —— GeckoLib 的 {@code applyRotations} 根本**不处理俯仰** ✗
 *       （只转 Y + 死亡/睡觉 ✓）；而 MC 的 {@code xRot} 是"低头为正"、模型里是"抬头为正" ✓
 *       ⇒ 取负号，龙才会真的"跟着准星点头"✓（t4/t5 是往准星方向俯冲的 ✓）。</li>
 * </ol>
 */
public class TNDragonRenderer extends GeoEntityRenderer<TNDragonEntity> {

    public TNDragonRenderer(EntityRendererProvider.Context context) {
        super(context, new TNDragonGeoModel());
        this.shadowRadius = 0.0F;                 // 发光体不投影 ✓（和光天使一致 ✓）
    }

    /** 自发光 + 半透明 ✓（和光天使/光翼同一套写法 ✓）。 */
    @Override
    public RenderType getRenderType(TNDragonEntity animatable, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    /**
     * ★ 朝向修正（见类注释 ✓）—— 光龙/暗龙的渲染器**都要有这一段** ✓
     * （直接调用 {@link TNDragonPose#fixFacing}，两边一个字都别抄岔 ✗）。
     */
    @Override
    public void preRender(PoseStack poseStack, TNDragonEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, com.mojang.blaze3d.vertex.VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        TNDragonPose.fixFacing(poseStack, animatable, partialTick);
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack,
                                    TNDragonEntity animatable, BakedGeoModel model, boolean isReRender,
                                    float partialTick, int packedLight, int packedOverlay) {
        super.scaleModelForRender(widthScale, heightScale, poseStack, animatable, model,
                isReRender, partialTick, packedLight, packedOverlay);
        float s = (float) animatable.scale();
        poseStack.scale(s, s, s);
    }
}

