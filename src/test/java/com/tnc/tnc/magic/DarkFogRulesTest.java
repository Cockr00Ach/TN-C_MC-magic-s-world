package com.tnc.tnc.magic;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 黑雾体积与抓取的闸门（2026-11-11 作者：「我要你瞬间给他拉进来」
 * ＋「黑雾的大小也要随着 t 级增长变大，你别只加宽度，高度也要加」）✓。
 *
 * <p>核心那条：**宽度和高度必须一起随档位长** —— 以前只有 JSON 里的半径在长，
 * 高度是渲染器写死的 1.1 + 档位×0.1（1.2 → 1.6，几乎不变 ✗）。
 */
class DarkFogRulesTest {

    /** 五个暗系黑雾法术的半径（JSON 里的 {@code volume.radius}）✓ */
    private static final double[] TIER_RADII = {4.5D, 5.0D, 6.0D, 7.5D, 9.0D};

    @Test
    void heightGrowsWithTierJustLikeWidth() {
        double previousWidth = 0.0D;
        double previousHeight = 0.0D;
        for (int i = 0; i < TIER_RADII.length; i++) {
            double radius = TIER_RADII[i];
            double width = DarkFogRules.width(radius);
            double height = DarkFogRules.height(radius);
            assertTrue(width > previousWidth, "t" + (i + 1) + " 的宽度没有比上一档大 ✗");
            assertTrue(height > previousHeight, "t" + (i + 1) + " 的高度没有比上一档大 ✗（作者：别只加宽度）");
            previousWidth = width;
            previousHeight = height;
        }
        // t1 → t5：宽度 9 → 18 格、高度 4.5 → 9 格（一起翻倍）✓
        assertEquals(9.0D, DarkFogRules.width(4.5D), 1.0E-9D, "t1 宽度不对 ✗");
        assertEquals(4.5D, DarkFogRules.height(4.5D), 1.0E-9D, "t1 高度不对 ✗");
        assertEquals(18.0D, DarkFogRules.width(9.0D), 1.0E-9D, "t5 宽度不对 ✗");
        assertEquals(9.0D, DarkFogRules.height(9.0D), 1.0E-9D, "t5 高度不对 ✗");
    }

    @Test
    void halfHeightFollowsTheRatioAndNeverCollapses() {
        for (double radius : TIER_RADII) {
            assertEquals(radius * DarkFogRules.HEIGHT_RATIO, DarkFogRules.halfHeight(radius), 1.0E-9D,
                    "半径 " + radius + " 的半高不是 半径×比例 ✗");
        }
        assertEquals(DarkFogRules.MIN_HALF_HEIGHT, DarkFogRules.halfHeight(0.5D), 1.0E-9D,
                "极小的雾没有兜底高度 ⇒ 会贴成一张纸 ✗");
        assertEquals(DarkFogRules.MIN_HALF_HEIGHT, DarkFogRules.halfHeight(0.0D), 1.0E-9D);
    }

    @Test
    void bossDomainFogKeepsItsOldSize() {
        // 领域雾是"整个战场都是雾"，长高没意义，而且 150×0.5=75 格会把整张图糊掉 ✗
        double domain = 150.0D;
        assertEquals(DarkFogRules.DOMAIN_HALF_HEIGHT, DarkFogRules.halfHeight(domain), 1.0E-9D,
                "Boss 领域雾的半高被改动了 ✗");
        // 领域雾的抓取半径被**封顶**（96 格）：150×1.45 会变成两百格，太重 ✗
        assertEquals(96.0D, DarkFogRules.gripRadius(domain), 1.0E-9D, "领域雾的抓取半径封顶失效 ✗");
    }

    @Test
    void gripReachesBeyondTheFogAndInsideIsComfortablyInside() {
        for (double radius : TIER_RADII) {
            assertTrue(DarkFogRules.gripRadius(radius) > radius * 1.3D,
                    "抓取半径只到雾边缘 ⇒ 雾外圈的人抓不到 ✗");
            double inside = DarkFogRules.insideDistance(radius);
            assertTrue(inside < radius * 0.5D, "拽进来的落点还在雾外圈 ✗");
            assertTrue(inside >= DarkFogRules.INSIDE_MIN, "落点离雾心太近 ⇒ 会抖动 ✗");
        }
    }

    @Test
    void theFogIsACylinderNotACircle() {
        double radius = 4.5D;
        double half = DarkFogRules.halfHeight(radius);
        assertTrue(DarkFogRules.insideVolume(radius, 2.0D, 0.0D, 0.0D), "雾内的点被判成外面 ✗");
        assertTrue(DarkFogRules.insideVolume(radius, 0.0D, half * 0.9D, 0.0D), "高度内的点被判成外面 ✗");
        assertFalse(DarkFogRules.insideVolume(radius, 0.0D, half * 1.5D, 0.0D),
                "头顶太高的点也算在雾里 ⇒ 高度形同虚设 ✗");
        assertFalse(DarkFogRules.insideVolume(radius, radius * 1.2D, 0.0D, 0.0D),
                "半径外的点也算在雾里 ✗");
    }

    /** 源码扫描：瞬移与高度真的接上了（不是只写在规则里）✓。 */
    @Test
    void theEntityAndRendererActuallyUseIt() throws Exception {
        String entity = Files.readString(Path.of(
                "src/main/java/com/tnc/tnc/magic/DarkFogCloudEntity.java"));
        assertTrue(entity.contains("victim.teleportTo("), "没有「瞬间拉进来」的瞬移 ✗");
        assertTrue(entity.contains("SQUID_INK"), "被拽进来没有黑雾粒子反馈 ✗");
        assertTrue(entity.contains("DarkFogRules.halfHeight("), "高度没接上规则 ✗");
        assertTrue(entity.contains("DarkFogRules.insideDistance("), "拽进来的落点没接上规则 ✗");
        // 领域雾必须走"轻推"而不是瞬移 ✓
        int guard = entity.indexOf("if (domain) {");
        int teleport = entity.indexOf("victim.teleportTo(");
        assertTrue(guard >= 0 && teleport > guard,
                "Boss 领域雾没有排除在瞬移之外 ⇒ 会把玩家甩到 BOSS 脸上 ✗");

        String renderer = Files.readString(Path.of(
                "src/main/java/com/tnc/tnc/client/DarkMistRenderer.java"));
        assertTrue(renderer.contains("entity.syncedHeight()"), "渲染器没读同步过来的半高 ✗");
        assertFalse(renderer.contains("1.1F+tier*.1F"),
                "渲染器里还留着写死的半高（那正是「只加宽度」的原因 ✗）");
    }
}
