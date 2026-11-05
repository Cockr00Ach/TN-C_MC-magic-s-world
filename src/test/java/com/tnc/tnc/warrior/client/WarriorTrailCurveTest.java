package com.tnc.tnc.warrior.client;

import static com.tnc.tnc.warrior.client.Vec3Assert.assertVec;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tnc.tnc.warrior.WarriorTrailCurve;
import com.tnc.tnc.warrior.WarriorWeaponFamily;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * 刀光的几何不变量：这些是"看起来顺不顺"背后的硬指标。
 *
 * <p>渲染本身没法自动测，但曲线插值、"点够不够密"和"每把武器参数确实不同"是可以钉住的：
 * 一旦有人改错 Catmull-Rom 的系数、把自适应细分改回固定 3 个点，或者把三个 Profile 抄成一样，
 * 这里会先炸。
 */
class WarriorTrailCurveTest {

    @Test
    void catmullPassesThroughBothControlPoints() {
        Vec3 p0 = new Vec3(0, 0, 0);
        Vec3 p1 = new Vec3(1, 0, 0);
        Vec3 p2 = new Vec3(2, 1, 0);
        Vec3 p3 = new Vec3(3, 1, 0);
        assertVec(p1, WarriorTrailCurve.catmull(p0, p1, p2, p3, 0f), "t=0 必须落在 p1 上");
        assertVec(p2, WarriorTrailCurve.catmull(p0, p1, p2, p3, 1f), "t=1 必须落在 p2 上");
    }

    @Test
    void catmullStaysInsideTheNeighbourhood() {
        Vec3 p0 = new Vec3(0, 0, 0);
        Vec3 p1 = new Vec3(1, 0, 0);
        Vec3 p2 = new Vec3(2, 1, 0);
        Vec3 p3 = new Vec3(3, 1, 0);
        // 三个细分点都该夹在 p1、p2 的包围盒里（插值过冲会表现为"光带甩出去"）
        for (int s = 1; s < 4; s++) {
            Vec3 at = WarriorTrailCurve.catmull(p0, p1, p2, p3, s / 4f);
            assertTrue(at.x >= 0.9 && at.x <= 2.1, "细分点 x 跑出邻域：" + at);
            assertTrue(at.y >= -0.1 && at.y <= 1.1, "细分点 y 跑出邻域：" + at);
        }
    }

    /**
     * 自适应细分：慢挥少插、快挥多插，且每段转角不许超过上限。
     *
     * <p>这条是"顺滑"的核心。旧实现固定插 3 个点（每 120° 一圈的话够用，一秒两圈就成多边形）
     * —— 现在按角度算：每段 ≤ {@link WarriorTrailCurve#MAX_SEGMENT_DEGREES} 度。
     */
    @Test
    void subdivisionCountFollowsTheTurnAngle() {
        assertEquals(1, WarriorTrailCurve.subdivisions(0));
        assertEquals(1, WarriorTrailCurve.subdivisions(WarriorTrailCurve.MAX_SEGMENT_DEGREES));
        assertEquals(2, WarriorTrailCurve.subdivisions(8.0));
        assertEquals(WarriorTrailCurve.MAX_SUBDIVISIONS, WarriorTrailCurve.subdivisions(180.0));
        for (double degrees = 0; degrees <= 180; degrees += 1.0) {
            int steps = WarriorTrailCurve.subdivisions(degrees);
            assertTrue(steps >= 1 && steps <= WarriorTrailCurve.MAX_SUBDIVISIONS,
                    "段数越界：" + degrees + " -> " + steps);
            if (steps < WarriorTrailCurve.MAX_SUBDIVISIONS) {
                // 没撞上限时，每段转角必须在 4° 以内；一旦到 12 段封顶，就按上限走
                assertTrue(degrees / steps <= WarriorTrailCurve.MAX_SEGMENT_DEGREES + 1.0E-9,
                        "每段转角超过上限：" + degrees + " -> " + steps);
            } else {
                assertEquals(WarriorTrailCurve.MAX_SUBDIVISIONS, steps,
                        "撞上限之后只能一路 12 段：" + degrees);
            }
        }
        // 退化/非有限输入不能抛，也不能返回 0（0 段会让整条光带消失）
        assertEquals(1, WarriorTrailCurve.subdivisions(Double.NaN));
        assertEquals(1, WarriorTrailCurve.subdivisions(Double.POSITIVE_INFINITY));
        assertEquals(1, WarriorTrailCurve.subdivisions(null, new Vec3(0, 0, 1)));
        assertEquals(1, WarriorTrailCurve.subdivisions(new Vec3(0, 0, 0), new Vec3(0, 0, 1)));
        // 长度不同但方向一致 = 同一个转角：拆段数只看方向，不看剑有多长
        assertEquals(1, WarriorTrailCurve.subdivisions(new Vec3(0, 1, 0), new Vec3(0, 9, 0)));
        double tilted = Math.toDegrees(Math.atan2(0.6, 3.0));
        assertEquals(WarriorTrailCurve.subdivisions(tilted),
                WarriorTrailCurve.subdivisions(new Vec3(0, 1, 0), new Vec3(0, 3, 0.6)));
    }

