package com.tnc.tnc.magic.water;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 水球链 t4/t5「法阵群 → 齐射」的**纯规则**自检 ✓（作者 2026-11-09 的设计稿）。
 *
 * <p>为什么值得钉：作者这次特意强调了<b>顺序</b> ✗ ——
 * "记得要先出现法阵，法阵全部出现完之后再一起发射水波"✓。
 * 这类"时间线"错误在游戏里只表现为"感觉有点乱/看着像直接射"✗，
 * 靠试玩几乎抓不住 ✗；而它完全可以在这里用数字钉死 ✓。
 */
class WaterGateRulesTest {

    @Test
    void higherTierUsesMoreAndBiggerCircles() {
        assertEquals(6, WaterGateRules.portals(4), "t4 六个法阵");
        assertEquals(8, WaterGateRules.portals(5), "t5 八个法阵");
        assertTrue(WaterGateRules.ringRadius(5) > WaterGateRules.ringRadius(4), "t5 的阵要更大");
        assertTrue(WaterGateRules.sealRadius(5) > WaterGateRules.sealRadius(4), "t5 的阵面要更大");
        assertTrue(WaterGateRules.lanceRadius(5) > WaterGateRules.lanceRadius(4), "t5 的水波要更粗");
        assertTrue(WaterGateRules.giantRadius(5) > WaterGateRules.lanceRadius(5) * 2,
                "收尾那道巨大水波要比普通水波大一大圈");
    }

    @Test
    void mergedSealIsExactlyFiveTimesTheSmallOne() {
        // 作者 2026-11-10："t5 汇聚成的大小要是小的的 5 倍大"✗
        for (int tier : new int[]{4, 5}) {
            assertEquals(WaterGateRules.sealRadius(tier) * 5.0D, WaterGateRules.giantSealRadius(tier), 1.0E-9D,
                    "t" + tier + " 汇聚成的大阵必须是小阵的 5 倍");
        }
        // 那道巨大水波从大阵里出来 ⇒ 粗细和大阵一致 ✓（比大阵还粗就会"溢出阵口"✗）
        assertEquals(WaterGateRules.giantSealRadius(5), WaterGateRules.giantRadius(5), 1.0E-9D,
                "巨大水波要正好填满汇聚成的大阵");
    }

    @Test
    void circlesAreSpreadOutEnoughToRead() {
        // 作者 2026-11-10："几座法阵之间的间隔要大一点"✗
        for (int tier : new int[]{4, 5}) {
            int count = WaterGateRules.portals(tier);
            double ring = WaterGateRules.ringRadius(tier);
            // 相邻两个阵心之间的距离（环上等角分布 ✓）
            double chord = 2.0D * ring * Math.sin(Math.PI / count);
            double sealWidth = WaterGateRules.sealRadius(tier) * 2.0D;
            assertTrue(chord > sealWidth * 1.6D,
                    "t" + tier + "：相邻法阵的间距（" + chord + " 格）要明显大于阵面直径（" + sealWidth + " 格）");
        }
    }

    @Test
    void everyCircleIsUpBeforeTheVolleyFires() {
        for (int tier : new int[]{4, 5}) {
            int count = WaterGateRules.portals(tier);
            double open = WaterGateRules.openTicks(tier);
            // 最后一个法阵从 (count-1)*stagger 开始长，长满要 APPEAR_TICKS ⇒ 那一刻才算"全部出现完" ✓
            double lastFull = (count - 1) * WaterGateRules.APPEAR_STAGGER + WaterGateRules.APPEAR_TICKS;
            assertEquals(1.0F, WaterGateRules.appear(count - 1, open), 1.0E-6F,
                    "t" + tier + "：齐射那一刻全部法阵必须已经亮满（作者：全部出现完再一起射）");
            assertEquals(1.0F, WaterGateRules.appear(count - 1, lastFull), 1.0E-6F,
                    "最后一个法阵在 lastFull 时应该刚好亮满");
            assertTrue(WaterGateRules.appear(count - 1, lastFull - 1) < 1.0F,
                    "lastFull 之前最后一个法阵不该已经亮满");
            assertTrue(open > lastFull,
                    "t" + tier + "：齐射必须晚于最后一个法阵亮完（还差 " + (open - lastFull) + " tick）");
            // 第一个法阵在 0 tick 就开始了（不能所有阵一起憋到最后 ✗）
            assertTrue(WaterGateRules.appear(0, 0) <= 0.001F, "第一个阵从 0 开始长");
            assertTrue(WaterGateRules.appear(0, WaterGateRules.APPEAR_TICKS) >= 0.999F, "第一个阵先亮完");
        }
    }

