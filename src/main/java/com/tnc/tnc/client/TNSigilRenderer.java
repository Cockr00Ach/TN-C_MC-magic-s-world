package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.fire.TNMeteorFallEntity;
import com.tnc.tnc.magic.fire.TNSkyfallEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 火系法阵的渲染器 —— <b>画真正的魔法阵，不是一圈火焰粒子</b>。
 *
 * <p>作者 2026-10-05 给参考图时明确说过：「不要就一圈火焰粒子代表法阵」，
 * 于是这里用几何体一笔一笔画。作者 2026-10-11 又给了一张<b>金色占卜法阵</b>参考图
 * （黑石底 ＋ 金线；见 {@code docs/art/refs/fire_sigil_reference.png}，已作为<b>火系法阵参考图</b>存档），
 * 并说「以上面的法阵修改目前的火系法阵」—— 现在这个构成就是照它来的（自上而下看）：
 *
 * <ol>
 *   <li><b>外缘</b>：一圈柔光晕（夜里那层"发着光"）＋ 粗主环 ＋ 内侧细环 ＋ 最外一道发丝环；
 *       正上下左右四个方向各一枚<b>大十字花饰</b>（参考图那四枚往外戳的十字／菱尖）</li>
 *   <li><b>外符文带</b>：主环与内环之间夹着一条环带，带内密密排着 <b>符文刻痕</b>
 *       —— 每道符文由 2~4 笔小短划／小弧／小菱拼成（几何体画不出真字形，
 *       但自上而下看，这一圈"字"的味道和参考图一致 ✓）；
 *       带上还嵌着 <b>八颗卫星圆</b>（每 45° 一枚：外环 ＋ 内环 ＋ 中央一朵十字花）</li>
 *   <li><b>八向轴线</b>：从中心一直拉到符文带，八个方向各一条</li>
 *   <li><b>八角星</b>：两枚方框套在一起（一枚顶点朝正方向、一枚转 45°）——
 *       参考图里那枚把八个卫星圆串起来的星 ✓</li>
 *   <li><b>内符文带</b>：中心星外围又一圈更密的符文（参考图里那第二圈"字"）</li>
 *   <li><b>中心</b>：内环群 ＋ 一枚小巧的八角星 ＋ 中心亮核，外带一圈散落的<b>星点</b></li>
 * </ol>
 *
 * <p>法阵是<b>水平</b>的（法线朝上）—— 地面法阵自上而下看是正圆 ✅，
 * 头顶法阵从下往上看是对称的环 ✅
 *
 * <p>{@code grand = true} 时就是"更复杂"的那一座（{@link TNMeteorFallEntity} 的陨星坠）：
 * 光晕多一圈、符文更密、卫星内的花纹更细 ✓
 *
 * <p>配色跟参考图一致：<b>金色线稿</b>，用四档金分层（亮金画主线、中金画环与尖饰、
 * 淡金画符文与内芯、橙金只在外圈光晕里压一点暖）—— 靠亮度差做金色的层次，
 * 走的还是自发光通道，夜里／洞里都亮。
 *
 * <p>性能：所有几何体都进 {@code WaterRenderTypes.geometry()} 这一个 QUADS 缓冲。
 * 细管一律 4 侧面、方框每条边只一根管、星点只画一两笔 ——
 * 半径 6 格的普通阵约 1200 个四边形、半径 15~18 格的大阵约 2600 个，量级和原来那版一致 ✓
 */
public final class TNSigilRenderer<T extends Entity> extends EntityRenderer<T> {

    /** 不会被真正贴图（几何体自带颜色），指向一张一定存在的原版贴图只为不触发缺图警告。 */
    private static final ResourceLocation PLACEHOLDER =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/particle/flame.png");

