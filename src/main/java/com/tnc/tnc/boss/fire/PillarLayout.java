package com.tnc.tnc.boss.fire;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.RandomSource;

/**
 * 熔岩之柱（任务 7）的柱位表：服务端与客户端共用同一套推导，避免“看到的柱子”和“判定的柱子”不一致。
 *
 * <p>种子用施法开始时刻 {@code gameTime}（该值随 {@code DATA_PILLAR_START} 同步到客户端），
 * 因此两端拿到完全相同的 5–7 个柱位；后续调整间距/数量只改这里。</p>
 */
public final class PillarLayout {

    /** 走廊长度（格），从身体前缘起算。 */
    public static final int LENGTH = 18;
    /** 走廊宽度（格）。 */
    public static final int WIDTH = 6;
    public static final int MIN_COUNT = 5;
    public static final int MAX_COUNT = 7;

    /** 一个柱位：forward 沿锁定的前进方向、lateral 沿右方向（单位格）。 */
    public record Spot(double forward, double lateral) {
    }

    private PillarLayout() {
    }

    public static List<Spot> spots(long startTick) {
        RandomSource random = RandomSource.create(startTick * 2654435761L + 0x5F3759DFL);
        int count = MIN_COUNT + random.nextInt(MAX_COUNT - MIN_COUNT + 1);
        List<Spot> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double t = count <= 1 ? 0.5D : i / (double) (count - 1);
            double forward = 3.0D + t * (LENGTH - 5.0D);
            // 左右交错，避免一条直线；抖动限制在走廊宽度内
            double lateral = (i % 2 == 0 ? -1.0D : 1.0D) * (0.9D + random.nextDouble() * 0.7D);
            out.add(new Spot(forward, lateral));
        }
        return out;
    }
}
