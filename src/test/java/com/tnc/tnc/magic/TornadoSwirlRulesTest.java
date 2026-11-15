package com.tnc.tnc.magic;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 龙卷风强力吸力的闸门（2026-11-13 作者：「龙卷风加上强力吸力，四格以下的敌人会在其中卷到飞起」）✓。
 *
 * <p>守：
 * <ul>
 *   <li>范围就是<b>四格</b>（不是更大、也不是更小）✓</li>
 *   <li>被卷的人：<b>有切向速度</b>（绕着转）、<b>有上升速度</b>（飞起来）、<b>有向内的收力</b>✓</li>
 *   <li>边缘吸得比中心狠（不然读起来像磁铁而不是漩涡 ✓）</li>
 *   <li>速度必须封顶（t5 也不能把怪甩到一百格外 ✗）</li>
 *   <li>升到风柱上段就保持高度（不会一路飞没 ✗）</li>
 * </ul>
 */
class TornadoSwirlRulesTest {

    @Test
    void theSuctionRadiusIsFourBlocks() {
        assertEquals(4.0D, TornadoSwirlRules.LIFT_RADIUS, 1.0E-9D, "吸力半径不是四格 ✗");
        assertTrue(TornadoSwirlRules.inside(3.9D, 0.0D, 1.0D, 10.0D), "四格内应该被吸 ✗");
        assertFalse(TornadoSwirlRules.inside(4.1D, 0.0D, 1.0D, 10.0D), "四格外不该被吸 ✗");
        assertFalse(TornadoSwirlRules.inside(0.0D, 0.0D, 12.0D, 10.0D), "高过风柱的人不该被吸 ✗");
    }

    @Test
    void liftedEnemiesSpinRiseAndGetPulledIn() {
        double dx = -3.0D;
        double dz = 0.0D;
        double[] v = TornadoSwirlRules.velocity(dx, dz, 0.0D, 10.0D, 3);
        assertEquals(TornadoSwirlRules.RISE_SPEED, v[1], 1.0E-9D, "没有被往上卷 ⇒ 不会「飞起」✗");

        // 切向：径向是 (-1,0)，切向应该是 (0,±1) ⇒ z 方向必须有明显分量 ✓
        assertTrue(Math.abs(v[2]) > 0.4D, "没有切向速度 ⇒ 只是被吸过去、不是旋转 ✗");
        // 径向：敌人在 -x 侧 ⇒ 速度的 x 分量必须朝 +x（往风眼收）✓
        assertTrue(v[0] * dx < 0.0D, "没有向内的收力 ✗");
    }

    @Test
    void theEdgeIsSuckedHarderThanTheCore() {
        double near = TornadoSwirlRules.inwardSpeed(0.5D);
        double far = TornadoSwirlRules.inwardSpeed(3.8D);
        assertTrue(far > near * 2.0D, "边缘没有吸得更狠（漩涡读感会变成磁铁 ✗）");
        assertTrue(near >= TornadoSwirlRules.INWARD_MIN * 0.99D, "中心的收力被压到 0 ⇒ 会抖出圈 ✗");
    }

    @Test
    void speedIsCappedAndTheTopIsHeld() {
        for (int tier = 3; tier <= 5; tier++) {
            for (double d : new double[]{0.2D, 2.0D, 3.99D}) {
                double[] v = TornadoSwirlRules.velocity(d, 0.0D, 0.0D, 12.0D, tier);
                double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
                assertTrue(len <= TornadoSwirlRules.MAX_SPEED + 1.0E-9D,
                        "t" + tier + " 在 " + d + " 格处的速度 " + len + " 超上限（会把怪甩飞 ✗）");
            }
        }
        // 到顶之后不再上升 ✓
        double top = TornadoSwirlRules.liftTop(10.0D);
        double[] holding = TornadoSwirlRules.velocity(2.0D, 0.0D, top + 0.5D, 10.0D, 5);
        assertTrue(holding[1] <= 0.0D, "到顶了还在上升 ⇒ 会一路飞出视野 ✗");
    }

    @Test
    void higherTierSpinsFaster() {
        assertTrue(TornadoSwirlRules.swirlSpeed(5) > TornadoSwirlRules.swirlSpeed(4), "t5 应该比 t4 转得快 ✗");
        assertTrue(TornadoSwirlRules.swirlSpeed(4) > TornadoSwirlRules.swirlSpeed(3), "t4 应该比 t3 转得快 ✗");
    }

    /** 源码扫描：每 tick 真的改写速度、并且清了坠落伤害 ✓。 */
    @Test
    void theTornadoActuallyAppliesIt() throws Exception {
        String tornado = Files.readString(Path.of(
                "src/main/java/com/tnc/tnc/magic/TNTornadoEntity.java"));
        assertTrue(tornado.contains("swirlInside("), "没有每 tick 的强力吸力 ✗");
        assertTrue(tornado.contains("TornadoSwirlRules.velocity("), "速度没走规则 ✗");
        assertTrue(tornado.contains("fallDistance = 0.0F"), "被卷起来的人掉下来会摔死 ✗");
        assertTrue(tornado.contains("TornadoSwirlRules.LIFT_RADIUS"), "半径没走规则 ✗");
        // 每 tick 调用（不是只在 SUCK_INTERVAL 那 10 tick 里）✓
        int swirl = tornado.indexOf("swirlInside(tier);");
        int ageCheck = tornado.indexOf("if (age % SUCK_INTERVAL == 0)");
        assertTrue(swirl >= 0 && ageCheck > swirl, "swirlInside 没有排在 10-tick 判定之外 ⇒ 不是每 tick ✗");
    }
}
