package com.tnc.tnc.warrior.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * 刀光贴图的"资源自检"。
 *
 * <p>为什么这个测试必须在（而且能在）无头环境跑：贴图路径写错、被资源过滤漏掉、有人手滑把渐变
 * 换回纯白、或者把丝线调成一整块，都会让刀光变成"一张发光的布"，而服务端 GameTest 里看不出来。
 * 这里直接读 classpath 里的 PNG，校验尺寸、边羽化、以及<b>中间确实有丝</b>（亮度起伏和局部峰数），
 * 构建期就能拦住。
 */
class TrailPlumeTextureTest {

    private static final String PATH = "/assets/tnc/textures/particle/trail_plume.png";
    private static final String PATH_EDGE = "/assets/tnc/textures/particle/trail_plume_edge.png";
    private static final String PATH_CORE = "/assets/tnc/textures/particle/trail_plume_core.png";

    private static BufferedImage load() throws Exception {
        return load(PATH);
    }

    private static BufferedImage load(String path) throws Exception {
        try (InputStream in = TrailPlumeTextureTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "贴图没进 jar：" + path + "（跑 tools/gen_trail_texture.py 重新生成）");
            BufferedImage image = ImageIO.read(in);
            assertNotNull(image, "贴图读不出来，文件可能坏了");
            return image;
        }
    }

    @Test
    void allThreeWeightVariantsShipInTheJar() throws Exception {
        for (String path : new String[]{PATH, PATH_EDGE, PATH_CORE}) {
            BufferedImage image = load(path);
            assertEquals(128, image.getWidth(), path + " 宽度（横跨光带，丝线要靠它切得开）");
            assertEquals(128, image.getHeight(), path + " 高度（沿轨迹：丝线要斜着走、并且沿 v 明暗变化）");
        }
    }

    @Test
    void edgeVariantIsHeavyOnTheSidesAndCoreVariantInTheMiddle() throws Exception {
        // 用户点名的两条：镰刀重边缘、锤重中心。用整列平均判断，避免被单条丝带偏。
        double edgeSides = sideMean(load(PATH_EDGE));
        double edgeMiddle = middleColumnMean(load(PATH_EDGE));
        assertTrue(edgeSides > edgeMiddle,
                "镰刀那张两侧(" + edgeSides + ")要比中间(" + edgeMiddle + ")亮");

        double coreMiddle = middleColumnMean(load(PATH_CORE));
        double coreSides = sideMean(load(PATH_CORE));
        assertTrue(coreMiddle > coreSides * 1.15,
                "锤那张中间(" + coreMiddle + ")要明显比两侧(" + coreSides + ")亮");

        // 均衡那张：中间最亮，但两侧比镰刀那张暗（镰刀才是"两边重"）
        double balancedMiddle = middleColumnMean(load(PATH));
        double balancedSides = sideMean(load(PATH));
        assertTrue(balancedMiddle > balancedSides, "均衡那张也是中间亮");
        assertTrue(edgeSides > balancedSides,
                "镰刀那张的两侧(" + edgeSides + ")应该比均衡那张(" + balancedSides + ")更亮才对");
    }

    private static double columnMean(BufferedImage image, int x) {
        double sum = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            sum += alpha(image, x, y);
        }
        return sum / image.getHeight();
    }

    private static double middleColumnMean(BufferedImage image) {
        int mid = image.getWidth() / 2;
        return (columnMean(image, mid) + columnMean(image, mid - 1) + columnMean(image, mid + 1)) / 3;
    }

    /** 两侧（避开最外面 1/16，那里是所有档都要羽化到 0 的地方）。 */
    private static double sideMean(BufferedImage image) {
        int w = image.getWidth();
        int lo = w / 16;
        int hi = w - 1 - w / 16;
        return (columnMean(image, lo) + columnMean(image, hi)) / 2;
    }

    @Test
    void bothEdgesFadeOut() throws Exception {
        BufferedImage image = load();
        int y = image.getHeight() / 2;
        int left = alpha(image, 0, y);
        int right = alpha(image, image.getWidth() - 1, y);

        assertTrue(left <= 60, "左边要羽化（现在是 " + left + "/255），否则就是硬边");
        assertTrue(right <= 60, "右边要羽化（现在是 " + right + "/255），否则就是硬边");
        assertTrue(Math.abs(left - right) <= 24,
                "两侧羽化程度要接近（" + left + " vs " + right + "），不然换绕序就亮一边");
    }

    @Test
    void theMiddleIsThreadedNotASolidSlab() throws Exception {
        BufferedImage image = load();
        int y = image.getHeight() / 2;
        int width = image.getWidth();

        // 1) 中间得够亮：不然光带会断成两半
        int middle = alpha(image, width / 2, y);
        assertTrue(middle >= 120, "正中太暗（" + middle + "/255）");

        // 2) 中间得有"丝"：统计 1/4..3/4 区间的亮度起伏
        int[] middleHalf = new int[width / 2];
        for (int i = 0; i < middleHalf.length; i++) {
            middleHalf[i] = alpha(image, width / 4 + i, y);
        }
        double mean = 0;
        for (int v : middleHalf) {
            mean += v;
        }
        mean /= middleHalf.length;
        double variance = 0;
        for (int v : middleHalf) {
            variance += (v - mean) * (v - mean);
        }
        double spread = Math.sqrt(variance / middleHalf.length);
        assertTrue(spread >= 18, "中间的亮度起伏只有 " + spread + "，又变成一整块了");

        // 3) 丝的数量：局部峰（比两侧都亮且足够亮）
        int peaks = 0;
        for (int x = 1; x < width - 1; x++) {
            int value = alpha(image, x, y);
            if (value >= 170 && value >= alpha(image, x - 1, y) && value > alpha(image, x + 1, y)) {
                peaks++;
            }
        }
        assertTrue(peaks >= 6, "只数出 " + peaks + " 条丝，线感不足（要 ≥6）");
    }

    @Test
    void strandsRunDiagonallySoTheTrailIsNotATargetRing() throws Exception {
        // 丝线如果完全平行于光带，整条刀光看起来就是一圈圈等高线（靶环）。
        // 所以上下两行的剖面必须有明显错位，并且每一列沿 v 都得有明暗变化。
        BufferedImage image = load();
        int width = image.getWidth();
        int top = image.getHeight() / 4;
        int bottom = image.getHeight() * 3 / 4;

        int bestShift = 0;
        double bestScore = -Double.MAX_VALUE;
        for (int shift = -12; shift <= 12; shift++) {
            double score = 0;
            for (int x = 12; x < width - 12; x++) {
                score += alpha(image, x, top) * alpha(image, x + shift, bottom);
            }
            if (score > bestScore) {
                bestScore = score;
                bestShift = shift;
            }
        }
        assertTrue(Math.abs(bestShift) >= 1,
                "上下两行的丝完全对齐（位移 " + bestShift + "），丝是直的，会看起来像靶环");

        int changedColumns = 0;
        for (int x = 0; x < width; x++) {
            int low = 255;
            int high = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                int value = alpha(image, x, y);
                low = Math.min(low, value);
                high = Math.max(high, value);
            }
            if (high - low >= 12) {
                changedColumns++;
            }
        }
        assertTrue(changedColumns >= width / 3,
                "只有 " + changedColumns + " 列沿 v 有明暗变化，丝线会显得死板（要 ≥" + (width / 3) + "）");
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) & 0xFF;
    }
}
