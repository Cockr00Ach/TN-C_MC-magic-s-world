package com.tnc.tnc.client.lightning;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * ★ <b>把一整批顶点染成半透明</b>的 {@link MultiBufferSource} 包装器
 * （2026-11-05 第二批：作者"那个闪电登神人物背后的雷神怎么也没有变透明"）。
 *
 * <h2>为什么需要它</h2>
 * 神 / 雷球 / 跟随神都是<b>引擎的 baked item model</b>，由
 * {@code net.spell_engine.api.render.CustomModels#render} 画 ——
 * 那条路径里<b>顶点颜色的 alpha 是写死的 1.0</b>（{@code putBulkData(..., 1,1,1,1, ...)}），
 * 唯一的透明度来源是<b>贴图自己的 alpha</b>。
 * 于是模型上"实心"的那部分（god 的甲、脸，约占 40% 像素 alpha=255）永远是<b>不透</b>的 ✗，
 * 只有镂空处能看到后面 —— 观感就是"一尊实心雕像"，而不是作者要的"雷神是一团半透明的光"✗。
 *
 * <p>换 RenderType 解决不了这件事（贴图 alpha 就那样），所以这里在
 * {@link VertexConsumer} 这一层<b>把 alpha 乘一个系数</b>：
 * 模型照常画、贴图照常采样，只是每个顶点都淡下去 ⇒ 真正"看得见后面"的雷神 ✓。
 *
 * <p>只包 {@code getBuffer} 返回的那一层，<b>不影响</b>其它 draw（辉光、闪电几何仍走原 buffer ✓）。
 * 未知/非顶点路径（比如 {@code endVertex} 之外的实现）全部原样透传 ⇒ 不会破坏任何渲染状态 ✓。
 */
public final class AlphaFadeBuffer implements MultiBufferSource {

    private final MultiBufferSource delegate;
    private final float alpha;

    private AlphaFadeBuffer(MultiBufferSource delegate, float alpha) {
        this.delegate = delegate;
        this.alpha = alpha;
    }

    /**
     * @param source 原 buffer source
     * @param alpha  0~1；{@code >= 0.999} 直接返回原对象（不额外分配）✓
     */
    public static MultiBufferSource of(MultiBufferSource source, float alpha) {
        if (source == null) {
            return null;
        }
        if (alpha >= 0.999F) {
            return source;
        }
        return new AlphaFadeBuffer(source, Math.max(0.02F, Math.min(1.0F, alpha)));
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return new Faded(this.delegate.getBuffer(type), this.alpha);
    }

    /** 逐顶点转发 + 只改 alpha ✓ */
    private record Faded(VertexConsumer inner, float alpha) implements VertexConsumer {

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.inner.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int a) {
            this.inner.color(red, green, blue, (int) (a * this.alpha));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            this.inner.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            this.inner.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            this.inner.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            this.inner.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            this.inner.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int a) {
            this.inner.defaultColor(red, green, blue, (int) (a * this.alpha));
        }

        @Override
        public void unsetDefaultColor() {
            this.inner.unsetDefaultColor();
        }
    }
}
