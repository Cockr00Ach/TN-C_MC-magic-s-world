package com.tnc.tnc.light.client;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ★★ <b>把光翼模型"带贴图"离线画成 PNG</b> ✓（作者 2026-10-01："你自己看看图片这是啥"✗）。
 *
 * <h2>为什么要自己写个小软渲染</h2>
 * 我这个会话**看不到游戏画面** ✗，而"这模型到底像不像一对翅膀"是纯视觉的事 ✗。
 * 之前用数字/字符画判断过一轮 —— 那只能说明"点云对称"✗，**说明不了"看起来是什么样"**✗
 * （作者看到的是一堆散板子 ✗）。所以这里用**和渲染完全相同的那份数学**
 * （{@link TNLightWingsModel#previewFaces} ✓，它内部直接调 {@code buildMatrices} ✓）
 * 把六面体贴上图、投影成图片 ✓ —— 我看得见，作者也看得见 ✓。
 *
 * <p>算法：画家算法（面按到相机的距离排序，远的先画 ✓）＋ 最近邻采样 ✓；
 * 不做光照（游戏里是自发光 ✓），alpha = 贴图 alpha × 0.78 ✓（和游戏一致 ✓）。
 * 还会画一个"原版躯干"当参照物 ✓（x±0.25 / y0.75..1.5 / z±0.125 ✓）——
 * 这样"翅膀长在哪儿、朝哪边"一眼就能看出来 ✓。
 */
class LightWingsPreviewTest {

    private static final int W = 720;
    private static final int H = 720;

    /** 一个待画的面：位置 4 个 + uv 4 组；{@code solid != 0} 时是纯色（画参照物用 ✓）。 */
    private record Quad(double[][] pos, double[][] uv, int solid) {
    }

    @Test
    void renderTexturedPreview() throws Exception {
        loadModel();
        BufferedImage tex = loadTexture();
        List<TNLightWingsModel.PreviewFace> faces = TNLightWingsModel.previewFaces(0.0F, false);
        assertTrue(faces.size() > 200, "面数太少：" + faces.size());

        // 视角：yaw 0 = 从 +Z 方向看过去（模型 +Z 是胸口 ⇒ 这就是"站在角色背后看" ✓）
        render(faces, tex, 0.0D, "build/light_wings_view_back.png", false);
        render(faces, tex, 90.0D, "build/light_wings_view_side.png", false);
        render(faces, tex, 205.0D, "build/light_wings_view_3q.png", false);
        // ★ 同一份几何、**不贴图**（纯色）⇒ 把"几何对不对"和"贴图缺不缺"彻底分开 ✓
        render(faces, tex, 0.0D, "build/light_wings_geometry_only.png", true);
        System.out.println("[光翼预览已写出] build/light_wings_view_back|side|3q.png + geometry_only.png");
    }

    /**
     * ★★ <b>旋转手性 A/B</b>（2026-10-01 作者："你每个随便自己转肯定不对的"✗）：
     * 同一份几何、同一个视角，只把"Bedrock 的旋转方向"取正／取反各画一张 ✓ ——
     * 羽毛是靠 Z 旋转成扇的（25°~62.5° ✓），方向反了整对翅膀会朝下摊成裙子 ✗。
     * 两张图都写进 `build/` ✓：谁像翅膀，用谁 ✓（作者一眼就能指 ✓）。
     */
    @Test
    void renderRotationHandednessAb() throws Exception {
        loadModel();
        BufferedImage tex = loadTexture();
        // 三张都画（纯色、排除贴图干扰 ✓）：不旋转 / +1 / −1 ✓
        render(TNLightWingsModel.previewFaces(0.0F, false, 0.0F), tex,
                0.0D, "build/light_wings_rot_none.png", true);
        render(TNLightWingsModel.previewFaces(0.0F, false, TNLightWingsModel.ROT_PLUS), tex,
                0.0D, "build/light_wings_rot_plus.png", true);
        render(TNLightWingsModel.previewFaces(0.0F, false, TNLightWingsModel.ROT_MINUS), tex,
                0.0D, "build/light_wings_rot_minus.png", true);
        System.out.println("[旋转手性对比已写出] build/light_wings_rot_none|plus|minus.png（纯色 ✓）");
    }

    private static void render(List<TNLightWingsModel.PreviewFace> faces, BufferedImage tex,
                               double yawDeg, String out, boolean solidOnly) throws Exception {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                img.setRGB(x, y, 0xFF0E0E14);
            }
        }
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(10.0D);
        double distance = 7.5D;
        double[] target = {0.0D, 1.15D, 0.0D};
        double[] eye = {
                target[0] + Math.sin(yaw) * Math.cos(pitch) * distance,
                target[1] + Math.sin(pitch) * distance,
                target[2] + Math.cos(yaw) * Math.cos(pitch) * distance};
        double[] fwd = norm(new double[]{target[0] - eye[0], target[1] - eye[1], target[2] - eye[2]});
        double[] right = norm(cross(fwd, new double[]{0.0D, 1.0D, 0.0D}));
        double[] up = cross(right, fwd);

        List<Quad> quads = new ArrayList<>();
        // 参照物：原版玩家（腿 / 躯干 / 头 ✓，纯色 ✓）
        addBox(quads, 0.0D, 0.0D, 0.0D, 0.5D, 0.75D, 0.25D, 0xFF2B2B36);
        addBox(quads, 0.0D, 0.75D, 0.0D, 0.5D, 0.75D, 0.25D, 0xFF40404F);
        addBox(quads, 0.0D, 1.5D, 0.0D, 0.5D, 0.5D, 0.5D, 0xFF35353F);
        for (TNLightWingsModel.PreviewFace f : faces) {
            if (f.guide()) {
                continue;                     // 参考块不画（游戏里也不画 ✓）
            }
            double[][] pos = new double[4][];
            for (int i = 0; i < 4; i++) {
                pos[i] = new double[]{f.pos()[i][0], f.pos()[i][1], f.pos()[i][2]};
            }
            double[][] uv = new double[][]{
                    {f.uv()[0][0], f.uv()[0][1]}, {f.uv()[1][0], f.uv()[1][1]},
                    {f.uv()[2][0], f.uv()[2][1]}, {f.uv()[3][0], f.uv()[3][1]}};
            quads.add(new Quad(pos, uv, 0));
        }
        quads.sort(Comparator.comparingDouble((Quad q) -> -depthOf(q, eye)));
        for (Quad q : quads) {
            fillQuad(img, q, tex, eye, fwd, right, up, solidOnly);
        }
        Files.createDirectories(Path.of("build"));
        ImageIO.write(img, "png", Path.of(out).toFile());
    }

    private static void addBox(List<Quad> out, double cx, double cy, double cz,
                               double sx, double sy, double sz, int argb) {
        double x0 = cx - sx / 2, x1 = cx + sx / 2;
        double y0 = cy, y1 = cy + sy;
        double z0 = cz - sz / 2, z1 = cz + sz / 2;
        double[][][] faces = {
                {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}},
                {{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}},
                {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}},
                {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}},
                {{x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}, {x0, y0, z0}},
                {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
        };
        for (double[][] f : faces) {
            out.add(new Quad(f, null, argb));
        }
    }

    /** 逐像素：重心插值取 uv → 贴图最近邻采样 ✓（纯色面直接填 ✓）。 */
    private static void fillQuad(BufferedImage img, Quad quad, BufferedImage tex,
                                 double[] eye, double[] fwd, double[] right, double[] up, boolean solidOnly) {
        double[][] s = new double[4][];
        for (int i = 0; i < 4; i++) {
            s[i] = project(quad.pos()[i], eye, fwd, right, up);
        }
        double minX = Math.min(Math.min(s[0][0], s[1][0]), Math.min(s[2][0], s[3][0]));
        double maxX = Math.max(Math.max(s[0][0], s[1][0]), Math.max(s[2][0], s[3][0]));
        double minY = Math.min(Math.min(s[0][1], s[1][1]), Math.min(s[2][1], s[3][1]));
        double maxY = Math.max(Math.max(s[0][1], s[1][1]), Math.max(s[2][1], s[3][1]));
        int px0 = (int) Math.max(0, Math.floor(minX));
        int px1 = (int) Math.min(W - 1, Math.ceil(maxX));
        int py0 = (int) Math.max(0, Math.floor(minY));
        int py1 = (int) Math.min(H - 1, Math.ceil(maxY));
        boolean solid = quad.solid() != 0;
        for (int y = py0; y <= py1; y++) {
            for (int x = px0; x <= px1; x++) {
                double[] bc = barycentric(s, x + 0.5D, y + 0.5D);
                if (bc == null) {
                    continue;
                }
                if (solid) {
                    img.setRGB(x, y, blend(img.getRGB(x, y), quad.solid() & 0xFFFFFF, 235));
                    continue;
                }
                if (solidOnly) {
                    // ★ 不贴图：同一个颜色铺满 ⇒ 只看几何形状 ✓（贴图缺不缺与它无关 ✓）
                    img.setRGB(x, y, blend(img.getRGB(x, y), 0xFFF2E2A8, 235));
                    continue;
                }
                double u = bc[0] * quad.uv()[0][0] + bc[1] * quad.uv()[1][0]
                        + bc[2] * quad.uv()[2][0] + bc[3] * quad.uv()[3][0];
                double v = bc[0] * quad.uv()[0][1] + bc[1] * quad.uv()[1][1]
                        + bc[2] * quad.uv()[2][1] + bc[3] * quad.uv()[3][1];
                int tx = (int) Math.floor(u * tex.getWidth());
                int ty = (int) Math.floor(v * tex.getHeight());
                if (tx < 0 || ty < 0 || tx >= tex.getWidth() || ty >= tex.getHeight()) {
                    continue;
                }
                int argb = tex.getRGB(tx, ty);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha <= 8) {
                    continue;                 // 贴图这块没画 ⇒ 游戏里也看不见 ✓
                }
                img.setRGB(x, y, blend(img.getRGB(x, y), argb & 0xFFFFFF, (int) (alpha * 0.78D)));
            }
        }
    }

    private static double[] project(double[] p, double[] eye, double[] fwd, double[] right, double[] up) {
        double[] rel = {p[0] - eye[0], p[1] - eye[1], p[2] - eye[2]};
        double depth = Math.max(0.05D, dot(rel, fwd));
        double f = (H * 0.9D) / (2.0D * Math.tan(Math.toRadians(30.0D)) * depth);
        return new double[]{W / 2.0D + dot(rel, right) * f, H / 2.0D - dot(rel, up) * f, depth};
    }

    private static double[] barycentric(double[][] s, double px, double py) {
        double[] t1 = tri(s[0], s[1], s[2], px, py);
        if (t1 != null) {
            return new double[]{t1[0], t1[1], t1[2], 0.0D};
        }
        double[] t2 = tri(s[0], s[2], s[3], px, py);
        if (t2 != null) {
            return new double[]{t2[0], 0.0D, t2[1], t2[2]};
        }
        return null;
    }

    private static double[] tri(double[] a, double[] b, double[] c, double px, double py) {
        double den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1]);
        if (Math.abs(den) < 1.0E-9D) {
            return null;
        }
        double l1 = ((b[1] - c[1]) * (px - c[0]) + (c[0] - b[0]) * (py - c[1])) / den;
        double l2 = ((c[1] - a[1]) * (px - c[0]) + (a[0] - c[0]) * (py - c[1])) / den;
        double l3 = 1.0D - l1 - l2;
        if (l1 < -1.0E-6D || l2 < -1.0E-6D || l3 < -1.0E-6D) {
            return null;
        }
        return new double[]{l1, l2, l3};
    }

    private static double depthOf(Quad q, double[] eye) {
        double cx = 0.0D, cy = 0.0D, cz = 0.0D;
        for (double[] p : q.pos()) {
            cx += p[0] / 4.0D;
            cy += p[1] / 4.0D;
            cz += p[2] / 4.0D;
        }
        return Math.sqrt(sq(cx - eye[0]) + sq(cy - eye[1]) + sq(cz - eye[2]));
    }

    private static int blend(int dst, int rgb, int a) {
        int dr = (dst >> 16) & 0xFF, dg = (dst >> 8) & 0xFF, db = dst & 0xFF;
        int sr = (rgb >> 16) & 0xFF, sg = (rgb >> 8) & 0xFF, sb = rgb & 0xFF;
        return 0xFF000000 | (((sr * a + dr * (255 - a)) / 255) << 16)
                | (((sg * a + dg * (255 - a)) / 255) << 8)
                | ((sb * a + db * (255 - a)) / 255);
    }

    private static double[] norm(double[] v) {
        double l = Math.sqrt(sq(v[0]) + sq(v[1]) + sq(v[2]));
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double sq(double x) {
        return x * x;
    }

    private static void loadModel() throws Exception {
        try (var stream = LightWingsPreviewTest.class.getResourceAsStream(
                "/assets/tnc/geo/entity/light_wings.geo.json")) {
            assertNotNull(stream, "找不到 light_wings.geo.json");
            TNLightWingsModel.parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    private static BufferedImage loadTexture() throws Exception {
        try (var stream = LightWingsPreviewTest.class.getResourceAsStream(
                "/assets/tnc/textures/entity/light_wings_bedrock.png")) {
            assertNotNull(stream, "找不到贴图");
            BufferedImage img = ImageIO.read(stream);
            assertNotNull(img);
            return img;
        }
    }
}
