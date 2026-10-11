package com.tnc.tnc.client;

import com.tnc.tnc.magic.fire.TNSolarJudgmentField;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * <b>射线链 t5「太阳の审判」的渲染器</b>（作者 2026-10-05 设计）✓
 *
 * <h2>画什么</h2>
 * <p>只画<b>脚下那两座巨大法阵</b>（两层叠着、外层反向自转 ✓）。
 * <b>太阳光球</b>与<b>从太阳往阵内倾泻的光线</b>都不在这里画 —— 它们已经全改成
 * {@link TNSolarJudgmentField} 每 tick 撒的粒子 ✓
 * （作者：「将上方的球体和发射的射线改为粒子状态」✓；射线的数量与落点也在那边 ✓）
 *
 * <p>⚠️ 坐标一律<b>相对实体位置</b> ✗（{@code EntityRenderer} 的 pose 已经平移到实体位置 ✓）
 */
public final class TNSolarJudgmentRenderer extends EntityRenderer<TNSolarJudgmentField> {

    private static final ResourceLocation PLACEHOLDER =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");

    public TNSolarJudgmentRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TNSolarJudgmentField entity) {
        return PLACEHOLDER;
    }

    @Override
    public boolean shouldRender(TNSolarJudgmentField field, Frustum frustum, double x, double y, double z) {
        return field.distanceToSqr(x, y, z) < 200.0D * 200.0D;
    }

    @Override
    public void render(TNSolarJudgmentField field, float yaw, float partial, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        VertexConsumer out = buffers.getBuffer(WaterRenderTypes.geometry());
        Matrix4f pose = stack.last().pose();

        double age = field.tickCount + partial;
        double radius = field.radius();
        if (radius < 0.5D) {
            return;
        }
        // ⚠️ sunPosition() 是世界坐标 ✓ ⇒ 换相对坐标 ✗
        Vec3 sun = field.sunPosition().subtract(field.position());

        // ★ 2026-10-05：作者「t4/t5 中的法阵有问题，不好修就把火球链的法阵拿来用」✓
        //   ⇒ 直接**复用火球链那座**（TNSigilRenderer，照作者参考图画的那版 ✓）
        //     自己手搓的那套 drawSigil 已删除 ✗（两套法阵叠在一起就是"有问题"的来源 ✗）
        // 出场渐显（照 TNSigilRenderer 的写法 ✓，免得"啪"地凭空出现 ✗）
        float fade = (float) Math.min(1.0D, (field.tickCount + partial) / 8.0D);
        // ★ 2026-10-05：作者「t5 的法阵用 t4 那个，再在 t4 的基础上**多一些线条与粒子**」✓
        //   ⇒ 第一层照 t4 一样（半径 15 ✓），**再叠一层**略大、反向自转的 ✓
        //     两层线条交错 ⇒ 比 t4 明显更繁复 ✓
        TNSigilRenderer.drawSigil(out, pose, Vec3.ZERO, 15.0D, true, age, fade);
        TNSigilRenderer.drawSigil(out, pose, Vec3.ZERO, 17.5D, true, -age * 1.4D, fade * 0.75F);
        // （太阳与光线现在都由 TNSolarJudgmentField 每 tick 撒粒子 ✓，渲染器不再画几何体 ✗）
    }
}