    // ---- 金色分层（参考图那种"金线 ＋ 淡金芯"）----
    /** 亮金：主环、八角星、中心核 —— 线稿里最实的那一档。 */
    private static final float[] MAIN = {1.00F, 0.83F, 0.38F};
    /** 中金：四向花饰、卫星圆外环、符文带导引环。 */
    private static final float[] MID = {1.00F, 0.90F, 0.52F};
    /** 淡金：符文、内芯、星点 —— 近看是浅金，远看融进主线的光里。 */
    private static final float[] PALE = {1.00F, 0.97F, 0.78F};
    /** 橙金：只在最外侧那圈柔光晕里用一点，压住"贴纸黄"的塑料感。 */
    private static final float[] BLOOM = {1.00F, 0.70F, 0.26F};

    private final double radius;
    private final boolean grand;

    public TNSigilRenderer(EntityRendererProvider.Context ctx, double radius, boolean grand) {
        super(ctx);
        this.radius = radius;
        this.grand = grand;
        shadowRadius = 0.0F;
    }

    @Override
    public boolean shouldRender(T entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        return entity.distanceToSqr(x, y, z) < 192.0D * 192.0D;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return PLACEHOLDER;
    }

    @Override
    public void render(T entity, float yaw, float partial, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        var out = buffers.getBuffer(WaterRenderTypes.geometry());
        Matrix4f pose = stack.last().pose();
        double age = entity.tickCount + partial;

        // 出场渐显：前 8 tick 从 0 涨到 1（免得"啪"地整座法阵凭空出现）
        float fade = (float) Math.min(1.0D, (entity.tickCount + partial) / 8.0D);

        // ⚠️ 中心必须是 **原点**，不能用 entity.position()。
        //    EntityRenderer 被调用之前，派发器已经做过
        //    poseStack.translate(x - camX, y - camY, z - camZ) ——
        //    也就是说**姿态的原点已经落在实体身上了**。
        //    这里再传一次世界坐标，等于偏移了整整一个世界坐标的距离，
        //    几何体会被画到天边去（作者 2026-10-05 实测：「为什么还是这样，没有变化」）
        //    —— 水球/火球两个渲染器都是按原点画的，照它们来 ✓
        drawSigil(out, pose, Vec3.ZERO, radius, grand, age, fade);
    }

