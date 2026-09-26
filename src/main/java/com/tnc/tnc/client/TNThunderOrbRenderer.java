package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNThunderOrbEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * 环绕雷球的渲染器 —— 用<b>作者做的新球 {@code lightingball_2}</b>，<b>全亮度</b>画（它就是发光的球 ✓）。
 *
 * <h2>2026-09-27 换模型（作者："我做了一个新球 lightingball_2，把环绕雷球和超级无敌大雷球的模型换一下，
 * 体积放大到和现在差不多的占地格数"）</h2>
 *
 * <h3>为什么不再用 {@link TNThunderOrbModel}（代码搭的 ModelPart）</h3>
 * 老球只有 <b>7 个方块</b>，可以手抄成 {@code ModelPart}；新球是 <b>58 个小方块</b>拼的碎块球，
 * 而且每个面都<b>单独指了贴图上的一个像素</b>（UV 跨度 0.25 ＝ 64×64 贴图上的 1 px ✗ 不是整块 UV），
 * 而 {@code CubeListBuilder.addBox} 只会按"整块展开"自动算 UV ✗ —— 手抄必然花掉 ✗。
 *
 * <p>所以这里直接渲染<b>已经烘焙好的原版方块模型</b>（{@code assets/tnc/models/projectile/lightingball_2.json}）：
 * <ul>
 *   <li>它由 {@link TNModelBaking}（{@code ModelEvent.RegisterAdditional} ＋ 变体 {@code "standalone"}）烘焙 ✓；</li>
 *   <li>面/UV 完全是作者导出的样子 ✓，我一个数字都没动；</li>
 *   <li>贴图走<b>方块图集</b>（{@link TextureAtlas#LOCATION_BLOCKS}）✗ 不是独立贴图文件 ——
 *       方块模型烘焙出来的 UV 是图集坐标，用独立贴图路径画会整个错位 ✗。</li>
 * </ul>
 *
 * <h3>尺寸</h3>
 * 新球基准 <b>13/16 = 0.8125 格</b>（老球 6/16 = 0.375 格）。要维持"占地格数差不多"＝
 * 老球 {@code 0.375 × 8.55 = 3.21 格} ⇒ 新 {@link #SCALE} ＝ {@code 8.55 × 0.375 / 0.8125 = 3.95} ✓。
 *
 * <h3>居中</h3>
 * 方块模型的坐标原点在"方块角"，不是球心 ✗ —— 作者的球心在 (9.5, 11.5, 8.5) 单位处，
 * 先平移到原点再缩放（{@code scale} 在前、{@code translate} 在后 ＝ 先平移后缩放 ✓）。
 */
public class TNThunderOrbRenderer extends EntityRenderer<TNThunderOrbEntity> {

    /** 备用：万一新模型没被烘焙出来（资源重载失败等），退回代码搭的老球，至少不是紫黑方块 ✓。 */
    private static final ResourceLocation OLD_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, "textures/spell_projectile/lightingball.png");

    /**
     * 1.0 ＝ 模型原尺寸（13/16 格 ≈ 0.8125 格）。
     *
     * <p>老球用 8.55（＝爆炸雷球同尺寸，3.21 格）—— 换模型时按
     * {@code 8.55 × 0.375 / 0.8125 = 3.946} 折算成 <b>3.95</b>，占地格数不变 ✓。
     */
    private static final float SCALE = 3.95F;

    /** 作者那颗球的球心（单位 → 格）：(9.5, 11.5, 8.5) / 16 ✓ 由 {@code tools/import_lightingball_2.ps1} 打印。 */
    private static final float CENTER_X = 9.5F / 16.0F;
    private static final float CENTER_Y = 11.5F / 16.0F;
    private static final float CENTER_Z = 8.5F / 16.0F;

    private final ModelResourceLocation modelLocation =
            TNProjectileModels.standalone(TNProjectileModels.LIGHTNINGBALL_2);

    /** 兜底模型（只在烘焙失败时用）。 */
    private final TNThunderOrbModel fallbackModel;

    public TNThunderOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.fallbackModel = new TNThunderOrbModel(context.bakeLayer(TNThunderOrbModel.LAYER));
        this.shadowRadius = 0.0F;       // 悬浮的光球不投影子
    }

    @Override
    public ResourceLocation getTextureLocation(TNThunderOrbEntity entity) {
        return OLD_TEXTURE;
    }

    @Override
    public void render(TNThunderOrbEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        // 自转：让碎块球的面转起来，静态看着像卡住了 ✓
        float spin = (entity.tickCount + partialTick) * 12.0F;

        BakedModel baked = Minecraft.getInstance().getModelManager().getModel(this.modelLocation);
        // 注意：方块模型的面都是"有朝向"的，用 null 方向查会得到空表 ✗ —— 拿 UP 面试探 ✓
        net.minecraft.util.RandomSource rand = net.minecraft.util.RandomSource.create(42L);
        boolean hasModel = baked != Minecraft.getInstance().getModelManager().getMissingModel()
                && !baked.getQuads(null, net.minecraft.core.Direction.UP, rand).isEmpty();

        poseStack.pushPose();
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spin));

        if (hasModel) {
            poseStack.scale(SCALE, SCALE, SCALE);
            // 先平移（在缩放后的空间里）→ 球心落在实体原点 ✓
            poseStack.translate(-CENTER_X, -CENTER_Y, -CENTER_Z);
            VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
            Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                    poseStack.last(), consumer, null, baked,
                    1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } else {
            // 兜底：老球（代码搭的 7 方块）
            poseStack.scale(8.55F, 8.55F, 8.55F);
            VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(OLD_TEXTURE));
            this.fallbackModel.renderToBuffer(poseStack, consumer, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
