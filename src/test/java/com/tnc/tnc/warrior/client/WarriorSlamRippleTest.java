package com.tnc.tnc.warrior.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tnc.tnc.warrior.WarriorWeaponFamily;
import org.junit.jupiter.api.Test;

/**
 * 战锤下砸的"扩散影"：形状曲线和触发条件都是纯数学，钉死它们，
 * 免得以后调参数时把"越扩越大、越扩越淡"改成"越扩越亮"或者扩到屏幕外面去。
 */
class WarriorSlamRippleTest {

    @Test
    void radiusOnlyGrowsAndStopsAtTheEnd() {
        double previous = -1;
        for (int i = 0; i <= 40; i++) {
            double t = i / 40.0;
            double radius = WarriorSlamRipple.radius(t);
            assertTrue(radius >= previous, "半径必须单调不减（t=" + t + "）");
            previous = radius;
        }
        assertEquals(WarriorSlamRipple.START_RADIUS, WarriorSlamRipple.radius(0), 1e-9, "刚砸下时是起始半径");
        assertEquals(WarriorSlamRipple.END_RADIUS, WarriorSlamRipple.radius(1), 1e-9, "消失时正好是终点半径");
        // 越界输入要夹住，不能扩到天上去
        assertEquals(WarriorSlamRipple.END_RADIUS, WarriorSlamRipple.radius(5), 1e-9);
        assertEquals(WarriorSlamRipple.START_RADIUS, WarriorSlamRipple.radius(-5), 1e-9);
        // 前半段扩得快：t=0.3 时至少要走过全程的一半
        assertTrue(WarriorSlamRipple.radius(0.3) - WarriorSlamRipple.START_RADIUS
                > (WarriorSlamRipple.END_RADIUS - WarriorSlamRipple.START_RADIUS) * 0.5, "前半段要扩得快");
    }

    @Test
    void alphaFadesToNothing() {
        float previous = 2f;
        for (int i = 0; i <= 40; i++) {
            float alpha = WarriorSlamRipple.alpha(i / 40.0);
            assertTrue(alpha <= previous + 1e-6f, "透明度必须单调变淡");
            assertTrue(alpha >= 0f && alpha <= 1f, "透明度要落在 0..1");
            previous = alpha;
        }
        assertEquals(0f, WarriorSlamRipple.alpha(1), 1e-6f, "结束时必须完全消失");
        assertTrue(WarriorSlamRipple.alpha(0) > 0.3f, "刚砸下时要有存在感");
    }

    @Test
    void theRingWidensAsItExpands() {
        assertTrue(WarriorSlamRipple.width(1) > WarriorSlamRipple.width(0), "外扩时环也要变宽");
        assertTrue(WarriorSlamRipple.width(0) > 0, "厚度不能是 0，否则看不见");
        // 环不能薄成一条线：任何时刻内沿和外沿都要有肉眼可见的差距
        for (int i = 0; i <= 10; i++) {
            double t = i / 10.0;
            assertTrue(WarriorSlamRipple.radius(t) + WarriorSlamRipple.width(t)
                    > WarriorSlamRipple.radius(t), "外沿必须在内沿外面");
        }
    }

    @Test
    void onlyHammersGetASlamRing() {
        assertTrue(WarriorSlamRipple.isHammer("hammer", WarriorWeaponFamily.HAMMER));
        assertTrue(WarriorSlamRipple.isHammer("earthshaker_hammer", WarriorWeaponFamily.GREATSWORD),
                "id 里带 hammer 的武器也算（家族表以后变了不会漏）");
        assertFalse(WarriorSlamRipple.isHammer("doom_scythe", WarriorWeaponFamily.GREATSWORD));
        assertFalse(WarriorSlamRipple.isHammer("greatsword", WarriorWeaponFamily.GREATSWORD));
        assertFalse(WarriorSlamRipple.isHammer(null, null));
    }
}
