package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.water.WaterSpellRules;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 水球链 t4/t5 水波的**肉眼对照图**（旧水管 ⬆ / 新闪电 ⬇）✓。
 *
 * <p>2026-11-09 作者：「一根直直的闪电像不像水波嘞，然后你把这个闪电加粗加大到跟水波一样，
 * 替换一下看看效果」—— 效果只能靠看 ✗，而这一轮我进不了游戏 ✗ ⇒ 这里用<b>软件光栅</b>
 * 把两份几何真的画出来：
 * <ol>
 *   <li>顶点从<b>真正的渲染代码</b>里取（{@link WaterBeamVisuals#render} 和下面那份旧实现 ✓），
 *       不是另写一份近似 ✗；</li>
 *   <li>两张图用<b>同一个相机、同一把尺子</b>（下角有 10 格标尺 ✓）⇒ 粗细/长度能直接比 ✓；</li>
 *   <li>加色混合 + 光晕 tonemap（{@code 1-exp(-x)} ✓）—— 和游戏里那层 {@code LIGHTNING_TRANSPARENCY}
 *       一样是"越叠越白"，所以"白芯会不会过曝"这张图就能看出来 ✓。</li>
 * </ol>
 *
 * <p>输出：{@code build/water_beam_t4_before_after.png}、
 * {@code build/water_beam_t5_before_after.png}。
 */
class WaterBeamLightningPreviewTest {

    private static final int W = 960;
    private static final int H = 480;

    // ------------------------------------------------------------------
    //  采集：把真的顶点录下来
    // ------------------------------------------------------------------

    /** 一个四边形（渲染层写死了 QUADS ✓）+ 四个顶点的颜色。 */
    private record Quad(Vec3[] p, float[][] c) {
    }

    /** 假 {@link VertexConsumer}：把 {@code vertex(Matrix4f,x,y,z).color(...).endVertex()} 记成四边形 ✓。 */
    private static List<Quad> capture(java.util.function.Consumer<VertexConsumer> draw) {
        List<Vec3> pts = new ArrayList<>();
        List<float[]> cols = new ArrayList<>();
        VertexConsumer out = (VertexConsumer) Proxy.newProxyInstance(
                WaterBeamLightningPreviewTest.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "vertex" -> {
                            Matrix4f m = (Matrix4f) args[0];
                            float x = (Float) args[1], y = (Float) args[2], z = (Float) args[3];
                            pts.add(new Vec3(
                                    m.m00() * x + m.m10() * y + m.m20() * z + m.m30(),
                                    m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
                                    m.m02() * x + m.m12() * y + m.m22() * z + m.m32()));
                        }
                        case "color" -> {
                            float[] c = new float[4];
                            if (args.length == 4) {
                                for (int i = 0; i < 4; i++) {
                                    c[i] = ((Number) args[i]).floatValue();
                                }
                            } else {
                                for (int i = 0; i < 4; i++) {
                                    c[i] = ((Number) args[i]).intValue() / 255F;
                                }
                            }
                            cols.add(c);
                        }
                        default -> {
                        }
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

    // ------------------------------------------------------------------
    //  旧版水波（替换前那份，逐行照抄，只为了对照 ✓）
    // ------------------------------------------------------------------

    private static void legacy(VertexConsumer body, VertexConsumer glow, Matrix4f pose, int tier, Vec3 dir,
                               double length, double age, float fade, boolean detail) {
        if (length <= 0) {
            return;
        }
        double r = WaterSpellRules.radius(tier);
        Vec3 end = dir.scale(length), right = WaterSpellRules.right(dir);
        Vec3 up = right.cross(dir).normalize();
        int segments = detail ? 32 : 16;
        WaterGeometry.tube(body, pose, Vec3.ZERO, end, r, .03F, .25F, .98F, fade * .52F, segments);
        WaterGeometry.tube(glow, pose, Vec3.ZERO, end, r * .10, .55F, .92F, 1, fade * .32F, detail ? 12 : 8);
        int strands = detail ? 3 : 2;
        double step = tier == 5 ? (detail ? 4 : 8) : (detail ? 2 : 4);
        for (int strand = 0; strand < strands; strand++)
            for (double z = 0; z < length; z += step) {
                double z2 = Math.min(length, z + step), a = z * .32 - age * .24 + strand * Math.PI * 2 / strands;
                double b = z2 * .32 - age * .24 + strand * Math.PI * 2 / strands;
                Vec3 p = dir.scale(z).add(WaterGeometry.radial(right, up, a, r * .96));
                Vec3 q = dir.scale(z2).add(WaterGeometry.radial(right, up, b, r * .96));
                boolean violet = (strand + (int) (z / step)) % 10 == 9;
                WaterGeometry.stroke(body, pose, dir, p, q, tier == 5 ? .18 : .035,
                        violet ? .58F : .25F, violet ? .14F : .65F, 1, fade * .7F);
            }
        double spacing = tier == 5 ? 16 : 8;
        for (double z = (age * 1.4) % spacing; z < length; z += spacing) {
            WaterGeometry.ringColor(body, pose, dir.scale(z), dir, r, .06 + r * .005,
                    .42F, .86F, 1, fade * .5F, segments);
        }
        WaterGeometry.disk(body, pose, end, dir, r, .08F, .52F, 1, fade * .35F, segments);
        WaterGeometry.ringColor(glow, pose, end, dir, r, .06 + r * .01, .65F, .95F, 1, fade * .68F, segments);
        WaterGeometry.ringColor(body, pose, end, dir, r * 1.15, .06 + r * .004, .30F, .75F, 1, fade * .45F, segments);
    }

    // ------------------------------------------------------------------
    //  软件光栅：加法混合 + tonemap
    // ------------------------------------------------------------------

    private static final class Camera {
        final Vec3 eye, forward, right, up;
        final double focal;

        Camera(Vec3 eye, Vec3 lookAt, double focal) {
            this.eye = eye;
            this.forward = lookAt.subtract(eye).normalize();
            Vec3 r = forward.cross(new Vec3(0, 1, 0));
            if (r.lengthSqr() < 1e-6) {
                r = new Vec3(1, 0, 0);
            }
            this.right = r.normalize();
            this.up = right.cross(forward).normalize();
            this.focal = focal;
        }

        /** 世界点 → 屏幕（z = 相机前方距离；<=0 表示在身后 ✓）。 */
        double[] project(Vec3 p) {
            Vec3 v = p.subtract(eye);
            double z = v.dot(forward);
            if (z < 0.05) {
                return null;
            }
            return new double[]{W * 0.5 + v.dot(right) / z * focal, H * 0.5 - v.dot(up) / z * focal, z};
        }
    }

    private static BufferedImage raster(List<Quad> quads, Camera camera, Color background) {
        float[] hdr = new float[W * H * 3];
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
                    s[k] = camera.project(quad.p()[idx[k]]);
                    if (s[k] == null) {
                        s = null;
                        break;
                    }
                }
                if (s == null) {
                    continue;
                }
                float[][] c = {quad.c()[idx[0]], quad.c()[idx[1]], quad.c()[idx[2]]};
                fillTriangle(hdr, s, c);
            }
        }
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int o = (y * W + x) * 3;
                int r = tone(hdr[o]), g = tone(hdr[o + 1]), b = tone(hdr[o + 2]);
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    /** 逐像素相加（加色混合 ✓ 和游戏里那层 LIGHTNING_TRANSPARENCY 同一套语义）。 */
    private static void fillTriangle(float[] hdr, double[][] s, float[][] c) {
        double minX = Math.min(s[0][0], Math.min(s[1][0], s[2][0]));
        double maxX = Math.max(s[0][0], Math.max(s[1][0], s[2][0]));
        double minY = Math.min(s[0][1], Math.min(s[1][1], s[2][1]));
        double maxY = Math.max(s[0][1], Math.max(s[1][1], s[2][1]));
        int x0 = Math.max(0, (int) Math.floor(minX)), x1 = Math.min(W - 1, (int) Math.ceil(maxX));
        int y0 = Math.max(0, (int) Math.floor(minY)), y1 = Math.min(H - 1, (int) Math.ceil(maxY));
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
                int o = (y * W + x) * 3;
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

    // ------------------------------------------------------------------
    //  出图
    // ------------------------------------------------------------------

    @Test
    void rendersBeforeAndAfterForBothTiers() throws IOException {
        Files.createDirectories(Path.of("build"));
        for (int tier : new int[]{4, 5}) {
            double radius = WaterSpellRules.radius(tier);
            double length = WaterSpellRules.range(tier);
            Vec3 dir = new Vec3(1, 0, 0);
            // 侧视：光束横穿画面 ✓ 两遍用同一个相机 ⇒ 粗细/长度可以直接比 ✓
            double focal = W * 0.55;
            double distance = length * focal / (W * 0.88);
            Vec3 camera = new Vec3(length * 0.5, 0, distance);
            Vec3 lookAt = new Vec3(length * 0.5, 0, 0);
            Camera cam = new Camera(camera, lookAt, focal);

            List<Quad> before = capture(out -> legacy(out, out, new Matrix4f(), tier, dir, length, 40, 1F, true));
            List<Quad> after = capture(out -> WaterBeamVisuals.render(out, out, new Matrix4f(), tier, dir,
                    length, 40, 1F, true, camera));

            BufferedImage top = raster(before, cam, new Color(10, 20, 30));
            BufferedImage bottom = raster(after, cam, new Color(10, 20, 30));
            BufferedImage page = new BufferedImage(W, H * 2 + 8, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = page.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(top, 0, 0, null);
            g.setColor(new Color(40, 60, 80));
            g.fillRect(0, H, W, 8);
            g.drawImage(bottom, 0, H + 8, null);

            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 22));
            g.setColor(Color.WHITE);
            g.drawString("BEFORE  water tube  (t" + tier + "  r=" + radius + "  len=" + (int) length + ")",
                    22, 34);
            g.drawString("AFTER   lightning bolt scaled to the wave  (t" + tier + ")", 22, H + 44);
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 16));
            g.setColor(new Color(190, 220, 240));
            g.drawString("same camera, additive glow, 1 block = measured by the ruler", 22, 58);
            g.drawString("same camera, additive glow, 1 block = measured by the ruler", 22, H + 68);
            for (int panel = 0; panel < 2; panel++) {
                int dy = panel == 0 ? 0 : H + 8;
                drawRuler(g, cam, dir, dy);
            }
            g.dispose();
            Path out = Path.of("build", "water_beam_t" + tier + "_before_after.png");
            ImageIO.write(page, "png", out.toFile());
            System.out.println("水波对照图 t" + tier + " → " + out + "（旧 " + before.size()
                    + " 个四边形 / 新 " + after.size() + " 个四边形）");
        }
    }

    /** 沿光束方向画一把"每格一格"的尺子，直接量粗细/长度 ✓。 */
    private static void drawRuler(Graphics2D g, Camera cam, Vec3 dir, int dy) {
        g.setStroke(new BasicStroke(1.6F));
        double length = 20;
        double[] a = cam.project(new Vec3(0, -1.5, 0));
        double[] b = cam.project(new Vec3(length, -1.5, 0));
        if (a == null || b == null) {
            return;
        }
        g.setColor(new Color(120, 190, 235));
        g.drawLine((int) a[0], (int) a[1] + dy, (int) b[0], (int) b[1] + dy);
        for (int i = 0; i <= (int) length; i++) {
            double[] p = cam.project(new Vec3(i, -1.5, 0));
            if (p == null) {
                continue;
            }
            int tick = i % 5 == 0 ? 7 : 4;
            g.drawLine((int) p[0], (int) p[1] + dy - tick, (int) p[0], (int) p[1] + dy + tick);
        }
        g.setColor(new Color(160, 210, 240));
        g.drawString("20 blocks", (int) b[0] + 8, (int) b[1] + dy + 5);
    }
}
