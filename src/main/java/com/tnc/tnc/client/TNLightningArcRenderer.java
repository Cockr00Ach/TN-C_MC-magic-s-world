package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import com.tnc.tnc.client.lightning.LightningTheme;
import com.tnc.tnc.magic.TNLightningArcEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * 画 {@link TNLightningArcEntity}：一段<b>横着走的新雷电</b>（和落雷/雷龙同一套几何）✓。
 *
 * <h2>2026-11-05 作者："闪电链的 t1 和 t3 怎么没特效一样"</h2>
 * 之前 t1 只有引擎给的几颗电花粒子（{@code CURSOR} 瞬间结算，连投射物都没有）；
 * t3 虽然劈了一根落雷，但"雷从手上出去"这一截是空的 ⇒ 两档看着都像"什么也没发生"✗。
 * 现在统一用这道电弧补上：<b>起手 → 落点</b>连成一条，看得出是谁放的、打到哪 ✓。
 *
 * <p>细节：
 * <ul>
 *   <li>每 2 tick 换一次 {@code seed} ⇒ 电弧一直"抖"，不是一张静止贴图 ✓</li>
 *   <li>主干 + 2 条分叉（{@code bolt} 自带四层：外晕/中层/白芯/高光）✓</li>
 *   <li>落点默认补一圈地网 + 地光 + 一点爆闪（{@code impact}）✓</li>
 * </ul>
 */
public class TNLightningArcRenderer extends EntityRenderer<TNLightningArcEntity> {

    private static final ResourceLocation TEXTURE = LightningRenderTypes.WHITE_TEXTURE;

    public TNLightningArcRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TNLightningArcEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(TNLightningArcEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        int age = entity.tickCount;
        int life = entity.life();
        if (age > life) {
            return;
        }
        // 前 2 tick 涨起来、最后 2 tick 灭掉 ⇒ 不会"啪"一下就没了 ✓
        float fadeIn = Math.min(1.0F, (age + partialTick) / 2.0F);
        float fadeOut = Math.min(1.0F, (life - age + partialTick) / 2.0F);
        float alpha = Math.max(0.0F, Math.min(fadeIn, fadeOut));
        if (alpha <= 0.004F) {
            return;
        }

        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 at = entity.getPosition(partialTick);
        double cx = cam.x - at.x;
        double cy = cam.y - at.y;
        double cz = cam.z - at.z;

        double ex = entity.dx();
        double ey = entity.dy();
        double ez = entity.dz();
        double len = Math.sqrt(ex * ex + ey * ey + ez * ez);
        if (len < 1.0E-4D) {
            return;
        }

        int theme = LightningTheme.clamp(entity.theme());
        var m = poseStack.last().pose();

        // 每 2 tick 换种子：电在抖 ✓
        long seed = entity.getId() * 1315423911L + (age / 2) * 7919L;
        float w = entity.width();
        float a = alpha * 0.95F;

        // ★ 2026-11-05 作者："闪电要是横着来是不是就是黑线和血线了" ⇒ 暗系电弧不画白芯，
        //   改成**黑芯 ＋ 血红边**（普通 alpha 混合，见 LightningRenderTypes.DARK）✓
        if (theme == LightningTheme.DARK) {
            VertexConsumer dvc = buffer.getBuffer(LightningRenderTypes.DARK);
            LightningBoltGeometry.darkBolt(dvc, m, 0.0D, 0.0D, 0.0D, ex, ey, ez, seed, w, a,
                    cx, cy, cz);
            if (entity.impact()) {
                float radius = (float) Math.min(3.2D, Math.max(0.55D, w * 26.0D));
                // 落点不是"雷网"，是两圈往回收的暗红细线（黑线环）✓
                LightningBoltGeometry.darkRing(dvc, m, ex, ey, ez, radius, 16, seed ^ 0x51EDL,
                        0.045F, a * 0.65F, age * 0.04D, cx, cy, cz);
                LightningBoltGeometry.darkRing(dvc, m, ex, ey, ez, radius * 0.52D, 12, seed ^ 0x9E37L,
                        0.035F, a * 0.50F, -age * 0.06D, cx, cy, cz);
            }
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }

        VertexConsumer vc = buffer.getBuffer(LightningRenderTypes.BOLT);

        // 主干（四层：晕/中/白芯/高光）—— 分叉按长度给，短电弧不要太多叉 ✓
        int branches = len > 6.0D ? 3 : (len > 2.5D ? 2 : 1);
        LightningBoltGeometry.bolt(vc, m, 0.0D, 0.0D, 0.0D, ex, ey, ez, seed, w, a, theme, branches,
                cx, cy, cz);

        // 起手/落点各一个小电花团（让两端"接上"，不然像凭空浮着的一根 ✓）
        LightningBoltGeometry.point(vc, m, 0.0D, 0.0D, 0.0D, Math.max(0.16F, w * 4.0F), a * 0.75F,
                theme, cx, cy, cz);

        if (entity.impact()) {
            float radius = (float) Math.min(3.2D, Math.max(0.55D, w * 26.0D));
            LightningBoltGeometry.groundWeb(vc, m, ex, ey, ez, radius, seed ^ 0x51EDL,
                    theme, a * 0.55F, age * 0.05F, cx, cy, cz, true);
            LightningBoltGeometry.groundFlash(vc, m, ex, ey, ez, radius * 0.72F, theme, a * 0.30F);
            LightningBoltGeometry.billboardFlash(vc, m, ex, ey, ez, radius * 0.55F, theme, a * 0.55F,
                    cx, cy, cz);
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
