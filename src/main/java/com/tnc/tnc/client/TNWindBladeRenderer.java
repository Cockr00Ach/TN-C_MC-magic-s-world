package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNWindBladeEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * ★ <b>飞出去的风刃的渲染器</b>（龙卷风吐出来的那一片，2026-10-10）✓
 *
 * <p>用的就是 {@code tnc:projectile/windblade} 那片**月牙** ✓ —— 和 t1/t2 直接飞出去的
 * 风刃**同一个模型** ✓（作者要的"风刃"就是它；龙卷风只是换了个发射方式 ✓）。
 *
 * <h2>朝向</h2>
 * 模型的新月躺在 <b>XY 平面</b>上、薄的那一维是 Z ⇒ 只要按飞行方向做
 * {@code yaw → pitch} 两步旋转（和引擎 {@code SpellProjectileRenderer} 同一套算法 ✓），
 * 水平飞的时候刀口朝前 ✓。发射时服务端已经把 yaw/pitch 算好同步过来了 ✓，
 * 这里不自己反推方向 ✗（那样两边会不一致）。
 *
 * <h2>自转</h2>
 * 额外绕模型自身的 <b>Z 轴</b>转（{@link TNWindBladeEntity#spin()}）⇒ 飞出去像"斩"出来的 ✓
 * （作者的水法海浪斩击也是转着飞的 ✓）。转速由发射方给（龙卷风每发随机 18~28°/tick ✓）。
 *
 * <h2>★ 引擎内部那 −0.5 格：这里**不要**再补 ✗（2026-10-10 修）</h2>
 * 引擎 {@code CustomModels.render} 里那句 {@code translate(-0.5,-0.5,-0.5)}
 * （反编译实例里那份 spell_engine 核对过 ✓）已经把"方块模型空间"挪成**以原点为中心** ✓。
 * 而月牙模型的包围盒**正好以 (8,8,8) 单位为中心**（＝ 0.5 格）✓
 * ⇒ 引擎那一句就把它对到实体原点了 ✓。
 *
 * <p>所以这里**一个字都不能补** ✗ —— 以前跟着 {@link TNTornadoRenderer} 补了
 * {@code (+0.5,+0.5,+0.5)} ⇒ 月牙整体偏出 0.87 格（斜角）✗ ——
 * 判定在实体中心、画面却偏在半格外，"打中了但看着没打到"就是这么来的 ✗。
 */
public class TNWindBladeRenderer extends EntityRenderer<TNWindBladeEntity> {

    private static final ResourceLocation MODEL_ID = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, TNProjectileModels.WINDBLADE);

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, "textures/spell_projectile/windblade.png");

    public TNWindBladeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TNWindBladeEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(TNWindBladeEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float scale = (float) entity.sizeScale();

        poseStack.pushPose();
        // ① 朝向：yaw（绕 Y）→ pitch（绕已经转过去的 X）✓
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(entity.bladeYaw()));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(entity.bladePitch()));
        // ② 自转：绕模型自身那根"薄"的轴（Z）✓
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(entity.spin()));
        // ③ 缩放 —— **不要再 translate** ✗：月牙以 (8,8,8) 单位为中心，
        //    引擎那句 −0.5 已经把它对到实体原点了（见类注释 ✓）
        poseStack.scale(scale, scale, scale);

        renderWithEngine(poseStack, buffer);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static void renderWithEngine(PoseStack poseStack, MultiBufferSource buffer) {
        try {
            net.minecraft.client.renderer.RenderType layer =
                    net.minecraft.client.renderer.RenderType.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
            net.spell_engine.api.render.CustomModels.render(layer,
                    net.minecraft.client.Minecraft.getInstance().getItemRenderer(), MODEL_ID,
                    poseStack, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } catch (Throwable ignored) {
        }
    }
}
