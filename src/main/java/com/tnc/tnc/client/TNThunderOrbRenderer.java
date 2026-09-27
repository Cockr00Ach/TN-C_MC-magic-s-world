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
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 环绕雷球的渲染器 —— 用<b>作者做的新球 {@code lightingball_2}</b>，<b>全亮度</b>画（它就是发光的球 ✓）。
 *
 * <h2>2026-09-27 换模型（作者："我做了一个新球 lightingball_2……"）</h2>
 * 新球是 <b>58 个小方块</b>拼的碎块球，每个面<b>单独取贴图上的 1 px</b>（UV 跨度 0.25）。
 * 老球只有 7 个方块，是手抄成 {@code ModelPart} 的；新球手抄必然花掉 ✗
 * （{@code CubeListBuilder.addBox} 只会按"整块展开"算 UV）。
 *
 * <h2>2026-09-27 第二版 —— 修"环绕球变紫黑"（作者："环绕的怎么变成紫黑了，估计是名字没对上"）</h2>
 * 第一版我自己去查烘焙模型：{@code getModelManager().getModel(new ModelResourceLocation(id, "standalone"))} ✗
 * —— <b>查不到</b>。反编译引擎 {@code SpellProjectileRenderer} 看到它查的是
 * <b>{@code getModelManager().getModel(ResourceLocation)}（没有 {@code #standalone} 变体）</b> ✓，
 * 而查不到时 Forge 返回的那个"缺失模型"<b>不是</b> {@code getMissingModel()} 那个共享实例 ✗，
 * 于是我"模型存在吗"的判断被骗过去，把<b>紫黑的缺失方块</b>画了出来 ✗。
 *
 * <p>现在<b>不再自己查</b>，直接调用引擎渲染投射物的那一行
 * （反编译确认：{@code SpellModelHelper.LAYERS.get(light_emission)} 取层 ＋
 * {@code CustomModels.render(layer, itemRenderer, modelId, ...)}）✓ ——
 * 和神级大雷球走的是<b>同一条路</b>，它已经验证能显示 ✓。
 *
 * <h3>尺寸 / 居中</h3>
 * 新球基准 <b>13/16 = 0.8125 格</b>（老球 6/16 = 0.375 格）。维持"占地格数差不多"＝
 * 老球 {@code 0.375 × 8.55 = 3.21 格} ⇒ {@link #SCALE} ＝ {@code 8.55 × 0.375 / 0.8125 = 3.95} ✓。
 * 方块模型原点在"方块角"、球心在 (9.5, 11.5, 8.5)/16 ⇒ 先缩放再平移把球心挪到实体原点 ✓。
 */
public class TNThunderOrbRenderer extends EntityRenderer<TNThunderOrbEntity> {

    private static final Logger LOGGER = LogManager.getLogger("TN-C/orb");

    /** 兜底用的老贴图（只在引擎渲染抛异常时用）。 */
    private static final ResourceLocation OLD_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, "textures/spell_projectile/lightingball.png");

    /** 给引擎的模型号 —— <b>普通 ResourceLocation</b>，不要加 {@code #standalone} 变体 ✗。 */
    private static final ResourceLocation MODEL_ID = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, TNProjectileModels.LIGHTNINGBALL_2);

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

    /** 只记一次日志，免得每帧刷屏。 */
    private static boolean logged;

    /** 兜底模型（引擎渲染抛异常时才用；正常路径下不构造它的网格数据也行，但留着更安全）。 */
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

        poseStack.pushPose();
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spin));
        poseStack.scale(SCALE, SCALE, SCALE);
        // 先平移（在缩放后的空间里）→ 球心落在实体原点 ✓
        poseStack.translate(-CENTER_X, -CENTER_Y, -CENTER_Z);

        if (!renderWithEngine(poseStack, buffer)) {
            // 兜底：老球（代码搭的 7 方块），至少不会变成紫黑方块 ✗
            VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(OLD_TEXTURE));
            this.fallbackModel.renderToBuffer(poseStack, consumer, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * 走<b>引擎渲染投射物的同一条路</b> ✓。
     *
     * <p>引擎类引用放在这个独立方法里 ＋ 外面 try/catch（软依赖）：引擎不在时抛
     * {@code NoClassDefFoundError}（也是 Throwable ✓）→ 直接走兜底 ✓，不影响别的渲染。
     *
     * @return true ＝ 引擎画成功
     */
    private static boolean renderWithEngine(PoseStack poseStack, MultiBufferSource buffer) {
        try {
            RenderType layer = net.spell_engine.client.render.SpellModelHelper.LAYERS
                    .get(net.spell_engine.api.render.LightEmission.RADIATE);
            net.spell_engine.api.render.CustomModels.render(layer, Minecraft.getInstance().getItemRenderer(),
                    MODEL_ID, poseStack, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            if (!logged) {
                logged = true;
                LOGGER.info("TN-C: 环绕雷球 <- 引擎渲染路径 {} layer={} ✓", MODEL_ID, layer);
            }
            return true;
        } catch (Throwable t) {
            if (!logged) {
                logged = true;
                LOGGER.warn("TN-C: 环绕雷球引擎渲染失败，退回旧模型（{}）", t.toString());
            }
            return false;
        }
    }
}
