package com.tnc.tnc.client.lightning;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;

import org.joml.Matrix4f;

/**
 * <b>雷系几何</b>：整根"从天劈到地"的闪电、分叉、地面雷网、身上的雷笼，
 * 全部用<b>朝向相机的加色光带</b>现画（不依赖任何模型／贴图资源）。
 *
 * <h2>怎么做到"中心白、渐变到边、然后发光"</h2>
 * 一根闪电不是一条线，而是<b>四层</b>叠起来的（见 {@link #strands}）：
 * <ol>
 *   <li><b>外辉光</b>：最宽（约 3 倍宽），暗主题色 + 低 alpha —— 负责把周围的空气"照亮"；</li>
 *   <li><b>主色层</b>：中等宽度，主题色 —— 看得出这是黄雷 / 蓝雷 / 紫雷；</li>
 *   <li><b>白芯</b>：窄，纯白 + 高 alpha —— 真正的"白热"；</li>
 *   <li><b>高光丝</b>：最细的一条纯白 —— 让芯有"电流在跑"的层次。</li>
 * </ol>
 * 每一层的横截面都是<b>中心亮、边缘 alpha 归零</b>（两半条带用不同顶点色）⇒
 * 叠在一起就是白芯 → 浅色 → 主题色 → 透明的连续渐变，而不是一根纯色塑料棒 ✓。
 *
 * <h2>为什么每段都要重算"侧向"</h2>
 * 光带是很多四边形拼起来的。每个截面按"这一段的方向 × 指向相机的方向"求一个侧向量
 * （{@link #side}）⇒ 整条带子永远正对镜头，从任何角度看都有厚度 ✓；
 * 相邻两段<b>共用同一个截面的侧向量</b>，所以拐弯处不会裂开 ✓。
 *
 * <h2>开销</h2>
 * 全部是即时生成的顶点，不做缓存：一根 16 段的闪电约 {@code 16 × 4 层 × 2 半 = 128} 个四边形，
 * 加两根分叉约 250 个；一次雷暴同时六七道也只是几千个四边形 —— 和地形比可以忽略 ✓。
 */
public final class LightningBoltGeometry {

    /** 主闪电路径的细分层数（4 层 ⇒ 16 段；再多就只是更抖、看不出细节 ✗）。 */
    private static final int PATH_LEVELS = 4;
    /** 主闪电的抖动幅度（占段长的比例 ✓ —— 顶部段长 48 格 ⇒ 歪 2~4 格，够"劈"）。 */
    private static final double PATH_JITTER = 0.055D;
    /** 分叉的细分层数 / 抖动。 */
    private static final int BRANCH_LEVELS = 3;
    private static final double BRANCH_JITTER = 0.10D;

    private LightningBoltGeometry() {
    }

    // ------------------------------------------------------------------
    //  一根闪电（含分叉）
    // ------------------------------------------------------------------

    /**
     * 画一根从 {@code (ax,ay,az)} 到 {@code (bx,by,bz)} 的完整闪电。
     *
     * @param width  白芯的半宽（格）—— 4.5 倍的小雷约 0.35，14 倍的大雷约 1.0
     * @param alpha  整体亮度（0..1）
     * @param theme  {@link LightningTheme} 里的主题
     * @param branches 分叉数量（0 = 光杆）
     * @param camX/camY/camZ 相机在<b>几何坐标空间</b>里的位置（渲染器已经减掉了实体原点）
     */
    public static void bolt(VertexConsumer vc, Matrix4f m,
                            double ax, double ay, double az,
                            double bx, double by, double bz,
                            long seed, float width, float alpha, int theme, int branches,
                            double camX, double camY, double camZ) {
        bolt0(vc, m, ax, ay, az, bx, by, bz, seed, width, alpha,
                LightningTheme.glow(theme), LightningTheme.mid(theme), LightningTheme.edge(theme),
                branches, camX, camY, camZ);
    }

    /**
     * 自定义配色的 {@link #bolt} —— 火/水/风/土/光这些没有 {@link LightningTheme} 主题的元素用。
     *
     * <p>几何、分叉、白芯、辉光层次和雷完全一样，只有颜色不同；这样七系形态看起来是
     * “同一种语言的不同元素”，而不是七套各画各的 ✗。
     */
    public static void boltTinted(VertexConsumer vc, Matrix4f m,
                                  double ax, double ay, double az,
                                  double bx, double by, double bz,
                                  long seed, float width, float alpha,
                                  float[] mid, float[] glow, int branches,
                                  double camX, double camY, double camZ) {
        boltTinted(vc, m, ax, ay, az, bx, by, bz, seed, width, alpha, mid, glow, branches, 1.0D,
                camX, camY, camZ);
    }

    /**
     * 同上，但可以<b>调抖动幅度</b> ✓（{@code jitterScale = 1} 就是雷系原样 ✗）。
     *
     * <p>★ 2026-11-09 水球链 t4/t5：「一根直直的闪电像不像水波嘞」——
     * 雷系那条 4 级中点位移是按<b>段长</b>抖的 ✗（48 格的光束会歪出好几格 ✗），
     * 拿去做"水柱"就成了一根乱窜的蛇 ✗。水要的是"大致笔直、略有起伏" ⇒ 传 0.3 左右 ✓。
     *
     * @param jitterScale 抖动倍率（1 = 雷系原样；0 = 完全笔直）
     */
    public static void boltTinted(VertexConsumer vc, Matrix4f m,
                                  double ax, double ay, double az,
                                  double bx, double by, double bz,
                                  long seed, float width, float alpha,
                                  float[] mid, float[] glow, int branches, double jitterScale,
                                  double camX, double camY, double camZ) {
        bolt0(vc, m, ax, ay, az, bx, by, bz, seed, width, alpha,
                glow, mid, mul(mid, 0.55F), branches, jitterScale, camX, camY, camZ);
    }

    private static void bolt0(VertexConsumer vc, Matrix4f m,
                              double ax, double ay, double az,
                              double bx, double by, double bz,
                              long seed, float width, float alpha,
                              float[] glow, float[] mid, float[] edge,
                              int branches,
                              double camX, double camY, double camZ) {
        bolt0(vc, m, ax, ay, az, bx, by, bz, seed, width, alpha, glow, mid, edge, branches, 1.0D,
                camX, camY, camZ);
    }

