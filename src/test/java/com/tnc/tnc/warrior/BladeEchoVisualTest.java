package com.tnc.tnc.warrior;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 幻影刃的闸门（2026-11-13 作者：「幻影刃的大小变为现在的三倍，并且可以再透明点」）✓。
 *
 * <p>守：
 * <ul>
 *   <li>长度真的 ×3（拿 sink 收到的第一个/第三个顶点量 root→tip 的距离）✓</li>
 *   <li>透明度更低（单层 alpha &lt; 0.06；下游 {@code ab < .15} 的约束照样满足）✓</li>
 *   <li>四边形数量不变（3 条 × 3 层 = 9）⇒ 顶点预算不变 ✓</li>
 * </ul>
 */
class BladeEchoVisualTest {

    @Test
    void theEchoIsThreeTimesLonger() {
        assertEquals(3.0D, WarriorBladeEcho.ECHO_SCALE, 1.0E-9D, "幻影刃没有放大三倍 ✗");

        var points = new ArrayList<WarriorTrailVolume.Point>();
        for (int i = 0; i < 64; i++) {
            points.add(new WarriorTrailVolume.Point(Vec3.ZERO, new Vec3(i * 0.01D, 2.0D, 0.0D), i));
        }
        double[] length = new double[1];
        WarriorTrailVolume.Sink sink = (a, b, c, d, color, aa, ab, ac, ad) -> {
            if (length[0] == 0.0D) {
                length[0] = a.distanceTo(c);          // root → tip
            }
        };
        WarriorBladeEcho.emit(points, 64.0D, 100.0D, 1.0D, 0xffffff, sink);

        // 最后一个点：hilt=(0,0,0)、tip=(0.63,2,0) ⇒ 刃长 |blade| = √(0.63²+4)
        double blade = Math.sqrt(0.63D * 0.63D + 4.0D);
        double expected = blade * 0.7D * WarriorBladeEcho.ECHO_SCALE;  // root 在 0.3 处，tip 在 1.0 处
        assertEquals(expected, length[0], 0.15D, "幻影刃的长度不是三倍 ✗");
    }

    @Test
    void theEchoIsMoreTransparentThanBefore() {
        assertTrue(WarriorBladeEcho.ALPHA < 0.06F,
                "单层不透明度 " + WarriorBladeEcho.ALPHA + " 还是太实（作者要「再透明点」）✗");
        assertTrue(WarriorBladeEcho.ALPHA < 0.13F, "没有比原来（0.13）更透明 ✗");

        var points = new ArrayList<WarriorTrailVolume.Point>();
        for (int i = 0; i < 64; i++) {
            points.add(new WarriorTrailVolume.Point(Vec3.ZERO, new Vec3(i * 0.01D, 2.0D, 0.0D), i));
        }
        int[] quads = {0};
        float[] maxAlpha = {0.0F};
        WarriorTrailVolume.Sink sink = (a, b, c, d, color, aa, ab, ac, ad) -> {
            quads[0]++;
            maxAlpha[0] = Math.max(maxAlpha[0], Math.max(Math.max(aa, ab), Math.max(ac, ad)));
        };
        WarriorBladeEcho.emit(points, 64.0D, 100.0D, 8.0D, 0xffffff, sink);
        assertEquals(9, quads[0], "幻影刃的四边形数量变了（预算会跟着变）✗");
        assertTrue(maxAlpha[0] < 0.06F, "实际画出来的 alpha " + maxAlpha[0] + " 还是太实 ✗");
    }
}
