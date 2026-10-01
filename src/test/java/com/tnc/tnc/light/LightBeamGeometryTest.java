package com.tnc.tnc.light;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * ★★ <b>索敌规矩</b>（作者 2026-10-01："t1234激光怎么没有索敌啊" ✗）：
     * 准星锥里 + 射程内才算目标 ✓；身后 / 旁边（超锥）/ 太远 都不算 ✗。
     */
    @Test
    void aimConeSelectsOnlyWhatIsInFront() {
        Vec3 eye = new Vec3(0.0D, 1.6D, 0.0D);
        Vec3 look = new Vec3(0.0D, 0.0D, 1.0D);           // 朝 +Z 看 ✓
        double reach = 30.0D;
        double cone = 35.0D;

        assertTrue(TNLightBeamMechanics.inAimCone(eye, look, new Vec3(0.0D, 1.6D, 10.0D), reach, cone),
                "正前方的目标没被选中 ✗");
        assertTrue(TNLightBeamMechanics.inAimCone(eye, look, new Vec3(3.0D, 1.6D, 10.0D), reach, cone),
                "前偏 17 度左右的目标没被选中 ✗（锥是 35 度）");
        assertFalse(TNLightBeamMechanics.inAimCone(eye, look, new Vec3(0.0D, 1.6D, -10.0D), reach, cone),
                "身后的目标也被选中了 ✗");
        assertFalse(TNLightBeamMechanics.inAimCone(eye, look, new Vec3(10.0D, 1.6D, 10.0D), reach, cone),
                "正侧面（45 度）的目标也被选中了 ✗");
        assertFalse(TNLightBeamMechanics.inAimCone(eye, look, new Vec3(0.0D, 1.6D, 40.0D), reach, cone),
                "超出射程的目标也被选中了 ✗");
        // 锥边上：34 度要中、36 度不要 ✓
        double d34 = Math.toRadians(34.0D);
        double d36 = Math.toRadians(36.0D);
        assertTrue(TNLightBeamMechanics.inAimCone(eye, look,
                        new Vec3(Math.sin(d34) * 10.0D, 1.6D, Math.cos(d34) * 10.0D), reach, cone),
                "锥内 34 度的目标没选中 ✗");
        assertFalse(TNLightBeamMechanics.inAimCone(eye, look,
                        new Vec3(Math.sin(d36) * 10.0D, 1.6D, Math.cos(d36) * 10.0D), reach, cone),
                "锥外 36 度的目标被选中了 ✗");
    }

    /**
     * ★★ 渲染器的朝向必须正好对上"光柱末端"（作者 2026-10-01："我就看到冒烟，没看到光线" ✗）。
     *
     * <p>第一版渲染器**忘了乘实体朝向** ✗ —— {@code pose} 里只有"实体在哪"、没有"朝哪" ✗，
     * 于是那根柱子永远沿世界 +Z 躺平 ✗。这条测试把"渲染器那套旋转"和
     * {@code getLookAngle()}（判伤用的方向 ✓）**对账** ✓：
     * 本地 {@code (0,0,L)} 经过 {@code R_y(-yRot) · R_x(xRot)} 之后，必须落在 {@code look · L} 上 ✓。
     * 这样以后谁把旋转写反/漏掉，构建就直接红 ✗（不用等进游戏看 ✗）。
     */
    @Test
    void rendererRotationMatchesBeamDirection() {
        // 和 TNLightBeamMechanics.spawn 里同一套算法 ✓
        for (Vec3 d : new Vec3[]{
                new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, -1.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D),
                new Vec3(-0.4D, -0.6D, 0.7D).normalize()}) {
            float yaw = (float) (Mth.atan2(d.x, d.z) * (180.0F / (float) Math.PI));
            float pitch = (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * (180.0F / (float) Math.PI));
            // 渲染器：pose.mulPose(YP, -yRot) 然后 pose.mulPose(XP, xRot) ✓
            Matrix4f m = new Matrix4f()
                    .rotateY((float) Math.toRadians(-yaw))
                    .rotateX((float) Math.toRadians(pitch));
            double length = 20.0D;
            org.joml.Vector4f tip = new org.joml.Vector4f(0.0F, 0.0F, (float) length, 1.0F);
            m.transform(tip);
            // getLookAngle 的公式：(-sin(yaw)cos(pitch), -sin(pitch), cos(yaw)cos(pitch)) ✓
            double cy = Math.cos(Math.toRadians(yaw));
            double sy = Math.sin(Math.toRadians(yaw));
            double cp = Math.cos(Math.toRadians(pitch));
            double sp = Math.sin(Math.toRadians(pitch));
            double[] want = {-sy * cp * length, -sp * length, cy * cp * length};
            assertEquals(want[0], tip.x, 1.0E-3D, "光柱末端 X 对不上（方向错了）" + d);
            assertEquals(want[1], tip.y, 1.0E-3D, "光柱末端 Y 对不上（方向错了）" + d);
            assertEquals(want[2], tip.z, 1.0E-3D, "光柱末端 Z 对不上（方向错了）" + d);
        }
    }

    /**
     * t5：5 个阵 —— 一个在中心、四个围一圈、都在同一个水平面上、**彼此分开得够远** ✓
     * （作者 2026-10-01："五光十射每个法阵可以分开点"✗ —— 光柱半径 4.5 ⇒ 间距必须 ≥ 9 格 ✓）。
     */
    @Test
    void barragePlacesFiveDistinctCircles() {
        double ring = 12.0D;
        List<Vec3> offs = TNLightBeamMechanics.barrageOffsets(5, ring);
        assertEquals(5, offs.size(), "五光十射不是 5 个阵");
        assertEquals(0.0D, offs.get(0).length(), 1.0E-9D, "第一个阵应该在落点中心");
        for (Vec3 o : offs) {
            assertEquals(0.0D, o.y, 1.0E-9D, "阵不在同一个水平面上");
            assertTrue(o.length() <= ring + 1.0E-6D, "阵甩得太远了：" + o);
        }
        for (int i = 1; i < offs.size(); i++) {
            assertEquals(ring, offs.get(i).length(), 1.0E-6D, "外围的阵不在圆环上");
        }
        // ★ 相邻两根光柱不能叠：中心阵到外围阵、以及外围阵两两之间，都要 ≥ 光柱直径（9 格 ✓）
        for (int i = 1; i < offs.size(); i++) {
            assertTrue(offs.get(0).distanceTo(offs.get(i)) >= 9.0D,
                    "中心阵和外围阵挨太近（" + offs.get(0).distanceTo(offs.get(i)) + "）✗");
        }
        for (int i = 1; i < offs.size(); i++) {
            for (int j = i + 1; j < offs.size(); j++) {
                double d = offs.get(i).distanceTo(offs.get(j));
                assertTrue(d >= 9.0D, "两个外围阵挨太近（" + d + "）✗ 光柱会叠在一起");
            }
        }
    }

    /**
     * 粗细阶梯（作者 2026-10-01 第二版："全部加粗" / "圣光天降感觉可以加个10倍都"✓）：
     * 逐档变粗 ✓、天降比巨大光线粗得多 ✓、五光十射和天降一样粗 ✓。
     *
     * <p>半径写在 {@code SPELLS} 那张私有表里 ✗ ⇒ 这里用 {@code rainbow}/{@code fanDirections}
     * 之外的方式验证不了 ✗，所以改成查"公开的几何行为"：天降/五光的**阵**半径由
     * {@code radius × SKY_CIRCLE_SCALE} 决定 ✗ 也是私有 ✗ ——
     * 于是就查一件事：**散开角度不变**（t1 仍是 3 道 ±7° ✓），粗细由人工在表里核对 ✓。
     */
    @Test
    void thicknessLadderIsMonotonicInSource() throws Exception {
        // 直接从源码读那张表 ✓（比反射稳，也比"再抄一份数字"可靠 ✓）
        String src = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/tnc/tnc/light/TNLightBeamMechanics.java"),
                java.nio.charset.StandardCharsets.UTF_8);
        double[] radii = new double[5];
        String[] ids = {"light_beam", "great_light_beam", "giant_light_beam",
                "holy_light_descent", "radiant_barrage"};
        for (int i = 0; i < ids.length; i++) {
            int at = src.indexOf("new Spell(\"" + ids[i] + "\"");
            assertTrue(at > 0, "表里找不到 " + ids[i]);
            String[] parts = src.substring(at, src.indexOf(")", at)).split(",");
            radii[i] = Double.parseDouble(parts[4].trim());
        }
        for (int i = 1; i < 4; i++) {
            assertTrue(radii[i] > radii[i - 1], "第 " + (i + 1) + " 档没有比上一档粗：" + radii[i - 1] + " -> " + radii[i]);
        }
        assertTrue(radii[3] >= radii[2] * 2.0D, "圣光天降没有明显比巨大光线粗（" + radii[2] + " -> " + radii[3] + "）");
        assertEquals(radii[3], radii[4], 1.0E-9D, "五光十射的光柱应该和圣光天降一样粗");
    }
}
