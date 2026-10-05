package com.tnc.tnc.light.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.light.TNDragonDisplayEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 龙的渲染器 ✓ —— <b>自己发顶点</b>，几何/贴图全内嵌 ✓（{@link TNDragonModel} ✓）。
 *
 * <h2>为什么最后是这个形态（三次换路的终点 ✓）</h2>
 * <ol>
 *   <li>GeckoLib：非生物实体 yaw 恒为 0 ✗ + 大模型被视锥剔掉 ✗ ⇒ 看不见 ✗；</li>
 *   <li>原版 {@code Display.BlockDisplay}：原版只给**内置的** {@code minecraft:block_display}
 *       注册渲染器 ✗（自定义实体类型没有 ✗）⇒ {@code EntityRenderDispatcher} 空指针
 *       ⇒ <b>释放龙直接卡退</b> ✗（崩报原文：
 *       {@code Cannot invoke "...EntityRenderer.shouldRender(...)" because "entityrenderer" is null} ✓）；</li>
 *   <li>那就老实写一个普通 Forge {@code EntityRenderer} ✓ ——
 *       它一定会被注册上 ✓（就在 {@code TNSpellOrbClientEvents} 里 ✓），
 *       也不依赖资源包 ✗（模型 json 被整合包的资源包遮住了 ✓，日志里连
 *       {@code gamble_table} 那种老方块都是同样的报错 ✓）。</li>
 * </ol>
 *
 * <p>另外两点顺手解决的 ✓：
 * <ul>
 *   <li><b>剔除</b>：{@link #shouldRender} 只按距离判 ✓ —— 一条 23→273 格长的模型，
 *       原版那套"拿实体原点的小盒子跟视锥求交"必然误杀 ✗；</li>
 *   <li><b>旋转</b>：这里用的是普通实体渲染管线 ⇒ <b>模型正面 = +Z</b> ✓（原版约定 ✓），
 *       而这条龙的鼻子在局部 −Z ✓ ⇒ {@code setYRot(yaw + 180)} ✓
 *       （由 {@code tools/check_dragon_charge_facing.py} 的东南西北 × 俯仰验算背书 ✓）。</li>
 * </ul>
 */
public class TNDragonRenderer extends EntityRenderer<TNDragonDisplayEntity> {

    /** 多远之内无条件画（格 ✓）—— 够 t5 那条 273 格长的龙横穿视野 ✓。 */
    private static final double RENDER_DISTANCE = 512.0D;

    public TNDragonRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    /**
     * ★ <b>不看视锥，只看距离</b> ✓（作者报的"显示一会就消失"有一半是这里 ✗）。
     * 大模型的包围盒和视锥求交非常容易在"半边身子在屏幕里"时判成不可见 ✗。
     */
    @Override
    public boolean shouldRender(TNDragonDisplayEntity entity,
                                net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        return entity.distanceToSqr(camX, camY, camZ) < RENDER_DISTANCE * RENDER_DISTANCE;
    }

    @Override
    public ResourceLocation getTextureLocation(TNDragonDisplayEntity entity) {
        return TNDragonModel.texture(entity.isDark());
    }

    @Override
    public void render(TNDragonDisplayEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        boolean dark = entity.isDark();
        // 光龙：满亮（作者原话"贴图里的亮金/白热像素就是要它发光"✓）；
        // 暗龙：吃场景光 ✗（满亮会把黑鳞照成灰的 ✓）
        int light = dark ? packedLight : LightTexture.FULL_BRIGHT;
        float alpha = dark ? 1.0F : 0.92F;
        float tint = dark ? 0.55F : 1.0F;

        poseStack.pushPose();
        // 方块模型的顶点是"格 × 16"，模型原点在方块中心 ⇒ 缩到格、再挪半格 ✓
        poseStack.scale(0.0625F, 0.0625F, 0.0625F);
        poseStack.translate(0.5D, 0.5D, 0.5D);

        VertexConsumer buffer = bufferSource.getBuffer(
                dark ? RenderType.entityCutoutNoCull(getTextureLocation(entity))
                     : RenderType.entityTranslucentEmissive(getTextureLocation(entity)));
        TNDragonModel.render(poseStack.last(), buffer, light, alpha, 0.0F,
                tint, tint, tint);
        poseStack.popPose();

        // ★ 注意：这里**不调** {@code super.render} ✗ —— 它会再洗一次名字牌/对齐那套，
        //   而我们不需要名字牌 ✓（龙是法术投射物 ✓）。
        //   ★ 朝向那一下（原版 {@code ry(180 - yRot)} ✓）是**外面**的
        //   {@code EntityRenderDispatcher} 在调我们之前就压进 {@code poseStack} 的 ✓
        //   —— 所以我们只要在**不动旋转**的前提下画顶点就行 ✓（上面只加了缩放和平移 ✓）。
    }
}
