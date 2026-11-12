package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.TNWindBladeEntity;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 风刃改成「战士剑气」+ 体积统一（2026-11-09 作者：「这个风刃也改一改，其实可以改成战士那里的
 * 剑气你觉得如何，体积统一一下」）的闸门 ✓。
 *
 * <p>守三件事：
 * <ul>
 *   <li>画出来真的是一片<b>弯月</b>（中间厚、两端收尖、整体带弧度），不是一块方板 ✗</li>
 *   <li>顶点有限、有预算上限、alpha 不越界（每片刃每帧都在画 ✓）</li>
 *   <li><b>体积统一</b>：风法三档用同一个尺寸倍率，且这个世界尺寸和战士剑气是同一个量级 ✓</li>
 * </ul>
 */
class WindBladeQiTest {

    private static final double HALF = TNWindBladeEntity.BASE_SPAN
            * TNWindBladeEntity.SPELL_BLADE_SIZE * 0.5D;

    @Test
    void drawingEmitsFiniteVerticesWithinBudget() {
        List<double[]> vertices = new ArrayList<>();
        Matrix4f m = new Matrix4f();
        int claimed = WindBladeQi.draw(strictConsumer(vertices), m, HALF, 1.0F);

        assertFalse(vertices.isEmpty(), "一片刃都没画出来 ⇒ 风刃是隐形的 ✗");
        assertEquals(WindBladeQi.vertices(), claimed, "预算数和实际顶点数对不上 ✗");
        assertEquals(claimed, vertices.size(), "实际发出的顶点数和返回数不一致 ✗");
        assertTrue(vertices.size() <= 3000, "每片刃 " + vertices.size() + " 个顶点，超预算 ✗");
        for (double[] v : vertices) {
            for (int i = 0; i < 3; i++) {
                assertTrue(Double.isFinite(v[i]), "顶点出现 NaN/Inf ✗");
            }
            assertTrue(v[3] >= 0.0D && v[3] <= 1.0D, "alpha 越界（" + v[3] + "）⇒ 会画糊 ✗");
        }
    }

    @Test
    void theCrescentIsThickInTheMiddleAndPointedAtBothEnds() {
        double span = HALF;
        double middle = thickness(0.0D, span);
        double quarter = thickness(0.5D, span);
        double tip = thickness(0.98D, span);
        assertTrue(middle > quarter, "中间应该比 1/4 处厚（月牙要「胖在中间」）✗");
        assertTrue(quarter > tip, "越靠两端越薄 ✗");
        assertTrue(tip < middle * 0.35D, "两端没有收成尖（tip=" + tip + " middle=" + middle + "）✗");

        // 弧度：中间要往 −Y 收，两端回到 0 ⇒ 是弯的不是直的 ✓
        double[] center = WindBladeQi.outer(span, 0.0D, 0.0D);
        double[] edge = WindBladeQi.outer(span, 1.0D, 0.0D);
        double[] half = WindBladeQi.outer(span, 0.5D, 0.0D);
        assertTrue(Math.abs(center[1]) > Math.abs(half[1]), "月牙的弧度方向不对 ✗");
        assertEquals(0.0D, edge[1], 1.0E-9D, "两端应该回到中轴（不然是斜的）✗");
        // 横向跨度 = 2 × 半宽 ✓
        assertEquals(span, edge[0], 1.0E-9D, "横向跨度应该等于半宽 ×2 的一半 ✗");
        assertEquals(-span, WindBladeQi.outer(span, -1.0D, 0.0D)[0], 1.0E-9D, "左右要对称 ✗");
    }

    @Test
    void fadingOutOrZeroSizeDrawsNothing() {
        List<double[]> a = new ArrayList<>();
        assertEquals(0, WindBladeQi.draw(strictConsumer(a), new Matrix4f(), HALF, 0.0F));
        assertTrue(a.isEmpty(), "alpha = 0 还在发顶点 ⇒ 白画 ✗");

        List<double[]> b = new ArrayList<>();
        assertEquals(0, WindBladeQi.draw(strictConsumer(b), new Matrix4f(), 0.0D, 1.0F));
        assertTrue(b.isEmpty(), "尺寸为 0 还在发顶点 ✗");
    }