    @Test
    void vertexBudgetShrinksDensityWithoutBreakingTheShape() {
        // 够用就不动
        int[] raw = {3, 4, 5};
        assertSame(raw, WarriorTrailCurve.limit(raw, 12), "预算大于总数时不该动");
        assertSame(raw, WarriorTrailCurve.limit(raw, 999));
        // 超了就等比降密：12+12+12 压到 9 → 每段 3
        int[] dense = {12, 12, 12};
        int[] limited = WarriorTrailCurve.limit(dense, 9);
        assertEquals(9, limited[0] + limited[1] + limited[2], "压完要正好落在预算上");
        assertEquals(3, limited[0]);
        // 每段至少 1：预算再紧也不能变成 0 段（0 段 = 那一段消失）
        int[] tight = WarriorTrailCurve.limit(dense, 2);
        for (int n : tight) {
            assertTrue(n >= 1, "每段至少 1");
        }
        // 大转角的那段降密后仍然 >= 小转角那段（不能把该细的地方反而压得更狠）
        int[] skew = {12, 1};
        int[] skewed = WarriorTrailCurve.limit(skew, 7);
        assertTrue(skewed[0] >= skewed[1], "原来更密的段压完不能比原来稀的段还稀");
        // 边界：null / 空 / 负预算都不能炸
        assertEquals(0, WarriorTrailCurve.limit(null, 5).length);
        assertEquals(0, WarriorTrailCurve.limit(new int[0], 5).length);
        int[] negative = WarriorTrailCurve.limit(dense, -1);
        assertSame(dense, negative, "负预算按'不限制'处理");
    }

    @Test
    void everyWeaponFamilyHasItsOwnSilhouette() {
        WarriorTrail.Profile sword = WarriorTrail.profile(WarriorWeaponFamily.LONGSWORD);
        WarriorTrail.Profile great = WarriorTrail.profile(WarriorWeaponFamily.GREATSWORD);
        WarriorTrail.Profile hammer = WarriorTrail.profile(WarriorWeaponFamily.HAMMER);

        assertTrue(great.life() > sword.life(), "重剑的斩气要比长剑留得久");
        assertTrue(great.outerStart() < sword.outerStart(), "重剑从更靠近握把的地方开始铺");
        assertTrue(hammer.outerAlpha() > sword.outerAlpha(), "战锤是一大片闷光，底部更厚");
        assertFalse(hammer.tipStreak(), "钝器没有刃口，不该画剑影");
        assertTrue(sword.tipStreak() && great.tipStreak(), "两把剑都要有剑影");
        assertTrue(great.samples() > sword.samples(), "重剑弧线更长，采样点更多");
    }

    @Test
    void fiveFamiliesSortIntoOneLadder() {
        // 每个家族都要能取到参数，且参数本身成立（不是 0、不是负数、起点在剑身上）
        for (WarriorWeaponFamily family : WarriorWeaponFamily.values()) {
            WarriorTrail.Profile profile = WarriorTrail.profile(family);
            assertTrue(profile.life() > 0 && profile.finisherLife() >= profile.life(), family + " 的寿命");
            assertTrue(profile.samples() > 1 && profile.finisherSamples() >= profile.samples(), family + " 的采样数");
            assertTrue(profile.outerAlpha() > 0f && profile.coreAlpha() > 0f, family + " 的亮度");
            assertTrue(profile.outerStart() >= 0d && profile.outerStart() < 1d, family + " 的起点要在剑身上");
            assertTrue(profile.coreStart() > profile.outerStart(), family + " 亮芯要比外圈更靠剑尖");
            assertTrue(profile.headBoost() >= 1f, family + " 前端的提亮不该是「压暗」");
            assertFalse(family == WarriorWeaponFamily.HAMMER && profile.tipStreak(), "钝器没有刃口，不该画剑影");
            assertFalse(profile.tipStreak() && profile.tipStreakAlpha() <= 0f, family + " 开了剑影就要有亮度");
        }
        // 两条梯子必须和设计意图一致：改一个数就同时被这两条钉住
        // 厚度（outerAlpha）：锤 > 重剑 > 长剑 > 长枪 —— 钝器一大片闷光，枪是一条细光
        assertTrue(alpha(WarriorWeaponFamily.HAMMER) > alpha(WarriorWeaponFamily.GREATSWORD));
        assertTrue(alpha(WarriorWeaponFamily.GREATSWORD) > alpha(WarriorWeaponFamily.LONGSWORD));
        assertTrue(alpha(WarriorWeaponFamily.LONGSWORD) > alpha(WarriorWeaponFamily.SPEAR));
        // 余味（life）：重剑 > 长剑 > 长枪 > 战锤 —— 锤子最急促
        assertTrue(life(WarriorWeaponFamily.GREATSWORD) > life(WarriorWeaponFamily.LONGSWORD));
        assertTrue(life(WarriorWeaponFamily.LONGSWORD) > life(WarriorWeaponFamily.SPEAR));
        assertTrue(life(WarriorWeaponFamily.SPEAR) > life(WarriorWeaponFamily.HAMMER));
        // 弧线长度（samples）：重剑 > 长枪 > 长剑 > 战锤
        assertTrue(samples(WarriorWeaponFamily.GREATSWORD) > samples(WarriorWeaponFamily.SPEAR));
        assertTrue(samples(WarriorWeaponFamily.SPEAR) > samples(WarriorWeaponFamily.LONGSWORD));
        assertTrue(samples(WarriorWeaponFamily.LONGSWORD) > samples(WarriorWeaponFamily.HAMMER));
        // 弓不是近战：默认退回长剑那套（不抛异常、不是锤子那套闷光）
        assertEquals(WarriorTrail.profile(WarriorWeaponFamily.LONGSWORD), WarriorTrail.profile(WarriorWeaponFamily.BOW));
        assertEquals(WarriorTrail.profile(WarriorWeaponFamily.HAMMER), WarriorTrail.profile(null));
    }

    private static float alpha(WarriorWeaponFamily family) {
        return WarriorTrail.profile(family).outerAlpha();
    }

    private static long life(WarriorWeaponFamily family) {
        return WarriorTrail.profile(family).life();
    }

    private static int samples(WarriorWeaponFamily family) {
        return WarriorTrail.profile(family).samples();
    }
}