    private static void bolt0(VertexConsumer vc, Matrix4f m,
                              double ax, double ay, double az,
                              double bx, double by, double bz,
                              long seed, float width, float alpha,
                              float[] glow, float[] mid, float[] edge,
                              int branches, double jitterScale,
                              double camX, double camY, double camZ) {
        if (width <= 0.0F || alpha <= 0.004F) {
            return;
        }
        double pathJitter = PATH_JITTER * jitterScale;
        double branchJitter = BRANCH_JITTER * jitterScale;
        RandomSource rnd = RandomSource.create(seed);
        double[][] path = path(ax, ay, az, bx, by, bz, PATH_LEVELS, pathJitter, rnd);
        strands(vc, m, path, width, alpha, glow, mid, edge, camX, camY, camZ);

        if (branches <= 0) {
            return;
        }
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-3D) {
            return;
        }
        double ux = dx / len;
        double uy = dy / len;
        double uz = dz / len;
        for (int i = 0; i < branches; i++) {
            int at = 2 + rnd.nextInt(Math.max(1, path.length - 5));
            double[] p = path[Math.min(at, path.length - 2)];
            double angle = rnd.nextDouble() * Math.PI * 2.0D;
            // 分叉往外斜、同时顺着主闪电的方向往下走 ✓（纯水平叉会像"树枝"，不像电 ✗）
            double reach = len * (0.14D + rnd.nextDouble() * 0.18D);
            double ex = p[0] + Math.cos(angle) * reach + ux * reach * 0.55D;
            double ey = p[1] + uy * reach * 0.75D - reach * 0.10D;
            double ez = p[2] + Math.sin(angle) * reach + uz * reach * 0.55D;
            double[][] sub = path(p[0], p[1], p[2], ex, ey, ez, BRANCH_LEVELS, branchJitter, rnd);
            strands(vc, m, sub, width * 0.52F, alpha * 0.72F, glow, mid, edge, camX, camY, camZ);
            // 二级分叉：只给最粗的大雷（width 够大时）—— 小雷画了也看不见，纯浪费 ✗
            if (width > 0.34F && rnd.nextBoolean()) {
                int at2 = 1 + rnd.nextInt(Math.max(1, sub.length - 2));
                double[] q = sub[Math.min(at2, sub.length - 2)];
                double a2 = rnd.nextDouble() * Math.PI * 2.0D;
                double r2 = len * (0.07D + rnd.nextDouble() * 0.09D);
                double[][] sub2 = path(q[0], q[1], q[2],
                        q[0] + Math.cos(a2) * r2, q[1] - r2 * 0.55D, q[2] + Math.sin(a2) * r2,
                        2, 0.11D * jitterScale, rnd);
                strands(vc, m, sub2, width * 0.28F, alpha * 0.55F, glow, mid, edge, camX, camY, camZ);
            }
        }
    }

    /** 颜色乘一个系数（做"更暗的边"用）。 */
    private static float[] mul(float[] color, float factor) {
        return new float[]{color[0] * factor, color[1] * factor, color[2] * factor};
    }

    /** 四层叠出来的"白芯 + 彩色光晕"。 */
    private static void strands(VertexConsumer vc, Matrix4f m, double[][] pts, float width,
                                float alpha, float[] glow, float[] mid, float[] edge,
                                double camX, double camY, double camZ) {
        float[] white = LightningTheme.WHITE;
        // 1) 外辉光：宽、暗、几乎不挡住背后
        ribbon(vc, m, pts, width * 3.4F, glow, 0.15F * alpha, glow, 0.0F, camX, camY, camZ);
        // 2) 主色：看得出颜色
        ribbon(vc, m, pts, width * 1.55F, mid, 0.40F * alpha, edge, 0.0F, camX, camY, camZ);
        // 3) 白芯：中心纯白，往边过渡到主色（这一层就是"中心白、渐变到边"）
        ribbon(vc, m, pts, width * 0.55F, white, 0.92F * alpha, mid, 0.55F * alpha, camX, camY, camZ);
        // 4) 高光丝：让芯里还有一条更亮的东西
        ribbon(vc, m, pts, width * 0.17F, white, 0.88F * alpha, white, 0.0F, camX, camY, camZ);
    }

    // ------------------------------------------------------------------
    //  任意折线（风系龙卷风：螺旋缠绕的一束线）
    // ------------------------------------------------------------------

    /**
     * 把一条<b>任意折线</b>画成三层加色光带（外辉光 / 主色 / 白芯）✓。
     *
     * <p>为什么不直接用 {@link #arcTinted}：那个只吃"两点 + 随机抖动" ✗，
     * 而龙卷风要的是<b>自己指定的折点</b>（作者 2026-11-09：「线螺旋缠绕起来变成龙卷风……
     * 可以有明确的锐角，极具那种线条特色」）—— 折点在哪里、折多锐，必须由
     * {@link com.tnc.tnc.magic.TornadoLineShape} 说了算 ✓。
     *
     * <p>三层宽度比沿用雷电那一套（3.4 / 1.5 / 0.5），所以风线和闪电是同一种"亮线"读法 ✓，
     * 只是颜色换成风系的青白 ✓。
     *
     * @param pts   折线（至少两点，单位 = 当前 pose 空间）
     * @param width 白芯半宽
     * @param alpha 总不透明度（0..1）
     * @param core  白芯色（通常纯白）
     * @param mid   主色（外缘过渡色）
     * @param glow  外辉光色
     */
    public static void polyline(VertexConsumer vc, Matrix4f m, double[][] pts,
                                float width, float alpha,
                                float[] core, float[] mid, float[] glow,
                                double camX, double camY, double camZ) {
        if (pts == null || pts.length < 2 || width <= 0.0F || alpha <= 0.004F) {
            return;
        }
        // 1) 外辉光：宽、很淡，边缘直接透明（光带而不是实心管）✓
        ribbon(vc, m, pts, width * 3.4F, glow, 0.15F * alpha, glow, 0.0F, camX, camY, camZ);
        // 2) 主色：看得出是青绿的风 ✓
        ribbon(vc, m, pts, width * 1.5F, mid, 0.42F * alpha, mid, 0.08F * alpha,
                camX, camY, camZ);
        // 3) 白芯：中心亮到发白，边缘退回主色 ⇒ 远看是一条细亮线 ✓
        ribbon(vc, m, pts, width * 0.5F, core, 0.95F * alpha, mid, 0.55F * alpha,
                camX, camY, camZ);
    }

    /**
     * 同上，但<b>每一段可以不一样粗</b>（根细 → 慢慢变粗 → 后面一样粗 ✓）。
     *
     * <p>★ 2026-11-10 作者：「t4t5 发射水波时，水波的根部应该从细到粗然后保持一样，
     * 也就是法阵那个根部要细细的，不然就很突兀」✗ ——
     * 原来整根光带是**等宽**的 ✗：法阵口才 0.9 格，水波一冒出来就有 1.4 格宽 ⇒ 像从针眼里挤出一根柱子 ✗。
     *
     * <p>为什么不做成"整根锥形"✗：作者要的是"**从细到粗然后保持一样**"✓ ——
     * 只在根部 {@code ramp} 那一小段收细，往后的宽度恒定 ✓（全程锥形会像漏斗 ✗）。
     *
     * @param half 每个<b>点</b>的宽度倍率（{@code pts.length} 个，白芯半宽 = {@code width*half[i]}）
     */
    public static void polylineTapered(VertexConsumer vc, Matrix4f m, double[][] pts, float[] half,
                                       float width, float alpha,
                                       float[] core, float[] mid, float[] glow,
                                       double camX, double camY, double camZ) {
        if (pts == null || pts.length < 2 || half == null || half.length != pts.length
                || width <= 0.0F || alpha <= 0.004F) {
            return;
        }
        ribbon(vc, m, pts, half, width * 3.4F, glow, 0.15F * alpha, glow, 0.0F, camX, camY, camZ);
        ribbon(vc, m, pts, half, width * 1.5F, mid, 0.42F * alpha, mid, 0.08F * alpha,
                camX, camY, camZ);
        ribbon(vc, m, pts, half, width * 0.5F, core, 0.95F * alpha, mid, 0.55F * alpha,
                camX, camY, camZ);
    }

    // ------------------------------------------------------------------
    //  短电弧（雷笼上乱窜的电 / 命中时溅出去的电）
    // ------------------------------------------------------------------

    /**
     * 两点之间的一条<b>短锯齿电弧</b>（比 {@link #bolt} 省：只细分 4 段、只画两层）。
     *
     * @param width 白芯半宽（格）—— 0.02~0.06 就够，太粗会像光管 ✗
     */
    public static void arc(VertexConsumer vc, Matrix4f m,
                           double ax, double ay, double az,
                           double bx, double by, double bz,
                           long seed, float width, float alpha, int theme,
                           double camX, double camY, double camZ) {
        if (width <= 0.0F || alpha <= 0.004F) {
            return;
        }
        RandomSource rnd = RandomSource.create(seed);
        double[][] pts = path(ax, ay, az, bx, by, bz, 2, 0.11D, rnd);
        webLine(vc, m, pts, width, alpha, theme, camX, camY, camZ);
    }

    // ------------------------------------------------------------------
    //  自定义配色的短电弧（火/水/风/土/光的拖尾都用它）
    // ------------------------------------------------------------------

    /**
     * 两点之间的一条<b>自定义配色短电弧</b>：三层（外辉光 / 主色 / 白芯）。
     *
     * <p>和 {@link #arc} 的区别只是颜色不再取自 {@link LightningTheme}——
     * 元素附身的拖尾要按元素上色，但几何必须和雷共用同一套"锯齿 + 辉光"的读法 ✓。
     *
     * @param core 中心色（通常白，土/暗可以给深色）
     * @param mid  主色
     * @param glow 外辉光色
     */
    public static void arcTinted(VertexConsumer vc, Matrix4f m,
                                 double ax, double ay, double az,
                                 double bx, double by, double bz,
                                 long seed, float width, float alpha,
                                 float[] core, float[] mid, float[] glow,
                                 double camX, double camY, double camZ) {
        if (width <= 0.0F || alpha <= 0.004F) {
            return;
        }
        RandomSource rnd = RandomSource.create(seed);
        double[][] pts = path(ax, ay, az, bx, by, bz, 2, 0.11D, rnd);
        ribbon(vc, m, pts, width * 3.0F, glow, 0.16F * alpha, glow, 0.0F, camX, camY, camZ);
        ribbon(vc, m, pts, width, core, 0.62F * alpha, mid, 0.35F * alpha,
                camX, camY, camZ);
    }

    // ------------------------------------------------------------------
    //  暗系：黑线 ＋ 血线（横向的"暗闪电"）
    // ------------------------------------------------------------------

    /** 黑芯：几乎纯黑，靠 alpha 压住背后的亮处（加色层里它是看不见的 ✗，要走 {@code DARK} 层）。 */
    private static final float[] DARK_CORE = {0.015F, 0.010F, 0.030F};
    /** 血线主色（暗红，不是亮红 —— 亮红在夜里像火法 ✗）。 */
    private static final float[] BLOOD = {0.62F, 0.030F, 0.070F};
    /** 血线的雾边：更暗的红。 */
    private static final float[] BLOOD_DIM = {0.28F, 0.000F, 0.035F};

    /**
     * ★ <b>一条"黑线 ＋ 血线"</b>（横着走的暗闪电）。
     *
     * <p>作者 2026-11-05：「闪电要是横着来是不是就是黑线和血线了」—— 是。
     * 竖着劈下来的叫"雷"，横着游过去的读起来就是"某种黑红色的东西在爬" ✓。
     * 这里把雷的<b>白芯换成黑芯</b>、把彩色辉光换成<b>血红</b>，四层叠出：
     * 血雾（宽/极暗）→ 血线（中/暗红）→ 黑芯（细/最黑）→ 一线血高光。
     *
     * <p>⚠️ 调用方必须用 {@code LightningRenderTypes.DARK}（普通 alpha 混合）建 consumer；
     * 用 {@code BOLT}（加色）的话黑芯会直接消失 ✗。
     */
    public static void darkBolt(VertexConsumer vc, Matrix4f m,
                                double ax, double ay, double az,
                                double bx, double by, double bz,
                                long seed, float width, float alpha,
                                double camX, double camY, double camZ) {
        darkBolt(vc, m, ax, ay, az, bx, by, bz, seed, width, alpha, 1.0D, camX, camY, camZ);
    }

    /**
     * ★ 同上，但<b>只画到 {@code growth}（0..1）这么长</b>——"一节一节过去"✓。
     *
     * <p>作者 2026-11-05：「我不希望像雷电一样瞬间就过去，你可以一节一节过去」。
     * 所以暗系的线不走"整条啪一下出现"，而是沿着路径**一截一截长**：
     * 每条线自己带一个"长到哪了"的比例，服务端/渲染器按 tick 推进即可。
     *
     * <p>线头会亮一颗小血珠（{@link #darkBead}），所以能看到"它在爬" ✓。
     */
    public static void darkBolt(VertexConsumer vc, Matrix4f m,
                                double ax, double ay, double az,
                                double bx, double by, double bz,
                                long seed, float width, float alpha, double growth,
                                double camX, double camY, double camZ) {
        if (width <= 0.0F || alpha <= 0.004F || growth <= 0.01D) {
            return;
        }
        RandomSource rnd = RandomSource.create(seed);
        double[][] full = path(ax, ay, az, bx, by, bz, PATH_LEVELS, PATH_JITTER, rnd);
        double[][] pts = clipPath(full, growth);
        if (pts.length < 2) {
            return;
        }
        darkStrands(vc, m, pts, width, alpha, camX, camY, camZ);
        if (growth < 0.985D) {
            // 生长中的线头：一颗小血珠 ⇒ 看得出"还在长" ✓
            double[] head = pts[pts.length - 1];
            darkBead(vc, m, head[0], head[1], head[2], width * 2.4F, alpha, camX, camY, camZ);
        }
    }

    /**
     * 两点之间的<b>短黑线</b>（黑阵的门环/勾爪、暗系命中溅出的"线"）。
     *
     * <p>比 {@link #darkBolt} 少两层、少两段细分：短距离不需要那么"活"，而且数量多 ✓。
     */
    public static void darkArc(VertexConsumer vc, Matrix4f m,
                               double ax, double ay, double az,
                               double bx, double by, double bz,
                               long seed, float width, float alpha,
                               double camX, double camY, double camZ) {
        darkArc(vc, m, ax, ay, az, bx, by, bz, seed, width, alpha, 1.0D, camX, camY, camZ);
    }

    /** 同上，但只画到 {@code growth}（0..1）—— 手指/爪尖要"一节一节"伸出来时用它 ✓。 */
    public static void darkArc(VertexConsumer vc, Matrix4f m,
                               double ax, double ay, double az,
                               double bx, double by, double bz,
                               long seed, float width, float alpha, double growth,
                               double camX, double camY, double camZ) {
        if (width <= 0.0F || alpha <= 0.004F || growth <= 0.01D) {
            return;
        }
        RandomSource rnd = RandomSource.create(seed);
        double[][] full = path(ax, ay, az, bx, by, bz, 2, 0.10D, rnd);
        double[][] pts = clipPath(full, growth);
        if (pts.length < 2) {
            return;
        }
        darkStrands(vc, m, pts, width, alpha, camX, camY, camZ);
    }

    /**
     * 一颗<b>面向相机的小血珠</b>（线性生长的线头 / 指尖 / 吸血的节点）✓。
     *
     * <p>三层同心：暗红雾 → 血色 → 黑芯。用同一层 {@code DARK} 画，
     * 所以它是"暗红色的点"，不是加色的亮点 ✓。
     */
    public static void darkBead(VertexConsumer vc, Matrix4f m,
                                double x, double y, double z, float radius, float alpha,
                                double camX, double camY, double camZ) {
        if (radius <= 0.0F || alpha <= 0.004F) {
            return;
        }
        double[] fwd = {camX - x, camY - y, camZ - z};
        double fl = Math.sqrt(fwd[0] * fwd[0] + fwd[1] * fwd[1] + fwd[2] * fwd[2]);
        if (fl < 1.0E-4D) {
            return;
        }
        fwd[0] /= fl;
        fwd[1] /= fl;
        fwd[2] /= fl;
        double[] right = {fwd[2], 0.0D, -fwd[0]};
        double rl = Math.sqrt(right[0] * right[0] + right[2] * right[2]);
        if (rl < 1.0E-4D) {
            right = new double[]{1.0D, 0.0D, 0.0D};
            rl = 1.0D;
        }
        right[0] /= rl;
        right[2] /= rl;
        right[1] = 0.0D;
        // up = fwd × right
        double[] up = {
                fwd[1] * right[2] - fwd[2] * right[1],
                fwd[2] * right[0] - fwd[0] * right[2],
                fwd[0] * right[1] - fwd[1] * right[0]};
        beadQuad(vc, m, x, y, z, right, up, radius * 2.3D, BLOOD_DIM, 0.28F * alpha);
        beadQuad(vc, m, x, y, z, right, up, radius * 1.25D, BLOOD, 0.55F * alpha);
        beadQuad(vc, m, x, y, z, right, up, radius * 0.55D, DARK_CORE, 0.95F * alpha);
    }

    private static void beadQuad(VertexConsumer vc, Matrix4f m, double x, double y, double z,
                                 double[] right, double[] up, double half,
                                 float[] color, float alpha) {
        double rx = right[0] * half;
        double rz = right[2] * half;
        double ux = up[0] * half;
        double uy = up[1] * half;
        double uz = up[2] * half;
        vertex(vc, m, (float) (x - rx - ux), (float) (y - uy), (float) (z - rz - uz), color, alpha);
        vertex(vc, m, (float) (x + rx - ux), (float) (y - uy), (float) (z + rz - uz), color, alpha);
        vertex(vc, m, (float) (x + rx + ux), (float) (y + uy), (float) (z + rz + uz), color, alpha);
        vertex(vc, m, (float) (x - rx + ux), (float) (y + uy), (float) (z - rz + uz), color, alpha);
    }

    /**
     * 把折线按<b>弧长比例</b>剪到 {@code growth}（0..1）。
     *
     * <p>剪完的最后一点是"插值出来的"⇒ 线头像真的在爬，而非跳到下一个折点 ✓。
     */
    private static double[][] clipPath(double[][] pts, double growth) {
        if (growth >= 0.999D) {
            return pts;
        }
        if (growth <= 0.0D) {
            return new double[0][];
        }
        int n = pts.length;
        double[] seg = new double[n - 1];
        double total = 0.0D;
        for (int i = 0; i < n - 1; i++) {
            double dx = pts[i + 1][0] - pts[i][0];
            double dy = pts[i + 1][1] - pts[i][1];
            double dz = pts[i + 1][2] - pts[i][2];
            seg[i] = Math.sqrt(dx * dx + dy * dy + dz * dz);
            total += seg[i];
        }
        if (total < 1.0E-6D) {
            return new double[][]{pts[0], pts[n - 1]};
        }
        double target = total * Math.max(0.0D, Math.min(1.0D, growth));
        double run = 0.0D;
        int keep = 1;
        for (int i = 0; i < n - 1; i++) {
            if (run + seg[i] >= target) {
                double t = seg[i] < 1.0E-9D ? 0.0D : (target - run) / seg[i];
                double[][] out = new double[keep + 1][];
                System.arraycopy(pts, 0, out, 0, keep);
                out[keep] = new double[]{
                        pts[i][0] + (pts[i + 1][0] - pts[i][0]) * t,
                        pts[i][1] + (pts[i + 1][1] - pts[i][1]) * t,
                        pts[i][2] + (pts[i + 1][2] - pts[i][2]) * t};
                return out;
            }
            run += seg[i];
            keep++;
        }
        return pts;
    }

    /**
     * 一圈横着的<b>黑线环</b>（贴地/悬空都行）—— 暗系落点、黑阵外圈用它。
     *
     * @param y 环的高度（离 {@code cy} 多少格；贴地给 0.05 左右）
     */
    public static void darkRing(VertexConsumer vc, Matrix4f m,
                                double cx, double cy, double cz,
                                double radius, int segments, long seed,
                                float width, float alpha, double spin,
                                double camX, double camY, double camZ) {
        if (radius <= 0.02D || alpha <= 0.004F) {
            return;
        }
        for (int i = 0; i < segments; i++) {
            double a0 = spin + i * (Math.PI * 2.0D / segments);
            double a1 = a0 + Math.PI * 2.0D / segments;
            darkArc(vc, m,
                    cx + Math.cos(a0) * radius, cy, cz + Math.sin(a0) * radius,
                    cx + Math.cos(a1) * radius, cy, cz + Math.sin(a1) * radius,
                    seed + i * 31L, width, alpha, camX, camY, camZ);
        }
    }

    /** 黑线四层：血雾 → 血线 → 黑芯 → 血色高光 ✓ */
    private static void darkStrands(VertexConsumer vc, Matrix4f m, double[][] pts, float width,
                                    float alpha, double camX, double camY, double camZ) {
        darkStrands(vc, m, pts, width, alpha, BLOOD, BLOOD_DIM, camX, camY, camZ);
    }

    /**
     * 同上，但<b>血色可换</b>（作者 2026-11-06：「我希望是深暗红色 …… 黑暗之手链太亮」）。
     *
     * <p>四层的比例保持不变，只换中间那两层的颜色 ⇒ 换的是"血色有多深"，不是"线有多亮" ✓。
     * 献祭/落点用的仍是默认 {@link #BLOOD}（作者说那个刚好 ✓），只有线手换成更深的一档。
     */
    private static void darkStrands(VertexConsumer vc, Matrix4f m, double[][] pts, float width,
                                    float alpha, float[] blood, float[] bloodDim,
                                    double camX, double camY, double camZ) {
        // 1) 血雾：最宽、最暗，只负责"这附近有东西"
        ribbon(vc, m, pts, width * 4.0F, bloodDim, 0.10F * alpha, bloodDim, 0.0F, camX, camY, camZ);
        // 2) 血线：看得出是暗红
        ribbon(vc, m, pts, width * 1.8F, blood, 0.42F * alpha, bloodDim, 0.0F, camX, camY, camZ);
        // 3) 黑芯：细、几乎纯黑、alpha 高 ⇒ 把背后的光"吃"掉，这才叫黑线 ✓
        ribbon(vc, m, pts, width * 0.80F, DARK_CORE, 0.92F * alpha, DARK_CORE, 0.45F * alpha,
                camX, camY, camZ);
        // 4) 一线血高光：黑芯中间留一点红，不然整条会糊成剪影
        ribbon(vc, m, pts, width * 0.22F, blood, 0.80F * alpha, blood, 0.0F, camX, camY, camZ);
    }

    /**
     * ★ <b>深暗红版</b>的黑线（2026-11-06 作者：「我希望是深暗红色 …… 黑暗之手链太亮」）。
     *
     * <p>和 {@link #darkBolt} 完全同一条路径，只把血色压深一档：血雾 `{0.10,0,0.012}`、
     * 血线 `{0.30,0.012,0.030}` —— 比默认 {@code BLOOD {0.62,0.03,0.07}} 暗一半，
     * 在夜里读起来是"深红发黑"而不是"亮红发光"✓。献祭/落点继续用默认档（作者说刚好 ✓）。
     */
    public static final float[] BLOOD_DEEP = {0.30F, 0.012F, 0.030F};
    /** 深暗红版的血雾（更暗一档）。 */
    public static final float[] BLOOD_DEEP_DIM = {0.105F, 0.002F, 0.013F};

    /** 同上，只画到 {@code growth}（0..1），颜色换成<b>深暗红</b> ✓。 */
    public static void darkArcDeep(VertexConsumer vc, Matrix4f m,
                                   double ax, double ay, double az,
                                   double bx, double by, double bz,
                                   long seed, float width, float alpha, double growth,
                                   double camX, double camY, double camZ) {
        if (width <= 0.0F || alpha <= 0.004F || growth <= 0.01D) {
            return;
        }
        RandomSource rnd = RandomSource.create(seed);
        double[][] full = path(ax, ay, az, bx, by, bz, 2, 0.10D, rnd);
        double[][] pts = clipPath(full, growth);
        if (pts.length < 2) {
            return;
        }
        darkStrands(vc, m, pts, width, alpha, BLOOD_DEEP, BLOOD_DEEP_DIM, camX, camY, camZ);
    }

    /** 同上，整条（不剪）✓。 */
    public static void darkArcDeep(VertexConsumer vc, Matrix4f m,
                                   double ax, double ay, double az,
                                   double bx, double by, double bz,
                                   long seed, float width, float alpha,
                                   double camX, double camY, double camZ) {
        darkArcDeep(vc, m, ax, ay, az, bx, by, bz, seed, width, alpha, 1.0D, camX, camY, camZ);
    }

    /** 深暗红版的黑线（带生长 + 线头血珠）✓。 */
    public static void darkBoltDeep(VertexConsumer vc, Matrix4f m,
                                    double ax, double ay, double az,
                                    double bx, double by, double bz,
                                    long seed, float width, float alpha, double growth,
                                    double camX, double camY, double camZ) {
        if (width <= 0.0F || alpha <= 0.004F || growth <= 0.01D) {
            return;
        }
        RandomSource rnd = RandomSource.create(seed);
        double[][] full = path(ax, ay, az, bx, by, bz, PATH_LEVELS, PATH_JITTER, rnd);
        double[][] pts = clipPath(full, growth);
        if (pts.length < 2) {
            return;
        }
        darkStrands(vc, m, pts, width, alpha, BLOOD_DEEP, BLOOD_DEEP_DIM, camX, camY, camZ);
        if (growth < 0.985D) {
            double[] head = pts[pts.length - 1];
            darkBeadDeep(vc, m, head[0], head[1], head[2], width * 2.4F, alpha, camX, camY, camZ);
        }
    }

    /** 深暗红版的"血珠"（比默认那档暗一半，夜里不会发光 ✓）。 */
    public static void darkBeadDeep(VertexConsumer vc, Matrix4f m,
                                    double x, double y, double z, float radius, float alpha,
                                    double camX, double camY, double camZ) {
        if (radius <= 0.0F || alpha <= 0.004F) {
            return;
        }
        double[] fwd = {camX - x, camY - y, camZ - z};
        double fl = Math.sqrt(fwd[0] * fwd[0] + fwd[1] * fwd[1] + fwd[2] * fwd[2]);
        if (fl < 1.0E-4D) {
            return;
        }
        fwd[0] /= fl;
        fwd[1] /= fl;
        fwd[2] /= fl;
        double[] right = {fwd[2], 0.0D, -fwd[0]};
        double rl = Math.sqrt(right[0] * right[0] + right[2] * right[2]);
        if (rl < 1.0E-4D) {
            right = new double[]{1.0D, 0.0D, 0.0D};
            rl = 1.0D;
        }
        right[0] /= rl;
        right[2] /= rl;
        right[1] = 0.0D;
        double[] up = {
                fwd[1] * right[2] - fwd[2] * right[1],
                fwd[2] * right[0] - fwd[0] * right[2],
                fwd[0] * right[1] - fwd[1] * right[0]};
        beadQuad(vc, m, x, y, z, right, up, radius * 2.3D, BLOOD_DEEP_DIM, 0.28F * alpha);
        beadQuad(vc, m, x, y, z, right, up, radius * 1.25D, BLOOD_DEEP, 0.55F * alpha);
        beadQuad(vc, m, x, y, z, right, up, radius * 0.55D, DARK_CORE, 0.95F * alpha);
    }

    // ------------------------------------------------------------------
    //  地面雷网（蛛网）
    // ------------------------------------------------------------------

    /**
     * 贴地的<b>蛛网雷网</b>：三圈锯齿环 ＋ 十二根辐条 ＋ 若干斜拉线 ＋ 节点亮点。
     *
     * <p>作者 2026-11-05："类似蛛网一样的雷网特效可以丰富我们的法阵" ✓ ——
     * 所以法阵和落点都用它叠一层：贴图给"符文"，它给"电"。
     *
     * @param radius 网的外半径
     * @param spin   整体旋转（度转弧度由调用方算好；法阵会慢慢转 ✓）
     * @param nodes  要不要画交点上的亮点（落点爆炸时画；法阵上画得更稀）
     */
    public static void groundWeb(VertexConsumer vc, Matrix4f m,
                                 double cx, double cy, double cz,
                                 float radius, long seed, int theme, float alpha, float spin,
                                 double camX, double camY, double camZ, boolean nodes) {
        if (radius <= 0.02F || alpha <= 0.004F) {
            return;
        }
        RandomSource rnd = RandomSource.create(seed);
        int spokes = 12;
        double[][][] node = new double[3][spokes][3];
        double[] frac = {0.34D, 0.66D, 1.0D};
        for (int r = 0; r < 3; r++) {
            for (int k = 0; k < spokes; k++) {
                double a = spin + k * (Math.PI * 2.0D / spokes) + (rnd.nextDouble() - 0.5D) * 0.07D;
                double rr = radius * frac[r] * (0.965D + rnd.nextDouble() * 0.07D);
                node[r][k][0] = cx + Math.cos(a) * rr;
                node[r][k][1] = cy + 0.055D + (rnd.nextDouble() - 0.5D) * 0.025D;
                node[r][k][2] = cz + Math.sin(a) * rr;
            }
        }
        float lineWidth = 0.014F + radius * 0.0026F;

        // 三圈：每两个节点之间插两个抖动的点 ⇒ 环不是多边形，而是"电弧"✓
        for (int r = 0; r < 3; r++) {
            int perChord = 2;
            double[][] ring = new double[spokes * (perChord + 1) + 1][3];
            int idx = 0;
            for (int k = 0; k < spokes; k++) {
                double[] a = node[r][k];
                double[] b = node[r][(k + 1) % spokes];
                ring[idx++] = a;
                for (int s = 1; s <= perChord; s++) {
                    double t = s / (double) (perChord + 1);
                    double jitter = radius * 0.028D;
                    ring[idx][0] = a[0] + (b[0] - a[0]) * t + (rnd.nextDouble() - 0.5D) * jitter;
                    ring[idx][1] = a[1] + (b[1] - a[1]) * t;
                    ring[idx][2] = a[2] + (b[2] - a[2]) * t + (rnd.nextDouble() - 0.5D) * jitter;
                    idx++;
                }
            }
            ring[idx] = ring[0].clone();                 // 闭合
            webLine(vc, m, ring, lineWidth * (r == 0 ? 0.8F : 1.0F), alpha, theme, camX, camY, camZ);
        }

        // 十二根辐条：从中心附近长到外环
        for (int k = 0; k < spokes; k++) {
            double[] out = node[2][k];
            double dx = out[0] - cx;
            double dz = out[2] - cz;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-3D) {
                continue;
            }
            double sx = cx + dx / len * radius * 0.07D;
            double sz = cz + dz / len * radius * 0.07D;
            double[][] spoke = path(sx, cy + 0.05D, sz, out[0], out[1], out[2], 2, 0.035D, rnd);
            webLine(vc, m, spoke, lineWidth, alpha * 0.95F, theme, camX, camY, camZ);
        }

        // 斜拉线（蛛网的"网眼"）：每两格连一条，隔一环错开一位
        for (int r = 0; r < 2; r++) {
            for (int k = 0; k < spokes; k += 2) {
                double[] a = node[r][k];
                double[] b = node[r + 1][(k + 1) % spokes];
                double[][] chord = path(a[0], a[1], a[2], b[0], b[1], b[2], 1, 0.03D, rnd);
                webLine(vc, m, chord, lineWidth * 0.85F, alpha * 0.7F, theme, camX, camY, camZ);
            }
        }

        // 交点亮点：白热的小光斑（落点爆炸时很提神 ✓；法阵上隔一个画一个，免得糊成一片）
        if (nodes) {
            for (int r = 1; r < 3; r++) {
                for (int k = 0; k < spokes; k += (r == 2 ? 1 : 2)) {
                    double[] p = node[r][k];
                    float size = radius * (r == 2 ? 0.035F : 0.028F);
                    point(vc, m, p[0], p[1] + 0.02D, p[2], size,
                            alpha * 0.85F, theme, camX, camY, camZ);
                }
            }
        }
    }

    /** 网线：两层就够（外辉光 + 白芯），比闪电省一半。 */
    private static void webLine(VertexConsumer vc, Matrix4f m, double[][] pts, float width,
                                float alpha, int theme,
                                double camX, double camY, double camZ) {
        float[] mid = LightningTheme.mid(theme);
        float[] white = LightningTheme.WHITE;
        ribbon(vc, m, pts, width * 3.0F, LightningTheme.glow(theme), 0.16F * alpha, mid, 0.0F,
                camX, camY, camZ);
        ribbon(vc, m, pts, width, white, 0.62F * alpha, mid, 0.35F * alpha,
                camX, camY, camZ);
    }

    // ------------------------------------------------------------------
    //  自身周围的雷笼
    // ------------------------------------------------------------------

    /**
     * 绕着自己的一圈<b>雷笼</b>：底环 → 顶部收拢的竖弧 ＋ 两道横环 ＋ 随机斜拉弧。
     *
     * <p>作者要的"自身周围的雷网"用它实现 ✓。半径刻意给得比玩家大（4~5 格）——
     * 第一人称站在里面时才看得到"四周劈啪作响的笼子"，而不会被糊一脸 ✗。
     */
    public static void cage(VertexConsumer vc, Matrix4f m,
                            double cx, double cy, double cz,
                            float radius, float height, long seed, int theme, float alpha, float spin,
                            double camX, double camY, double camZ) {
        if (radius <= 0.05F || height <= 0.05F || alpha <= 0.004F) {
            return;
        }
        RandomSource rnd = RandomSource.create(seed);
        int spokes = 10;
        float lineWidth = 0.012F + radius * 0.0022F;
        double[][] base = new double[spokes][3];
        double[][] top = new double[spokes][3];
        float topRadius = radius * 0.34F;
        for (int k = 0; k < spokes; k++) {
            double a = spin + k * (Math.PI * 2.0D / spokes);
            base[k][0] = cx + Math.cos(a) * radius;
            base[k][1] = cy + 0.06D;
            base[k][2] = cz + Math.sin(a) * radius;
            // 顶部稍微错开一点：正圆会像"灯罩"，错开才像电荷乱窜 ✓
            double ta = a + (rnd.nextDouble() - 0.5D) * 0.12D;
            top[k][0] = cx + Math.cos(ta) * topRadius;
            top[k][1] = cy + height * (0.92D + rnd.nextDouble() * 0.08D);
            top[k][2] = cz + Math.sin(ta) * topRadius;
        }
        // 竖弧（主体）
        for (int k = 0; k < spokes; k++) {
            double[][] arc = path(base[k][0], base[k][1], base[k][2],
                    top[k][0], top[k][1], top[k][2], 3, 0.045D, rnd);
            webLine(vc, m, arc, lineWidth, alpha, theme, camX, camY, camZ);
        }
        // 底环 ＋ 两道横环：横环半径按高度收拢
        for (int ring = 0; ring < 3; ring++) {
            double t = ring == 0 ? 0.0D : (ring == 1 ? 0.38D : 0.72D);
            double rr = radius + (topRadius - radius) * t;
            double yy = cy + 0.06D + height * t;
            double[][] pts = ringPolyline(cx, yy, cz, rr, spokes, spin + t, rnd);
            webLine(vc, m, pts, lineWidth * 0.9F, alpha * (ring == 0 ? 1.0F : 0.78F),
                    theme, camX, camY, camZ);
        }
        // 顶部一小圈
        webLine(vc, m, ringPolyline(cx, cy + height, cz, topRadius, spokes, spin, rnd),
                lineWidth * 0.8F, alpha * 0.65F, theme, camX, camY, camZ);
        // 斜拉弧：横环之间乱窜的电
        for (int i = 0; i < 8; i++) {
            int k = rnd.nextInt(spokes);
            double h1 = 0.25D + rnd.nextDouble() * 0.45D;
            double h2 = Math.min(0.95D, h1 + 0.18D + rnd.nextDouble() * 0.25D);
            double r1 = radius + (topRadius - radius) * h1;
            double r2 = radius + (topRadius - radius) * h2;
            double a1 = spin + k * (Math.PI * 2.0D / spokes);
            double a2 = a1 + (rnd.nextDouble() - 0.5D) * 0.9D;
            double[][] arc = path(
                    cx + Math.cos(a1) * r1, cy + height * h1, cz + Math.sin(a1) * r1,
                    cx + Math.cos(a2) * r2, cy + height * h2, cz + Math.sin(a2) * r2,
                    2, 0.06D, rnd);
            webLine(vc, m, arc, lineWidth * 0.8F, alpha * 0.72F, theme, camX, camY, camZ);
        }
    }

    // ------------------------------------------------------------------
    //  爆炸：地面冲击圆盘 ＋ 空中的白闪
    // ------------------------------------------------------------------

    /**
     * 贴地的<b>加色光斑</b>（中心白 → 边缘主题色 → 透明），爆炸那一下的"底光"。
     *
     * <p>用三角扇画（中心一个顶点纯白，外圈 20 个顶点 alpha 归零）⇒ 是真正的径向渐变，
     * 不是一张方形贴图拉出来的硬边 ✗。
     */
    public static void groundFlash(VertexConsumer vc, Matrix4f m,
                                   double cx, double cy, double cz, float radius,
                                   int theme, float alpha) {
        if (radius <= 0.02F || alpha <= 0.004F) {
            return;
        }
        float[] white = LightningTheme.WHITE;
        float[] mid = LightningTheme.mid(theme);
        int segments = 20;
        // 只允许 QUADS（渲染层的 Mode 写死了 ✓），所以"圆心"用两个重合顶点凑成一个退化四边形
        for (int i = 0; i < segments; i++) {
            double a0 = i * (Math.PI * 2.0D / segments);
            double a1 = (i + 1) * (Math.PI * 2.0D / segments);
            double c0 = Math.cos(a0);
            double s0 = Math.sin(a0);
            double c1 = Math.cos(a1);
            double s1 = Math.sin(a1);
            float y = (float) (cy + 0.05D);
            // 内圈：圆心(白, 最亮) → 0.42r(主题色, 半亮)
            vertex(vc, m, cx, y, cz, white, 0.88F * alpha);
            vertex(vc, m, cx, y, cz, white, 0.88F * alpha);
            vertex(vc, m, cx + c0 * radius * 0.42D, y, cz + s0 * radius * 0.42D, mid, 0.42F * alpha);
            vertex(vc, m, cx + c1 * radius * 0.42D, y, cz + s1 * radius * 0.42D, mid, 0.42F * alpha);
            // 外圈：0.42r(主题色) → r(透明) ⇒ 边缘是渐隐的，没有硬边 ✓
            vertex(vc, m, cx + c0 * radius * 0.42D, y, cz + s0 * radius * 0.42D, mid, 0.42F * alpha);
            vertex(vc, m, cx + c1 * radius * 0.42D, y, cz + s1 * radius * 0.42D, mid, 0.42F * alpha);
            vertex(vc, m, cx + c1 * radius, y, cz + s1 * radius, mid, 0.0F);
            vertex(vc, m, cx + c0 * radius, y, cz + s0 * radius, mid, 0.0F);
        }
    }

    /** 空中的<b>竖立白闪</b>：正对相机的圆盘，中心白、外圈透明（不是贴图 ✗）。 */
    public static void billboardFlash(VertexConsumer vc, Matrix4f m,
                                      double cx, double cy, double cz, float radius,
                                      int theme, float alpha,
                                      double camX, double camY, double camZ) {
        flash0(vc, m, cx, cy, cz, radius, LightningTheme.mid(theme), alpha, camX, camY, camZ);
    }

    /** 自定义配色的白闪（火/水/风/土/光用；中心仍是白，外圈是给的颜色）。 */
    public static void flashTinted(VertexConsumer vc, Matrix4f m,
                                   double cx, double cy, double cz, float radius,
                                   float[] mid, float alpha,
                                   double camX, double camY, double camZ) {
        flash0(vc, m, cx, cy, cz, radius, mid, alpha, camX, camY, camZ);
    }

    private static void flash0(VertexConsumer vc, Matrix4f m,
                               double cx, double cy, double cz, float radius,
                               float[] mid, float alpha,
                               double camX, double camY, double camZ) {
        if (radius <= 0.002F || alpha <= 0.004F) {
            return;
        }
        double vx = camX - cx;
        double vy = camY - cy;
        double vz = camZ - cz;
        double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (vl < 1.0E-3D) {
            return;
        }
        vx /= vl;
        vy /= vl;
        vz /= vl;
        // 右向量 = 世界上方 × 视线（一定是水平的，圆盘因此不会歪 ✓）
        double rx = vz;
        double rz = -vx;
        double rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1.0E-4D) {
            // 视线正好竖直（抬头/低头看落点）：随便给个水平方向，盘面转 90° 也不影响观感
            rx = 1.0D;
            rz = 0.0D;
            rl = 1.0D;
        }
        rx /= rl;
        rz /= rl;
        // 上向量 = 视线 × 右向量 ⇒ 三个基正交，圆盘正好正对镜头 ✓
        double ux = vy * rz;
        double uy = vz * rx - vx * rz;
        double uz = -vy * rx;
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1.0E-4D) {
            ux = 1.0D;
            uy = 0.0D;
            uz = 0.0D;
            ul = 1.0D;
        }
        ux /= ul;
        uy /= ul;
        uz /= ul;
        float[] white = LightningTheme.WHITE;
        int segments = 16;
        float inner = radius * 0.40F;
        for (int i = 0; i < segments; i++) {
            double a0 = i * (Math.PI * 2.0D / segments);
            double a1 = (i + 1) * (Math.PI * 2.0D / segments);
            double c0 = Math.cos(a0);
            double s0 = Math.sin(a0);
            double c1 = Math.cos(a1);
            double s1 = Math.sin(a1);
            // 内盘：圆心(白) → 内圈(主题色)
            vertex(vc, m, cx, cy, cz, white, 0.85F * alpha);
            vertex(vc, m, cx, cy, cz, white, 0.85F * alpha);
            vertex(vc, m, cx + (rx * c0 + ux * s0) * inner, cy + uy * s0 * inner,
                    cz + (rz * c0 + uz * s0) * inner, mid, 0.48F * alpha);
            vertex(vc, m, cx + (rx * c1 + ux * s1) * inner, cy + uy * s1 * inner,
                    cz + (rz * c1 + uz * s1) * inner, mid, 0.48F * alpha);
            // 外圈：内圈(主题色) → r(透明)
            vertex(vc, m, cx + (rx * c0 + ux * s0) * inner, cy + uy * s0 * inner,
                    cz + (rz * c0 + uz * s0) * inner, mid, 0.48F * alpha);
            vertex(vc, m, cx + (rx * c1 + ux * s1) * inner, cy + uy * s1 * inner,
                    cz + (rz * c1 + uz * s1) * inner, mid, 0.48F * alpha);
            vertex(vc, m, cx + (rx * c1 + ux * s1) * radius, cy + uy * s1 * radius,
                    cz + (rz * c1 + uz * s1) * radius, mid, 0.0F);
            vertex(vc, m, cx + (rx * c0 + ux * s0) * radius, cy + uy * s0 * radius,
                    cz + (rz * c0 + uz * s0) * radius, mid, 0.0F);
        }
    }

    /** 相机正对的小光点（网交点亮斑）。 */
    public static void point(VertexConsumer vc, Matrix4f m,
                             double cx, double cy, double cz, float size,
                             float alpha, int theme,
                             double camX, double camY, double camZ) {
        billboardFlash(vc, m, cx, cy, cz, size, theme, alpha, camX, camY, camZ);
    }

    // ------------------------------------------------------------------
    //  底层：路径 / 光带 / 顶点
    // ------------------------------------------------------------------

    /**
     * 递归中点位移：从 a 到 b 插出 {@code 2^levels} 段锯齿折线。
     *
     * <p>抖动量按<b>该段长度</b>给 ⇒ 顶部的段长（几十格）歪得远、越往下越细 ✓，
     * 看起来就像真实闪电"大折 + 小折"的层次，而不是一条等幅的正弦波 ✗。
     */
    private static double[][] path(double ax, double ay, double az,
                                   double bx, double by, double bz,
                                   int levels, double jitter, RandomSource rnd) {
        double[][] pts = {{ax, ay, az}, {bx, by, bz}};
        for (int level = 0; level < levels; level++) {
            int n = pts.length;
            double[][] out = new double[n * 2 - 1][3];
            for (int i = 0; i < n; i++) {
                out[i * 2][0] = pts[i][0];
                out[i * 2][1] = pts[i][1];
                out[i * 2][2] = pts[i][2];
            }
            for (int i = 0; i < n - 1; i++) {
                double[] p = pts[i];
                double[] q = pts[i + 1];
                double mx = (p[0] + q[0]) * 0.5D;
                double my = (p[1] + q[1]) * 0.5D;
                double mz = (p[2] + q[2]) * 0.5D;
                double dx = q[0] - p[0];
                double dy = q[1] - p[1];
                double dz = q[2] - p[2];
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double[] mid = out[i * 2 + 1];
                if (len < 1.0E-6D) {
                    mid[0] = mx;
                    mid[1] = my;
                    mid[2] = mz;
                    continue;
                }
                // 随便取一个向量，减掉沿段方向的分量 ⇒ 得到垂直方向
                double rx = rnd.nextDouble() * 2.0D - 1.0D;
                double ry = rnd.nextDouble() * 2.0D - 1.0D;
                double rz = rnd.nextDouble() * 2.0D - 1.0D;
                double dot = (rx * dx + ry * dy + rz * dz) / (len * len);
                rx -= dot * dx;
                ry -= dot * dy;
                rz -= dot * dz;
                double rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
                if (rl < 1.0E-5D) {
                    // 随机向量正好和段平行：换一个确定能用的垂直方向
                    rx = -dy;
                    ry = dx;
                    rz = 0.0D;
                    rl = Math.sqrt(rx * rx + ry * ry);
                    if (rl < 1.0E-5D) {
                        rx = 1.0D;
                        ry = 0.0D;
                        rz = 0.0D;
                        rl = 1.0D;
                    }
                }
                double amp = jitter * len * (0.55D + rnd.nextDouble() * 0.9D);
                mid[0] = mx + rx / rl * amp;
                mid[1] = my + ry / rl * amp;
                mid[2] = mz + rz / rl * amp;
            }
            pts = out;
        }
        return pts;
    }

    /** 一条（半宽 half）的加色光带：中心 core 色、边缘 edge 色，两侧各一条。 */
    private static void ribbon(VertexConsumer vc, Matrix4f m, double[][] pts, float half,
                               float[] core, float coreAlpha,
                               float[] edge, float edgeAlpha,
                               double camX, double camY, double camZ) {
        if (half <= 0.0F) {
            return;
        }
        float[] unit = new float[pts.length];
        java.util.Arrays.fill(unit, 1.0F);
        ribbon(vc, m, pts, unit, half, core, coreAlpha, edge, edgeAlpha, camX, camY, camZ);
    }

    /**
     * 带<b>逐点宽度倍率</b>的光带 ✓ —— 段 i 的两端各用自己的倍率，所以过渡是连续的 ✓
     * （根部细、往后再一样粗；见 {@link #polylineTapered} ✓）。
     */
    private static void ribbon(VertexConsumer vc, Matrix4f m, double[][] pts, float[] taper, float half,
                               float[] core, float coreAlpha,
                               float[] edge, float edgeAlpha,
                               double camX, double camY, double camZ) {
        if (half <= 0.0F) {
            return;
        }
        int n = pts.length;
        for (int i = 0; i < n - 1; i++) {
            double[] p = pts[i];
            double[] q = pts[i + 1];
            double[] sp = side(tangent(pts, i), p[0], p[1], p[2], camX, camY, camZ);
            double[] sq = side(tangent(pts, i + 1), q[0], q[1], q[2], camX, camY, camZ);
            if (sp == null || sq == null) {
                continue;
            }
            // 两端各自的半宽（根部细 ⇒ 这一段的 p 端窄、q 端宽 ✓ 自然的锥形过渡）
            double hp = half * taper[i];
            double hq = half * taper[Math.min(i + 1, n - 1)];
            if (hp <= 1.0E-5D && hq <= 1.0E-5D) {
                continue;
            }
            float px = (float) p[0];
            float py = (float) p[1];
            float pz = (float) p[2];
            float qx = (float) q[0];
            float qy = (float) q[1];
            float qz = (float) q[2];
            // 左半条（边缘 → 中心）
            vertex(vc, m, px - sp[0] * hp, py - sp[1] * hp, pz - sp[2] * hp, edge, edgeAlpha);
            vertex(vc, m, px, py, pz, core, coreAlpha);
            vertex(vc, m, qx, qy, qz, core, coreAlpha);
            vertex(vc, m, qx - sq[0] * hq, qy - sq[1] * hq, qz - sq[2] * hq, edge, edgeAlpha);
            // 右半条（中心 → 边缘）
            vertex(vc, m, px, py, pz, core, coreAlpha);
            vertex(vc, m, px + sp[0] * hp, py + sp[1] * hp, pz + sp[2] * hp, edge, edgeAlpha);
            vertex(vc, m, qx + sq[0] * hq, qy + sq[1] * hq, qz + sq[2] * hq, edge, edgeAlpha);
            vertex(vc, m, qx, qy, qz, core, coreAlpha);
        }
    }

    /** 点 i 处的单位切线（用前后两点差分 ⇒ 拐点两侧共用同一条切线，不会裂）。 */
    private static double[] tangent(double[][] pts, int i) {
        int a = Math.max(0, i - 1);
        int b = Math.min(pts.length - 1, i + 1);
        double dx = pts[b][0] - pts[a][0];
        double dy = pts[b][1] - pts[a][1];
        double dz = pts[b][2] - pts[a][2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6D) {
            return new double[]{0.0D, 1.0D, 0.0D};
        }
        return new double[]{dx / len, dy / len, dz / len};
    }

    /**
     * 切线与"指向相机"的叉积 = 光带的侧向量（单位化）。
     *
     * <p>返回值是<b>新数组</b>；相机正好在这条线的延长线上时（叉积退化）返回 null，
     * 这一段跳过 —— 反正正对着看本来也看不见厚度 ✓。
     */
    private static double[] side(double[] tangent, double px, double py, double pz,
                                 double camX, double camY, double camZ) {
        double vx = camX - px;
        double vy = camY - py;
        double vz = camZ - pz;
        double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (vl < 1.0E-4D) {
            return null;
        }
        vx /= vl;
        vy /= vl;
        vz /= vl;
        double sx = tangent[1] * vz - tangent[2] * vy;
        double sy = tangent[2] * vx - tangent[0] * vz;
        double sz = tangent[0] * vy - tangent[1] * vx;
        double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1.0E-3D) {
            return null;
        }
        return new double[]{sx / sl, sy / sl, sz / sl};
    }

    /** 一个封闭的锯齿圆（横环）。 */
    private static double[][] ringPolyline(double cx, double cy, double cz, double radius,
                                           int spokes, double spin, RandomSource rnd) {
        double[][] pts = new double[spokes + 1][3];
        for (int k = 0; k < spokes; k++) {
            double a = spin + k * (Math.PI * 2.0D / spokes);
            double rr = radius * (0.94D + rnd.nextDouble() * 0.12D);
            pts[k][0] = cx + Math.cos(a) * rr;
            pts[k][1] = cy + (rnd.nextDouble() - 0.5D) * 0.04D;
            pts[k][2] = cz + Math.sin(a) * rr;
        }
        pts[spokes] = pts[0].clone();
        return pts;
    }

    /** 顶点（统一走这里：贴图取正中间的白点，颜色/alpha 全在顶点上 ✓）。 */
    private static void vertex(VertexConsumer vc, Matrix4f m,
                               double x, double y, double z,
                               float[] color, float alpha) {
        vc.vertex(m, (float) x, (float) y, (float) z)
                .color(color[0], color[1], color[2], alpha)
                .uv(0.5F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}