    @Test
    void spellBladesAreOneSizeForEveryTier() throws Exception {
        // 作者：「体积统一一下」⇒ 龙卷风不再按 5/10/20 倍甩不同大小的刃 ✓
        String tornado = Files.readString(Path.of(
                "src/main/java/com/tnc/tnc/magic/TNTornadoEntity.java"));
        assertTrue(tornado.contains("TNWindBladeEntity.SPELL_BLADE_SIZE"),
                "龙卷风没有用统一尺寸（还在按档位 5/10/20 倍）✗");
        assertTrue(tornado.contains("体积统一一下") || tornado.contains("统一"),
                "统一尺寸这件事没在注释里写清楚 ⇒ 以后容易被改回去 ✗");

        // 世界尺寸：跨度 ≈ 2.8 格 —— 和战士剑气（半宽 1.2 ⇒ 2.4 格）同量级 ✓
        // ⚠️ 这里**不能**调 `hitRadiusFor(...)` ✗：那是实例/类方法，会触发 TNWindBladeEntity
        //    的静态初始化（注册 SynchedEntityData）⇒ 单元测试里直接 ExceptionInInitializerError ✗。
        //    所以判定半径那边的"√尺寸 + 封顶"改成源码扫描来钉 ✓。
        double span = TNWindBladeEntity.BASE_SPAN * TNWindBladeEntity.SPELL_BLADE_SIZE;
        assertTrue(span > 3.0D && span < 6.0D, "统一并加大后的跨度 " + span + " 格不对 ✗");
        // 2026-11-10 作者：「发射出的风刃不要一直旋转，就顺着龙卷风的风向直射出去就行」✓
        String tornadoSource = Files.readString(Path.of(
                "src/main/java/com/tnc/tnc/magic/TNTornadoEntity.java"));
        assertTrue(tornadoSource.contains("setSpinRate(0.0F)"),
                "龙卷风吐出的风刃还在自转（应为 0）✗");
        // 半宽必须正好是跨度的一半（渲染器直接用这个数，别再乘一次 ✗）
        assertEquals(span * 0.5D, HALF, 1.0E-9D, "半宽和跨度的换算错了 ✗");

        String entity = Files.readString(Path.of(
                "src/main/java/com/tnc/tnc/magic/TNWindBladeEntity.java"));
        assertTrue(entity.contains("public double halfSpanBlocks()"),
                "风刃没有把「半宽」暴露给渲染器 ⇒ 渲染只能再猜一次尺寸 ✗");
        assertTrue(entity.contains("BASE_HIT_RADIUS * Math.sqrt"),
                "判定半径不再是「√尺寸」（那样缩小后会和画面对不上）✗");
        assertTrue(entity.contains("MAX_HIT_RADIUS"),
                "判定半径没有封顶 ⇒ 一出生就够到旁边的怪 ✗");
    }

    @Test
    void theRendererNoLongerDrawsTheBakedModel() throws Exception {
        String renderer = Files.readString(Path.of(
                "src/main/java/com/tnc/tnc/client/TNWindBladeRenderer.java"));
        assertTrue(renderer.contains("WindBladeQi.draw("), "渲染器没有走新的剑气几何 ✗");
        assertFalse(renderer.contains("CustomModels.render"),
                "渲染器还在画烘焙月牙模型 ⇒ 改了个寂寞 ✗");
    }

    /**
     * ★ 2026-11-10 03:00 作者报的崩溃：{@code IllegalStateException: Not filled all elements
     * of the vertex}（栈顶 {@code WindBladeQi.vertex}）。
     *
     * <p>根因：{@code LightningRenderTypes.BOLT} 的顶点格式是
     * <b>position + color + uv + overlay + uv2 + normal</b>，只发 position/color 就会在
     * {@code endVertex()} 里被 {@code BufferBuilder} 当场抓出来 ✗。
     *
     * <p>这条闸门用一个<b>会较真</b>的假消费者：每个顶点少任何一个元素就在
     * {@code endVertex()} 抛异常 ⇒ 以后谁再简化顶点写法，测试当场红 ✓（不用等进游戏崩）。
     */
    @Test
    void everyVertexFillsTheWholeFormat() {
        List<double[]> vertices = new ArrayList<>();
        int claimed = WindBladeQi.draw(strictConsumer(vertices), new Matrix4f(), HALF, 1.0F);
        assertEquals(WindBladeQi.vertices(), claimed);
        assertEquals(claimed, vertices.size(), "有顶点没走到 endVertex ✗");
    }

    // ------------------------------------------------------------------
    //  工具
    // ------------------------------------------------------------------

    /** 某个 t 处的"厚度"（内缘 − 外缘的 y 差）✓。 */
    private static double thickness(double t, double span) {
        double[] outer = WindBladeQi.outer(span, t, 0.0D);
        double[] inner = WindBladeQi.inner(span, t, 0.34D * span, 0.0D);
        return inner[1] - outer[1];
    }

    /**
     * 较真的假消费者 ✓ —— 记录顶点，并检查<b>每个顶点都把格式填满了</b>
     * （position / color / uv / overlay / uv2 / normal）✗ 少一个就抛，和真 BufferBuilder 一样。
     */
    private static VertexConsumer strictConsumer(List<double[]> sink) {
        return (VertexConsumer) Proxy.newProxyInstance(
                WindBladeQiTest.class.getClassLoader(),
                new Class[]{VertexConsumer.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("vertex")) {
                        int start = args[0] instanceof Matrix4f ? 1 : 0;
                        sink.add(new double[]{
                                ((Number) args[start]).doubleValue(),
                                ((Number) args[start + 1]).doubleValue(),
                                ((Number) args[start + 2]).doubleValue(),
                                1.0D, 0.0D});
                    } else if (name.equals("color")) {
                        if (args.length == 4 && !sink.isEmpty()) {
                            sink.get(sink.size() - 1)[3] = ((Number) args[3]).doubleValue();
                            sink.get(sink.size() - 1)[4] += 1.0D;      // 标记"填过 color"
                        }
                    } else if (name.equals("uv") || name.equals("uv2")
                            || name.equals("overlayCoords") || name.equals("normal")) {
                        if (!sink.isEmpty()) {
                            sink.get(sink.size() - 1)[4] += 1.0D;      // 四个元素各记一次
                        }
                    } else if (name.equals("endVertex")) {
                        if (sink.isEmpty()) {
                            throw new IllegalStateException("endVertex 之前没有 vertex ✗");
                        }
                        double filled = sink.get(sink.size() - 1)[4];
                        if (filled < 5.0D) {
                            throw new IllegalStateException(
                                    "Not filled all elements of the vertex（只填了 "
                                            + (int) filled + "/5 个元素）✗ —— 就是 2026-11-10 那次崩溃");
                        }
                    }
                    return method.getReturnType() == void.class ? null : proxy;
                });
    }
}
