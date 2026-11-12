package com.tnc.tnc.magic;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.client.WindTornadoLines;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 风系龙卷风改"线条"（2026-11-09 作者：「我们的闪电不是一个一个线吗，我想这个线螺旋缠绕起来
 * 变成龙卷风……可以有明确的锐角，龙卷风极具那种线条特色」）的闸门 ✓。
 *
 * <p>这里守的是<b>美术承诺</b>，不是随便算几个数：
 * <ul>
 *   <li>线要真的<b>螺旋缠绕</b>（转够圈数、多根线错开）✓</li>
 *   <li>折点要真的<b>锐</b>（相邻两段夹角大），不能退化成光滑圆锥 ✗</li>
 *   <li>轮廓要<b>收口</b>（下窄上宽），不然不像龙卷风 ✗</li>
 *   <li>自转要跟着实体的 {@code spin} 走（整束线一起转）✓</li>
 *   <li>顶点预算有上限（每帧都在画，不能无限膨胀 ✗）✓</li>
 * </ul>
 */
class TornadoLineShapeTest {

    private static final double BOTTOM = TNTornadoEntity.MODEL_BASE_RADIUS / 16.0D;
    private static final double TOP = TNTornadoEntity.MODEL_TOP_RADIUS / 16.0D;
    private static final double HEIGHT = TNTornadoEntity.MODEL_HEIGHT / 16.0D;

    // ------------------------------------------------------------------
    //  形状
    // ------------------------------------------------------------------

    @Test
    void sevenStrandsSpiralUpTheWholeFunnel() {
        List<double[][]> strands = TornadoLineShape.strands(0.0D, BOTTOM, TOP, HEIGHT, 1234L);
        assertEquals(TornadoLineShape.STRANDS, strands.size(), "线的根数变了 ⇒ 缠绕感会变 ✗");
        assertTrue(TornadoLineShape.STRANDS >= 5, "少于 5 根就像弹簧，不是「缠绕」✗");
        assertTrue(TornadoLineShape.TURNS >= 1.2D, "转不到 1.2 圈就不像龙卷风 ✗");

        for (double[][] pts : strands) {
            assertEquals(TornadoLineShape.SEGMENTS + 1, pts.length, "折点数必须固定（预算/测试都要它）✗");
            for (double[] p : pts) {
                for (double v : p) {
                    assertTrue(Double.isFinite(v), "顶点出现 NaN/Inf ⇒ 整帧渲染崩 ✗");
                }
            }
            // 从脚下升到顶（y 单调不降，允许一点点抖动）
            assertEquals(0.0D, pts[0][1], 1.0E-6D, "底端必须落在实体脚下 y=0 ✗");
            assertTrue(pts[pts.length - 1][1] >= HEIGHT * 0.80D,
                    "每根线都要长到接近顶部（不然上半截是空的）✗ 实际 " + pts[pts.length - 1][1]);
            assertTrue(pts[pts.length - 1][1] <= HEIGHT * 1.10D,
                    "线尖冲出太高 ⇒ 顶口会炸出一堆长刺 ✗");
            for (int i = 1; i < pts.length; i++) {
                assertTrue(pts[i][1] >= pts[i - 1][1] - 1.0E-9D, "y 必须一路往上（龙卷风不倒流）✗");
            }
        }
    }

    @Test
    void theFunnelIsNarrowAtTheFootAndWideAtTheTop() {
        // 注意：不能用"首点/末点"比 ✗ —— 首点可能正好是一根刃尖（半径 ×1.7），
        // 那样会误判成"底比顶宽"。要比的是**下三分之一 / 上三分之一的平均半径** ✓。
        List<double[][]> strands = TornadoLineShape.strands(0.0D, BOTTOM, TOP, HEIGHT, 7L);
        for (double[][] pts : strands) {
            int third = Math.max(1, pts.length / 3);
            double low = 0.0D;
            double high = 0.0D;
            for (int i = 0; i < third; i++) {
                low += horizontalRadius(pts[i]);
                high += horizontalRadius(pts[pts.length - 1 - i]);
            }
            low /= third;
            high /= third;
            assertTrue(high > low * 1.7D,
                    "顶部明显比底部宽才对（漏斗）✗ 实际 " + low + " → " + high);
        }
    }

