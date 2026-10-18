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
 * {@link TNTornadoEntity#spin()}（服务端每 tick 加 {@link TNTornadoEntity#SPIN_PER_TICK} 度、
 * 并同步给客户端 ✓）
 * 绕 <b>Y 轴</b>转出来的 ✓。所以转不转、转多快都归实体管 ✓，
 * 客户端不自己计时 ✗（否则两边不同步，/reload 之后会跳一下 ✗）。
 *
 * <h2>★★ 那 −0.5 格：引擎**确实**平移了，但我们**不能**再补一次（2026-10-10 修）</h2>
 * 作者实机反馈：「**龙卷风怎么不是圆心旋转**」✗ —— 它会绕着身边一个小圈甩。
 *
 * <p><b>实测（反编译实例里那份 {@code spell_engine-0.15.12+1.20.1.jar}）</b>：
 * {@code CustomModels.render} 在画之前**一定有这一句** ——
 * {@code class_4587.method_22904(-0.5, -0.5, -0.5)}（＝ {@code poseStack.translate(-0.5,-0.5,-0.5)}）✓。
 * 它把"方块模型空间"（0..16 单位 ＝ 0..1 格、原点在角上）挪成**以原点为中心** ✓。
 *
 * <p>而本模型的包围盒是 <b>x/z 以 (8,8) 单位为中心</b>（＝ 0.5 格）⇒ 引擎那一句
 * **正好把它对到实体中轴** ✓。所以本渲染器**不该再补任何 x/z 平移** ✗
 * —— 补了就多偏 0.5 格，转起来就变成"绕圈甩" ✗（这就是作者看到的现象 ✓）。
 *
 * <p>唯一要补的是 <b>y</b>：本模型的底边在 {@code y=0}（漏斗站在地上 ✓，
 * 不是居中 ✗），引擎的 −0.5 会把它**沉下去半格** ✗ ⇒ 补回 {@code +0.5}，
 * 让"模型底边 = 实体脚下" ✓。
 *
 * <p>合起来就是 {@code translate(0, +0.5, 0)} ✓ —— x/z 交给引擎、y 自己补。
 * ⚠️ 别照抄黑夜之手/环绕雷球那两份的 {@code (+0.5,+0.5,+0.5)} ✗：那两份的模型
 * 是按另一套口径摆的，照抄就会歪（本文件以前就是照抄来的 ✗）。
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
        //    ⚠️ 那份每 tick 的增量**读实体的常量** ✗ 不要在这里写死数字 ✗
        //    （以前写死 9.0F ⇒ 作者让"加快三倍"时，这份插值会和同步值错位 ✗）
        float spin = (float)entity.visualSpin(partialTick);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spin));
        // ② 按档位放大
        poseStack.scale(scale, scale, scale);
        // ③ 只补 y：x/z 的中轴已经由引擎那句 −0.5 对齐了（见类注释 ✗ 不能再补 ✗）
        poseStack.translate(0.0F, 0.5F, 0.0F);

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