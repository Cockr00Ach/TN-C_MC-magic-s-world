package com.tnc.tnc.light.client;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 把作者的 {@code light_wings.geo.json} 用**渲染器那份数学**离线投影成字符画 ✓。
 *
 * <h2>为什么要这么"土"的办法（2026-10-01）</h2>
 * 作者："出现了但是模型有点不对吧" ✗ —— 而"哪儿不对"是画面上的事 ✓，
 * 我这边的会话**看不到游戏画面** ✗：只能靠"我改一个数 → 你重启看一眼"来回猜 ✗（一轮十分钟 ✗）。
 * 所以把渲染数学抽成 {@link TNLightWingsModel#buildMatrices} ✓，单测里喂同一份 geo ✓、
 * 投影成 ASCII 打印出来 ✓ —— 我自己就能看出"像不像一对翅膀、左右对不对称、羽片朝哪边" ✓。
 *
 * <p>★ 这里**必须**用生产代码那份数学（不是复制一份 ✗）：复制一份的话，
 * 单测永远是绿的、而画面上照样是歪的 ✗✗（这类"两套实现"的坑本仓库踩过 ✓）。
 */
class LightWingsProjectionTest {

    /** 正交前视图 / 侧视图 / 俯视图，每格 2 个字符宽 ✓。 */
    @Test
    void projectWingsToAscii() throws Exception {
        loadModel();
        Matrix4f root = new Matrix4f();                 // 单位矩阵：只看模型自己的形状 ✓
        List<float[]> pts = TNLightWingsModel.corners(root, 0.0F);

        assertTrue(pts.size() > 200, "角点数太少（" + pts.size() + "），geo 可能没解析对");
        print("前视图 (X 左右 / Y 上下)", pts, 0, 1);
        print("侧视图 (Z 前后 / Y 上下)", pts, 2, 1);
        print("俯视图 (X 左右 / Z 前后)", pts, 0, 2);

        // ★ 左右对称：X 的分布应该关于 0 对称 ✓（作者的 wingright 就是 wingLeft 的镜像 ✓）
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (float[] p : pts) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
            minZ = Math.min(minZ, p[2]);
            maxZ = Math.max(maxZ, p[2]);
        }
        System.out.printf("包围盒(格): X %.3f..%.3f (宽 %.3f)  Y %.3f..%.3f (高 %.3f)  Z %.3f..%.3f (厚 %.3f)%n",
                minX, maxX, maxX - minX, minY, maxY, maxY - minY, minZ, maxZ, maxZ - minZ);
        assertEquals(0.0F, minX + maxX, 0.08F, "左右不对称：X 应该是 ±(对称)");
        System.out.printf("对称中心 X = %.4f（应为 0）%n", (minX + maxX) / 2.0F);
    }

    /** 扇动 20° 之后，两翼应该**同向**抬（不是一只上一只下 ✗）。 */
    @Test
    void flappingKeepsWingsMirrored() throws Exception {
        loadModel();
        Matrix4f root = new Matrix4f();
        List<float[]> rest = TNLightWingsModel.corners(root, 0.0F);
        List<float[]> up = TNLightWingsModel.corners(root, 20.0F);
        double leftRise = 0.0D, rightRise = 0.0D;
        int leftN = 0, rightN = 0;
        for (int i = 0; i < rest.size(); i++) {
            float dx = rest.get(i)[0];
            double dy = up.get(i)[1] - rest.get(i)[1];
            if (dx < -0.2F) {
                leftRise += dy;
                leftN++;
            } else if (dx > 0.2F) {
                rightRise += dy;
                rightN++;
            }
        }
        assertTrue(leftN > 0 && rightN > 0, "左右两翼没都找到（左 " + leftN + " / 右 " + rightN + "）");
        double l = leftRise / leftN, r = rightRise / rightN;
        System.out.printf("扇动 20° 时平均升降: 左翼 %.4f 格, 右翼 %.4f 格（应同号）%n", l, r);
        assertTrue(l * r > 0, "两翼扇动方向相反 ✗（一只上一只下）: 左 " + l + " 右 " + r);
    }

    private static void loadModel() throws Exception {
        try (var stream = LightWingsProjectionTest.class.getResourceAsStream(
                "/assets/tnc/geo/entity/light_wings.geo.json")) {
            assertNotNull(stream, "找不到 light_wings.geo.json");
            TNLightWingsModel.parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        assertTrue(TNLightWingsModel.isLoaded(), "geo 解析后 isLoaded() 仍为 false");
    }

    /** 把点云按 (横向轴, 纵向轴) 画成字符画 ✓（纵向从上往下 ✓，原点标 O ✓）。 */
    private static void print(String title, List<float[]> pts, int hAxis, int vAxis) {
        float minH = Float.MAX_VALUE, maxH = -Float.MAX_VALUE;
        float minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
        for (float[] p : pts) {
            minH = Math.min(minH, p[hAxis]);
            maxH = Math.max(maxH, p[hAxis]);
            minV = Math.min(minV, p[vAxis]);
            maxV = Math.max(maxV, p[vAxis]);
        }
        int cols = 78;
        int rows = 26;
        float padH = (maxH - minH) * 0.03F + 0.02F;
        float padV = (maxV - minV) * 0.03F + 0.02F;
        minH -= padH; maxH += padH; minV -= padV; maxV += padV;
        char[][] grid = new char[rows][cols];
        for (char[] row : grid) {
            java.util.Arrays.fill(row, ' ');
        }
        for (float[] p : pts) {
            int cx = (int) ((p[hAxis] - minH) / (maxH - minH) * (cols - 1));
            int cy = (int) ((maxV - p[vAxis]) / (maxV - minV) * (rows - 1));
            cx = Math.max(0, Math.min(cols - 1, cx));
            cy = Math.max(0, Math.min(rows - 1, cy));
            grid[cy][cx] = '#';
        }
        // 原点（0,0,0 在这个投影面上的位置）标一个 O ✓ —— 作者加的两个根部方块就在它旁边 ✓
        int ox = (int) ((0.0F - minH) / (maxH - minH) * (cols - 1));
        int oy = (int) ((maxV - 0.0F) / (maxV - minV) * (rows - 1));
        if (ox >= 0 && ox < cols && oy >= 0 && oy < rows && grid[oy][ox] == ' ') {
            grid[oy][ox] = 'O';
        }
        StringBuilder sb = new StringBuilder();
        sb.append('\n').append(title)
                .append(String.format(java.util.Locale.ROOT, "  横 %.2f..%.2f  纵 %.2f..%.2f%n",
                        minH, maxH, minV, maxV));
        for (char[] row : grid) {
            sb.append("  |").append(row).append("|\n");
        }
        System.out.print(sb);
        ART.append(sb);
    }

    /**
     * ★★ <b>贴图对不上几何</b>的自检：把每个方块六个面用到的 UV 矩形拿去**实际贴图**里查不透明像素占比 ✓。
     *
     * <h2>为什么查这个（2026-10-01）</h2>
     * 作者："模型有点不对" ✗，而几何本身（对称、扁平、位置）已经查过没问题 ✓。
     * 那就剩"**采样到了没画的地方**"这一条 ✗ —— 实测贴图只有 **y 0..31**（上半）画了东西 ✓，
     * 而模型里有好几个方块（`wingLeftTip*` 那片羽毛）的 v 一直用到 **42** ✗ ⇒ 那些羽毛会**整片透明** ✗，
     * 看起来就是"翅膀缺了一块/不对劲" ✓。这条检查把"哪几个方块采空"直接点出来 ✓。
     */
    @Test
    void textureCoveragePerCube() throws Exception {
        loadModel();
        java.awt.image.BufferedImage tex;
        try (var stream = LightWingsProjectionTest.class.getResourceAsStream(
                "/assets/tnc/textures/entity/light_wings_bedrock.png")) {
            assertNotNull(stream, "找不到 light_wings_bedrock.png");
            tex = javax.imageio.ImageIO.read(stream);
        }
        assertNotNull(tex, "贴图读不出来");
        int texW = tex.getWidth();
        int texH = tex.getHeight();

        StringBuilder sb = new StringBuilder("\n贴图采样检查（" + texW + "x" + texH + "）\n");
        StringBuilder rectDump = new StringBuilder();
        double sum = 0.0D;
        int count = 0;
        int emptyCubes = 0;
        for (TNLightWingsModel.CubeReport r : TNLightWingsModel.cubeUvReport()) {
            int opaque = 0;
            int total = 0;
            int outOfTexture = 0;
            for (float[] face : r.faces()) {                 // 每面 {u, v, w, h}
                int x0 = Math.round(face[0]);
                int y0 = Math.round(face[1]);
                int w = Math.round(face[2]);
                int h = Math.round(face[3]);
                int faceOpaque = 0;
                int faceTotal = 0;
                for (int x = x0; x < x0 + w; x++) {
                    for (int y = y0; y < y0 + h; y++) {
                        total++;
                        faceTotal++;
                        if (x < 0 || y < 0 || x >= texW || y >= texH) {
                            outOfTexture++;
                            continue;
                        }
                        if (((tex.getRGB(x, y) >>> 24) & 0xFF) > 8) {
                            opaque++;
                            faceOpaque++;
                        }
                    }
                }
                // 给"标图脚本"用的机读清单：RECT u v w h 是否画了 标签 ✓
                rectDump.append(String.format(java.util.Locale.ROOT, "RECT %d %d %d %d %d %s%n",
                        x0, y0, w, h, faceOpaque * 2 >= faceTotal ? 1 : 0,
                        "c" + r.index() + "-" + r.bone()));
            }
            double ratio = total == 0 ? 0.0D : (double) opaque / total;
            sum += ratio;
            count++;
            if (ratio < 0.15D) {
                emptyCubes++;
            }
            sb.append(String.format(java.util.Locale.ROOT,
                    "  %-18s 方块#%-3d uv(%3.0f,%3.0f) size(%3.0f,%3.0f,%3.0f)  不透明 %5.1f%%  越界像素 %d%n",
                    r.bone(), r.index(), r.u(), r.v(), r.sizeX(), r.sizeY(), r.sizeZ(),
                    ratio * 100.0D, outOfTexture));
        }
        sb.append(String.format(java.util.Locale.ROOT,
                "平均不透明率 %.1f%%  基本采空(<15%%)的方块 %d / %d%n", sum / count * 100.0D, emptyCubes, count));
        System.out.print(sb);
        ART.append(sb);
        java.nio.file.Files.writeString(java.nio.file.Path.of("build", "uv_rects.txt"),
                rectDump.toString(), StandardCharsets.UTF_8);

        assertTrue(sum / count > 0.35D,
                "平均不透明率太低（" + (sum / count * 100.0D) + "%）—— UV 布局和贴图对不上，翅膀会缺块 ✗");
    }

    /**
     * ★★ 作者 2026-10-01："我希望在站着的时候翅膀不动的，你只有起飞的时候就播放那个 waving" ✓
     * —— 这条测试就是把这句话钉住 ✓：
     * <ol>
     *   <li><b>站着（animated=false）</b>：姿势与时间无关 ⇒ 一动不动 ✓；</li>
     *   <li><b>飞起来（animated=true）</b>：到关键帧 t=0.75s 时确实动了 ✓，而且两翼**对称**地动 ✓；</li>
     *   <li>动画真的读进来了（作者的文件是 waving / 2 秒 / 6 根骨头 ✓），并且**会循环**（t=0 与 t=2 同姿势 ✓）。</li>
     * </ol>
     */
    @Test
    void standingIsStillAndFlyingPlaysWaving() throws Exception {
        loadModel();
        try (var stream = LightWingsProjectionTest.class.getResourceAsStream(
                "/assets/tnc/animations/entity/light_wings.animation.json")) {
            assertNotNull(stream, "找不到 light_wings.animation.json");
            TNLightWingsModel.parseAnimation(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        assertTrue(TNLightWingsModel.hasAnimation(), "waving 动画没读进来 ✗");
        System.out.println("动画: " + TNLightWingsModel.describeAnimation());
        ART.append("\n动画: ").append(TNLightWingsModel.describeAnimation()).append('\n');

        Matrix4f root = new Matrix4f();
        // ① 站着：换个时间点，姿势必须完全一样（翅膀静止 ✓）
        List<float[]> idleA = TNLightWingsModel.corners(root, 0.0F, 0.0F, false);
        List<float[]> idleB = TNLightWingsModel.corners(root, 0.0F, 1.3F, false);
        assertEquals(0.0D, maxDelta(idleA, idleB), 1.0E-5D, "站着的时候翅膀还在动 ✗");

        // ② 飞起来：t=0.75s（作者关键帧的峰值）必须真的动，而且左右对称
        List<float[]> fly0 = TNLightWingsModel.corners(root, 0.0F, 0.0F, true);
        List<float[]> flyPeak = TNLightWingsModel.corners(root, 0.0F, 0.75F, true);
        double moved = maxDelta(fly0, flyPeak);
        System.out.printf("waving t=0 -> 0.75s 最大位移: %.3f 格%n", moved);
        assertTrue(moved > 0.03D, "飞起来也没动（waving 没生效）✗ 位移只有 " + moved);

        // ★ 作者的 waving 主要是**绕 Y** 摆（关键帧是 [0, ±7.5/±10, 0] ✓）⇒ 位移主要在 X/Z 上 ✗，
        //   所以这里量的是**三维位移**，不是只看高度 ✓（第一版只量了 Y，误判成"没动"✗）
        double leftMax = 0.0D, rightMax = 0.0D;
        double leftY = 0.0D, rightY = 0.0D;
        for (int i = 0; i < fly0.size(); i++) {
            double dx = flyPeak.get(i)[0] - fly0.get(i)[0];
            double dy = flyPeak.get(i)[1] - fly0.get(i)[1];
            double dz = flyPeak.get(i)[2] - fly0.get(i)[2];
            double d3 = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double x0 = fly0.get(i)[0];
            if (x0 < -0.3F) {
                leftMax = Math.max(leftMax, d3);
                leftY = Math.max(leftY, Math.abs(dy));
            } else if (x0 > 0.3F) {
                rightMax = Math.max(rightMax, d3);
                rightY = Math.max(rightY, Math.abs(dy));
            }
        }
        System.out.printf("两翼三维最大位移: 左 %.3f / 右 %.3f 格（应接近）%n", leftMax, rightMax);
        System.out.printf("两翼竖直方向最大位移: 左 %.3f / 右 %.3f 格%n", leftY, rightY);
        assertTrue(leftMax > 0.02D && rightMax > 0.02D, "有一侧没动 ✗");
        assertEquals(leftMax, rightMax, Math.max(0.02D, leftMax * 0.35D), "两翼动得不一样多 ✗");

        // ③ 循环：t=0 与 t=动画总长 应该回到同一姿势
        List<float[]> loopEnd = TNLightWingsModel.corners(root, 0.0F, 2.0F, true);
        List<float[]> loopStart = TNLightWingsModel.corners(root, 0.0F, 0.0F, true);
        assertTrue(maxDelta(loopStart, loopEnd) < 1.0E-4D, "waving 没有循环（2 秒后没回到原点）✗");
    }

    private static double maxDelta(List<float[]> a, List<float[]> b) {
        assertEquals(a.size(), b.size(), "点数不一致");
        double max = 0.0D;
        for (int i = 0; i < a.size(); i++) {
            max = Math.max(max, Math.abs(a.get(i)[0] - b.get(i)[0]));
            max = Math.max(max, Math.abs(a.get(i)[1] - b.get(i)[1]));
            max = Math.max(max, Math.abs(a.get(i)[2] - b.get(i)[2]));
        }
        return max;
    }

    /** 三张图一起写一份文件 ✓ —— 从构建日志里捞字符画太费劲 ✗，落盘之后直接读 ✓。 */    private static final StringBuilder ART = new StringBuilder();

    @org.junit.jupiter.api.AfterAll
    static void writeArt() throws Exception {
        java.nio.file.Path out = java.nio.file.Path.of("build", "light_wings_projection.txt");
        java.nio.file.Files.createDirectories(out.getParent());
        java.nio.file.Files.writeString(out, ART.toString(), StandardCharsets.UTF_8);
        System.out.println("[投影图已写入] " + out.toAbsolutePath());
    }
}
