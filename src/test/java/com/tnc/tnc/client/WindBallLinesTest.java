package com.tnc.tnc.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 风球漩涡线的闸门（2026-11-10 作者：「不要组成一个球，要漩涡感；线条数变三倍；
 * 不要一样的线条，多做渐变线条」）✓。
 *
 * <p>守：
 * <ul>
 *   <li>是<b>漩涡</b>（下窄上宽、绕轴打旋），不是一个球壳 ✗</li>
 *   <li>条数是上一版的三倍（t1＝15 …… t5＝27）✓</li>
 *   <li><b>线不重样</b>：宽度 / 亮度 / 配色都成梯度，主线明显比淡线粗且亮 ✓</li>
 *   <li>顶点预算有上限；渲染器仍然"原有特效 + 线条"两层 ✓</li>
 * </ul>
 */
class WindBallLinesTest {

    @Test
    void theLinesFormAVortexNotASphere() {
        double radius = 3.0D;
        List<WindBallLines.Line> lines = WindBallLines.vortex(radius, 3, 0.0D, 7L);
        assertEquals(WindBallLines.count(3), lines.size(), "漩涡线条数不对 ✗");

        double bottomRadius = 0.0D;
        double topRadius = 0.0D;
        int bold = WindBallLines.boldCount(3);
        for (int i = 0; i < lines.size(); i++) {
            WindBallLines.Line line = lines.get(i);
            double[][] pts = line.points();
            assertTrue(pts.length >= WindBallLines.FINE_SEGMENTS + 1, "线段太少了 ✗");
            for (double[] p : pts) {
                for (double v : p) {
                    assertTrue(Double.isFinite(v), "漩涡线出现 NaN/Inf ✗");
                }
                // 任何一条线都不该跑出漩涡的口径（±15% 容差）✓
                double r = Math.hypot(p[0], p[2]);
                assertTrue(r <= radius * WindBallLines.VORTEX_WIDE * 1.15D,
                        "线跑到漩涡外面了（r=" + r + "）✗");
            }
            // 只有"主线"铺满整个高度（淡线是短风丝）✓
            if (i < bold) {
                double[] first = pts[0];
                double[] last = pts[pts.length - 1];
                bottomRadius += Math.hypot(first[0], first[2]);
                topRadius += Math.hypot(last[0], last[2]);
                assertTrue(first[1] < 0.0D, "主线没有从球心下方起（漩涡下口）✗");
                assertTrue(last[1] > 0.0D, "主线没有收在球心上方（漩涡上口）✗");
                assertEquals(radius * WindBallLines.VORTEX_HEIGHT * 0.5D, last[1], radius * 0.12D,
                        "漩涡的总高度不对 ✗");
            }
        }
        bottomRadius /= bold;
        topRadius /= bold;
        assertTrue(topRadius > bottomRadius * 1.8D,
                "漩涡应该是下窄上宽（实际 " + bottomRadius + " → " + topRadius + "）✗");
        // 不是一个球：上下半径差这么多，说明它是漏斗不是球面 ✓
        assertTrue(topRadius <= radius * WindBallLines.VORTEX_WIDE * 1.25D,
                "漩涡上口张得太大 ✗");
    }

    @Test
    void thereAreThreeTimesAsManyLinesAsBefore() {
        for (int tier = 1; tier <= 5; tier++) {
            assertEquals(3 * (4 + tier), WindBallLines.count(tier),
                    "t" + tier + " 的线条数不是上一版的三倍 ✗");
        }
        assertEquals(15, WindBallLines.count(1), "t1 应该是 15 条 ✗");
        assertEquals(27, WindBallLines.count(5), "t5 应该是 27 条 ✗");
        // 档越高越多 ✓
        for (int tier = 2; tier <= 5; tier++) {
            assertTrue(WindBallLines.count(tier) > WindBallLines.count(tier - 1), "档位没让线变多 ✗");
        }
    }

    @Test
    void everyLineHasItsOwnGradient() {
        List<WindBallLines.Line> lines = WindBallLines.vortex(2.0D, 5, 0.0D, 11L);
        Set<Float> widths = new HashSet<>();
        Set<Float> alphas = new HashSet<>();
        Set<Integer> palettes = new HashSet<>();
        for (WindBallLines.Line line : lines) {
            widths.add(line.widthScale());
            alphas.add(line.alphaScale());
            palettes.add(line.palette());
            assertTrue(line.widthScale() > 0.2F && line.widthScale() < 2.0F, "线宽倍率失控 ✗");
            assertTrue(line.alphaScale() > 0.1F && line.alphaScale() <= 1.0F, "亮度倍率失控 ✗");
        }
        assertTrue(widths.size() >= 6, "线宽只有 " + widths.size() + " 种 ⇒ 不够渐变 ✗");
        assertTrue(alphas.size() >= 5, "亮度只有 " + alphas.size() + " 种 ⇒ 不够渐变 ✗");
        assertTrue(palettes.size() >= 2, "配色只有 " + palettes.size() + " 档 ⇒ 没有渐变层次 ✗");

        // 主线（前 1/3）必须明显比淡线粗、亮 ✓
        int bold = WindBallLines.boldCount(5);
        double boldWidth = 0.0D;
        double fineWidth = 0.0D;
        double boldAlpha = 0.0D;
        double fineAlpha = 0.0D;
        for (int i = 0; i < lines.size(); i++) {
            if (i < bold) {
                boldWidth += lines.get(i).widthScale();
                boldAlpha += lines.get(i).alphaScale();
            } else {
                fineWidth += lines.get(i).widthScale();
                fineAlpha += lines.get(i).alphaScale();
            }
        }
        boldWidth /= bold;
        boldAlpha /= bold;
        fineWidth /= (lines.size() - bold);
        fineAlpha /= (lines.size() - bold);
        assertTrue(boldWidth > fineWidth * 1.5D, "主线没有比淡线明显粗 ✗");
        assertTrue(boldAlpha > fineAlpha * 1.4D, "主线没有比淡线明显亮 ✗");
    }

