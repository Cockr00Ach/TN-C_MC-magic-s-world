package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.magic.water.WaterSpellRules;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 水球链 t4/t5（龙啸 / 龙滅）那道<b>水波</b>。
 *
 * <h2>2026-11-09 作者：「一根直直的闪电像不像水波嘞，把这个闪电加粗加大到跟水波一样，替换一下看看效果」</h2>
 * 原来这里画的是一根<b>真 3D 水管</b>：外壁 {@code tube(radius)} + 中轴细管 + 缠绕水流线 +
 * 每 8/16 格一圈水环 + 末端圆盘 —— 读起来是"水柱"，不像"浪"。
 *
 * <p>现在整根换成<b>雷系那套光带</b>（{@link LightningBoltGeometry#polyline} ✓ 和落雷、风系龙卷风
 * 同一套"外辉光 + 主色 + 白芯"三层加色光带，只把配色换成本系的蓝 ✓），并且按水柱半径放大：
 * <ul>
 *   <li><b>t4（半径 0.8）</b>：就是<b>一根</b>水色闪电，白芯半宽 0.27 格、主色层约 0.8 格
 *       ⇒ 粗细和原来的水管一致 ✓；</li>
 *   <li><b>t5（半径 15）</b>：一整根 30 格宽的"扁带"会变成一堵墙 ✗（光带永远正对镜头 ⇒ 越粗越平 ✗）
 *       ⇒ 改成<b>一束</b> 9 根细闪电撑满同一个圆截面 ✓ —— 远看是一根粗壮的水色光束，
 *       近看是九股缠在一起的电 ✓；</li>
 *   <li><b>抖动量小</b>：作者要"直直的"✓ ⇒ 起伏幅度按半径的 0.10 倍给，不是雷系那种按段长抖 ✗；</li>
 *   <li>每 4 tick 换一次 seed ⇒ 电弧一直在抖 ✓（雷是 2 tick、水放慢一半，读作流动 ✓）；</li>
 *   <li>末端一记水色爆闪 ✓。</li>
 * </ul>
 *
 * <p>⚠️ 这只是一次<b>表现替换</b>：判定、伤害、破坏半径（{@link WaterSpellRules#radius}）、
 * 范围、时长一个数都没动 ✗ —— 想换回水管，只要把 {@link #render} 改回原来那几行 ✓。
 *
 * <p>顶点预算（{@code WaterVisualsCostTest#completeBeamBodyHasBoundedNearAndFarCost} 钉着 ≤1800 ✓）：
 * 每根闪电 = 3 层 × 5 段 × 8 = 120 个顶点 ⇒ t5 远看 4 根 480、近看 9 根 1080 + 末端爆闪 128 ✓。
 */
final class WaterBeamVisuals {

    /** 白芯色（三层里最亮的一层 ✓）—— 不是纯白，带一点青，才读得出"水"✗。 */
    private static final float[] CORE = {0.78F, 0.94F, 1.00F};
    /** 主色（看起来"是水"的那层蓝）。 */
    private static final float[] MID = {0.34F, 0.74F, 1.00F};
    /** 深一点的主色 —— 一束里交替用，九根才不会糊成一片同色 ✗。 */
    private static final float[] MID_DEEP = {0.20F, 0.55F, 0.98F};
    /** 外辉光（负责把周围的空气照亮）。 */
    private static final float[] GLOW = {0.10F, 0.42F, 0.95F};

    /** 一根闪电用几段折线（段越多越"电"，但每段 24 个顶点 ✗）。
     *  根部刻意排得密（0 / .05 / .11）⇒ "从细到粗"那段才短 ✓ */
    private static final double[] TS_SINGLE = {0.0D, 0.05D, 0.12D, 0.26D, 0.46D, 0.68D, 0.86D, 1.0D};
    private static final double[] TS_BUNDLE = {0.0D, 0.07D, 0.14D, 0.32D, 0.54D, 0.78D, 1.0D};
    /** 根部收到多细（10% ✓ —— 法阵口那么大，水波得"挤"出来一点） */
    static final float ROOT_MIN = 0.10F;
    /** 走到全长多少比例时恢复满粗 ✓（0.13 ⇒ 40 格的水波前 5 格是锥形 ✓） */
    static final double ROOT_SPAN = 0.13D;
    /** t5：一束几根（近看 / 远看）。 */
    private static final int STRANDS_NEAR = 10;
    private static final int STRANDS_FAR = 4;

    private WaterBeamVisuals() {
    }

    /**
     * @param tier   档位（4 = 龙啸，5 = 龙滅）
     * @param dir    光束方向（单位向量，几何从 pose 原点往前画 ✓）
     * @param length 这一次画多长（格；服务端算出来的真实长度 ✓）
     * @param age    动画年龄（tick）—— 每 4 tick 换一次 seed ⇒ 电弧一直在抖 ✓
     * @param fade   淡入淡出（0..1）
     * @param detail 近看（true）才有全部九根 / 分叉 / 末端爆闪 ✓
     * @param camera <b>pose 空间</b>里的相机位置 ✓（光带要正对镜头，必须知道镜头在哪 ✗）
     */
    static void render(VertexConsumer body, VertexConsumer glow, Matrix4f pose, int tier, Vec3 dir,
                       double length, double age, float fade, boolean detail, Vec3 camera) {
        render(body, glow, pose, tier, dir, length, age, fade, detail, camera,
                WaterSpellRules.radius(tier));
    }

    /**
     * 同上，但<b>粗细由调用方给</b> ✓ —— 水球链 t4/t5 的齐射水波（{@link TNWaterLanceEntity}）
     * 用的是自己的口径（{@code WaterGateRules.lanceRadius} ✓），不是那根老光柱的 15 格 ✗。
     *
     * @param radius 这一道水波的半径（格）
     */
    static void render(VertexConsumer body, VertexConsumer glow, Matrix4f pose, int tier, Vec3 dir,
                       double length, double age, float fade, boolean detail, Vec3 camera,
                       double radius) {
        if (length <= 0.0D || dir == null || fade <= 0.004F) {
            return;
        }
        if (radius <= 0.0D) {
            return;
        }
        Vec3 end = dir.scale(length);
        double camX = camera != null ? camera.x : 0.0D;
        double camY = camera != null ? camera.y : 0.0D;
        double camZ = camera != null ? camera.z : 0.0D;
        long seed = 0x5EEDL + (long)(age * 0.25D) * 7919L + tier * 131L;
        // 大光束面积大两个数量级，同样的 alpha 会糊成一片白 ✗ ⇒ 按半径收一点 ✓
        float alpha = fade * (float)Math.min(1.0D, 1.9D / Math.sqrt(Math.max(1.0D, radius)));

        boolean bundle = radius > 4.0D;
        int strands = bundle ? (detail ? STRANDS_NEAR : STRANDS_FAR) : 1;
        RandomSource rnd = RandomSource.create(seed);
        // 一束里根数越多、每根越暗 ⇒ 叠起来才是一束"细丝"，而不是一整块白 ✗
        float strandAlpha = alpha * (bundle ? 0.55F : 1.0F);
        for (int i = 0; i < strands; i++) {
            // ① 每根在圆截面里的落点（t4 只有一根 ⇒ 正中 ✓；面积均匀 ⇒ 开方 ✓）
            double spread = bundle ? radius * 0.72D * Math.sqrt(rnd.nextDouble()) : 0.0D;
            double angle = rnd.nextDouble() * Math.PI * 2.0D;
            Vec3 offset = WaterGeometry.radial(WaterSpellRules.right(dir),
                    WaterSpellRules.right(dir).cross(dir).normalize(), angle, spread);
            // ② 粗细：t4 一根顶满半径；t5 一束里每根只有半径的 6%（30 格里不到 1 格 ✓ 细丝才像电 ✗）
            float width = (float)(radius * (bundle ? 0.055D + rnd.nextDouble() * 0.020D : 0.85D));
            // ③ 路径：轻微起伏，两端收敛到轴上 ✓（每根幅度/相位都不同 ⇒ 十根互相穿插，不像排梳子 ✗）
            double amplitude = radius * (bundle ? 0.16D + rnd.nextDouble() * 0.14D : 0.45D);
            double[] ts = bundle ? TS_BUNDLE : TS_SINGLE;
            double[][] path = path(dir, length, offset, amplitude, ts, rnd);
            float[] mid = i % 2 == 0 ? MID : MID_DEEP;
            // ★ 2026-11-10 作者："根部要细细的，不然就很突兀" ⇒ 根部收细、再慢慢变粗、之后一样粗 ✓
            float[] taper = rootTaper(ts);
            LightningBoltGeometry.polylineTapered(glow, pose, path, taper, width,
                    strandAlpha * (i == 0 ? 1.0F : 0.8F), CORE, mid, GLOW, camX, camY, camZ);
            // ④ 分叉：只给近看的前两根（水不劈叉，太多会像真正的雷 ✗）
            if (detail && i < 2) {
                fork(glow, pose, path, width * 0.55F, strandAlpha * 0.6F, radius, rnd, camX, camY, camZ);
            }
        }

        // ⑤ 末端爆闪（近看才有；远看省一个圆盘 ✓）
        if (detail) {
            LightningBoltGeometry.flashTinted(glow, pose, end.x, end.y, end.z,
                    (float)Math.max(0.6D, radius * 0.55D), MID, alpha * 0.6F, camX, camY, camZ);
        }
    }

    /**
     * 一根<b>大致笔直</b>的折线 ✓：沿 {@code dir} 走到 {@code ts} 给出的那些位置，中间的点沿垂直方向抖一点。
     *
     * <p>为什么不直接用 {@link LightningBoltGeometry} 的 {@code boltTinted}：那个的中点位移是
     * 按<b>段长</b>抖的 ✗（48 格的光束会歪出好几格、像蛇 ✗），而且内部路径拿不到 ✗
     * —— 一束里的每根要能<b>共用同一条主轴的起伏</b>才像"一整道水波"✓。
     *
     * <p>{@code ts} 是"沿轴的位置（0..1）"✓ —— 根部刻意排得密（0 / .05 / .11），
     * 这样"从细到粗"那一段才短、才看得出来 ✓（均匀等分会把锥形拉满整段 ✗）。
     */
    private static double[][] path(Vec3 dir, double length, Vec3 offset, double amplitude,
                                   double[] ts, RandomSource rnd) {
        Vec3 right = WaterSpellRules.right(dir);
        Vec3 up = right.cross(dir).normalize();
        double phase = rnd.nextDouble() * Math.PI * 2.0D;
        double tilt = (rnd.nextDouble() - 0.5D) * amplitude;
        double[][] points = new double[ts.length][3];
        for (int i = 0; i < ts.length; i++) {
            double t = ts[i];
            // 两端收敛（sin 包络 ✓）：起点在阵口、终点在命中点，都不许飘 ✓
            double envelope = Math.sin(Math.PI * t);
            double wave = Math.sin(t * Math.PI * 2.0D + phase) * amplitude * envelope;
            Vec3 at = dir.scale(length * t)
                    .add(offset)
                    .add(right.scale(wave + tilt * envelope))
                    .add(up.scale(Math.cos(t * Math.PI * 1.5D + phase) * amplitude * 0.55D * envelope));
            points[i][0] = at.x;
            points[i][1] = at.y;
            points[i][2] = at.z;
        }
        return points;
    }

    /**
     * 根部粗细曲线 ✓：阵口处只有 10%，到 {@link #ROOT_SPAN} 处长到 100%，之后一直是 100% ✓。
     *
     * <p>作者 2026-11-10：「水波的根部应该从细到粗然后保持一样，也就是法阵那个根部要细细的，
     * 不然就很突兀」✗ —— 法阵口才 0.9 格，水波一冒出来就有 1.4 格宽，像从针眼里挤出一根柱子 ✗。
     */
    static float[] rootTaper(double[] ts) {
        float[] taper = new float[ts.length];
        for (int i = 0; i < ts.length; i++) {
            double k = Math.min(1.0D, ts[i] / ROOT_SPAN);
            // 平滑一点（不是直线 ⇒ 根部不会像个倒三角 ✗）
            double smooth = k * k * (3.0D - 2.0D * k);
            taper[i] = (float)(ROOT_MIN + (1.0D - ROOT_MIN) * smooth);
        }
        return taper;
    }

    /** 从折线中段岔出去的一根短须 ✓（跟着主轴的垂直基走，不会翻到奇怪的方向 ✗）。 */
    private static void fork(VertexConsumer glow, Matrix4f pose, double[][] path, float width,
                             float alpha, double radius, RandomSource rnd,
                             double camX, double camY, double camZ) {
        int at = 2 + rnd.nextInt(Math.max(1, path.length - 3));
        double[] base = path[Math.min(at, path.length - 2)];
        double angle = rnd.nextDouble() * Math.PI * 2.0D;
        double reach = radius * (0.55D + rnd.nextDouble() * 0.45D);
        double[] tip = {base[0] + Math.cos(angle) * reach,
                base[1] + (rnd.nextDouble() - 0.5D) * reach * 0.5D,
                base[2] + Math.sin(angle) * reach};
        double[][] fork = new double[4][3];
        for (int i = 0; i < 4; i++) {
            double t = i / 3.0D;
            fork[i][0] = base[0] + (tip[0] - base[0]) * t;
            fork[i][1] = base[1] + (tip[1] - base[1]) * t;
            fork[i][2] = base[2] + (tip[2] - base[2]) * t;
        }
        LightningBoltGeometry.polyline(glow, pose, fork, width, alpha, CORE, MID, GLOW,
                camX, camY, camZ);
    }
}