    @Test
    void cornersAreSharpNotSmooth() {
        // 实测（seed 99，调参后的最终形状）：中位拐角 61°、最大 90°、69/112 个折点 ≥ 10°。
        // 那些小于 10° 的折点，是"螺旋前进"和"半径锯齿"恰好互相抵消的地方 ——
        // 不可能全部都很尖（那就成了刺球 ✗），所以在**中位数**和**大角度占比**上立闸门 ✓。
        List<double[][]> strands = TornadoLineShape.strands(0.0D, BOTTOM, TOP, HEIGHT, 99L);
        List<Double> angles = new ArrayList<>();
        for (double[][] pts : strands) {
            for (int i = 1; i < pts.length - 1; i++) {
                angles.add(turnDegrees(pts[i - 1], pts[i], pts[i + 1]));
            }
        }
        assertFalse(angles.isEmpty(), "没有折点 ⇒ 画出来是光滑管，不是线 ✗");
        List<Double> sorted = new ArrayList<>(angles);
        java.util.Collections.sort(sorted);
        double median = sorted.get(sorted.size() / 2);
        double max = sorted.get(sorted.size() - 1);
        int sharp = 0;
        int verySharp = 0;
        for (double a : angles) {
            if (a >= 10.0D) {
                sharp++;
            }
            if (a >= 30.0D) {
                verySharp++;
            }
        }
        assertTrue(median >= 25.0D, "中位拐角只有 " + median + "° ⇒ 整体还是光滑曲线 ✗");
        assertTrue(max >= 60.0D, "最大拐角只有 " + max + "° ⇒ 没有「明确的锐角」✗");
        assertTrue(sharp * 100 >= angles.size() * 55,
                "大拐角太少（" + sharp + "/" + angles.size() + "）⇒ 线条特色不够 ✗");
        assertTrue(verySharp >= angles.size() / 4,
                "≥30° 的锐角只有 " + verySharp + " 个 ⇒ 不够「极具线条特色」✗");
    }

    @Test
    void radiusZigzagsInsteadOfFollowingASmoothCone() {
        List<double[][]> strands = TornadoLineShape.strands(0.0D, BOTTOM, TOP, HEIGHT, 5L);
        for (double[][] pts : strands) {
            int flips = 0;
            double previous = 0.0D;
            for (int i = 0; i < pts.length; i++) {
                double u = i / (double) (pts.length - 1);
                double ideal = TornadoLineShape.profile(u, BOTTOM, TOP);
                double delta = horizontalRadius(pts[i]) - ideal;
                if (i > 0 && Math.signum(delta) != Math.signum(previous)) {
                    flips++;
                }
                previous = delta;
            }
            // 半径锯齿（外挑/内收交替）⇒ 轮廓上才有"折角"而不是一条平滑母线 ✓
            assertTrue(flips >= 6, "半径没有来回锯齿（只翻了 " + flips + " 次）⇒ 轮廓是平滑锥 ✗");
        }
    }

    @Test
    void spinningTheEntityRotatesTheWholeBundle() {
        double spin = Math.toRadians(90.0D);
        List<double[][]> still = TornadoLineShape.strands(0.0D, BOTTOM, TOP, HEIGHT, 11L);
        List<double[][]> turned = TornadoLineShape.strands(spin, BOTTOM, TOP, HEIGHT, 11L);
        // 种子相同 ⇒ 锯齿一样，只有角度整体偏了 −90°（绕 +Y 右手旋转的约定）✓
        // 角度 a → a − 90° ⇒ (x, z) = (r cos a, r sin a) 变成 (z, −x) ✓
        for (int s = 0; s < still.size(); s++) {
            for (int i = 0; i < still.get(s).length; i++) {
                double[] a = still.get(s)[i];
                double[] b = turned.get(s)[i];
                assertEquals(a[1], b[1], 1.0E-6D, "自转不该改高度 ✗");
                assertEquals(a[2], b[0], 1.0E-9D, "自转不是绕中轴的纯旋转（x 分量对不上）✗");
                assertEquals(-a[0], b[2], 1.0E-9D, "自转不是绕中轴的纯旋转（z 分量对不上）✗");
                assertEquals(horizontalRadius(a), horizontalRadius(b), 1.0E-9D, "自转不该改半径 ✗");
            }
        }
    }

    @Test
    void slashesAreShortAndSharp() {
        List<double[][]> slashes = TornadoLineShape.slashes(0.0D, BOTTOM, TOP, HEIGHT, 3L);
        assertEquals(TornadoLineShape.SLASHES, slashes.size(), "风切数量变了 ✗");
        for (double[][] slash : slashes) {
            assertEquals(4, slash.length, "风切是 V 形 4 点折线 ✗");
            double minAngle = 180.0D;
            for (int i = 1; i < slash.length - 1; i++) {
                minAngle = Math.min(minAngle, turnDegrees(slash[i - 1], slash[i], slash[i + 1]));
            }
            assertTrue(minAngle >= 15.0D, "风切不够尖（最小拐角 " + minAngle + "°）⇒ 像弧线 ✗");
            for (double[] p : slash) {
                assertTrue(p[1] >= 0.0D && p[1] <= HEIGHT * 1.1D, "风切跑到漏斗外面去了 ✗");
                assertTrue(horizontalRadius(p) <= TOP * 1.6D, "风切离中轴太远 ✗");
            }
        }
    }

    // ------------------------------------------------------------------
    //  渲染（用假 VertexConsumer 数顶点，不启动游戏）
    // ------------------------------------------------------------------