    /**
     * 画一座法阵。<b>水平</b>放置（法线朝上），所有半径都写成 {@code R * 比例} 的形式，
     * 所以同一套构成在 6 格的小阵（天陨）和 15~17.5 格的大阵（炎魔龙之怒／太阳审判）上都能用 ✓
     *
     * @param grand 更复杂的那一座（陨星坠／太阳审判）
     * @param age   用于缓慢自转的相位
     * @param fade  整体透明度（出场渐显）
     */
    public static void drawSigil(VertexConsumer out, Matrix4f pose, Vec3 center, double radius,
                                 boolean grand, double age, float fade) {
        final double R = radius;

        // 三档复杂度：天陨（radius 6）／陨星坠（12.8）／太阳审判（15~17.5）。
        // 同一套构成在三档上分别用粗、中、细的密度 —— 免得 17 格的大阵铺出上万的面 ✗
        final boolean fine = R >= 11.0D;
        final boolean rich = grand && fine;

        // 旋转相位：符文带与内圈反向转 —— 参考图那种"机械感"（真字形转不了，用相位差代替）
        final double spinOut = age * 0.008D;
        final double spinIn = -age * 0.014D;

        // ================= 0) 外缘柔光晕 =================
        // 法阵外面再套 3~4 圈很淡、很宽的橙金环 —— 参考图里那层"金线在夜里发着光"的光晕。
        // 自上而下看是光圈，贴地看就是地面上淡淡的辉光 ✓
        // ⚠️ 透明度压在 0.12 以下：QUADS 是 alpha 混合，"上一层压一层"会越叠越白 ✗
        int bloom = rich ? 4 : 3;
        for (int b = 0; b < bloom; b++) {
            ring(out, pose, center, R * (1.034D + b * 0.030D), R * (0.022D + b * 0.032D),
                    BLOOM[0], BLOOM[1], BLOOM[2], (0.105F - b * 0.028F) * fade);
        }

        // ================= 1) 外缘三环 ＋ 四向大花饰 =================
        ring(out, pose, center, R * 1.000D, R * 0.030D, MAIN[0], MAIN[1], MAIN[2], 0.66F * fade);
        ring(out, pose, center, R * 0.958D, R * 0.008D, PALE[0], PALE[1], PALE[2], 0.40F * fade);
        ring(out, pose, center, R * 1.028D, R * 0.006D, MID[0], MID[1], MID[2], 0.30F * fade);

        // 四向大十字花饰（正上下左右）—— 参考图那四枚往外戳的十字／菱尖
        double filigreeLen = rich ? 0.150D : (fine ? 0.132D : 0.108D);
        for (int k = 0; k < 4; k++) {
            filigree(out, pose, center, k * Math.PI * 0.5D, R, filigreeLen,
                    MID[0], MID[1], MID[2], 0.70F * fade);
        }

        // ================= 2) 外符文带 ＋ 八颗卫星圆 =================
        // 带子的两条导引环（很淡 —— 只负责把这条带"框"出来）
        ring(out, pose, center, R * 0.944D, R * 0.005D, MID[0], MID[1], MID[2], 0.24F * fade);
        ring(out, pose, center, R * 0.790D, R * 0.005D, MID[0], MID[1], MID[2], 0.24F * fade);

        final double satOrbit = R * 0.868D;                 // 卫星圆心所在的半径（正落在这条带里）
        final double satRadius = R * (fine ? 0.081D : 0.078D);
        // 两枚卫星之间塞几个符文：格子宽度 = 2πr/总数，半径 15~18 格时约 1.6 格一格，
        // 再密单个"字"就糊成一团了 ✓
        final int glyphPerGap = fine ? (rich ? 6 : 5) : 3;
        // 符文上下占满带高的一半左右（带子 0.944~0.790，卫星圆正好撑满）——
        // 字太小就只剩一排"刻度"，太大又会顶到导引环 ✗
        final double bandHalf = R * 0.034D;
        for (int k = 0; k < 8; k++) {
            double a = spinIn + k * Math.PI * 0.25D;
            for (int m = 1; m <= glyphPerGap; m++) {
                double g = a + (m * (Math.PI * 0.25D)) / (glyphPerGap + 1);
                glyph(out, pose, center, g, R * 0.868D, R, k * 7 + m, bandHalf,
                        PALE[0], PALE[1], PALE[2], 0.48F * fade);
            }
            satellite(out, pose, center, a, satRadius, polar(center, a, satOrbit), R, spinIn,
                    rich, MID[0], MID[1], MID[2], PALE[0], PALE[1], PALE[2], fade);
        }

        // ================= 3) 八向轴线 =================
        // 从中心区一直拉到符文带 —— 参考图里把中心与八颗卫星连起来的那八条线
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI * 0.25D;
            tube(out, pose, polar(center, a, R * 0.290D), polar(center, a, R * 0.788D),
                    R * 0.0042D, MAIN[0], MAIN[1], MAIN[2], 0.34F * fade);
        }

        // ================= 4) 八角星：两枚方框套起来 =================
        // 顶点正好落在八颗卫星的内缘（外接半径 0.786R）、一枚朝正方向、一枚转 45° ——
        // 参考图里那枚把八个卫星圆串起来的大星 ✓
        square(out, pose, center, R * 0.786D, 0.0D, R * 0.0070D,
                MAIN[0], MAIN[1], MAIN[2], 0.56F * fade);
        square(out, pose, center, R * 0.786D, Math.PI * 0.25D, R * 0.0070D,
                MAIN[0], MAIN[1], MAIN[2], 0.56F * fade);

