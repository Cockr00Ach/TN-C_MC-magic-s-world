package com.tnc.tnc.client;

import com.tnc.tnc.magic.Element;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 魔法石界面左下角的「元素亲和度」控件。
 *
 * <h2>素材与坐标都来自用户</h2>
 * 底图是用户画的 {@code assets/tnc/textures/gui/affinity_crystal.png}（33x37），
 * 里面每个元素的 6 个亲和力点由<b>用户逐个标注了像素坐标</b>（见 {@link #PIPS}），
 * 所以这里只是"按数值点亮/压暗"，不改动任何美术。
 *
 * <h2>怎么表现"亲和力没满"</h2>
 * 用户的要求是"像生命那样，少一点就减一点，用元素那个周围边框的颜色填涂"。
 * 做法：把该点亮但没亮的点，画成<b>同一个像素的压暗版</b>（{@link #DIM} 系数）。
 * 好处是<b>不用为每个元素硬编码"空色"</b> —— 空色直接由用户画的那个点的颜色推出来 ✓，
 * 他以后重画贴图也不用改代码。
 *
 * <h2>光/暗为什么也是 6 个点</h2>
 * 用户说明"光暗六个珠子就是一样的颜色" —— 在贴图里就已经是同色了 ✓，
 * 所以代码不需要为它们特判，一视同仁即可。
 */
public final class AffinityWidget {

    /** 底图（用户画的）。 */
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("tnc", "textures/gui/affinity_crystal.png");

    /** 贴图尺寸（用户给的图就是 33x37）。 */
    public static final int TEX_W = 33;
    public static final int TEX_H = 37;

    /** 没点亮的点压暗到多少（1.0 = 原样）。 */
    private static final float DIM = 0.35F;

    private AffinityWidget() {
    }

    /**
     * 每个元素的 6 个亲和力点坐标，<b>顺序 = 第 1 点 → 第 6 点</b>（用户标注的原始顺序）。
     *
     * <p>⚠️ 改这个表就能改位置/顺序，别的都不用动。坐标是像素坐标，原点在贴图左上角。
     */
    private static final int[][] PIPS = new int[7][];

    static {
        // 顺序必须与 Element.values() 一致：WATER, FIRE, LIGHTNING, WIND, EARTH, LIGHT, DARK
        PIPS[Element.WATER.ordinal()] = new int[] { 6,16,  5,17,  4,17,  5,18,  4,18,  3,19 };
        PIPS[Element.FIRE.ordinal()] = new int[] { 10,5,  9,6,  8,6,  8,7,  9,7,  7,8 };
        PIPS[Element.LIGHTNING.ordinal()] = new int[] { 0,9,  1,10,  2,10,  2,11,  1,11,  3,12 };
        PIPS[Element.WIND.ordinal()] = new int[] { 20,17,  21,18,  22,18,  21,19,  22,19,  23,20 };
        PIPS[Element.EARTH.ordinal()] = new int[] { 15,18,  14,19,  13,19,  14,20,  13,20,  12,21 };
        PIPS[Element.LIGHT.ordinal()] = new int[] { 31,18,  31,19,  30,19,  30,20,  29,20,  29,21 };
        PIPS[Element.DARK.ordinal()] = new int[] { 28,11,  28,12,  29,12,  29,13,  30,13,  30,14 };
    }

    /**
     * 画亲和度控件。
     *
     * @param x       贴图左上角 x
     * @param y       贴图左上角 y
     * @param affinityOf 取某个元素当前亲和力的函数（0~6）
     */
    public static void render(GuiGraphics graphics, int x, int y, java.util.function.ToIntFunction<Element> affinityOf) {
        render(graphics, x, y, 1, affinityOf);
    }

    /**
     * 按缩放倍率画（用户要求"等比例放大，占满左下框 80%"）。
     *
     * <p>贴图整体放大 {@code scale} 倍，42 个点位的坐标也**同步乘以 scale** ——
     * 所以点位表不用改，放大缩小都自动对齐 ✓
     */
    public static void render(GuiGraphics graphics, int x, int y, int scale,
                              java.util.function.ToIntFunction<Element> affinityOf) {
        int s = Math.max(1, scale);
        // 先把整张底图画上去（水晶 + 用户画好的 6 点彩色阶）
        graphics.blit(TEXTURE, x, y, TEX_W * s, TEX_H * s, 0.0F, 0.0F, TEX_W, TEX_H, TEX_W, TEX_H);

        // 再把"没点亮的点"压暗。逐点画 1x1，坐标严格照用户标注的表
        for (Element element : Element.values()) {
            int[] pips = PIPS[element.ordinal()];
            if (pips == null) {
                continue;
            }
            int affinity = Math.max(0, Math.min(6, affinityOf.applyAsInt(element)));
            for (int i = 0; i < 6; i++) {
                if (i < affinity) {
                    continue;                       // 已点亮：保持用户画的原色
                }
                int px = pips[i * 2];
                int py = pips[i * 2 + 1];
                graphics.fill(x + px * s, y + py * s, x + px * s + s, y + py * s + s, dimColor(element, i));
            }
        }
    }

    /**
     * 第 i 个点"未点亮"时用的颜色。
     *
     * <p>先从用户贴图里读那个点的原始颜色，再按 {@link #DIM} 压暗 ——
     * 这样空色永远和用户画的点一致，他重画贴图也不用改这里 ✓
     * （贴图读不到时退化成各元素一个深色，保证不崩）。
     */
    private static int dimColor(Element element, int index) {
        int[] pips = PIPS[element.ordinal()];
        int argb = sample(pips[index * 2], pips[index * 2 + 1], element);
        int a = (argb >>> 24) & 0xFF;
        int r = (int) (((argb >> 16) & 0xFF) * DIM);
        int g = (int) (((argb >> 8) & 0xFF) * DIM);
        int b = (int) ((argb & 0xFF) * DIM);
        return (Math.max(a, 0xFF) << 24) | (r << 16) | (g << 8) | b;
    }

    /** 从贴图里取一个像素；取不到就用兜底色。 */
    private static int sample(int px, int py, Element element) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            var res = mc.getResourceManager().getResource(TEXTURE).orElse(null);
            if (res == null) {
                return fallback(element);
            }
            try (var in = res.open()) {
                var image = com.mojang.blaze3d.platform.NativeImage.read(in);
                if (px < 0 || py < 0 || px >= image.getWidth() || py >= image.getHeight()) {
                    image.close();
                    return fallback(element);
                }
                int argb = image.getPixelRGBA(px, py);
                image.close();
                return argb;
            }
        } catch (Throwable t) {
            return fallback(element);
        }
    }

    /** 读不到贴图时的兜底色（各元素一个深色）。 */
    private static int fallback(Element element) {
        return switch (element) {
            case WATER -> 0xFF0F2E6E;
            case FIRE -> 0xFF4A0A0C;
            case LIGHTNING -> 0xFF4A3A00;
            case WIND -> 0xFF0F3A20;
            case EARTH -> 0xFF3A2410;
            case LIGHT -> 0xFF6A6250;
            case DARK -> 0xFF2A1A40;
        };
    }
}