    @Test
    void spinningChangesTheVortex() {
        List<WindBallLines.Line> a = WindBallLines.vortex(2.0D, 3, 0.0D, 5L);
        List<WindBallLines.Line> b = WindBallLines.vortex(2.0D, 3, Math.PI / 2.0D, 5L);
        boolean differs = false;
        for (int i = 0; i < a.size() && !differs; i++) {
            double[][] pa = a.get(i).points();
            double[][] pb = b.get(i).points();
            for (int k = 0; k < pa.length; k++) {
                if (Math.abs(pa[k][0] - pb[k][0]) + Math.abs(pa[k][2] - pb[k][2]) > 1.0E-6D) {
                    differs = true;
                    break;
                }
            }
        }
        assertTrue(differs, "自转没换位置 ⇒ 漩涡是死的 ✗");
    }

    @Test
    void shockwaveExpandsOutwards() {
        double radius = 4.0D;
        List<double[][]> early = WindBallLines.shockRings(radius, 0.0D);
        List<double[][]> late = WindBallLines.shockRings(radius, 1.0D);
        assertEquals(WindBallLines.SHOCK_RINGS, early.size(), "冲击波环数不对 ✗");
        assertTrue(radiusOf(late.get(0)) > radiusOf(early.get(0)) * 1.5D, "冲击波没有往外扩 ✗");
        assertTrue(radiusOf(late.get(0)) <= radius * 2.4D, "冲击波扩得没边了 ✗");

        List<double[][]> streaks = WindBallLines.streaks(radius, 1.0D, 3L);
        assertEquals(WindBallLines.STREAKS, streaks.size(), "风痕条数不对 ✗");
        for (double[][] streak : streaks) {
            assertEquals(2, streak.length, "风痕是一条直线（两点）✗");
            assertTrue(length(streak[1]) > length(streak[0]), "风痕方向反了（应该往外甩）✗");
        }
    }

    @Test
    void thinLinesStayProportionalToTheBallAndBudgetIsBounded() {
        assertTrue(WindBallLines.width(67.5D) > WindBallLines.width(2.0D) * 10.0D,
                "线宽没跟半径成比例 ✗");
        assertTrue(WindBallLines.width(0.12D) >= 0.012D, "球压到极小时线宽应该有下限 ✗");

        int t1 = WindBallLines.vortexVertices(1);
        int t5 = WindBallLines.vortexVertices(5);
        assertTrue(t5 > t1, "档越高线越多，预算却没变 ✗");
        assertTrue(t5 <= 12000, "t5 漩涡 " + t5 + " 个顶点，太贵了 ✗");
        assertTrue(WindBallLines.shockVertices() <= 4000, "炸开的冲击波超预算 ✗");
    }

    /**
     * ★ 作者 2026-11-10：「你应该是在原有的特效上加上这个线条去优化」✓ ——
     * 渲染器里必须**同时**有原有 {@code WindConstructModels} 和新的漩涡线。
     */
    @Test
    void theRendererLayersTheVortexOnTopOfTheOriginalEffects() throws Exception {
        String renderer = Files.readString(Path.of(
                "src/main/java/com/tnc/tnc/client/WindChainRenderers.java"));
        assertTrue(renderer.contains("WindConstructModels.orb("),
                "原有的 orb 色带/核心被删了 ⇒ 不是「在原有特效上加线条」✗");
        assertTrue(renderer.contains("WindConstructModels.draw(\"cage\""), "炸开时原有的 cage 被删了 ✗");
        assertTrue(renderer.contains("WindBallLines.vortex("), "新的漩涡线没画 ✗");
        assertTrue(renderer.contains("WindBallLines.shockRings("), "炸开的冲击波环没画 ✗");
        int original = renderer.indexOf("WindConstructModels.orb(");
        int lines = renderer.indexOf("WindBallLines.vortex(");
        assertTrue(original >= 0 && lines > original, "顺序反了：应该先画原有、再叠漩涡 ✗");
    }

    // ------------------------------------------------------------------

    private static double radiusOf(double[][] ring) {
        double sum = 0.0D;
        int n = Math.max(1, ring.length - 1);
        for (int i = 0; i < n; i++) {
            sum += Math.hypot(ring[i][0], ring[i][2]);
        }
        return sum / n;
    }

    private static double length(double[] p) {
        return Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
    }
}
