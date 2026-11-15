package com.tnc.tnc.world.wind.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.client.WindBladeQi;
import com.tnc.tnc.client.WindTornadoLines;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import com.tnc.tnc.world.wind.CanglanTempestEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * ★ <b>苍岚（风系 Boss）的旋风和风刃</b> —— 2026-11-15 跟着玩家风法一起换成了"线"✓
 *
 * <h2>为什么改</h2>
 * 作者的玩家风法这十几轮已经换代三次：龙卷风 = 螺旋折线（{@code WindTornadoLines}）、
 * 风刃 = 剑气光带（{@code WindBladeQi}）、风球 = 漩涡线（{@code WindBallLines}）✓
 * 而 Boss 这边的 <b>旋风</b>还在画烘焙模型（7 层薄板）✗ ——
 * 同一个战场里玩家放出来是线、Boss 放出来是板子，像两套游戏 ✗。
 *
 * <p>现在：<b>旋风</b>用和玩家龙卷风完全同一套 {@link WindTornadoLines}（螺旋折线 ＋ 加色光带）✓，
 * 尺寸按 Boss 的 {@code visualScale} 放大；<b>风刃波</b>（非旋风模式）保留模型 ——
 * 那一发是"横着飞出去的一片刀光"，和玩家的风刃本来就不是同一个形状 ✓。
 */
public final class CanglanTempestRenderer extends EntityRenderer<CanglanTempestEntity> {

    public CanglanTempestRenderer(EntityRendererProvider.Context c) {
        super(c);
    }

    @Override
    public ResourceLocation getTextureLocation(CanglanTempestEntity e) {
        return LightningRenderTypes.WHITE_TEXTURE;
    }

    @Override
    public void render(CanglanTempestEntity e, float yaw, float partial, PoseStack p,
                       MultiBufferSource b, int light) {
        if (e.tornado()) {
            renderLineCyclone(e, partial, p, b);
            return;
        }
        // ★ 2026-11-15 作者「改啊」：风刃波也换成**玩家同款剑气**（{@link WindBladeQi}）✓
        //   以前这里画的是烘焙模型 projectile/windblade（一片贴图刀刃）✗；
        //   玩家侧的风刃早在 11-10 就换成了"四层渐隐的弯月剑气"，Boss 这一发和它不是一套语言 ✗。
        renderQiWave(e, partial, p, b);
    }

    /**
     * 风刃波：和玩家风刃**同一个几何**（{@code WindBladeQi}）✓
     *
     * <p>朝向：实体 YRot 就是飞行方向（{@code spawn} 里按 direction 设过）⇒
     * 只绕 Y 转一次，弯月的法线（+Z）就对着飞行方向 ✓（和玩家那个渲染器的约定一致）。
     * 尺寸按 Boss 体型放大；越接近寿命末尾越淡 ✓。
     */
    private static void renderQiWave(CanglanTempestEntity e, float partial, PoseStack p, MultiBufferSource b) {
        double age = e.tickCount + partial;
        float fade = Math.max(0.0F, 1.0F - (float) (age / Math.max(1.0F, 20.0F)));
        float alpha = Math.min(1.0F, fade * 1.15F);
        if (alpha <= 0.01F) {
            return;
        }
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var at = e.getPosition(partial);
        double camX = cam.x - at.x;
        double camY = cam.y - at.y;
        double camZ = cam.z - at.z;
        p.pushPose();
        p.mulPose(Axis.YP.rotationDegrees(e.getYRot()));
        Matrix4f m = p.last().pose();
        float halfWidth = (float) Math.max(0.7D, 2.2D * e.renderScale(partial));
        WindBladeQi.draw(b.getBuffer(LightningRenderTypes.BOLT), m, halfWidth, alpha);
        p.popPose();
    }

    /** 和玩家龙卷风同源：一束从下往上收的螺旋折线 ✓（单位＝格，所以不用再缩放）。 */
    private static void renderLineCyclone(CanglanTempestEntity e, float partial, PoseStack p, MultiBufferSource b) {
        double age = e.tickCount + partial;
        // 伤害半径就是 2.7 × visualScale（见实体），视觉比它略小一点，免得线飘到判定外面 ✗
        double radius = 2.7D * e.renderScale(partial) * 0.92D;
        if (radius < 0.3D) {
            return;
        }
        double spinRad = Math.toRadians((age * 28.0D) % 360.0D);
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var at = e.getPosition(partial);
        double camX = cam.x - at.x;
        double camY = cam.y - at.y;
        double camZ = cam.z - at.z;
        Matrix4f m = p.last().pose();
        VertexConsumer vc = b.getBuffer(LightningRenderTypes.BOLT);
        WindTornadoLines.draw(vc, m, spinRad, 0.85F,
                radius * 0.42D, radius, radius * 2.6D,
                camX, camY, camZ, e.getId());
    }
}
