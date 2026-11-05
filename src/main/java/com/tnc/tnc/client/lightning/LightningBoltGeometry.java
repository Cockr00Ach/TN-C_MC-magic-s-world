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
        if (width <= 0.0F || alpha <= 0.004F) {
            return;
        }
        RandomSource rnd = RandomSource.create(seed);
        double[][] path = path(ax, ay, az, bx, by, bz, PATH_LEVELS, PATH_JITTER, rnd);
        strands(vc, m, path, width, alpha, theme, camX, camY, camZ);

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
            double[][] sub = path(p[0], p[1], p[2], ex, ey, ez, BRANCH_LEVELS, BRANCH_JITTER, rnd);
            strands(vc, m, sub, width * 0.52F, alpha * 0.72F, theme, camX, camY, camZ);
            // 二级分叉：只给最粗的大雷（width 够大时）—— 小雷画了也看不见，纯浪费 ✗
            if (width > 0.34F && rnd.nextBoolean()) {
                int at2 = 1 + rnd.nextInt(Math.max(1, sub.length - 2));
                double[] q = sub[Math.min(at2, sub.length - 2)];
                double a2 = rnd.nextDouble() * Math.PI * 2.0D;
                double r2 = len * (0.07D + rnd.nextDouble() * 0.09D);
                double[][] sub2 = path(q[0], q[1], q[2],
                        q[0] + Math.cos(a2) * r2, q[1] - r2 * 0.55D, q[2] + Math.sin(a2) * r2,
                        2, 0.11D, rnd);
                strands(vc, m, sub2, width * 0.28F, alpha * 0.55F, theme, camX, camY, camZ);
            }
        }
    }

    /** 四层叠出来的"白芯 + 彩色光晕"。 */
    private static void strands(VertexConsumer vc, Matrix4f m, double[][] pts, float width,
                                float alpha, int theme,
                                double camX, double camY, double camZ) {
        float[] glow = LightningTheme.glow(theme);
        float[] mid = LightningTheme.mid(theme);
        float[] edge = LightningTheme.edge(theme);
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
        float[] mid = LightningTheme.mid(theme);
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
        int n = pts.length;
        for (int i = 0; i < n - 1; i++) {
            double[] p = pts[i];
            double[] q = pts[i + 1];
            double[] sp = side(tangent(pts, i), p[0], p[1], p[2], camX, camY, camZ);
            double[] sq = side(tangent(pts, i + 1), q[0], q[1], q[2], camX, camY, camZ);
            if (sp == null || sq == null) {
                continue;
            }
            float px = (float) p[0];
            float py = (float) p[1];
            float pz = (float) p[2];
            float qx = (float) q[0];
            float qy = (float) q[1];
            float qz = (float) q[2];
            // 左半条（边缘 → 中心）
            vertex(vc, m, px - sp[0] * half, py - sp[1] * half, pz - sp[2] * half, edge, edgeAlpha);
            vertex(vc, m, px, py, pz, core, coreAlpha);
            vertex(vc, m, qx, qy, qz, core, coreAlpha);
            vertex(vc, m, qx - sq[0] * half, qy - sq[1] * half, qz - sq[2] * half, edge, edgeAlpha);
            // 右半条（中心 → 边缘）
            vertex(vc, m, px, py, pz, core, coreAlpha);
            vertex(vc, m, px + sp[0] * half, py + sp[1] * half, pz + sp[2] * half, edge, edgeAlpha);
            vertex(vc, m, qx + sq[0] * half, qy + sq[1] * half, qz + sq[2] * half, edge, edgeAlpha);
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
