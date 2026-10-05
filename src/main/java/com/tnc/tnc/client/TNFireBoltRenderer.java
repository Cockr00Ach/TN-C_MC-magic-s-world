package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.fire.FireSpellRules;
import com.tnc.tnc.magic.fire.TNFireBoltEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 火球的渲染器 —— <b>一颗真正的球</b>，不是一团散火星。
 *
 * <h2>为什么改成几何体（2026-10-05 作者反馈）</h2>
 * 上一版为了先把机制跑通，视觉全交给粒子（渲染器是空的）。作者实测后指出
 * 「<b>建模不对，要先形成一个球形再发射出去</b>」—— 粒子看着是"一团火花"，
 * 而不是"一颗火球" ✗。所以这里改成画实体几何：
 * <b>外层橙红球壳 + 中层亮橙 + 内层白热核心 + 三条火舌拖尾 + 两圈脉动光环</b>，
 * 和 {@code TNWaterBoltRenderer} 同一套做法。
 *
 * <h2>为什么能直接用水系那两个类</h2>
 * {@code WaterGeometry} / {@code WaterRenderTypes} 是<b>包内可见</b>的通用工具
 * （{@code quad} / {@code tube} / {@code radial} / {@code ringColor} 全都带颜色参数，
 * 本来就不是水系专用的，只是当年为水法写的所以沿用了这个名字）。
 * 本渲染器与它们同在 {@code com.tnc.tnc.client} 包内，可以直接用 ✓
 *
 * <p>{@code WaterRenderTypes.geometry()} 用的是 {@code RENDERTYPE_LIGHTNING_SHADER}
 * （自发光、不吃场景光照），正好是火球该有的样子 —— 在洞里也是亮的。
 *
 * <h2>大小跟着法术走</h2>
 * 半径从实体的同步字段 {@code radius()} 取（火球术 0.35 / 熔岩火球 0.50 / 大火球术 0.62），
 * 所以"大火球术的球确实更大"在表现上也成立 ✓
 */
public final class TNFireBoltRenderer extends EntityRenderer<TNFireBoltEntity> {

    /** 不会被真正贴图（几何体自带颜色），指向一张一定存在的原版贴图只为不触发缺图警告。 */
    private static final ResourceLocation PLACEHOLDER =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/particle/flame.png");

    public TNFireBoltRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.0F;
    }

    @Override
    public boolean shouldRender(TNFireBoltEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        return entity.distanceToSqr(x, y, z) < 160.0D * 160.0D;
    }

    @Override
    public ResourceLocation getTextureLocation(TNFireBoltEntity entity) {
        return PLACEHOLDER;
    }

    @Override
    public void render(TNFireBoltEntity entity, float yaw, float partial, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        var out = buffers.getBuffer(WaterRenderTypes.geometry());
        Matrix4f pose = stack.last().pose();

        Vec3 dir = entity.safeDirection();
        Vec3 right = FireSpellRules.right(dir);
        Vec3 up = right.cross(dir).normalize();

        // 成形阶段：球从 25% 长到 100%（作者要求"先形成一个球形再发射出去"）
        double radius = entity.radius() * entity.formProgress(partial);
        double age = entity.tickCount + partial;
        // 燃烧的呼吸感：两层球的透明度轻微波动（相位不同，看着像"在烧"）
        float flicker = (float) (0.82D + 0.18D * Math.sin(age * 0.55D));

        // ---- 外层球壳：橙红、半透明 ----
        sphere(out, pose, dir, right, up, radius * 1.00D, 1.00F, 0.42F, 0.06F, 0.72F * flicker);
        // ---- 中层：亮橙，小一圈，把"球"的实体感压出来 ----
        sphere(out, pose, dir, right, up, radius * 0.78D, 1.00F, 0.62F, 0.12F, 0.80F * flicker);
        // ---- 内层白热核心：最小最亮，是这个球最烫的地方 ----
        sphere(out, pose, dir, right, up, radius * 0.45D, 1.00F, 0.90F, 0.55F, 0.95F);

        // ---- 火舌拖尾：三条从球尾甩出去的火线 ----
        double tail = radius * 5.2D;
        for (int strand = 0; strand < 3; strand++) {
            for (int i = 0; i < 14; i++) {
                double t = i / 14.0D;
                double q = (i + 1) / 14.0D;
                double a = age * 0.42D + t * Math.PI * 3.0D + strand * Math.PI * 2.0D / 3.0D;
                double b = age * 0.42D + q * Math.PI * 3.0D + strand * Math.PI * 2.0D / 3.0D;
                Vec3 p = dir.scale(-tail * t).add(WaterGeometry.radial(right, up, a, radius * (1.0D - t) * 0.85D));
                Vec3 r = dir.scale(-tail * q).add(WaterGeometry.radial(right, up, b, radius * (1.0D - q) * 0.85D));
                WaterGeometry.tube(out, pose, p, r, radius * 0.10D,
                        1.00F, 0.50F - (float) t * 0.18F, 0.10F, (float) (0.65D * (1.0D - t)));
            }
        }

        // ---- 两圈脉动的光环：让"这是一颗球"从侧面看也成立 ----
        WaterGeometry.ringColor(out, pose, Vec3.ZERO, right, radius * 1.30D, radius * 0.08D,
                1.00F, 0.66F, 0.18F, 0.45F * flicker);
        WaterGeometry.ringColor(out, pose, Vec3.ZERO, up, radius * 1.22D, radius * 0.07D,
                1.00F, 0.80F, 0.30F, 0.35F * flicker);
    }

    /**
     * 画一颗球：沿飞行方向切成 10 圈，每圈再切成 20 段四边形。
     *
     * <p>参数化照抄 {@code TNWaterBoltRenderer} 里那颗水球（已验证可用），
     * 只把颜色换成火系 —— <b>不自己另发明一套球面参数</b>。
     */
    private static void sphere(VertexConsumer out, Matrix4f pose, Vec3 dir, Vec3 right, Vec3 up,
                               double radius, float r, float g, float b, float alpha) {
        for (int j = 0; j < 10; j++) {
            double a = -Math.PI / 2.0D + j * Math.PI / 10.0D;
            double c = a + Math.PI / 10.0D;
            for (int i = 0; i < 20; i++) {
                double u = i * Math.PI / 10.0D;
                double v = (i + 1) * Math.PI / 10.0D;
                Vec3 p = dir.scale(Math.sin(a) * radius).add(WaterGeometry.radial(right, up, u, Math.cos(a) * radius));
                Vec3 q = dir.scale(Math.sin(a) * radius).add(WaterGeometry.radial(right, up, v, Math.cos(a) * radius));
                Vec3 s = dir.scale(Math.sin(c) * radius).add(WaterGeometry.radial(right, up, v, Math.cos(c) * radius));
                Vec3 t = dir.scale(Math.sin(c) * radius).add(WaterGeometry.radial(right, up, u, Math.cos(c) * radius));
                WaterGeometry.quad(out, pose, p, q, s, t, r, g, b, alpha);
            }
        }
    }
}
