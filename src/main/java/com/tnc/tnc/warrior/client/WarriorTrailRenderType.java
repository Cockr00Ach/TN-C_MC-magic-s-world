package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Vanilla emissive entity routing: translucent, two-sided and colour-only depth mask. */
final class WarriorTrailRenderType {
    static final RenderType TYPE=RenderType.entityTranslucentEmissive(
            new ResourceLocation("tnc","textures/entity/beam_white.png"));

    /**
     * 刀光贴图有<b>三档重心</b>（都见 {@code tools/gen_trail_texture.py}）：
     * <ul>
     *   <li>{@link #PLUME} 均衡：两侧羽化 + 中间斜丝 —— 长剑 / 重剑 / 长枪；</li>
     *   <li>{@link #PLUME_EDGE} 边缘重：中间压暗、两头亮 —— <b>镰刀</b>（刃在两头，光跟着刃走）；</li>
     *   <li>{@link #PLUME_CORE} 中心重：中间最实、两侧收 —— <b>战锤</b>（钝器是一团闷光）。</li>
     * </ul>
     * 其它效果（剑气/剑波/终结技/武器纹饰）继续用 {@link #TYPE}：它们的四边形语义不一样，
     * 不该被这张贴图的渐隐影响。
     */
    private static final ResourceLocation PLUME_TEXTURE=
            new ResourceLocation("tnc","textures/particle/trail_plume.png");
    private static final ResourceLocation PLUME_EDGE_TEXTURE=
            new ResourceLocation("tnc","textures/particle/trail_plume_edge.png");
    private static final ResourceLocation PLUME_CORE_TEXTURE=
            new ResourceLocation("tnc","textures/particle/trail_plume_core.png");
    static final RenderType PLUME=RenderType.entityTranslucentEmissive(PLUME_TEXTURE);
    static final RenderType PLUME_EDGE=RenderType.entityTranslucentEmissive(PLUME_EDGE_TEXTURE);
    static final RenderType PLUME_CORE=RenderType.entityTranslucentEmissive(PLUME_CORE_TEXTURE);

    private WarriorTrailRenderType() {}

    static VertexConsumer buffer(MultiBufferSource buffers){
        return new WarriorEffectVertexConsumer(buffers.getBuffer(TYPE));
    }

    /**
     * 刀光的顶点消费者：额外按几何算 UV，让贴图真的铺在光带上（而不是全带一个点）。
     *
     * @param vFrequency 沿轨迹每格多少个贴图周期 —— 每种武器不一样（锤/重剑更疏、枪更密），
     *                   这是除颜色外第二条"一眼认出是哪把武器"的线索
     * @param variant    重心档位：{@link WarriorTrail#PLUME_BALANCED} /
     *                   {@code PLUME_EDGE} / {@code PLUME_CORE}
     */
    static VertexConsumer plume(MultiBufferSource buffers,double vFrequency,int variant){
        RenderType type=switch(variant){
            case WarriorTrail.PLUME_EDGE->PLUME_EDGE;
            case WarriorTrail.PLUME_CORE->PLUME_CORE;
            default->PLUME;
        };
        return new WarriorTrailUVConsumer(buffers.getBuffer(type),vFrequency);
    }

    /**
     * 只要丝线、不挑重心时的写法（剑气/穿刺轨迹/终结技那些"非刀光"的轨迹都用这一档）：
     * 它们没有武器家族语义，用均衡那张最稳。
     */
    static VertexConsumer plume(MultiBufferSource buffers,double vFrequency){
        return plume(buffers,vFrequency,WarriorTrail.PLUME_BALANCED);
    }
}
