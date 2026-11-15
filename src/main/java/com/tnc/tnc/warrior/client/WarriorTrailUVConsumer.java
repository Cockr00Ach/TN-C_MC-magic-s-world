package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraftforge.client.model.pipeline.VertexConsumerWrapper;

/**
 * 给刀光的四边形<b>算 UV</b> 的顶点消费者：让贴图（`trail_plume.png`）的软边贴在光带上，
 * 而不是整条光带共用贴图里的同一个点。
 *
 * <p>为什么不能用常量 UV（{@code .5,.5}，别的效果就是这么干的）：那样一张再好的渐变贴图
 * 也只会取到中间一个点，光带的边依旧是四边形切出来的硬边 —— "贴纸感"正是这么来的。
 *
 * <p>UV 怎么来：`WarriorTrailVolume` 每段发 7 个四边形，每个四边形都是"沿剑身（内→外）×
 * 沿时间（上一帧→这一帧）"的薄片。哪个方向是"横跨光带"，接口里没告诉消费者，所以这里按几何判断，
 * 并且<b>沿用上一片的方向</b>（时间连贯）：
 * <ol>
 *   <li>第一片没有参照，取整片中间那一行（u=0.5）；</li>
 *   <li>之后每片的"横跨方向" = 上一片两条边里更长的那条（主面片那条边是剑身方向，
 *       比一个时间步长大一个量级；羽毛片/侧条则跟着主面片的方向走，不会每隔一段转一次）。</li>
 * </ol>
 * 贴图左右对称（见 {@code tools/gen_trail_texture.py}），所以 u 从哪头算都不影响观感。
 *
 * <p>UV 必须在 {@code endVertex()} 之前写进去，而一个四边形的四个顶点是<b>逐个</b>进来的，
 * 所以能用的参照只能是**上一片**已经完整的四个点 —— 对连续的光带来说差一片完全看不出来。
 */
final class WarriorTrailUVConsumer extends VertexConsumerWrapper {
    private final double[] xs = new double[4];
    private final double[] ys = new double[4];
    private final double[] zs = new double[4];
    /** 四边形里已经收到几个顶点（0..3）。 */
    private int slot;
    /** 当前这一片的"横跨方向"，取上一片算出来的：沿边投影 (d·n)/|n|² 直接得到 0..1。 */
    private double ax, ay, az;
    private boolean hasAxis;
    /**
     * v 是"沿轨迹走了多远"（格）× 频率。不用"四边形序号"，因为一段刀光会发 7 片，
     * 序号会让 v 每片跳一次 → 丝线被切成一段段的；用**累积弧长**则只有真的往前走时才推进，
     * 同一段的 7 片 v 几乎一样，丝线就贴在光带上随刀走（不会像贴在世界上那样"游动"）。
     */
    private final double vFrequency;
    private double arcLength;
    private double lastCx, lastCy, lastCz;
    private boolean hasCenter;

    WarriorTrailUVConsumer(VertexConsumer parent, double vFrequency) {
        super(parent);
        this.vFrequency = vFrequency;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        if (slot == 0) {
            // 上一片的四个点还在数组里 —— 用它定方向、并推进弧长，再覆盖
            pickAxisFromPrevious();
            advanceArc();
        }
        xs[slot] = x;
        ys[slot] = y;
        zs[slot] = z;
        return super.vertex(x, y, z);
    }

    @Override
    public void endVertex() {
        int index = slot;
        parent.uv(horizontal(index), vertical())
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(0, 1, 0);
        parent.endVertex();
        slot = (slot + 1) & 3;
    }

    /** 上一片四个点的中心走了一步就推进弧长；同段的 7 片中心几乎重合，不会推进。 */
    private void advanceArc() {
        double cx = (xs[0] + xs[1] + xs[2] + xs[3]) * 0.25;
        double cy = (ys[0] + ys[1] + ys[2] + ys[3]) * 0.25;
        double cz = (zs[0] + zs[1] + zs[2] + zs[3]) * 0.25;
        if (hasCenter) {
            double dx = cx - lastCx;
            double dy = cy - lastCy;
            double dz = cz - lastCz;
            double step = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (step < 4.0) {
                arcLength += step;
            }
        }
        lastCx = cx;
        lastCy = cy;
        lastCz = cz;
        hasCenter = true;
    }

    /**
     * v：累积弧长 × 频率，取小数部分（贴图循环）。
     */
    private float vertical() {
        double v = arcLength * vFrequency;
        return (float) (v - Math.floor(v));
    }

    /** 这个顶点在"横跨光带"方向上的位置：0（一边）→ 1（另一边）。 */
    private float horizontal(int index) {
        if (!hasAxis) {
            return 0.5f;
        }
        double dx = xs[index] - xs[0];
        double dy = ys[index] - ys[0];
        double dz = zs[index] - zs[0];
        double along = dx * ax + dy * ay + dz * az;
        return (float) Math.max(0d, Math.min(1d, along));
    }

    /**
     * 用上一片的两条边定"横跨方向"：取更长的那条（主面片 = 剑身方向）。
     * 存成 {@code n/|n|²}，这样 {@code (d·a)} 直接就是 0..1 的位置。
     */
    private void pickAxisFromPrevious() {
        double e1x = xs[1] - xs[0], e1y = ys[1] - ys[0], e1z = zs[1] - zs[0];
        double e3x = xs[3] - xs[0], e3y = ys[3] - ys[0], e3z = zs[3] - zs[0];
        double l1 = Math.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
        double l3 = Math.sqrt(e3x * e3x + e3y * e3y + e3z * e3z);
        double nx, ny, nz, len;
        if (l1 >= l3 && l1 > 1e-7) {
            nx = e1x; ny = e1y; nz = e1z; len = l1;
        } else if (l3 > 1e-7) {
            nx = e3x; ny = e3y; nz = e3z; len = l3;
        } else {
            hasAxis = false;
            return;
        }
        // 和上一片保持一致的方向（点积为负就翻过来），避免相邻片 u 反向导致花纹跳动
        if (hasAxis && (nx * ax + ny * ay + nz * az) < 0) {
            nx = -nx; ny = -ny; nz = -nz;
        }
        ax = nx / (len * len);
        ay = ny / (len * len);
        az = nz / (len * len);
        hasAxis = true;
    }
}