        // ================= 5) 中圈细环 ＋ 内符文带（中心星外面那圈"项圈"） =================
        // 参考图在外带与中心之间还夹着两道很淡的细环 ✓
        ring(out, pose, center, R * 0.700D, R * 0.0045D, MID[0], MID[1], MID[2], 0.22F * fade);
        ring(out, pose, center, R * 0.668D, R * 0.0045D, MID[0], MID[1], MID[2], 0.22F * fade);
        // 内圈符文带：一整圈环绕中心星的字，比中心环大一圈（0.600~0.495）✓
        ring(out, pose, center, R * 0.600D, R * 0.006D, MID[0], MID[1], MID[2], 0.30F * fade);
        ring(out, pose, center, R * 0.495D, R * 0.006D, MID[0], MID[1], MID[2], 0.30F * fade);
        int innerGlyphs = fine ? (rich ? 34 : 28) : 16;
        for (int k = 0; k < innerGlyphs; k++) {
            double a = spinOut + (k + 0.5D) * Math.PI * 2.0D / innerGlyphs;
            glyph(out, pose, center, a, R * 0.548D, R, k * 3 + 1, R * 0.024D,
                    PALE[0], PALE[1], PALE[2], 0.44F * fade);
        }
        // 项圈里再套一枚中等大小的八角星 —— 参考图中心是"星套星套星" ✓
        square(out, pose, center, R * 0.440D, spinIn, R * 0.0050D,
                PALE[0], PALE[1], PALE[2], 0.46F * fade);
        square(out, pose, center, R * 0.440D, spinIn + Math.PI * 0.25D, R * 0.0050D,
                PALE[0], PALE[1], PALE[2], 0.46F * fade);

