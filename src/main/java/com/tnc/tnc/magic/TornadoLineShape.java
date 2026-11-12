package com.tnc.tnc.magic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ★ <b>龙卷风的"线条骨架"</b>（2026-11-09 作者：「我们的闪电不是一个一个线吗，
 * 我想这个线螺旋缠绕起来变成龙卷风……可以有明确的锐角，龙卷风极具那种线条特色」）✓
 *
 * <h2>为什么单独一个纯数学类</h2>
 * 龙卷风以前是<b>烘焙模型</b>（{@code tnc:projectile/wind_tornado}，7 层 × 4 片薄板）✗ ——
 * 作者实机看下来是"几块板在转"，没有线条感 ✗。
 * 现在改成<b>一束螺旋缠绕的折线</b>：每一根线都是<b>少折点 + 大幅度</b>的折线
 * ⇒ 每一节都是明确锐角（和雷的锯齿同一套读法）✓。
 *
 * <p>本类只算<b>顶点</b>（{@code double[n][3]}，单位：格，原点在脚下中轴）✓：
 * 客户端渲染器（{@code WindTornadoLines}）拿去画光带，
 * 实体自己的粒子（{@link TNTornadoEntity#spawnSwirlParticles}）拿去撒风粒子 ——
 * <b>两边共用同一份相位</b> ⇒ 粒子和线永远缠在同一条螺旋上 ✓（各算各的会当场穿帮 ✗）。
 *
 * <h2>形状</h2>
 * <ul>
 *   <li><b>收口</b>：底半径小、顶半径大，{@code pow(u, 0.85)} 让下半段更窄（漏斗感 ✓）</li>
 *   <li><b>螺旋</b>：整根线绕 {@link #TURNS} 圈（1.7 圈，比旧模型的 0.375 圈紧得多 ✓）</li>
 *   <li><b>锐角</b>：{@link #SEGMENTS} 只有 17 段 ⇒ 每段约 36° 圆弧，
 *       再叠 {@link #KINK} 的半径锯齿（外挑/内收交替）⇒ 每一节都是折角 ✓</li>
 *   <li><b>收顶</b>：每根线到顶的高度不同（0.84~1.0）× 顶口往内收 ⇒ 顶口不会变成一圈"帽檐" ✓</li>
 *   <li><b>刃尖</b>：偶尔（{@link #SPUR_CHANCE}）某个折点向外挑 {@link #SPUR} ⇒ 轮廓上戳出尖刺 ✓</li>
 *   <li><b>风切</b>：另外撒 {@link #SLASHES} 道短的 V 形折线 ⇒ "风刃掠过" ✓</li>
 * </ul>
 *
 * <p>⚠️ 角度一律写成 {@code -(spin + twist*u)}（和旧模型/粒子同一个方向 ✓）：
 * 绕 +Y 的右手旋转会把 {@code (cos φ, sin φ)} 映到 {@code φ − spin}，
 * 写成正号就会和粒子**反向**转 ✗。
 */
public final class TornadoLineShape {

    /** 几根线缠在一起 ✓（作者："线螺旋缠绕起来" ⇒ 多根才像缠绕，一根像弹簧 ✗）。 */
    public static final int STRANDS = 7;

    /** 整根线绕几圈 ✓（1.7 圈：看得清螺旋，又不会稠成一团格子 ✗）。 */
    public static final double TURNS = 1.70D;

    /** 一根线有多少段 ✓ —— 段数越少、折角越大；17 段 ≈ 每段 36° ⇒ 锐角清楚 ✓。 */
    public static final int SEGMENTS = 17;

    /** 半径锯齿幅度（相对当前半径）✓ —— 外挑 / 内收交替，这就是"锐角"的来源 ✓。 */
    public static final double KINK = 0.14D;

    /** 偶发刃尖向外挑多少 ✓（相对当前半径）。 */
    public static final double SPUR = 0.26D;

    /** 刃尖出现概率 ✓（越靠顶越低，见 {@link #spurChance(double)}）。 */
    public static final double SPUR_CHANCE = 0.14D;

    /** 刃尖比普通折点再往上挑多少（相对总高）✓ —— 让尖是"斜着挑出去"，不是横杠 ✗。 */
    public static final double SPUR_LIFT = 0.060D;

    /** 漏斗顶部开始往内收的高度 ✓（u 超过它就开始收口，免得顶口变成一圈"帽檐" ✗）。 */
    public static final double TOP_CURL_FROM = 0.82D;

    /** 另外补几道短风切 ✓。 */
    public static final int SLASHES = 6;

    /**
     * <b>一层线</b>（作者 2026-11-09：「做成两层，里一层外一层」）✓
     *
     * @param name        名字（日志/自检用）
     * @param radiusScale 半径相对外层漏斗的比例（内层 &lt; 1 ⇒ 套在里面 ✓）
     * @param strands     这一层几根线
     * @param turnsScale  圈数相对 {@link #TURNS} 的比例（两层圈数不同 ⇒ 不会像一圈同心管 ✗）
     * @param phaseShift  这一层整体的角度偏移（弧度，让两层错开 ✓）
     * @param widthScale  线宽比例
     * @param alphaScale  不透明度比例
     * @param salt        随机种子盐（两层锯齿不同 ✓）
     * @param whiteness   白芯的白度（1 = 纯白；内层给 0.3 ⇒ 芯偏青绿，一眼看出是"里面那层" ✓）
     */
    public record Shell(String name, double radiusScale, int strands, double turnsScale,
                        double phaseShift, float widthScale, float alphaScale, long salt,
                        float whiteness) {
    }

    /** 外层：贴着漏斗壁的那一圈 ✓（原来那层，参数没动）。 */
    public static final Shell OUTER = new Shell("outer", 1.00D, STRANDS, 1.00D, 0.0D, 1.00F, 1.00F, 0L, 1.00F);

    /**
     * 内层：套在里面的第二层 ✓ —— 半径 0.55、5 根、圈数略少、整体错开 72°，
     * 而且<b>芯偏青绿</b>（whiteness 0.30）⇒ 加色混合下一眼能分出"外层白、内层绿" ✓
     * （两层都纯白的话会糊成一根管子 ✗）。
     */
    public static final Shell INNER = new Shell("inner", 0.55D, 5, 0.92D, Math.PI * 0.4D, 0.80F, 0.95F, 977L, 0.30F);

    /** 两层一起 ✓（渲染器按这个顺序画：先外后内 ✓）。 */
    public static final java.util.List<Shell> SHELLS = java.util.List.of(OUTER, INNER);

    private TornadoLineShape() {
    }

    /** 整根线绕多少弧度 ✓（= {@link #TURNS} × 2π）。 */
    public static double totalTwist() {
        return TURNS * Math.PI * 2.0D;
    }

    /**
     * 高度 u（0 = 底、1 = 顶）处的<b>基准半径</b>（格，未乘档位）✓。
     *
     * @param bottom 底半径（格）
     * @param top    顶半径（格）
     */
    public static double profile(double u, double bottom, double top) {
        double t = Math.max(0.0D, Math.min(1.0D, u));
        return bottom + (top - bottom) * Math.pow(t, 0.72D);
    }

    /** 高度 u 处的刃尖概率 ✓（越靠顶越低 —— 顶口要收拢，不能甩出一圈横杠 ✗）。 */
    public static double spurChance(double u) {
        return SPUR_CHANCE * Math.max(0.0D, 1.0D - Math.max(0.0D, Math.min(1.0D, u)));
    }

    /**
     * 顶口的收口量（1 = 不收，越小越往内收）✓。
     *
     * @param u 高度（0..1）
     * @param curl 这根线的收口强度（0..0.3）
     */
    public static double topTaper(double u, double curl) {
        if (u <= TOP_CURL_FROM) {
            return 1.0D;
        }
        double k = (u - TOP_CURL_FROM) / (1.0D - TOP_CURL_FROM);
        return 1.0D - curl * k;
    }

    /**
     * 高度 u 处第 {@code strand} 根线的角度（弧度，含自身错位）✓。
     *
     * @param spinRad 当前自转角（弧度，实体同步过来的）
     */
    public static double angleAt(double u, double spinRad, int strand, int strands) {
        return angleAt(u, spinRad, strand, strands, TURNS, 0.0D);
    }

    /**
     * 带圈数 / 层偏移的角度（两层用不同的圈数和相位 ✓）。
     *
     * @param turns     这一层绕几圈
     * @param phaseShift 这一层整体的角度偏移（弧度）
     */
    public static double angleAt(double u, double spinRad, int strand, int strands,
                                 double turns, double phaseShift) {
        return phaseShift + strand * (Math.PI * 2.0D / strands)
                - (spinRad + turns * Math.PI * 2.0D * u);
    }

    /**
     * 全部螺旋线（每根一条折线）✓。
     *
     * @param spinRad 自转角（弧度）
     * @param bottom  底半径（格）
     * @param top     顶半径（格）
     * @param height  高度（格）
     * @param seed    换种子 = 换一组锯齿（客户端每帧不用换，抖动由自转本身给 ✓）
     * @return 每根线一组点（{@code [i][0..2] = x,y,z}，原点在脚下中轴）
     */
    public static List<double[][]> strands(double spinRad, double bottom, double top,
                                           double height, long seed) {
        return strands(spinRad, bottom, top, height, seed, OUTER);
    }

    /**
     * 某一层的全部螺旋线 ✓。
     *
     * @param shell 哪一层（外层 {@link #OUTER} / 内层 {@link #INNER}）
     */
    public static List<double[][]> strands(double spinRad, double bottom, double top,
                                           double height, long seed, Shell shell) {
        Random rnd = new Random(seed * 131L + shell.salt());
        double layerBottom = bottom * shell.radiusScale();
        double layerTop = top * shell.radiusScale();
        List<double[][]> out = new ArrayList<>(shell.strands());
        for (int s = 0; s < shell.strands(); s++) {
            double[][] pts = new double[SEGMENTS + 1][3];
            double phase = (rnd.nextDouble() - 0.5D) * 0.55D;      // 每根线错开一点 ✓
            double kink = 0.78D + rnd.nextDouble() * 0.5D;         // 每根线锯齿力度不同 ✓
            double heightScale = 0.84D + rnd.nextDouble() * 0.16D; // 到顶高度不同 ⇒ 顶口不是一圈"帽檐" ✓
            double topCurl = 0.10D + rnd.nextDouble() * 0.12D;     // 顶口往内收的程度 ✓
            double lift = 0.0D;                                    // 挑上去的高度（会衰减）✓
            for (int i = 0; i <= SEGMENTS; i++) {
                double u = i / (double) SEGMENTS;
                double r = profile(u, layerBottom, layerTop);
                boolean spike = (i % 2 == 0);
                double factor;
                if (spike) {
                    factor = 1.0D + KINK * kink;
                    if (rnd.nextDouble() < spurChance(u)) {
                        factor = 1.0D + SPUR * kink;               // 刃尖 ✓
                        // 尖是斜着往上挑的 ✓，而且挑上去的高度**累积着慢慢衰减**（不是立刻掉回来）——
                        // 这样 y 始终单调往上（不会出现"倒流"的一小段 ✗），视觉上也还是一路往上 ✓
                        lift = Math.min(height * 0.10D, lift + height * SPUR_LIFT);
                    } else {
                        lift *= 0.55D;
                    }
                } else {
                    factor = 1.0D - KINK * 0.55D * kink;           // 内收 ✓
                    lift *= 0.55D;
                }
                double rr = Math.max(1.0E-3D, r * factor) * topTaper(u, topCurl);
                double a = angleAt(u, spinRad, s, shell.strands(), TURNS * shell.turnsScale(),
                        shell.phaseShift()) + phase;
                double y = u * height * heightScale + lift;
                if (i > 0 && i < SEGMENTS) {
                    y += (rnd.nextDouble() - 0.5D) * height * 0.008D;   // 轻微高低 ⇒ 不是规整弹簧 ✓
                }
                pts[i][0] = Math.cos(a) * rr;
                pts[i][1] = y;
                pts[i][2] = Math.sin(a) * rr;
            }
            out.add(pts);
        }
        return out;
    }

    /**
     * 几道<b>短风切</b>（V 形折线，贴在漏斗外壁上）✓ —— 让轮廓上有"刃掠过"的锐角，
     * 而不是只有一圈规整的螺旋 ✓。
     */
    public static List<double[][]> slashes(double spinRad, double bottom, double top,
                                           double height, long seed) {
        Random rnd = new Random(seed * 31L + 7L);
        List<double[][]> out = new ArrayList<>(SLASHES);
        for (int k = 0; k < SLASHES; k++) {
            double u = 0.18D + 0.72D * ((k + rnd.nextDouble() * 0.6D) / SLASHES);
            double r = profile(u, bottom, top);
            double a = angleAt(u, spinRad, k, SLASHES) + (rnd.nextDouble() - 0.5D) * 0.5D;
            double y = u * height;
            double spread = 0.22D + rnd.nextDouble() * 0.16D;      // 横向张开（弧度）
            double[][] pts = new double[4][3];
            double[] radial = {1.0D, 1.16D, 0.94D, 1.28D};          // 尖-收-尖 ⇒ 两个锐角 ✓
            for (int i = 0; i < 4; i++) {
                double aa = a + (i - 0.5D) * spread;
                double rr = r * radial[i];
                pts[i][0] = Math.cos(aa) * rr;
                pts[i][1] = y + (i - 1.5D) * height * 0.035D;       // 斜着往上掠 ✓
                pts[i][2] = Math.sin(aa) * rr;
            }
            out.add(pts);
        }
        return out;
    }
}