    @Test
    void volleyCooldownCoversTheWholeAnimation() {
        for (int tier : new int[]{4, 5}) {
            int cycle = WaterGateRules.cycleTicks(tier);
            assertTrue(cycle >= WaterGateRules.openTicks(tier) + WaterGateRules.FIRE_TICKS,
                    "一次动作至少要盖住开阵 + 齐射");
            assertEquals(WaterGateRules.openTicks(tier) + WaterGateRules.FIRE_TICKS
                            + WaterGateRules.LINGER_TICKS
                            + (WaterGateRules.giant(tier)
                                    ? WaterGateRules.CONVERGE_TICKS + WaterGateRules.GIANT_TICKS : 0),
                    cycle, "一次动作 = 开阵 + 齐射 + 残留（+ t5 汇聚与大波）");
            assertTrue(Math.max(WaterGateRules.VOLLEY_COOLDOWN, cycle) >= cycle,
                    "内置冷却不许比动作本身还短（否则上一轮的阵还没散就又要开 ✗）");
        }
        // t5 多了"汇聚 + 巨大水波"，所以它的一整套动作必然比 t4 长 ✓
        assertTrue(WaterGateRules.cycleTicks(5) > WaterGateRules.cycleTicks(4),
                "t5 的动作里有汇聚和巨大水波，必须更长");
    }

    @Test
    void sealsLingerHalfASecondAfterTheVolley() {
        // 作者 2026-11-10："射完激光之后法阵还要残留半秒"✗
        assertEquals(10, WaterGateRules.LINGER_TICKS, "半秒 = 10 tick");
        assertEquals(1.0F, WaterGateRules.linger(0), 1.0E-6F, "齐射刚结束那一 tick 阵还是全亮的");
        assertEquals(0.0F, WaterGateRules.linger(WaterGateRules.LINGER_TICKS), 1.0E-6F,
                "半秒之后阵必须已经散干净");
        float previous = 1.0F;
        for (int tick = 1; tick <= WaterGateRules.LINGER_TICKS; tick++) {
            float value = WaterGateRules.linger(tick);
            assertTrue(value < previous, "残留必须单调变淡（第 " + tick + " tick）");
            previous = value;
        }
        // 先慢后快：前半段不能掉得太狠（否则看起来像被"啪"地关掉 ✗）
        assertTrue(WaterGateRules.linger(5) > 0.2F,
                "半秒过半时阵还该看得见，实际 " + WaterGateRules.linger(5));
    }

