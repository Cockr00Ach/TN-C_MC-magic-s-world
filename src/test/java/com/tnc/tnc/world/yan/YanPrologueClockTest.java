package com.tnc.tnc.world.yan;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 处决演出的「时钟」= **亮墨漩涡阵 + 毛笔指针**（作者 2026-11-15 第二版）。
 *
 * <p>作者两版原话：
 * <blockquote>
 * ①「把那个时钟换成我们之前水墨法阵，旋转，然后加上分针时针啥的」<br>
 * ②「时针分针一点水墨感都没有，然后不用这个白色圆盘」
 * </blockquote>
 *
 * <p>为什么值得钉：这张脸只出现 **42 tick ≈ 2 秒** ✗ ——
 * "墨贴上去看不见""指针又变回矩形了""阵没转""指针顺时针跑"这几件事，
 * 试玩一晃就过去了 ✗，而且全都不报编译错 ✗。能算的都在这里算清楚：
 * <ol>
 *   <li>亮墨阵在：**亮**（月白）、**不是一整块实心盘**（覆盖率有上限）；</li>
 *   <li>两支指针是**笔锋**：从根部到笔尖越来越窄 ✓（矩形不会变窄 ✗）；</li>
 *   <li>阵在转（外层顺时针、内层反向更快 ✓）；</li>
 *   <li>指针**倒着走** ✓、分针比时针快 ✓。</li>
 * </ol>
 */
class YanPrologueClockTest {