        // ================= 6) 中心 =================
        ring(out, pose, center, R * 0.335D, R * 0.012D, MAIN[0], MAIN[1], MAIN[2], 0.58F * fade);
        ring(out, pose, center, R * 0.250D, R * 0.006D, MID[0], MID[1], MID[2], 0.44F * fade);
        // 中心那枚小巧的八角星（同样两枚方框叠起来）
        square(out, pose, center, R * 0.196D, spinIn * 0.5D, R * 0.0055D,
                PALE[0], PALE[1], PALE[2], 0.50F * fade);
        square(out, pose, center, R * 0.196D, spinIn * 0.5D + Math.PI * 0.25D, R * 0.0055D,
                PALE[0], PALE[1], PALE[2], 0.50F * fade);
        // 四向短线（星芒）
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI * 0.5D + Math.PI * 0.25D;
            tube(out, pose, polar(center, a, R * 0.040D), polar(center, a, R * 0.215D),
                    R * 0.0050D, PALE[0], PALE[1], PALE[2], 0.46F * fade);
        }
        // 中心亮核：一颗实心小圆 ＋ 一层白热芯
        WaterGeometry.disk(out, pose, center, UP, R * 0.046D,
                MAIN[0], MAIN[1], MAIN[2], 0.85F * fade);
        WaterGeometry.disk(out, pose, center.add(0.0D, 0.004D, 0.0D), UP, R * 0.022D,
                1.00F, 1.00F, 0.90F, 0.90F * fade);

        // ================= 7) 散落的星点 =================
        // 参考图上到处撒着那种小十字亮点（位置按编号算死 ⇒ 每帧一致、不闪 ✓）
        int sparks = rich ? 40 : (fine ? 28 : 12);
        for (int k = 0; k < sparks; k++) {
            sparkle(out, pose, center, R, k, PALE[0], PALE[1], PALE[2],
                    (0.30F + 0.28F * ((k * 5) % 3)) * fade, rich);
        }
    }

    // ------------------------------------------------------------------
    //  构成件
    // ------------------------------------------------------------------

    /**
     * 一格<b>符文</b> —— 参考图外圈那一整条"文字带"。
     *
     * <p>几何体画不出真字形，所以按格生成 2~4 笔小短划／小弧／小菱，笔数、朝向、长短都按格号变化
     * （不需要随机数：{@code seed} 一算就定死，每帧一致、不会闪 ✓）。
     * 关键是<b>每一道都封口</b>（笔画首尾相连），远看才像"字"，不像"刻度"✗
     *
     * @param cr   这一格的中心半径
     * @param half 单个"字"的径向半高（外圈带比内圈带大 —— 字要占满带高才像文字）
     */
    private static void glyph(VertexConsumer out, Matrix4f pose, Vec3 center, double a, double cr,
                              double R, int seed, double half,
                              float red, float green, float blue, float alpha) {
        double p0 = cr - half;
        double p1 = cr + half;
        int marks = 2 + (seed % 3);                 // 2~4 笔
        int variant = (seed / 3) % 4;
        for (int m = 0; m < marks; m++) {
            int sel = (variant + m) % 4;
            double da = 0.30D + 0.06D * ((seed * 5 + m * 3) % 4);
            double lo = p0 + (p1 - p0) * (0.08D + 0.20D * (((seed + m * 2) % 5)));
            double hi = lo + (p1 - p0) * (0.42D + 0.12D * (((seed * 3 + m) % 3)));
            if (hi > p1) {
                hi = p1;
            }
            if (sel == 0) {
                // 立笔：径向一道短划
                tube(out, pose, polar(center, a, lo), polar(center, a, hi),
                        R * 0.0060D, red, green, blue, alpha);
            } else if (sel == 1) {
                // 横笔：沿圆周一小段
                arc(out, pose, center, (lo + hi) * 0.5D, R * 0.0060D,
                        a - da * 0.5D, a + da * 0.5D, red, green, blue, alpha);
            } else if (sel == 2) {
                // 折笔：两道短划拼成"∠"
                double am = a + da * 0.20D;
                tube(out, pose, polar(center, a - da * 0.15D, lo), polar(center, am, (lo + hi) * 0.55D),
                        R * 0.0055D, red, green, blue, alpha);
                tube(out, pose, polar(center, am, (lo + hi) * 0.55D), polar(center, a + da * 0.15D, hi),
                        R * 0.0055D, red, green, blue, alpha);
            } else {
                // 菱点：一小颗菱形（参考图带子里那些"标点"）
                double mid = (lo + hi) * 0.5D;
                Vec3 up = polar(center, a, mid + (hi - lo) * 0.45D);
                Vec3 dn = polar(center, a, mid - (hi - lo) * 0.45D);
                Vec3 cw = polar(center, a + da * 0.45D, mid);
                Vec3 ccw = polar(center, a - da * 0.45D, mid);
                tube(out, pose, up, cw, R * 0.0055D, red, green, blue, alpha);
                tube(out, pose, cw, dn, R * 0.0055D, red, green, blue, alpha);
                tube(out, pose, dn, ccw, R * 0.0055D, red, green, blue, alpha);
                tube(out, pose, ccw, up, R * 0.0055D, red, green, blue, alpha);
            }
        }
    }

    /**
     * 一叶<b>四向大十字花饰</b> —— 参考图正上下左右那四枚往外戳的金色装饰。
     *
     * <p>从主环内侧起笔：底座一段横杆，正中一枚菱，再拉出一根长轴，
     * 轴端一枚实心菱尖，靠尖的两侧各一小勾 —— 远看就是十字 ＋ 箭头的味道 ✓
     */
    private static void filigree(VertexConsumer out, Matrix4f pose, Vec3 center, double a, double R,
                                 double len, float red, float green, float blue, float alpha) {
        double rIn = R * 0.930D;
        double rBase = R * 0.985D;
        double rMid = rIn + R * len * 0.55D;
        double rTip = rIn + R * len;
        // 底座横杆（尖饰的"底座"，跨在主环上）
        tube(out, pose, polar(center, a - R * 0.052D / rBase, rBase),
                polar(center, a + R * 0.052D / rBase, rBase),
                R * 0.0065D, red, green, blue, alpha);
        // 主环上的一枚小菱
        double rd = R * 0.995D;
        tube(out, pose, polar(center, a, rd + R * 0.030D), polar(center, a + R * 0.022D / rd, rd),
                R * 0.0055D, red, green, blue, alpha);
        tube(out, pose, polar(center, a + R * 0.022D / rd, rd), polar(center, a, rd - R * 0.030D),
                R * 0.0055D, red, green, blue, alpha);
        tube(out, pose, polar(center, a, rd - R * 0.030D), polar(center, a - R * 0.022D / rd, rd),
                R * 0.0055D, red, green, blue, alpha);
        tube(out, pose, polar(center, a - R * 0.022D / rd, rd), polar(center, a, rd + R * 0.030D),
                R * 0.0055D, red, green, blue, alpha);
        // 尖轴（两段，越往外越细）
        tube(out, pose, polar(center, a, rBase), polar(center, a, rMid),
                R * 0.0070D, red, green, blue, alpha);
        tube(out, pose, polar(center, a, rMid), polar(center, a, rTip),
                R * 0.0050D, red, green, blue, alpha);
        // 轴端实心菱尖
        Vec3 up = polar(center, a, rTip + R * 0.014D);
        Vec3 dn = polar(center, a, rTip - R * 0.014D);
        Vec3 cw = polar(center, a + 0.17D * (R / rTip), rTip);
        Vec3 ccw = polar(center, a - 0.17D * (R / rTip), rTip);
        tube(out, pose, up, cw, R * 0.0045D, red, green, blue, alpha);
        tube(out, pose, cw, dn, R * 0.0045D, red, green, blue, alpha);
        tube(out, pose, dn, ccw, R * 0.0045D, red, green, blue, alpha);
        tube(out, pose, ccw, up, R * 0.0045D, red, green, blue, alpha);
        // 靠尖的两侧小勾
        double hook = R * 0.018D;
        for (int s = -1; s <= 1; s += 2) {
            tube(out, pose, polar(center, a + s * 0.20D * (R / rMid), rMid),
                    polar(center, a + s * 0.32D * (R / rMid), rMid + hook),
                    R * 0.0040D, red, green, blue, alpha);
        }
    }

    /**
     * 一枚<b>卫星圆</b> —— 参考图里沿外圈分布的那八颗"小世界"。
     *
     * <p>外面一枚粗环、里面一枚细环，环心一朵十字花；{@code rich} 的那座再多一圈内环 ✓
     */
    private static void satellite(VertexConsumer out, Matrix4f pose, Vec3 center, double a, double sr,
                                  Vec3 at, double R, double spinIn, boolean rich,
                                  float mr, float mg, float mb, float pr, float pg, float pb, float fade) {
        ringAt(out, pose, at, sr, sr * 0.17D, mr, mg, mb, 0.62F * fade);
        ringAt(out, pose, at, sr * 0.74D, sr * 0.08D, pr, pg, pb, 0.40F * fade);
        if (rich) {
            ringAt(out, pose, at, sr * 0.44D, sr * 0.07D, pr, pg, pb, 0.30F * fade);
        }
        // 环心的十字花（四笔短划 —— 比实心圆便宜，远看也是"一点"）
        double c = sr * 0.30D;
        for (int t = 0; t < 4; t++) {
            double b0 = spinIn + t * Math.PI * 0.5D;
            tube(out, pose, polar(at, b0, c * 0.25D), polar(at, b0, c),
                    sr * 0.10D, pr, pg, pb, 0.52F * fade);
        }
    }

    /**
     * 一枚<b>方框</b>（外接半径 {@code side}、转 {@code rot} 角）—— 两枚方框叠起来就是八角星。
     *
     * <p>⚠️ 每条边只画<b>一根</b>直管（直线不需要切段）—— 四根管 16 个四边形，
     * 比"每边切 6 段再配四枚角环"省一个数量级 ✓
     */
    private static void square(VertexConsumer out, Matrix4f pose, Vec3 center, double side, double rot,
                               double width, float red, float green, float blue, float alpha) {
        for (int s = 0; s < 4; s++) {
            double a0 = rot + s * Math.PI * 0.5D;
            double a1 = a0 + Math.PI * 0.5D;
            tube(out, pose, polar(center, a0, side), polar(center, a1, side),
                    width, red, green, blue, alpha);
        }
    }

    /**
     * 一颗<b>星点</b> —— 参考图上散落的小十字亮斑。
     *
     * <p>位置由 {@code seed} 算死（两个互质的乘子打散），所以每帧都在同一个地方、不会闪 ✓
     */
    private static void sparkle(VertexConsumer out, Matrix4f pose, Vec3 center, double R, int seed,
                                float red, float green, float blue, float alpha, boolean cross) {
        double h = ((seed * 7919 + 104729) % 1000) / 1000.0D;
        double v = ((seed * 6151 + 40009) % 997) / 997.0D;
        double rr = R * (0.12D + 0.86D * h);
        Vec3 p = polar(center, v * Math.PI * 2.0D, rr);
        double s = R * (0.009D + 0.007D * v);
        tube(out, pose, p.add(-s, 0.0D, 0.0D), p.add(s, 0.0D, 0.0D),
                R * 0.0035D, red, green, blue, alpha);
        if (cross) {
            tube(out, pose, p.add(0.0D, 0.0D, -s), p.add(0.0D, 0.0D, s),
                    R * 0.0035D, red, green, blue, alpha);
        }
    }

    // ------------------------------------------------------------------
    //  小工具（都画在**水平面**上：法阵躺平，法线朝上）
    // ------------------------------------------------------------------

    /** 水平面上、距中心 {@code r}、方位角 {@code a} 的一点。 */
    private static Vec3 polar(Vec3 center, double a, double r) {
        return center.add(Math.cos(a) * r, 0.0D, Math.sin(a) * r);
    }

    /** 一圈正环（同心圆）。 */
    private static void ring(VertexConsumer out, Matrix4f pose, Vec3 center, double r, double width,
                             float red, float green, float blue, float alpha) {
        ringAt(out, pose, center, r, width, red, green, blue, alpha);
    }

    /** 以任意点为心的一圈环 —— 卫星圆用得多。 */
    private static void ringAt(VertexConsumer out, Matrix4f pose, Vec3 center, double r, double width,
                               float red, float green, float blue, float alpha) {
        WaterGeometry.ringColor(out, pose, center, UP, r, width, red, green, blue, alpha);
    }

    /** 一小段圆弧（用若干小段管子拼）—— 符文带的横笔、花饰的小勾都用它。 */
    private static void arc(VertexConsumer out, Matrix4f pose, Vec3 center, double r, double width,
                            double a0, double a1, float red, float green, float blue, float alpha) {
        // 段数跟着弧长走：短笔 2 段就够、长弧多切几段 —— 不然 17 格的大阵上
        // 每道符文都固定切 4 段，几万个小四边形白烧 ✓
        int seg = (int) Math.round(Math.abs(a1 - a0) * r / 0.09D);
        if (seg < 2) {
            seg = 2;
        }
        if (seg > 12) {
            seg = 12;
        }
        for (int i = 0; i < seg; i++) {
            double t0 = a0 + (a1 - a0) * i / seg;
            double t1 = a0 + (a1 - a0) * (i + 1) / seg;
            tube(out, pose, polar(center, t0, r), polar(center, t1, r), width, red, green, blue, alpha);
        }
    }

    private static void tube(VertexConsumer out, Matrix4f pose, Vec3 from, Vec3 to, double width,
                             float red, float green, float blue, float alpha) {
        // ⚠️ WaterGeometry.tube 里对 axis 做了 normalize：起终点重合会算出 NaN 顶点。
        //    符文/星点那种"由公式算出来的短笔"有可能撞上，这里先挡一道 ✓
        if (from.distanceToSqr(to) < 1.0E-6D) {
            return;
        }
        WaterGeometry.tube(out, pose, from, to, width, red, green, blue, alpha);
    }

    /** 法阵的法线：永远朝上（法阵是躺平的）。 */
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

    /** 给两个法阵实体类型用的两个实例（一个普通、一个"更复杂"）。 */
    public static TNSigilRenderer<TNSkyfallEntity> skyfall(EntityRendererProvider.Context ctx) {
        return new TNSigilRenderer<>(ctx, TNSkyfallEntity.SIGIL_RADIUS, false);
    }

    public static TNSigilRenderer<TNMeteorFallEntity> meteorFall(EntityRendererProvider.Context ctx) {
        return new TNSigilRenderer<>(ctx, TNMeteorFallEntity.SIGIL_RADIUS, true);
    }
}
