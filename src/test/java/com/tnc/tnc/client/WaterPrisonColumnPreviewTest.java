package com.tnc.tnc.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 罗马圣柱的**外观对照图**（作者 2026-11-10：「先做个圣柱的建模，然后弄成蓝色贴图再虚化」）。
 *
 * <p>为什么要有这张图：模型/贴图对不对，我只能靠"读 JSON"✗ —— 而"读得对"和"看着像根柱子"是两件事 ✗。
 * 这里把三段模型按**渲染器里那套比例**拼起来（柱础/柱头等比、柱身只拉高 ✓），
 * 用等轴测投影 + 贴图取色画出来 ✓，顺便把贴图本身贴在旁边 ✓。
 *
 * <p>输出 {@code build/water_prison_columns.png}。
 */
class WaterPrisonColumnPreviewTest {

    private static final int W = 980;
    private static final int H = 700;

    private record Face(double[][] p, Color color, double depth) {
    }

    private static JsonObject load(String name) throws Exception {
        String path = "/assets/tnc/models/water/" + name + ".json";
        try (InputStream in = WaterPrisonColumnPreviewTest.class.getResourceAsStream(path)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static BufferedImage texture() throws Exception {
        try (InputStream in = WaterPrisonColumnPreviewTest.class.getResourceAsStream(
                "/assets/tnc/textures/block/roman_column_blue.png")) {
            return ImageIO.read(in);
        }
    }

    /** 贴图里一块 16×16 区域的平均色（模型每个面都指向其中一块 ✓）。 */
    private static Color average(BufferedImage tex, int[] uv) {
        int u0 = uv[0], v0 = uv[1];
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int y = v0; y < v0 + 16; y++) {
            for (int x = u0; x < u0 + 16; x++) {
                int argb = tex.getRGB(x % tex.getWidth(), y % tex.getHeight());
                r += (argb >> 16) & 0xFF;
                g += (argb >> 8) & 0xFF;
                b += argb & 0xFF;
                n++;
            }
        }
        return new Color((int) (r / n), (int) (g / n), (int) (b / n));
    }

    /** 收集一个模型所有朝外的面（等轴测：视线方向 (1,1.6,1)）。 */
    private static List<Face> faces(JsonArray parts, BufferedImage tex, double scaleXZ, double scaleY,
                                    double originX, double originY, double originZ) {
        List<Face> out = new ArrayList<>();
        double[] view = {1.0, 1.6, 1.0};
        for (int i = 0; i < parts.size(); i++) {
            JsonObject part = parts.get(i).getAsJsonObject();
            double[] from = new double[3], to = new double[3];
            for (int k = 0; k < 3; k++) {
                from[k] = part.getAsJsonArray("from").get(k).getAsDouble();
                to[k] = part.getAsJsonArray("to").get(k).getAsDouble();
            }
            // 模型里的 rotation：柱身的 45° 板（Y 轴）、茛苕叶（X / Z 轴倾 22.5°）✓
            double angle = 0;
            char axisChar = 'y';
            double[] pivot = {8, 0, 8};
            if (part.has("rotation")) {
                JsonObject rot = part.getAsJsonObject("rotation");
                angle = Math.toRadians(rot.get("angle").getAsDouble());
                axisChar = rot.get("axis").getAsString().charAt(0);
                for (int k = 0; k < 3; k++) {
                    pivot[k] = rot.getAsJsonArray("origin").get(k).getAsDouble();
                }
            }
            String texKey = part.getAsJsonObject("faces").getAsJsonObject("north").get("texture").getAsString();
            int[] uv = new int[4];
            JsonArray uvArray = part.getAsJsonObject("faces").getAsJsonObject("north").getAsJsonArray("uv");
            for (int k = 0; k < 4; k++) {
                uv[k] = uvArray.get(k).getAsInt();
            }
            Color base = average(tex, uv);
            double[][] corners = {
                    {from[0], from[1], from[2]}, {to[0], from[1], from[2]},
                    {to[0], from[1], to[2]}, {from[0], from[1], to[2]},
                    {from[0], to[1], from[2]}, {to[0], to[1], from[2]},
                    {to[0], to[1], to[2]}, {from[0], to[1], to[2]}};
            for (double[] c : corners) {
                if (angle != 0) {
                    double dx = c[0] - pivot[0], dy = c[1] - pivot[1], dz = c[2] - pivot[2];
                    double cos = Math.cos(angle), sin = Math.sin(angle);
                    switch (axisChar) {
                        case 'x' -> {
                            c[1] = pivot[1] + dy * cos - dz * sin;
                            c[2] = pivot[2] + dy * sin + dz * cos;
                        }
                        case 'z' -> {
                            c[0] = pivot[0] + dx * cos - dy * sin;
                            c[1] = pivot[1] + dx * sin + dy * cos;
                        }
                        default -> {
                            c[0] = pivot[0] + dx * cos - dz * sin;
                            c[2] = pivot[2] + dx * sin + dz * cos;
                        }
                    }
                }
                c[0] = originX + (c[0] / 16.0 - 0.5) * scaleXZ;
                c[2] = originZ + (c[2] / 16.0 - 0.5) * scaleXZ;
                c[1] = originY + c[1] / 16.0 * scaleY;
            }
            int[][] quads = {{4, 5, 6, 7}, {0, 1, 2, 3}, {0, 1, 5, 4}, {3, 2, 6, 7}, {0, 3, 7, 4}, {1, 2, 6, 5}};
            double[][] normals = {{0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
            for (int f = 0; f < quads.length; f++) {
                double dot = normals[f][0] * view[0] + normals[f][1] * view[1] + normals[f][2] * view[2];
                if (dot <= 0) {
                    continue;
                }
                double[][] pts = new double[4][];
                double depth = 0;
                for (int k = 0; k < 4; k++) {
                    pts[k] = corners[quads[f][k]];
                    depth += pts[k][0] * view[0] + pts[k][1] * view[1] + pts[k][2] * view[2];
                }
                double shade = normals[f][1] > 0 ? 1.0 : (normals[f][2] != 0 ? 0.78 : 0.62);
                Color col = new Color(
                        (int) Math.min(255, base.getRed() * shade),
                        (int) Math.min(255, base.getGreen() * shade),
                        (int) Math.min(255, base.getBlue() * shade));
                out.add(new Face(pts, col, depth / 4.0));
            }
        }
        out.sort(Comparator.comparingDouble(Face::depth));
        return out;
    }

    /** 等轴测投影：世界 (x,y,z) → 屏幕（y 朝上 ✓）。 */
    private static double[] project(double x, double y, double z, double scale, int cx, int cy) {
        double sx = (x - z) * 0.866;
        double sy = (x + z) * 0.5 - y;
        return new double[]{cx + sx * scale, cy + sy * scale};
    }

    /** 只画一段模型（放大看细节用 ✓）。 */
    private static void drawParts(Graphics2D g, JsonArray parts, BufferedImage tex, double scaleXZ,
                                  double scaleY, double ox, double oy, double oz,
                                  double scale, int cx, int cy) {
        List<Face> all = new ArrayList<>(faces(parts, tex, scaleXZ, scaleY, ox, oy, oz));
        all.sort(Comparator.comparingDouble(Face::depth));
        for (Face face : all) {
            int[] xs = new int[4], ys = new int[4];
            for (int k = 0; k < 4; k++) {
                double[] p = project(face.p()[k][0], face.p()[k][1], face.p()[k][2], scale, cx, cy);
                xs[k] = (int) Math.round(p[0]);
                ys[k] = (int) Math.round(p[1]);
            }
            g.setColor(face.color());
            g.fillPolygon(xs, ys, 4);
            g.setColor(new Color(255, 255, 255, 40));
            g.drawPolygon(xs, ys, 4);
        }
    }

    private static void drawColumn(Graphics2D g, JsonObject base, JsonObject shaft, JsonObject capital,
                                   JsonObject ornament, BufferedImage tex, double height, double diameter,
                                   int cx, int cy, double scale, boolean detailed) {
        // 和 WaterSolidModels.romanColumns 同一套比例 ✓（收分 entasis 也照抄 ✓）
        double s = diameter / 0.5;
        double baseH = 0.3875 * s * 1.15;
        double capH = 0.3594 * s * 1.5;
        double shaftH = Math.max(0.4, height - baseH - capH);
        List<Face> all = new ArrayList<>();
        all.addAll(faces(base.getAsJsonArray("elements"), tex, s, s * 1.15, 0, 0, 0));
        double[] taper = {1.0, 0.985, 0.97};
        for (int seg = 0; seg < taper.length; seg++) {
            double segH = shaftH / taper.length;
            all.addAll(faces(shaft.getAsJsonArray("elements"), tex, s * taper[seg], segH,
                    0, baseH + segH * seg, 0));
        }
        all.addAll(faces(capital.getAsJsonArray("elements"), tex, s, s * 1.5, 0, baseH + shaftH, 0));
        if (detailed) {
            all.addAll(faces(ornament.getAsJsonArray("elements"), tex, s, s * 1.5, 0, baseH + shaftH, 0));
        }
        all.sort(Comparator.comparingDouble(Face::depth));
        for (Face face : all) {
            int[] xs = new int[4], ys = new int[4];
            for (int k = 0; k < 4; k++) {
                double[] p = project(face.p()[k][0], face.p()[k][1], face.p()[k][2], scale, cx, cy);
                xs[k] = (int) Math.round(p[0]);
                ys[k] = (int) Math.round(p[1]);
            }
            g.setColor(face.color());
            g.fillPolygon(xs, ys, 4);
            g.setColor(new Color(255, 255, 255, 28));
            g.drawPolygon(xs, ys, 4);
        }
    }

    @Test
    void rendersTheRomanColumns() throws Exception {
        Files.createDirectories(Path.of("build"));
        JsonObject base = load("roman_column_base");
        JsonObject shaft = load("roman_column_shaft");
        JsonObject capital = load("roman_column_capital");
        JsonObject ornament = load("roman_column_ornament");
        BufferedImage tex = texture();

        BufferedImage page = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = page.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(10, 18, 28));
        g.fillRect(0, 0, W, H);

        // 上面一排：**放大看细节**（柱头 + 柱础 ✓）—— 作者要的"精致"就看这两块 ✗
        drawParts(g, capital.getAsJsonArray("elements"), tex, 1.0, 1.5, 0, 0, 0, 150, 250, 210);
        drawParts(g, ornament.getAsJsonArray("elements"), tex, 1.0, 1.5, 0, 0, 0, 150, 250, 210);
        drawParts(g, base.getAsJsonArray("elements"), tex, 1.0, 1.15, 0, 0, 0, 150, 620, 210);
        g.setColor(new Color(180, 215, 240));
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString("capital close-up (with ornament)", 150, 320);
        g.drawString("base close-up", 550, 320);

        // 下面一排：四根柱子（比例 / 贴图 / LOD 一起看 ✓）
        double[] heights = {7.0, 12.6, 23.8, 23.8};
        double[] radius = {5.0, 12.0, 26.0, 26.0};
        boolean[] near = {true, true, true, false};
        String[] labels = {"t1  7 blocks", "t3  12.6 blocks", "t5  23.8 (near)", "t5 (far, no ornament)"};
        int[] cx = {150, 380, 640, 860};
        for (int i = 0; i < heights.length; i++) {
            double scale = 8.0;
            int cy = 640;
            int columns = WaterGeometry.columnCount(radius[i]);
            double diameter = WaterGeometry.columnDiameter(radius[i], heights[i], columns);
            drawColumn(g, base, shaft, capital, ornament, tex, heights[i], diameter, cx[i], cy, scale, near[i]);
            g.setColor(new Color(180, 215, 240));
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.drawString(labels[i], cx[i] - 74, 672);
            g.setColor(new Color(140, 180, 210));
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.drawString(columns + " cols, " + String.format(java.util.Locale.ROOT, "%.2f", diameter)
                    + " blocks", cx[i] - 74, 689);
        }

        // 贴图原图（放大 3 倍）贴在右上角 ✓
        int tx = 770, ty = 60, zoom = 3;
        g.setColor(new Color(30, 45, 60));
        g.fillRect(tx - 6, ty - 6, tex.getWidth() * zoom + 12, tex.getHeight() * zoom + 12);
        for (int y = 0; y < tex.getHeight(); y++) {
            for (int x = 0; x < tex.getWidth(); x++) {
                g.setColor(new Color(tex.getRGB(x, y), false));
                g.fillRect(tx + x * zoom, ty + y * zoom, zoom, zoom);
            }
        }
        g.setColor(new Color(180, 215, 240));
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString("roman_column_blue.png (64x64)", tx - 4, ty - 16);
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g.setColor(new Color(150, 190, 215));
        g.drawString("flutes / stone / gold / deep / band", tx - 4, ty + tex.getHeight() * zoom + 30);

        g.setColor(new Color(200, 230, 250));
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.drawString("water prison: Roman columns (base / shaft / capital, one scale)", 24, 34);
        g.dispose();
        Path out = Path.of("build", "water_prison_columns.png");
        ImageIO.write(page, "png", out.toFile());
        System.out.println("水牢圣柱预览 → " + out);
    }
}