    private static BufferedImage load(String path) throws Exception {
        try (InputStream in = YanPrologueClockTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "贴图没打进资源：" + path);
            return ImageIO.read(in);
        }
    }

    /** 不透明像素的平均亮度 ✓（只看画出来的部分）。 */
    private static double litLuma(BufferedImage image) {
        long sum = 0, n = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if (((argb >>> 24) & 0xFF) < 32) {
                    continue;
                }
                sum += (long)(0.299 * ((argb >> 16) & 0xFF) + 0.587 * ((argb >> 8) & 0xFF)
                        + 0.114 * (argb & 0xFF));
                n++;
            }
        }
        return n == 0 ? 0 : sum / (double)n;
    }

    /** 不透明像素占整张图的比例 ✓。 */
    private static double coverage(BufferedImage image, int y0, int y1) {
        long n = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) > 32) {
                    n++;
                }
            }
        }
        return n / (double)((y1 - y0) * image.getWidth());
    }

    @Test
    void theSealIsLuminousInkInsteadOfADarkDisc() throws Exception {
        BufferedImage seal = load("/assets/tnc/textures/gui/ink_clock_seal.png");
        assertEquals(seal.getWidth(), seal.getHeight(), "水墨阵必须是方的（转到 45° 才不会变椭圆 ✗）");
        double luma = litLuma(seal);
        double cover = coverage(seal, 0, seal.getHeight());
        System.out.printf("TN-C prologue clock: luminous ink luma %.1f, coverage %.1f%%%n",
                luma, cover * 100);
        // 处决画面是暗的 ⇒ 墨必须是**亮墨**（月白）才看得见 ✓（黑墨贴暗屏 = 没画 ✗）
        assertTrue(luma > 200, "漩涡阵不够亮（" + luma + "）—— 这就又变回黑墨贴黑屏了 ✗");
        // 但它必须是"墨迹"，不是一整块圆盘 ✗（作者第二版："不用这个白色圆盘"）
        assertTrue(cover > 0.15, "墨迹太少（覆盖率 " + cover + "），看着会不像阵 ✗");
        assertTrue(cover < 0.85, "几乎铺满整张（覆盖率 " + cover + "）—— 那就是一块实心盘了 ✗");
    }

    @Test
    void theHandsAreBrushStrokesNotRectangles() throws Exception {
        record Hand(String path, int w, int h) {
        }
        for (Hand hand : new Hand[]{
                new Hand("/assets/tnc/textures/gui/ink_clock_hand_hour.png",
                        YanPrologueClockRules.HAND_HOUR_W, YanPrologueClockRules.HAND_HOUR_H),
                new Hand("/assets/tnc/textures/gui/ink_clock_hand_minute.png",
                        YanPrologueClockRules.HAND_MINUTE_W, YanPrologueClockRules.HAND_MINUTE_H)}) {
            BufferedImage image = load(hand.path());
            assertEquals(hand.w(), image.getWidth(), hand.path() + " 的宽度和规则里的常量对不上");
            assertEquals(hand.h(), image.getHeight(), hand.path() + " 的高度和规则里的常量对不上");
            int height = image.getHeight();
            // 笔杆在图的**下端**、笔尖在**上端** ✓（渲染时绕下端中点转）
            double base = coverage(image, height - Math.max(6, height / 12), height);
            double tip = coverage(image, 0, Math.max(6, height / 12));
            assertTrue(base > 0.12, hand.path() + " 的笔杆太细（" + base + "）");
            assertTrue(tip < base * 0.55,
                    hand.path() + " 的笔尖没收细（根 " + base + " / 尖 " + tip + "）—— 这就是矩形了 ✗");
            assertTrue(litLuma(image) > 190, hand.path() + " 不够亮 ✗（暗屏上会看不见）");
        }
    }

    @Test
    void theSealRotatesAndTheInnerLayerGoesTheOtherWay() {
        float previous = Float.NEGATIVE_INFINITY;
        for (float t = 0; t <= 42; t += 1.5F) {
            float turn = YanPrologueClockRules.outerTurn(t);
            assertTrue(turn > previous, "外层水墨阵必须一直在转（停了就是一张静止贴图 ✗）");
            previous = turn;
        }
        float swept = Math.abs(YanPrologueClockRules.outerTurn(42) - YanPrologueClockRules.outerTurn(0));
        assertTrue(swept > 12.0F && swept < 90.0F, "外层两秒转的角度应该在 12~90 度之间，实际 " + swept);
        for (float t = 1; t <= 42; t += 4) {
            assertTrue(YanPrologueClockRules.innerTurn(t) * YanPrologueClockRules.outerTurn(t) < 0,
                    "内层必须和外层**反向**转（同向就是两层糊在一起 ✗）");
            assertTrue(Math.abs(YanPrologueClockRules.innerTurn(t)) > Math.abs(YanPrologueClockRules.outerTurn(t)),
                    "内层应该转得更快（那才是'墨化开'✗）");
        }
        assertTrue(YanPrologueClockRules.INNER_SCALE > 0.4F && YanPrologueClockRules.INNER_SCALE < 0.9F,
                "内层缩放要在 0.4~0.9 之间（太大盖住外层、太小看不见 ✗）");
    }

    @Test
    void theHandsRunBackwards() {
        float lastHour = YanPrologueClockRules.hourAngle(0);
        float lastMinute = YanPrologueClockRules.minuteAngle(0);
        for (float t = 1; t <= 42; t++) {
            float hour = YanPrologueClockRules.hourAngle(t);
            float minute = YanPrologueClockRules.minuteAngle(t);
            assertTrue(hour < lastHour, "时针必须**倒着**走（第 " + t + " tick）—— 这就是「时间倒流」✗");
            assertTrue(minute < lastMinute, "分针必须**倒着**走（第 " + t + " tick）");
            assertTrue(Math.abs(minute - lastMinute) >= Math.abs(hour - lastHour), "分针不能比时针慢 ✗");
            lastHour = hour;
            lastMinute = minute;
        }
        float hourSwept = Math.abs(YanPrologueClockRules.hourAngle(42) - YanPrologueClockRules.hourAngle(0));
        float minuteSwept = Math.abs(YanPrologueClockRules.minuteAngle(42) - YanPrologueClockRules.minuteAngle(0));
        assertTrue(hourSwept > 60.0F, "时针两秒里几乎没动（" + hourSwept + "°），看不出'倒流'✗");
        assertTrue(minuteSwept > hourSwept * 2.0F, "分针应该转得比时针多得多 ✗");
    }

    /**
     * 出图：暗底 + 亮墨阵（两层反向）+ 两支毛笔指针（t = 0 / 1 / 2 秒三帧 ✓）。
     *
     * <p>这张脸只有两秒 ✗，没有图就只能靠"进游戏那一下"看 ✗。
     */
    @Test
    void rendersTheClockFace() throws Exception {
        BufferedImage seal = load("/assets/tnc/textures/gui/ink_clock_seal.png");
        BufferedImage hour = load("/assets/tnc/textures/gui/ink_clock_hand_hour.png");
        BufferedImage minute = load("/assets/tnc/textures/gui/ink_clock_hand_minute.png");
        int cell = 360;
        BufferedImage page = new BufferedImage(cell * 3, cell + 32, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = page.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(9, 11, 18));
        g.fillRect(0, 0, page.getWidth(), page.getHeight());
        g.setColor(new java.awt.Color(190, 220, 240));
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 16));

        float[] times = {0.0F, 20.0F, 41.0F};
        for (int i = 0; i < times.length; i++) {
            float t = times[i];
            int cx = cell * i + cell / 2;
            int cy = cell / 2 + 6;
            int size = (int)(cell * 0.84F);
            java.awt.Graphics2D g2 = (java.awt.Graphics2D)g.create();
            // ① 外层墨阵（亮墨 ✓ 慢慢转）
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER,
                    YanPrologueClockRules.OUTER_ALPHA));
            g2.translate(cx, cy);
            g2.rotate(Math.toRadians(YanPrologueClockRules.outerTurn(t)));
            int outer = (int)(size * YanPrologueClockRules.OUTER_SCALE);
            g2.drawImage(seal, -outer / 2, -outer / 2, outer, outer, null);
            // ② 内层
            g2.setTransform(java.awt.geom.AffineTransform.getTranslateInstance(cx, cy));
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER,
                    YanPrologueClockRules.INNER_ALPHA));
            g2.rotate(Math.toRadians(YanPrologueClockRules.innerTurn(t)));
            int inner = (int)(outer * YanPrologueClockRules.INNER_SCALE);
            g2.drawImage(seal, -inner / 2, -inner / 2, inner, inner, null);
            // ③ 毛笔指针（图的下端在轴心 ✓ 倒着转）
            g2.setTransform(java.awt.geom.AffineTransform.getTranslateInstance(cx, cy));
            g2.setComposite(java.awt.AlphaComposite.SrcOver);
            drawBrush(g2, hour, YanPrologueClockRules.hourAngle(t),
                    size * YanPrologueClockRules.HOUR_LENGTH / 190.0F);
            drawBrush(g2, minute, YanPrologueClockRules.minuteAngle(t),
                    size * YanPrologueClockRules.MINUTE_LENGTH / 190.0F);
            g2.setColor(new java.awt.Color(0x8a2f2b));
            g2.fillOval(-4, -4, 8, 8);
            g2.dispose();
            g.drawString(String.format(java.util.Locale.ROOT, "t = %.1fs", t / 20.0F), cell * i + 14, cell + 24);
        }
        g.dispose();
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build"));
        java.nio.file.Path out = java.nio.file.Path.of("build", "prologue_clock_face.png");
        ImageIO.write(page, "png", out.toFile());
        System.out.println("处决时钟表盘预览 → " + out);
    }

    private static void drawBrush(java.awt.Graphics2D g, BufferedImage hand, float angle, float length) {
        float aspect = hand.getWidth() / (float)hand.getHeight();
        int w = Math.max(3, Math.round(length * aspect));
        java.awt.Graphics2D g2 = (java.awt.Graphics2D)g.create();
        g2.rotate(Math.toRadians(angle));
        // ① 淡边（湿笔洇开的一圈＋暗底上的轮廓 ✓）
        java.awt.Graphics2D halo = (java.awt.Graphics2D)g2.create();
        halo.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.45F));
        int hw = (w * 5) / 4;
        halo.drawImage(hand, -hw / 2, Math.round(-length * 1.04F), hw,
                Math.round(length * 1.04F), null);
        halo.dispose();
        // ② 浓墨本体（贴图是月白的 ⇒ 这里乘一层深色 ✓，和游戏里那个乘色一致）
        java.awt.image.BufferedImage ink = new java.awt.image.BufferedImage(
                hand.getWidth(), hand.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D inkG = ink.createGraphics();
        inkG.drawImage(hand, 0, 0, null);
        inkG.setComposite(java.awt.AlphaComposite.SrcIn);
        inkG.setColor(new java.awt.Color(61, 51, 46));
        inkG.fillRect(0, 0, ink.getWidth(), ink.getHeight());
        inkG.dispose();
        g2.drawImage(ink, -w / 2, Math.round(-length), w, Math.round(length), null);
        g2.dispose();
    }
}