    @Test
    void portalsSitOnOneArcAtChestHeightAwayFromTheEnemy() {
        Vec3 anchor = new Vec3(100, 64, -20);
        float awayYaw = 0.0F;                       // 敌人正南、玩家正北 ⇒ 阵全在北半边 ✓
        int count = WaterGateRules.portals(5);
        Vec3 away = Vec3.directionFromRotation(0.0F, awayYaw);
        for (int i = 0; i < count; i++) {
            Vec3 p = WaterGateRules.portalPos(anchor, 5, i, count, awayYaw);
            double horizontal = Math.hypot(p.x - anchor.x, p.z - anchor.z);
            assertEquals(WaterGateRules.ringRadius(5), horizontal, 0.02D,
                    "法阵必须落在同一个圆弧上，实际半径 " + horizontal);
            assertTrue(p.y - anchor.y > 0.9D && p.y - anchor.y < 1.8D, "法阵在胸口高度，实际 " + (p.y - anchor.y));
            // ★ 作者 2026-11-10："离敌人远点" ⇒ 朝敌人的那 120° 扇区里一个阵都没有 ✓
            Vec3 toward = Vec3.directionFromRotation(0.0F, awayYaw + 180.0F);
            Vec3 toPortal = new Vec3(p.x - anchor.x, 0, p.z - anchor.z).normalize();
            assertTrue(toPortal.dot(toward) <= Math.cos(Math.toRadians(60.0D)) + 1.0E-3D,
                    "第 " + i + " 个阵落进了朝敌人的 120° 扇区（dot=" + toPortal.dot(toward) + "）✗");
        }
        // 扇面 240°：最外侧两个阵在"正后方"各 ±120° ⇒ 它俩之间的**小角**是 360−240 = 120° ✓
        Vec3 first = WaterGateRules.portalPos(anchor, 5, 0, count, awayYaw).subtract(anchor);
        Vec3 last = WaterGateRules.portalPos(anchor, 5, count - 1, count, awayYaw).subtract(anchor);
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D,
                new Vec3(first.x, 0, first.z).normalize().dot(new Vec3(last.x, 0, last.z).normalize())))));
        assertEquals(360.0D - WaterGateRules.ARC_DEGREES, angle, 1.0D,
                "扇面张角应该是 " + WaterGateRules.ARC_DEGREES + "°（两侧各留 60° 不给敌人）");
    }

    @Test
    void orbitCirclesStayCloseToTheCaster() {
        Vec3 base = new Vec3(0, 64, 0);
        for (int step = 0; step < 200; step++) {
            Vec3 p = WaterGateRules.orbitPos(base, step % 2, 2, step * 1.7D);
            double horizontal = Math.hypot(p.x - base.x, p.z - base.z);
            assertTrue(Math.abs(horizontal - 1.15D) < 1.0E-6D, "环绕阵的半径恒定（不能忽远忽近 ✗）");
        }
        // 两个阵必须在圆的两头（不是叠在一起 ✗）
        Vec3 a = WaterGateRules.orbitPos(base, 0, 2, 0), b = WaterGateRules.orbitPos(base, 1, 2, 0);
        assertTrue(a.distanceTo(b) > 1.8D, "两个环绕阵要分得开，实际距离 " + a.distanceTo(b));
    }

    @Test
    void convergePointIsBetweenTheCasterAndTheEnemy() {
        Vec3 anchor = new Vec3(10, 64, 10);
        // awayYaw = 0 ⇒ 敌人在 -Z 方向、玩家在 +Z ⇒ 汇聚点应该往 -Z（朝敌人）挪 2.2 格 ✓
        Vec3 at = WaterGateRules.convergePos(anchor, 0F);
        assertTrue(anchor.z - at.z > 2.0D && anchor.z - at.z < 2.4D,
                "汇聚点应该在玩家和敌人之间（朝敌人 2.2 格），实际 " + (anchor.z - at.z));
        assertEquals(anchor.x, at.x, 1.0E-4D, "只沿水平朝向挪（不能左右偏 ✗）");
        // 敌人换到另一侧 ⇒ 汇聚点跟着翻过去 ✓
        Vec3 flipped = WaterGateRules.convergePos(anchor, 180F);
        assertTrue(flipped.z - anchor.z > 2.0D && flipped.z - anchor.z < 2.4D, "敌人换边，汇聚点也要换边");
        // 抬头低头不影响（只有水平朝向 ✓）
        for (float yaw : new float[]{0F, 45F, 137F, -90F}) {
            Vec3 p = WaterGateRules.convergePos(anchor, yaw);
            assertEquals(2.2D, Math.hypot(p.x - anchor.x, p.z - anchor.z), 1.0E-4D,
                    "汇聚点的水平距离恒为 2.2 格（yaw=" + yaw + "）");
        }
    }

    @Test
    void giantWaveHitsAboutTwiceAsHard() {
        for (int tier : new int[]{4, 5}) {
            assertEquals(WaterGateRules.lanceDamage(tier) * 2.0F, WaterGateRules.giantDamage(tier), 1.0E-6F,
                    "作者选的「比普通高一档 ≈ 2 倍」");
        }
        assertTrue(WaterGateRules.lanceDamage(5) > WaterGateRules.lanceDamage(4), "t5 单道水波比 t4 强");
        // 一次齐射是好几道同时命中 ⇒ 单道必须远低于老光柱的单次结算（老 t5 是 14 ✗）
        assertTrue(WaterGateRules.lanceDamage(5) < WaterSpellRules.damage(5),
                "单道水波要比老光柱的单次伤害低（同时命中的道数多了 ✗）");
    }

    @Test
    void wavesAreShortLivedLikeLightBeams() {
        for (int tier : new int[]{4, 5}) {
            int ticks = WaterGateRules.lanceTicks(tier);
            assertTrue(ticks >= 6 && ticks <= 16, "水波必须一闪而过（t" + tier + " = " + ticks + " tick）");
            // 和老的持续光柱（t4 3 秒 / t5 12 秒）比，短了一个量级 ✓
            assertTrue(ticks * 2 < WaterSpellRules.duration(tier), "必须明显短于老光柱的持续时间");
        }
    }

    @Test
    void facingNormalisesAndDegradesSafely() {
        Vec3 dir = WaterGateRules.facing(new Vec3(0, 0, 0), new Vec3(0, 0, 5));
        assertEquals(1.0D, dir.length(), 1.0E-6D);
        Vec3 same = WaterGateRules.facing(new Vec3(1, 2, 3), new Vec3(1, 2, 3));
        assertEquals(1.0D, same.length(), 1.0E-6D, "零向量也要给一个合法朝向（不能 NaN ✗）");
    }

    @Test
    void flashFadesOutInsteadOfPopping() {
        assertEquals(1.0F, WaterGateRules.flash(0), 1.0E-6F);
        assertEquals(0.0F, WaterGateRules.flash(WaterGateRules.FIRE_TICKS), 1.0E-6F);
        float previous = 1.0F;
        for (int i = 1; i <= WaterGateRules.FIRE_TICKS; i++) {
            float value = WaterGateRules.flash(i);
            assertTrue(value <= previous, "齐射闪光必须单调变暗");
            previous = value;
        }
    }
}
