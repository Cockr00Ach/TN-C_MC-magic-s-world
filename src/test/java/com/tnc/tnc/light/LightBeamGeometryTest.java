package com.tnc.tnc.light;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 光系第三条链「光线」的**纯几何**自检 ✓（作者 2026-10-01 定的五档 ✓）。
 *
 * <p>为什么先测几何：这条链是"向前射几道 / 天上开几个阵"，最容易出错的就是
 * "道数不对、散角不对、五个阵没排开"✗ —— 这些都能在**不进游戏**的情况下算清楚 ✓
 * （光翼链那轮就是靠离线算才把问题定位的 ✓，见 {@code docs/当前状态.md} 第十六节 ✓）。
 */
class LightBeamGeometryTest {

    /** t1：向前 3 道，彼此分开，且都还在视线的"前方"✓（不能有一道往回射 ✗）。 */
    @Test
    void fanSplitsRaysAndKeepsThemForward() {
        Vec3 look = new Vec3(0.0D, 0.0D, 1.0D);
        List<Vec3> dirs = TNLightBeamMechanics.fanDirections(look, 3, 14.0D);
        assertEquals(3, dirs.size(), "光线道数不对");
        for (Vec3 d : dirs) {
            assertTrue(d.z > 0.9D, "有一道没朝前射：" + d);
            assertEquals(1.0D, d.length(), 1.0E-6D, "方向没有归一化");
        }
        // 两道外侧光线 = 视线各转 ±spread/2 ✓（f = -0.5 .. +0.5 ⇒ 最外侧是 ∓7° ✓）
        Vec3 left = TNLightBeamMechanics.rotateAround(look, new Vec3(0.0D, 1.0D, 0.0D), -7.0D);
        Vec3 right = TNLightBeamMechanics.rotateAround(look, new Vec3(0.0D, 1.0D, 0.0D), 7.0D);
        double a = left.distanceTo(dirs.get(0));
        double b = right.distanceTo(dirs.get(2));
        assertTrue(a < 1.0E-6D && b < 1.0E-6D, "两侧散角不是 ±7°（-7° 差 " + a + "，+7° 差 " + b + "）");
        // 两道之间总共分开 14° ✓（两条单位向量夹角 θ 的距离 = 2 sin(θ/2) ✓）
        double total = dirs.get(0).distanceTo(dirs.get(2));
        assertEquals(2.0D * Math.sin(Math.toRadians(7.0D)), total, 1.0E-6D, "两道光线的总散角不是 14°");
        // 中间那道就是视线本身 ✓
        assertTrue(dirs.get(1).distanceTo(look.normalize()) < 1.0E-6D, "中间那道没对准视线");
    }

    /** t2/t3：一道的时候就是视线本身 ✓（散角参数不该动它 ✓）。 */
    @Test
    void singleRayFollowsTheLook() {
        Vec3 look = new Vec3(0.3D, -0.2D, 0.9D).normalize();
        List<Vec3> dirs = TNLightBeamMechanics.fanDirections(look, 1, 0.0D);
        assertEquals(1, dirs.size());
        assertTrue(dirs.get(0).distanceTo(look) < 1.0E-6D, "单道光线没对准视线");
    }

    /** 视线接近竖直时不炸（退化成绕 X 轴 ✓）。 */
    @Test
    void verticalLookStillFans() {
        List<Vec3> dirs = TNLightBeamMechanics.fanDirections(new Vec3(0.0D, 1.0D, 0.0D), 3, 14.0D);
        assertEquals(3, dirs.size());
        for (Vec3 d : dirs) {
            assertTrue(d.y > 0.9D, "朝天射时有一道歪掉了：" + d);
        }
    }

    /** 彩虹色：首尾不同、分量都在 0..1、循环回得来 ✓（"彩色"就是靠它 ✓）。 */
    @Test
    void rainbowIsColourfulAndInRange() {
        Vector3f a = TNLightBeamMechanics.rainbow(0.0D);
        Vector3f b = TNLightBeamMechanics.rainbow(0.5D);
        assertTrue(Math.abs(a.x - b.x) + Math.abs(a.y - b.y) + Math.abs(a.z - b.z) > 1.0F,
                "0 和 0.5 的颜色几乎一样，不像彩虹");
        for (double t = 0.0D; t <= 1.0001D; t += 0.05D) {
            Vector3f c = TNLightBeamMechanics.rainbow(t);
            assertTrue(c.x >= 0.0F && c.x <= 1.0F && c.y >= 0.0F && c.y <= 1.0F && c.z >= 0.0F && c.z <= 1.0F,
                    "颜色分量越界：" + c);
            assertTrue(c.x + c.y + c.z > 0.5F, "颜色太暗：" + c);
        }
        Vector3f loop = TNLightBeamMechanics.rainbow(1.0D);
        assertTrue(loop.distance(a) < 1.0E-5F, "1.0 没有绕回 0.0（不循环）");
    }

    /** t5：5 个阵 —— 一个在中心、四个围一圈、都在同一个水平面上、两两不重合 ✓。 */
    @Test
    void barragePlacesFiveDistinctCircles() {
        List<Vec3> offs = TNLightBeamMechanics.barrageOffsets(5, 4.5D);
        assertEquals(5, offs.size(), "五光十射不是 5 个阵");
        assertEquals(0.0D, offs.get(0).length(), 1.0E-9D, "第一个阵应该在落点中心");
        for (Vec3 o : offs) {
            assertEquals(0.0D, o.y, 1.0E-9D, "阵不在同一个水平面上");
            assertTrue(o.length() <= 4.5D + 1.0E-6D, "阵甩得太远了：" + o);
        }
        for (int i = 1; i < offs.size(); i++) {
            assertEquals(4.5D, offs.get(i).length(), 1.0E-6D, "外围的阵不在圆环上");
        }
        for (int i = 1; i < offs.size(); i++) {
            for (int j = i + 1; j < offs.size(); j++) {
                assertTrue(offs.get(i).distanceTo(offs.get(j)) > 1.0D, "两个阵叠在一起了");
            }
        }
    }
}
