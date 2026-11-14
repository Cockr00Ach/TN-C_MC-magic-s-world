package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 法阵长什么样的**对照图**（作者 2026-11-10：「法阵太简陋了吧，你根本没设计吧」✗）。
 *
 * <p>和 `WaterBeamLightningPreviewTest` 一个路子：顶点从<b>真正的渲染代码</b>
 * （{@link TNWaterGateRenderer#seal}）里取 ✓，再用软件光栅 + 加色混合画出来，
 * 这样"设计"是能被看见的（我这边进不了游戏 ✗）。
 *
 * <p>输出 {@code build/water_gate_seals.png}：t4 小阵 / t5 小阵 / 齐射闪光 / t5 汇聚大阵（5 倍）。
 */
class WaterGateSealPreviewTest {

    private static final int CELL_W = 420;
    private static final int CELL_H = 420;

    private record Quad(Vec3[] p, float[][] c) {
    }

    private static List<Quad> capture(java.util.function.Consumer<VertexConsumer> draw) {
        List<Vec3> pts = new ArrayList<>();
        List<float[]> cols = new ArrayList<>();
        VertexConsumer out = (VertexConsumer) Proxy.newProxyInstance(
                WaterGateSealPreviewTest.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("vertex")) {
                        Matrix4f m = (Matrix4f) args[0];
                        float x = (Float) args[1], y = (Float) args[2], z = (Float) args[3];
                        pts.add(new Vec3(
                                m.m00() * x + m.m10() * y + m.m20() * z + m.m30(),
                                m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
                                m.m02() * x + m.m12() * y + m.m22() * z + m.m32()));
                    } else if (method.getName().equals("color")) {
                        float[] c = new float[4];
                        for (int i = 0; i < 4; i++) {
                            c[i] = ((Number) args[i]).floatValue();
                        }
                        cols.add(c);
                    }
                    return method.getReturnType() == void.class ? null : proxy;
                });
        draw.accept(out);
        List<Quad> quads = new ArrayList<>();
        for (int i = 0; i + 3 < pts.size(); i += 4) {
            quads.add(new Quad(new Vec3[]{pts.get(i), pts.get(i + 1), pts.get(i + 2), pts.get(i + 3)},
                    new float[][]{cols.get(i), cols.get(i + 1), cols.get(i + 2), cols.get(i + 3)}));
        }
        return quads;
    }

    /** 顶点数（顺便量一下新阵面的成本 ✓）。 */
    private static int count(java.util.function.Consumer<VertexConsumer> draw) {
        var counter = new Object() {
            int vertices;
        };
        VertexConsumer out = (VertexConsumer) Proxy.newProxyInstance(
                WaterGateSealPreviewTest.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("vertex")) {
                        counter.vertices++;
                    }
                    return method.getReturnType() == void.class ? null : proxy;
                });
        draw.accept(out);
        return counter.vertices;
    }

    private static BufferedImage raster(List<Quad> quads, double scale, Color background) {
        float[] hdr = new float[CELL_W * CELL_H * 3];
        for (int i = 0; i < hdr.length; i += 3) {
            hdr[i] = background.getRed() / 255F;
            hdr[i + 1] = background.getGreen() / 255F;
            hdr[i + 2] = background.getBlue() / 255F;
        }
        for (Quad quad : quads) {
            for (int tri = 0; tri < 2; tri++) {
                int[] idx = tri == 0 ? new int[]{0, 1, 2} : new int[]{0, 2, 3};
                double[][] s = new double[3][];
                for (int k = 0; k < 3; k++) {
                    Vec3 p = quad.p()[idx[k]];
                    // 正交投影（法阵是正对着我们的一个平面 ✓）
                    s[k] = new double[]{CELL_W * 0.5 + p.x * scale, CELL_H * 0.5 - p.y * scale};
                }
                float[][] c = {quad.c()[idx[0]], quad.c()[idx[1]], quad.c()[idx[2]]};
                fillTriangle(hdr, s, c);
            }
        }
        BufferedImage image = new BufferedImage(CELL_W, CELL_H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < CELL_H; y++) {
            for (int x = 0; x < CELL_W; x++) {
                int o = (y * CELL_W + x) * 3;
                image.setRGB(x, y, tone(hdr[o]) << 16 | tone(hdr[o + 1]) << 8 | tone(hdr[o + 2]));
            }
        }
        return image;
    }

    private static void fillTriangle(float[] hdr, double[][] s, float[][] c) {
        double minX = Math.min(s[0][0], Math.min(s[1][0], s[2][0]));
        double maxX = Math.max(s[0][0], Math.max(s[1][0], s[2][0]));
        double minY = Math.min(s[0][1], Math.min(s[1][1], s[2][1]));
        double maxY = Math.max(s[0][1], Math.max(s[1][1], s[2][1]));
        int x0 = Math.max(0, (int) Math.floor(minX)), x1 = Math.min(CELL_W - 1, (int) Math.ceil(maxX));
        int y0 = Math.max(0, (int) Math.floor(minY)), y1 = Math.min(CELL_H - 1, (int) Math.ceil(maxY));
        if (x0 > x1 || y0 > y1) {
            return;
        }
        double area = (s[1][0] - s[0][0]) * (s[2][1] - s[0][1]) - (s[2][0] - s[0][0]) * (s[1][1] - s[0][1]);
        if (Math.abs(area) < 1e-9) {
            return;
        }
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double px = x + 0.5, py = y + 0.5;
                double w0 = ((s[1][0] - px) * (s[2][1] - py) - (s[2][0] - px) * (s[1][1] - py)) / area;
                double w1 = ((s[2][0] - px) * (s[0][1] - py) - (s[0][0] - px) * (s[2][1] - py)) / area;
                double w2 = 1.0 - w0 - w1;
                if (w0 < -1e-4 || w1 < -1e-4 || w2 < -1e-4) {
                    continue;
                }
                double alpha = w0 * c[0][3] + w1 * c[1][3] + w2 * c[2][3];
                if (alpha <= 0.002) {
                    continue;
                }
                int o = (y * CELL_W + x) * 3;
                hdr[o] += (w0 * c[0][0] + w1 * c[1][0] + w2 * c[2][0]) * alpha;
                hdr[o + 1] += (w0 * c[0][1] + w1 * c[1][1] + w2 * c[2][1]) * alpha;
                hdr[o + 2] += (w0 * c[0][2] + w1 * c[1][2] + w2 * c[2][2]) * alpha;
            }
        }
    }

    private static int tone(float value) {
        double mapped = 1.0 - Math.exp(-Math.max(0.0, value) * 1.35);
        return (int) Math.round(Math.max(0, Math.min(1, mapped)) * 255.0);
    }

    @Test
    void sealCostStaysWithinBudget() {
        Matrix4f pose = new Matrix4f();
        Vec3 axis = new Vec3(0, 0, 1);
        int compact = count(out -> TNWaterGateRenderer.seal(out, out, pose, Vec3.ZERO, axis, 0.62, 40, 1F, 5, false));
        int full = count(out -> TNWaterGateRenderer.seal(out, out, pose, Vec3.ZERO, axis, 0.62, 40, 1F, 5, true));
        int giant = count(out -> TNWaterGateRenderer.seal(out, out, pose, Vec3.ZERO, axis,
                com.tnc.tnc.magic.water.WaterGateRules.giantSealRadius(5), 40, 1F, 5, true));
        System.out.println("TN-C water cost: gate seal compact=" + compact + " full=" + full + " giant=" + giant);
        // 齐射那一瞬间同屏八成座 ⇒ 精简版必须够便宜（否则一次齐射就要上万顶点 ✗）
        assertTrue(compact * com.tnc.tnc.magic.water.WaterGateRules.portals(5) <= 9_000,
                "eight volley portals must stay under 9000 vertices, was " + compact * 8);
        assertTrue(full <= 2_200, "one fully designed portal must stay under 2200 vertices, was " + full);
        assertTrue(giant <= 3_400, "the merged giant seal must stay under 3400 vertices, was " + giant);
    }

    @Test
    void rendersTheDesignedSeal() throws Exception {
        Files.createDirectories(Path.of("build"));
        Matrix4f pose = new Matrix4f();
        Vec3 axis = new Vec3(0, 0, 1);
        Color bg = new Color(8, 16, 26);

        String[] labels = {
                "volley portal (compact)  r=0.62", "idle / solo portal (full)  r=0.62",
                "t5 portal (full)  r=0.95", "t5 merged  r=4.75 (5x)"};
        double[] radius = {0.62, 0.62, 0.95, 4.75};
        float[] alpha = {1.0F, 0.95F, 0.95F, 0.95F};
        double[] scale = {240, 240, 200, 30};     // 巨阵按同一比例缩放，直观看出 5 倍 ✓
        double[] time = {96, 40, 40, 120};
        boolean[] full = {false, true, true, true};

        BufferedImage page = new BufferedImage(CELL_W * 2, (CELL_H + 26) * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = page.createGraphics();
        g.setColor(bg);
        g.fillRect(0, 0, page.getWidth(), page.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        for (int i = 0; i < labels.length; i++) {
            final double r = radius[i];
            final float a = alpha[i];
            final double t = time[i];
            final boolean f = full[i];
            List<Quad> quads = capture(out -> TNWaterGateRenderer.seal(out, out, pose, Vec3.ZERO, axis, r, t, a, 5, f));
            int verts = count(out -> TNWaterGateRenderer.seal(out, out, pose, Vec3.ZERO, axis, r, t, a, 5, f));
            BufferedImage cell = raster(quads, scale[i], bg);
            int cx = (i % 2) * CELL_W, cy = (i / 2) * (CELL_H + 26);
            g.drawImage(cell, cx, cy, null);
            g.setColor(new Color(190, 225, 245));
            g.drawString(labels[i] + "   (" + quads.size() + " quads / " + verts + " verts)", cx + 14, cy + CELL_H + 18);
        }
        g.dispose();
        Path out = Path.of("build", "water_gate_seals.png");
        ImageIO.write(page, "png", out.toFile());
        System.out.println("法阵设计图 → " + out);
    }
}