    @Test
    void drawingStaysInsideTheVertexBudgetAndEmitsOnlyFinitePoints() {
        List<double[]> vertices = new ArrayList<>();
        VertexConsumer out = fakeConsumer(vertices);
        Matrix4f m = new Matrix4f();
        int claimed = WindTornadoLines.draw(out, m, 0.0D, 1.0F, BOTTOM, TOP, HEIGHT,
                10.0D, 6.0D, 10.0D, 42L);

        assertFalse(vertices.isEmpty(), "一个顶点都没画 ⇒ 龙卷风是隐形的 ✗");
        assertEquals(claimed, vertices.size(), "预算估算和实际发出的顶点数不一致 ✗");
        // 两层：12 根 × 17 段 × 24 顶点 + 6 道风切 ≈ 5300（旧的一层版是 2952）✓
        assertTrue(vertices.size() <= 6500, "每帧 " + vertices.size() + " 个顶点，超预算了 ✗");
        for (double[] v : vertices) {
            for (int i = 0; i < 3; i++) {
                assertTrue(Double.isFinite(v[i]), "渲染顶点出现 NaN/Inf ✗");
            }
            assertTrue(v[3] >= 0.0D && v[3] <= 1.0D, "alpha 越界 ⇒ 画出来一片糊 ✗");
        }
    }

    @Test
    void thereAreTwoNestedShells() {
        // 作者 2026-11-09：「做成两层，里一层外一层」✓
        assertEquals(2, TornadoLineShape.SHELLS.size(), "必须是两层（里一层外一层）✗");
        TornadoLineShape.Shell outer = TornadoLineShape.OUTER;
        TornadoLineShape.Shell inner = TornadoLineShape.INNER;
        assertTrue(inner.radiusScale() < outer.radiusScale() * 0.80D,
                "内层半径没明显缩进去（" + inner.radiusScale() + " vs " + outer.radiusScale()
                        + "）⇒ 看着还是一层 ✗");
        assertTrue(inner.strands() >= 4, "内层线太少（" + inner.strands() + " 根）⇒ 里层是空的 ✗");
        assertTrue(inner.turnsScale() != outer.turnsScale(), "两层圈数一样 ⇒ 像同心管 ✗");
        assertTrue(inner.whiteness() < outer.whiteness(),
                "内层芯色必须比外层更青绿，否则加色混合下两层糊成一根白管 ✗");

        // 几何上也要真的套在里面：内层每个点的半径都小于外层最大半径
        List<double[][]> outerLines = TornadoLineShape.strands(0.0D, BOTTOM, TOP, HEIGHT, 99L, outer);
        List<double[][]> innerLines = TornadoLineShape.strands(0.0D, BOTTOM, TOP, HEIGHT, 99L, inner);
        double outerMax = 0.0D;
        for (double[][] pts : outerLines) {
            for (double[] p : pts) {
                outerMax = Math.max(outerMax, horizontalRadius(p));
            }
        }
        for (double[][] pts : innerLines) {
            for (double[] p : pts) {
                assertTrue(horizontalRadius(p) < outerMax,
                        "内层的点跑到外层外面去了 ⇒ 不是「里一层」✗");
            }
        }
    }

    @Test
    void fadingOutDrawsNothing() {
        List<double[]> vertices = new ArrayList<>();
        int claimed = WindTornadoLines.draw(fakeConsumer(vertices), new Matrix4f(),
                0.0D, 0.0F, BOTTOM, TOP, HEIGHT, 0, 0, 0, 1L);
        assertEquals(0, claimed);
        assertTrue(vertices.isEmpty(), "alpha = 0 还在发顶点 ⇒ 白画 ✗");
    }

    // ------------------------------------------------------------------
    //  工具
    // ------------------------------------------------------------------

    private static double horizontalRadius(double[] p) {
        return Math.sqrt(p[0] * p[0] + p[2] * p[2]);
    }

    /** 折点处的拐角（度）✓。 */
    private static double turnDegrees(double[] a, double[] b, double[] c) {
        double[] u = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        double[] v = {c[0] - b[0], c[1] - b[1], c[2] - b[2]};
        double lu = Math.sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2]);
        double lv = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (lu < 1.0E-9D || lv < 1.0E-9D) {
            return 0.0D;
        }
        double cos = (u[0] * v[0] + u[1] * v[1] + u[2] * v[2]) / (lu * lv);
        cos = Math.max(-1.0D, Math.min(1.0D, cos));
        return Math.toDegrees(Math.acos(cos));
    }

    /** 假顶点消费者：记录 x/y/z 和 alpha，其余调用原样返回自己 ✓。 */
    private static VertexConsumer fakeConsumer(List<double[]> sink) {
        return (VertexConsumer) Proxy.newProxyInstance(
                TornadoLineShapeTest.class.getClassLoader(),
                new Class[]{VertexConsumer.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("vertex")) {
                        int start = args[0] instanceof Matrix4f ? 1 : 0;
                        sink.add(new double[]{
                                ((Number) args[start]).doubleValue(),
                                ((Number) args[start + 1]).doubleValue(),
                                ((Number) args[start + 2]).doubleValue(),
                                1.0D});
                    } else if (name.equals("color")) {
                        int n = args.length;
                        if (n == 4 && !sink.isEmpty()) {
                            sink.get(sink.size() - 1)[3] = ((Number) args[3]).doubleValue();
                        }
                    }
                    return method.getReturnType() == void.class ? null : proxy;
                });
    }
}
