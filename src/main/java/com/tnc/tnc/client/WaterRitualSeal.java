package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/**
 * 水法主阵的对外入口（只为 boss 包开一条缝）。
 *
 * <p>{@link WaterGeometry} 是包内私有的一整套水面几何，boss 渲染器在
 * {@code com.tnc.tnc.boss.client} 里看不到它。这里只暴露“起飞阵”一种形态：
 * 主阵 ＋ 两个卫星子阵，和玩家读条时脚下那圈是同一套画法，不另写一套近似品。
 */
public final class WaterRitualSeal {
    private static final Vec3 UP=new Vec3(0,1,0);

    private WaterRitualSeal() {
    }

    /**
     * 画一座平铺在 {@code center} 上的水法阵。
     *
     * @param buffers 世界渲染缓冲
     * @param stack   已经平移到法阵中心的 PoseStack
     * @param center  阵心（相对当前 pose 的局部坐标，通常是 {@link Vec3#ZERO}）
     * @param radius  主阵半径（格）
     * @param time    自转用的世界时间（tick 或帧均可，只影响转速）
     * @param alpha   0–1 的整体透明度
     */
    public static void launch(MultiBufferSource buffers, PoseStack stack, Vec3 center,
                              double radius, double time, float alpha) {
        WaterGeometry.richCircle(buffers, stack, center, UP, radius, time, alpha, 2, 3);
    }
}
