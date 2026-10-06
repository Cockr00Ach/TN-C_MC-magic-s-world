package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * <b>给整块模型染色的 {@link VertexConsumer} 包装器</b> ✓
 *
 * <p>⚠️ 作者 2026-10-05：「把龙头颜色改为**赤红色**与身体搭配」✓
 *
 * <p>为什么需要这个：原版末影龙头的模型是**方块模型** ✓，
 * 由 {@code BlockRenderDispatcher.renderSingleBlock(...)} 画出来 ✓ ——
 * 而那条路径给顶点的颜色**永远是白色** ✗（没有染色入口 ✗）。
 *
 * <p>所以这里套一层 ✓：把内层吐出来的每个 {@code color(...)} **乘上我们的染色** ✓，
 * 其余调用**原样转发** ✓ ⇒ 模型、贴图、朝向全都不动 ✓，只是整体变赤红 ✓
 *
 * <p>⚠️ 关键点：{@code vertex(...)} 必须返回 <b>this</b> ✗ —— 不能返回内层 ✓。
 * 原版模型的绘制代码是 {@code vc.vertex(...).color(...).uv(...)} 这样的**链式调用** ✓，
 * 如果 {@code vertex(...)} 返回了内层对象 ✗，链上后续的 {@code color(...)} 就绕过我这一层了 ✗
 * ⇒ 染色会**静默失效** ✗（这是这类包装器最常见的坑 ✓）
 */
public final class TintingVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final float tr;
    private final float tg;
    private final float tb;

    public TintingVertexConsumer(VertexConsumer delegate, float r, float g, float b) {
        this.delegate = delegate;
        this.tr = r;
        this.tg = g;
        this.tb = b;
    }

    // ---------------- 顶点：一律返回 this（保住链式染色 ✓）----------------

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer vertex(org.joml.Matrix4f pose, float x, float y, float z) {
        delegate.vertex(pose, x, y, z);
        return this;
    }


    // ---------------- 颜色：乘上染色 ✓ ----------------

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        delegate.color(
                Math.round(r * tr),
                Math.round(g * tg),
                Math.round(b * tb),
                a);
        return this;
    }

    @Override
    public VertexConsumer color(float r, float g, float b, float a) {
        delegate.color(r * tr, g * tg, b * tb, a);
        return this;
    }

    // ---------------- 其余原样转发 ✓ ----------------

    @Override
    public VertexConsumer uv(float u, float v) {
        delegate.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        delegate.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        delegate.endVertex();
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
        delegate.defaultColor(r, g, b, a);
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }
}
