package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNTornadoEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * ★ <b>龙卷风的渲染器</b>（风系范围风链 t3+，2026-10-10）✓
 *
 * <p>走的是**引擎渲染投射物的同一条通道** ✓（{@code SpellModelHelper.LAYERS} ＋
 * {@code CustomModels.render}）—— 和黑夜之手（{@code TNNightGripRenderer}）、
 * 环绕雷球（{@code TNThunderOrbRenderer}）一模一样的做法，那条路已经验证能显示 ✓。
 * 模型用 {@code tnc:projectile/wind_tornado}（已烘焙进
 * {@link TNProjectileModels#PROJECTILE_MODELS} ✓）。
 *
 * <h2>★ "一直在旋转"是怎么来的</h2>
 * 模型本身是**静态**的 ✗（生成器只出几何）—— 旋转是这里每帧按实体的
 * {@link TNTornadoEntity#spin()}（服务端每 tick +9°、并同步给客户端 ✓）
 * 绕 <b>Y 轴</b>转出来的 ✓。所以转不转、转多快都归实体管 ✓，
 * 客户端不自己计时 ✗（否则两边不同步，/reload 之后会跳一下 ✗）。
 *
 * <h2>★ 引擎内部那 −0.5 格（这个坑踩过）</h2>
 * {@code CustomModels.render} 内部先 {@code translate(−0.5,−0.5,−0.5)}，而那是
 * **已经缩放过**的坐标系 ⇒ 实际偏移 = {@code scale × 0.5} 格 ✓。
 * 本模型的包围盒在 x/z 上居中、y 从 0 起（底边贴地 ✓），所以缩放后补
 * {@code (+0.5, +0.5, +0.5)} 正好把底面中心对到实体原点 ✓
 * （推导见 {@code TNThunderOrbRenderer} 的同名注释 —— 它当初没补，整整歪了 2 格 ✗）。
 */
public class TNTornadoRenderer extends EntityRenderer<TNTornadoEntity> {

    /** 给引擎的模型号 —— <b>普通 ResourceLocation</b>，不要加 {@code #standalone} 变体 ✗。 */
    private static final ResourceLocation MODEL_ID = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, TNProjectileModels.WIND_TORNADO);

    /** 贴图（模型自带的那张 ✓；引擎自己会读，这里只是 {@code getTextureLocation} 的要求）。 */
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, "textures/spell_projectile/wind_tornado.png");

    public TNTornadoRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;       // 龙卷风不投影子 ✓（它是风，不是实体 ✗）
    }

    @Override
    public ResourceLocation getTextureLocation(TNTornadoEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(TNTornadoEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float scale = (float) entity.sizeScale();

        poseStack.pushPose();
        // ① ★ 转起来 ✓ —— 绕 Y 轴按实体给的转角（+ partialTick 让帧间平滑 ✓）
        float spin = entity.spin() + (partialTick * 9.0F);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spin));
        // ② 按档位放大，再补回引擎内部那 −0.5 格 ✓
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.5F, 0.5F, 0.5F);

        renderWithEngine(poseStack, buffer);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * 走<b>引擎渲染投射物的同一条路</b> ✓（软依赖：引擎不在时抛 {@code NoClassDefFoundError}，
     * 那也是 {@code Throwable} ⇒ 接住即可，绝不让它崩 ✓）。
     */
    private static void renderWithEngine(PoseStack poseStack, MultiBufferSource buffer) {
        try {
            net.minecraft.client.renderer.RenderType layer =
                    net.spell_engine.client.render.SpellModelHelper.LAYERS
                            .get(net.spell_engine.api.render.LightEmission.RADIATE);
            net.spell_engine.api.render.CustomModels.render(layer,
                    net.minecraft.client.Minecraft.getInstance().getItemRenderer(), MODEL_ID,
                    poseStack, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } catch (Throwable ignored) {
            // 引擎不在/渲染失败：什么都不画 ✓（风的表现还有粒子 ⇒ 不会"啥都看不见" ✓）
        }
    }
}