package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.fire.FireSpellRules;
import com.tnc.tnc.magic.fire.TNFireBoltEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 火球的渲染器 —— 一颗<b>拖长焰尾的彗星</b>（作者 2026-10-05 参照图定稿）。
 *
 * <h2>形态要求（照作者给的参考图）</h2>
 * <pre>
 *   尾部（细、暗、飘散）  ←────────  焰体（渐宽再收细）  ←──  白热核心（最亮、在最前）
 * </pre>
 * 参考图里是一颗**被拉长的火流星**：
 * <ul>
 *   <li>最前面是一团<b>白热核</b>（白黄、最亮、几乎不透）</li>
 *   <li>焰体<b>不是球</b>，是从核心向后拖出去的锥体：先略微涨到最宽，再一路收细到看不见</li>
 *   <li>焰体外面有几条<b>螺旋缠绕的火舌</b>，越往后越飘散</li>
 *   <li>颜色沿长度走：白黄 → 亮橙 → 橙 → 深橙红，透明度同步衰减</li>
 * </ul>
 *
 * <h2>为什么上一版不对</h2>
 * 上一版是"一颗球 + 三条细管 + 一圈粒子"，看着仍然像<b>球外面挂了点东西</b> ✗。
 * 关键差别是：参考图的<b>主体是那条焰尾</b>，球只是它的头部。
 * 所以这一版把焰尾做成**正片主几何**（{@link #flameBody}），核心只是收口的那一团
 * （{@link #coreBlob}），粒子退成点缀。
 *
 * <h2>为什么能直接用水系那两个类</h2>
 * {@code WaterGeometry} / {@code WaterRenderTypes} 是<b>包内可见</b>的通用工具
 * （{@code quad} / {@code radial} 都带颜色参数，只因当年为水法而写才沿用这个名字），
 * 本渲染器与它们同在 {@code com.tnc.tnc.client} 包，直接复用 ✓
 * 渲染通道用的是自发光着色器（{@code RENDERTYPE_LIGHTNING_SHADER}），洞里也亮。
 */
public final class TNFireBoltRenderer extends EntityRenderer<TNFireBoltEntity> {

    /** 不会被真正贴图（几何体自带颜色），指向一张一定存在的原版贴图只为不触发缺图警告。 */


    /** 我们自绘的**赤红像素龙头**贴图 ✓（32×32 ✓）。 */
    private static final ResourceLocation DRAGON_HEAD =
            ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/fire_dragon_head.png");

    private static final ResourceLocation PLACEHOLDER =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/particle/flame.png");

    /** 焰尾有多长（= 核心半径的多少倍）。参考图里尾巴远长于头部，所以给得大。 */
    private static final double TAIL_LENGTH_FACTOR = 8.5D;

    /** 焰体沿长度切多少段（越大越平滑，代价是顶点数）。 */
    private static final int BODY_SLICES = 18;
    /** 焰体一圈切多少边。 */
    private static final int BODY_SIDES = 16;
    /** 几条螺旋火舌。 */
    private static final int WISPS = 5;
    /** 火舌沿长度切多少段 / 一圈几边（比主体省，因为它们本来就细碎）。 */
    private static final int WISP_SLICES = 14;
    private static final int WISP_SIDES = 7;

    public TNFireBoltRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.0F;
    }

    @Override
    public boolean shouldRender(TNFireBoltEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        return entity.distanceToSqr(x, y, z) < 192.0D * 192.0D;
    }

    @Override
    public ResourceLocation getTextureLocation(TNFireBoltEntity entity) {
        return PLACEHOLDER;
    }

    @Override
    public void render(TNFireBoltEntity entity, float yaw, float partial, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        var out = buffers.getBuffer(WaterRenderTypes.geometry());
        Matrix4f pose = stack.last().pose();

        Vec3 dir = entity.safeDirection();
        Vec3 right = FireSpellRules.right(dir);
        Vec3 up = right.cross(dir).normalize();

        // 成形阶段整体长大（作者要求"先形成一个球形再发射出去"）；
        // 飞过射程 65% 之后逐渐消失（作者要求"离开一定距离后逐渐消失"）。
        // 两件事都作用在"这颗球现在多大 + 多亮"上，所以在这里一次算完。
        float fade = entity.fade(partial);
        if (fade <= 0.02F) {
            return;                                        // 已经飞到头，整颗收掉
        }
        double radius = entity.radius() * entity.formProgress(partial) * (0.35D + 0.65D * fade);
        if (radius < 0.02D) {
            return;
        }
        double age = entity.tickCount + partial;

        // 熔岩火球（t3）与熔岳天倾砸下来的那些：**黑岩外壳 + 熔岩裂缝**（照作者参照图）。
        // 其余档位仍是彗星焰尾。
        // 射线链 t1/t2：作者 2026-10-05 第 2 条「射线是一段长度有限的线条，
        // 可以理解为是长条状的火球」✗ ⇒ 画成**一根直的火舌**（不是彗星 ✓）
        if ("sun_ray".equals(entity.spellPath()) || "blast_ray".equals(entity.spellPath())) {
            // ⚠️ 作者 2026-10-05：「把 t2 的射线同步下，但颜色深一点」✓
            //    ⇒ t1/t2 **同一个造型** ✓，只有配色深浅不同 ✓（blast_ray 走深色 ✓）
            rayLance(out, pose, dir, right, up, age, fade,
                    "blast_ray".equals(entity.spellPath()));
            return;
        }

        // 射线链 t3「火龙术」：**整条龙由 TNDragonParticles 的粒子构成** ✓
        //   ⚠️ 作者 2026-10-05：「把龙后面的圆环即法阵删了」✗
        //   ⇒ 这里**什么都不画** ✓（连成形阶段的法阵也删了 ✓，几何体为零 ✓）
        if ("fire_dragon".equals(entity.spellPath())) {
            // ★ 2026-10-05：作者「龙头要朝向前面，而且龙头怎么说二维的」✓
            //   ⇒ 交叉双片是"永远面向摄像机"的取巧做法 ✗，天生是纸片 ✗
            //   ⇒ 改成**真正的方块龙头** ✓：头骨 + 前伸的吻 + 下颌 + 两只角，
            //     一共 5 个**长方体** ✓（方块拼的 ✗，不是光滑曲面 ✓）
            //     侧面（±right 面）贴我们的赤红像素贴图 ✓，其余面取贴图上一块实色 ✓
            float grow = (float) (0.30D + 0.70D * entity.formProgress(partial));
            float k = 3.0F * grow;                     // 整体尺寸：满成形约 3 格 ✓
            VertexConsumer hv = buffers.getBuffer(
                    net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(DRAGON_HEAD));
            // 头骨（略靠后 ✓）
            dragonBox(hv, pose, dir, up, right, -0.10F, 0.00F, 0.00F, 1.05F, 0.95F, 1.00F, k);
            // 吻（**前伸** ⇒ 一眼看出头朝前 ✓）
            dragonBox(hv, pose, dir, up, right, 0.95F, -0.12F, 0.00F, 1.05F, 0.55F, 0.66F, k);
            // 下颌（吻下方、稍短 ✓）
            dragonBox(hv, pose, dir, up, right, 0.72F, -0.52F, 0.00F, 0.85F, 0.26F, 0.58F, k);
            // 两只角（后上方、左右分开 ✓）
            dragonBox(hv, pose, dir, up, right, -0.55F, 0.78F, 0.36F, 0.55F, 0.62F, 0.22F, k);
            dragonBox(hv, pose, dir, up, right, -0.55F, 0.78F, -0.36F, 0.55F, 0.62F, 0.22F, k);
            return;
        }

        if (FireSpellRules.isLavaRock(entity.spellPath())) {
            lavaRock(out, pose, dir, right, up, radius, age, fade);
            return;
        }

        double length = radius * TAIL_LENGTH_FACTOR;

        // 画法顺序 = 从"最外层/最暗"到"最亮"，让白热核最后压上去。
        // （渲染通道只写颜色不写深度，所以后画的会盖在前面的上面）
        for (int w = 0; w < WISPS; w++) {
            wisp(out, pose, dir, right, up, radius, length, age, w, fade);
        }
        flameBody(out, pose, dir, right, up, radius, length, age, fade);
        coreBlob(out, pose, dir, right, up, radius, age, fade);
    }


    // ------------------------------------------------------------------
    //  射线链 t1/t2：一根**直的火舌**（作者要的"长条状的火球"）
    // ------------------------------------------------------------------

    /**
     * 射线链 t1/t2 的「条状火球」✓
     *
     * <p>作者 2026-10-05 第 2 条：「t1 和 t2 中的射线是一段长度有限的线条，
     * 你可以理解为是长条状的火球」✓
     * ⇒ 不像彗星那样"一个球 + 一条飘忽的尾巴"✗，而是**一根笔直的火舌** ✓：
     * 尖端白热、沿轴一节节变暗变细，全长约 {@link #LANCE_LENGTH} 倍半径 ✓
     * （颜色走像素色板 ⇒ 一节一个色块、硬边 ✓，见 {@link PixelFlame} ✓）
     */
    private static void rayLance(VertexConsumer out, Matrix4f pose, Vec3 dir, Vec3 right, Vec3 up,
                                 double age, float fade, boolean deep) {
        if (fade <= 0.02F) {
            return;
        }
        // ⚠️ 作者 2026-10-05：①「长度改为 1.5 格」②「美化下建模」
        //    ③「把 t2 的射线同步下，但颜色深一点」✓
        //
        //    造型取自参照图（外圈暗红外壳 + 内芯白热 ✓），但**不是一根直棍** ✗：
        //      · 前端收尖、尾端收细 ⇒ 一截**水滴形的火舌** ✓
        //      · 侧面随 age 微微起伏 ⇒ 像素风也能看出"在烧"✓
        //      · 三束绕轴的小火舌 ⇒ 轮廓不呆板 ✓
        //      · 一根细白热内芯贯穿 + 前端伸出 ✓
        final double half = 1.5D;          // 总长 3.0 格 ✓（作者 2026-10-05：射线长度 ×2 ✗）
        final double shell = 0.125D;       // 粗 1/4 格 ✓
        final int sides = 14;
        final int segments = 7;
        // 深色版（t2）：整条往色板后面挪两档 ⇒ 更暗更红 ✓
        final double shade = deep ? 0.30D : 0.00D;
        float[] color = new float[4];

        Vec3 prevCenter = null;
        double prevR = 0.0D;
        for (int s = 0; s <= segments; s++) {
            double t = s / (double) segments;                 // 0 = 前端 ✓ 1 = 尾端 ✓
            double along = half - t * 2.0D * half;
            // 前 18% 收尖 / 后 28% 收细 ⇒ 水滴形 ✓
            double taper;
            if (t < 0.18D) {
                taper = 0.52D + 0.48D * (t / 0.18D);
            } else if (t > 0.72D) {
                taper = 1.0D - 0.58D * ((t - 0.72D) / 0.28D);
            } else {
                taper = 1.0D;
            }
            // 侧面起伏（像素风：色块之间硬边 ✓，不是光滑渐变 ✓）
            double wobble = 1.0D + 0.10D * Math.sin(age * 0.85D + t * 8.5D);
            double r = shell * taper * wobble;
            Vec3 center = dir.scale(along);
            if (prevCenter != null && prevR > 1.0E-4D && r > 1.0E-4D) {
                PixelFlame.flat(shade + 0.40D + t * 0.46D, color);
                ring(out, pose, right, up, prevCenter, prevR, center, r, color, sides);
            }
            prevCenter = center;
            prevR = r;
        }

        // 白热内芯：一根细亮柱，前端略伸出 ✓（参照图里最亮的那条 ✓）
        PixelFlame.flat(0.0D, color);
        ring(out, pose, right, up, dir.scale(half * 0.96D), shell * 0.44D,
                dir.scale(-half * 0.80D), shell * 0.40D, color, 10);

        // 三束绕轴小火舌（像素风：短、硬、一段一个色块 ✓）
        for (int w = 0; w < 3; w++) {
            double a = age * 0.30D + w * (Math.PI * 2.0D / 3.0D);
            Vec3 prev = null;
            double prevW = 0.0D;
            for (int s = 0; s <= 3; s++) {
                double t = 0.20D + s * 0.22D;
                double along = half - t * 2.0D * half;
                double off = shell * (1.18D + 0.30D * Math.sin(age * 0.7D + w * 1.7D));
                Vec3 c = dir.scale(along)
                        .add(WaterGeometry.radial(right, up, a + t * 1.6D, off));
                double r = shell * (0.20D - 0.05D * s);
                if (prev != null && prevW > 1.0E-4D && r > 1.0E-4D) {
                    PixelFlame.flat(shade + 0.46D + s * 0.14D, color);
                    ring(out, pose, right, up, prev, prevW, c, r, color, 6);
                }
                prev = c;
                prevW = r;
            }
        }
    }
    // ------------------------------------------------------------------
    //  焰体：从核心往后拖出去的渐变锥（参考图的主体）
    // ------------------------------------------------------------------

    private static void flameBody(VertexConsumer out, Matrix4f pose, Vec3 dir, Vec3 right, Vec3 up,
                                  double radius, double length, double age, float fade) {
        float[] color = new float[4];
        Vec3 prevCenter = null;
        double prevR = 0.0D;
        for (int s = 0; s <= BODY_SLICES; s++) {
            double t = s / (double) BODY_SLICES;
            Vec3 center = axisPoint(dir, right, up, t, length, radius, age, 1.0D);
            double r = profile(t) * radius;
            if (prevCenter != null && prevR > 1.0E-4D && r > 1.0E-4D) {
                flameColor(t, 0.80F * fade, color);
                ring(out, pose, right, up, prevCenter, prevR, center, r, color, BODY_SIDES);
            }
            prevCenter = center;
            prevR = r;
        }
    }

    // ------------------------------------------------------------------
    //  螺旋火舌：缠绕在焰体外、越往后越飘散
    // ------------------------------------------------------------------

    private static void wisp(VertexConsumer out, Matrix4f pose, Vec3 dir, Vec3 right, Vec3 up,
                             double radius, double length, double age, int index, float fade) {
        float[] color = new float[4];
        Vec3 prevCenter = null;
        double prevR = 0.0D;
        double phase = index * (Math.PI * 2.0D / WISPS);
        for (int s = 0; s <= WISP_SLICES; s++) {
            double t = 0.06D + (s / (double) WISP_SLICES) * 0.94D;
            // 沿轴的位置 + 绕轴的螺旋偏移：角度随 t 与时间转，形成"火舌在缠"
            double angle = age * 0.30D + phase + t * 2.4D + index * 0.7D;
            double off = profile(t) * radius * (0.55D + 0.25D * Math.sin(age * 0.5D + index));
            Vec3 center = axisPoint(dir, right, up, t, length, radius, age, 1.35D)
                    .add(WaterGeometry.radial(right, up, angle, off));
            double r = profile(t) * radius * 0.40D * (1.0D - 0.35D * Math.sin(age * 0.8D + index * 1.3D));

            if (prevCenter != null && prevR > 1.0E-4D && r > 1.0E-4D) {
                // 火舌比主体更亮更透 —— 它是"蹿起来的火苗"
                flameColor(t * 0.85D, 0.55F * fade, color);
                color[0] = Math.min(1.0F, color[0] * 1.05F);
                color[2] = Math.min(1.0F, color[2] * 1.10F);
                ring(out, pose, right, up, prevCenter, prevR, center, r, color, WISP_SIDES);
            }
            prevCenter = center;
            prevR = r;
        }
    }

    // ------------------------------------------------------------------
    //  核心：白热的三层球（焰体从它后面接出去）
    // ------------------------------------------------------------------

    private static void coreBlob(VertexConsumer out, Matrix4f pose, Vec3 dir, Vec3 right, Vec3 up,
                                 double radius, double age, float fade) {
        // 燃烧的呼吸感
        float flicker = (float) (0.85D + 0.15D * Math.sin(age * 0.6D));
        sphere(out, pose, dir, right, up, radius * 1.00D, 1.00F, 0.66F, 0.20F, 0.55F * flicker * fade);
        sphere(out, pose, dir, right, up, radius * 0.74D, 1.00F, 0.86F, 0.42F, 0.78F * flicker * fade);
        sphere(out, pose, dir, right, up, radius * 0.44D, 1.00F, 0.97F, 0.82F, 0.95F * fade);
    }

    // ------------------------------------------------------------------
    //  熔岩岩球：一颗颗不规则岩块拼成的球（作者 2026-10-05 定稿）
    // ------------------------------------------------------------------

    /** 岩壳网格：切多少圈 / 多少边（岩块就是从这些格子聚出来的）。 */
    private static final int ROCK_LAT = 24;
    private static final int ROCK_LON = 40;
    /**
     * 岩块数量 = {@code SEED_LAT × SEED_LON} 个种子点。
     *
     * <p>种子放在<b>抖动过的经纬网格</b>上 —— 网格保证「每块差不多同等大小」，
     * 抖动保证「形状不规则、不是整齐方格」✓（作者 2026-10-05 的两条要求正好对应这两点）
     */
    private static final int SEED_LAT = 5;
    private static final int SEED_LON = 7;
    private static final int SEED_COUNT = SEED_LAT * SEED_LON;
    /** 每块岩石的半径差异（±）—— 让它们各自鼓出/缩进，像一颗颗独立岩块而不是一层壳。 */
    private static final double CHUNK_LUMP = 0.10D;
    /**
     * 「离缝多近算缝」—— 用<b>最近与次近种子</b>的点积差做连续量：
     * 差 &lt; 这个值就认为贴上等距面（= 两块岩块之间的缝）。
     *
     * <p>⚠️ 不能用"整格二选一"（相邻格子不同块就算缝）：那样 1 格宽的环会把
     * 有效面积吃掉一大半 —— 实测 35 块时岩浆占到 <b>84%</b>，整颗球反过来变成熔岩 ✗
     */
    private static final double EDGE_TOL = 0.020D;
    /** 有多少比例的**整块岩块**是熔融的（随机挑块，不是随机挑格子 —— 否则又会出方块）。 */
    private static final double MOLTEN_CHUNK_CHANCE = 0.22D;
    /** 熔融处凹进去多少 —— 岩块之间才有"缝"的深度感。 */
    private static final double LAVA_RECESS = 0.94D;

    // ---- 下面这些表都是**预计算一次**的（见 buildRockPattern），每帧只查表 ----
    /** 每格属于哪一块岩块。 */
    private static final int[] CELL = new int[ROCK_LAT * ROCK_LON];
    /** 每块岩块自己的半径倍率与深浅。 */
    private static final double[] CHUNK_RADIUS = new double[SEED_COUNT];
    private static final float[] CHUNK_SHADE = new float[SEED_COUNT];
    /** 这一块岩块整体是不是熔融的（随机挑**块** → 随机区域也是不规则多边形，不会出方块）。 */
    private static final boolean[] CHUNK_MOLTEN = new boolean[SEED_COUNT];
    /** 种子点在球面上的单位向量（局部坐标：极轴 = 飞行方向）。 */
    private static final double[] SEED = new double[SEED_COUNT * 3];
    /**
     * <b>顶点</b>级的归属与"热度"（0 = 纯岩石，1 = 纯岩浆）。
     *
     * <p>为什么要顶点级的：颜色如果在格子中心算、整格一个常数，
     * 24×40 的网格看上去就是<b>一格格的色块</b>（像马赛克）✗。
     * 顶点级 + 四边形内插值之后，岩块内部与缝上都是平滑过渡 ✓
     * （半径仍然按<b>格</b>取，所以一块块岩石之间还是有硬台阶 —— 那正是"一块块"的来源）
     */
    private static final int VLAT = ROCK_LAT + 1;
    private static final int VLON = ROCK_LON + 1;
    private static final int[] VCELL = new int[VLAT * VLON];
    private static final float[] VHEAT = new float[VLAT * VLON];

    static {
        buildRockPattern();
    }

    /**
     * 画一颗<b>熔岩岩球</b>：一颗颗不规则的、差不多同等大小的岩块拼成的球，
     * 块与块之间以及表面随机分布着岩浆（作者 2026-10-05 定稿）。
     *
     * <h2>演进（都是实测反馈推动的）</h2>
     * <ol>
     *   <li>第一版：经纬网格上切"岩板"，缝按格子算 → 只占 56% 面积，整颗球变熔岩 ✗</li>
     *   <li>第二版：岩壳画满 + 缝上叠细亮条 → 岩壳太棕、缝太规整像棋盘 ✗</li>
     *   <li><b>现在</b>：种子抖动网格 + Voronoi 归块 → 岩块<b>大小均匀、形状不规则</b>；
     *       每块还有自己的半径与深浅，所以是一颗颗<b>独立</b>的岩块，不是一层壳 ✓</li>
     * </ol>
     *
     * <p>关于"球型不好做可以做成正方体"：球面这套参数化在
     * {@code TNWaterBoltRenderer} 里早就跑通了，直接复用即可，<b>不用退成正方体</b> ✓
     */
    private static void lavaRock(VertexConsumer out, Matrix4f pose, Vec3 dir, Vec3 right, Vec3 up,
                                 double radius, double age, float fade) {
        // 岩壳（岩块 + 岩浆）
        rockShell(out, pose, dir, right, up, radius, fade);

        // 边缘舔上来的火苗：绕球一圈，长度各自抖（"在烧"的关键）
        // ⚠️ 用**锥形**（根部粗、尖端细）而不是等粗的管子 —— 等粗的会像一圈"黄纸片" ✗
        for (int k = 0; k < 7; k++) {
            double angle = age * 0.22D + k * Math.PI * 2.0D / 7.0D;
            Vec3 base = WaterGeometry.radial(right, up, angle, radius * 0.97D);
            double flick = 0.45D + 0.55D * Math.sin(age * 0.85D + k * 2.1D);
            Vec3 tip = base.add(base.normalize().scale(radius * 0.50D * flick));
            float[] flame = {1.00F, 0.58F, 0.10F, 0.50F * fade};
            ring(out, pose, right, up, base, radius * 0.085D, tip, radius * 0.015D, flame, 6);
        }

        // 一点点尾迹：它是飞出去的，不是浮在那儿（比彗星焰尾短得多）
        wisp(out, pose, dir, right, up, radius * 0.85D, radius * 1.8D, age, 0, 0.40F * fade);
    }

    /**
     * 岩壳：整面画满，<b>半径按岩块取、颜色按顶点热度算</b>。
     *
     * <ul>
     *   <li><b>岩块</b>：半径来自<b>所属岩块</b>（{@link #CELL}）—— 同一块内连成一片、
     *       块与块之间留有硬台阶，所以看上去是<b>一颗颗独立的、差不多同等大小的不规则岩块</b> ✓</li>
     *   <li><b>岩浆</b>：顶点热度 {@link #VHEAT} 决定颜色 —— 贴缝处渐变到 1、
     *       整块熔融的岩块恒为 1；热度高的地方半径也凹进去 {@link #LAVA_RECESS} ✓</li>
     * </ul>
     *
     * <p>⚠️ 颜色<b>必须按顶点算再插值</b>：早先按"整格一个常数"画，
     * 24×40 的网格看上去就是一格格的马赛克 ✗
     * 岩石的硬台阶只该来自<b>半径</b>（那才是"一块块"），颜色应当连续过渡 ✓
     */
    private static void rockShell(VertexConsumer out, Matrix4f pose, Vec3 dir, Vec3 right, Vec3 up,
                                  double radius, float fade) {
        for (int j = 0; j < ROCK_LAT; j++) {
            double a = -Math.PI / 2.0D + j * Math.PI / ROCK_LAT;
            double c = a + Math.PI / ROCK_LAT;
            for (int i = 0; i < ROCK_LON; i++) {
                double u = i * Math.PI * 2.0D / ROCK_LON;
                double v = (i + 1) * Math.PI * 2.0D / ROCK_LON;
                int chunk = CELL[j * ROCK_LON + i];
                float shade = CHUNK_SHADE[chunk];

                int vp = vIdx(i, j);
                int vq = vIdx(i + 1, j);
                int vs = vIdx(i + 1, j + 1);
                int vt = vIdx(i, j + 1);
                float hp = VHEAT[vp];
                float hq = VHEAT[vq];
                float hs = VHEAT[vs];
                float ht = VHEAT[vt];

                // 凹进去多少用四个角的平均热度（整格一个值）—— 台阶正好落在岩块边界上
                double heat = (hp + hq + hs + ht) * 0.25D;
                double chunkRadius = radius * CHUNK_RADIUS[chunk]
                        * (1.0D - (1.0D - LAVA_RECESS) * heat);

                Vec3 p = spherePoint(dir, right, up, chunkRadius, a, u);
                Vec3 q = spherePoint(dir, right, up, chunkRadius, a, v);
                Vec3 s = spherePoint(dir, right, up, chunkRadius, c, v);
                Vec3 t = spherePoint(dir, right, up, chunkRadius, c, u);

                // 色温也按格子抖一点 —— 岩浆的分布与颜色都不规则
                double tint = hash(i * 53 + 3, j * 59 + 7);
                hotspot(out, pose, p, hp, shade, tint, fade);
                hotspot(out, pose, q, hq, shade, tint, fade);
                hotspot(out, pose, s, hs, shade, tint, fade);
                hotspot(out, pose, t, ht, shade, tint, fade);
            }
        }
    }

    /** 顶点索引（顶点网格比格子多一圈/一列 —— 极点是顶点、经度是环）。 */
    private static int vIdx(int i, int j) {
        return j * VLON + i;
    }

    /**
     * 一个顶点：<b>岩石深浅</b>与<b>岩浆橙黄</b>按热度线性混合后写出去。
     *
     * <p>热度 0 = 纯岩石（近黑灰）、1 = 纯岩浆（橙黄）。
     * 中间值是渐变，所以缝上看起来是"石头上透出热"而不是一条硬边 ✓
     */
    private static void hotspot(VertexConsumer out, Matrix4f pose, Vec3 p,
                                double heat, float shade, double tint, float fade) {
        float r = (float) Math.min(1.0D, shade * (1.0D - heat) + 1.00D * heat);
        float g = (float) Math.min(1.0D, shade * (1.0D - heat) + (0.74D - 0.30D * tint) * heat);
        float b = (float) Math.min(1.0D, shade * (1.0D - heat) + (0.28D - 0.24D * tint) * heat);
        WaterGeometry.vertex(out, pose, p, r, g, b, fade);
    }

    /**
     * 预计算岩块图案 —— <b>只算一次</b>（放在 static 块里）。
     *
     * <p>为什么必须预计算：每格、每顶点都要找最近种子（Voronoi），是 O(点数 × 种子)；
     * 每帧现算的话，熔岳天倾那种同时飞好几颗的场合要做十几万次距离比较，纯属浪费 ✗
     * 而且图案本来就<b>不该每帧变</b> —— 用的是确定性 {@link #hash}，不是 {@code Math.random()} ✓
     *
     * <h2>三步</h2>
     * <ol>
     *   <li><b>种子</b>：抖动过的经纬网格 —— 网格保证「每块差不多同等大小」，
     *       抖动保证「不规则、不是整齐方格」；每块再各自随机一个半径、深浅、是否熔融</li>
     *   <li><b>格子</b>：归到最近的种子 —— 半径按块取，于是块与块之间有硬台阶（"一块块"的来源）</li>
     *   <li><b>顶点</b>：归属 + <b>热度</b>。热度 = 「离两块岩块的等距面有多近」，
     *       再与「这一块是不是熔融的」取较大值 —— 颜色按它插值，所以缝上是渐变、
     *       熔融块整片发亮 ✓</li>
     * </ol>
     */
    private static void buildRockPattern() {
        int[] best = new int[1];

        // ---- 1) 种子 ----
        for (int a = 0; a < SEED_LAT; a++) {
            for (int b = 0; b < SEED_LON; b++) {
                int k = a * SEED_LON + b;
                double lat = -Math.PI / 2.0D + (a + 0.5D) * Math.PI / SEED_LAT
                        + (hash(a * 13 + 5, b * 7 + 3) - 0.5D) * 0.7D * Math.PI / SEED_LAT;
                double lon = (b + 0.5D) * Math.PI * 2.0D / SEED_LON
                        + (hash(a * 3 + 91, b * 11 + 17) - 0.5D) * 0.7D * Math.PI * 2.0D / SEED_LON;
                SEED[k * 3] = Math.sin(lat);
                SEED[k * 3 + 1] = Math.cos(lat) * Math.cos(lon);
                SEED[k * 3 + 2] = Math.cos(lat) * Math.sin(lon);
                // 每块自己的半径、深浅、是否熔融：大小差不多，但各自不同
                CHUNK_RADIUS[k] = 1.0D + CHUNK_LUMP * (hash(k * 29 + 7, k * 17 + 23) - 0.5D) * 2.0D;
                CHUNK_SHADE[k] = (float) (0.030D + 0.040D * hash(k * 31 + 1, k * 19 + 9));
                CHUNK_MOLTEN[k] = hash(k * 41 + 13, k * 23 + 3) < MOLTEN_CHUNK_CHANCE;
            }
        }

        // ---- 2) 格子归属（半径按块取）----
        for (int j = 0; j < ROCK_LAT; j++) {
            double lat = -Math.PI / 2.0D + (j + 0.5D) * Math.PI / ROCK_LAT;
            for (int i = 0; i < ROCK_LON; i++) {
                nearestGap(lat, (i + 0.5D) * Math.PI * 2.0D / ROCK_LON, best);
                CELL[j * ROCK_LON + i] = best[0];
            }
        }

        // ---- 3) 顶点归属 + 热度（颜色按它插值）----
        for (int j = 0; j < VLAT; j++) {
            double lat = -Math.PI / 2.0D + j * Math.PI / ROCK_LAT;
            for (int i = 0; i < VLON; i++) {
                double gap = nearestGap(lat, i * Math.PI * 2.0D / ROCK_LON, best);
                int v = j * VLON + i;
                VCELL[v] = best[0];
                double edge = Math.max(0.0D, Math.min(1.0D, 1.0D - gap / EDGE_TOL));
                VHEAT[v] = (float) Math.max(edge, CHUNK_MOLTEN[best[0]] ? 1.0D : 0.0D);
            }
        }
    }

    /**
     * 找最近的种子，并返回「最近与次近的点积差」（越小 = 越贴近两块岩块的等距面）。
     *
     * @param bestOut 长度 ≥1 的数组，写出最近种子的下标（复用数组，避免热路径上分配对象）
     */
    private static double nearestGap(double lat, double lon, int[] bestOut) {
        double px = Math.sin(lat);
        double py = Math.cos(lat) * Math.cos(lon);
        double pz = Math.cos(lat) * Math.sin(lon);
        double first = -2.0D;
        double second = -2.0D;
        int bestK = 0;
        for (int k = 0; k < SEED_COUNT; k++) {
            double dot = px * SEED[k * 3] + py * SEED[k * 3 + 1] + pz * SEED[k * 3 + 2];
            if (dot > first) {
                second = first;
                first = dot;
                bestK = k;
            } else if (dot > second) {
                second = dot;
            }
        }
        bestOut[0] = bestK;
        return first - second;
    }

    /** 球面上的一片（经纬范围给全，内部按 dir/right/up 参数化）。 */
    private static void surfaceQuad(VertexConsumer out, Matrix4f pose, Vec3 dir, Vec3 right, Vec3 up,
                                    double radius, double latA, double latC, double lonU, double lonV,
                                    float r, float g, float b, float alpha) {
        Vec3 p = spherePoint(dir, right, up, radius, latA, lonU);
        Vec3 q = spherePoint(dir, right, up, radius, latA, lonV);
        Vec3 s = spherePoint(dir, right, up, radius, latC, lonV);
        Vec3 t = spherePoint(dir, right, up, radius, latC, lonU);
        WaterGeometry.quad(out, pose, p, q, s, t, r, g, b, alpha);
    }

    private static Vec3 spherePoint(Vec3 dir, Vec3 right, Vec3 up, double radius, double lat, double lon) {
        return dir.scale(Math.sin(lat) * radius)
                .add(WaterGeometry.radial(right, up, lon, Math.cos(lat) * radius));
    }

    /**
     * 稳定的整数哈希 → [0,1)。
     *
     * <p>要求"同一个格子每次得到同一个值" —— <b>不能每帧随机</b>，否则岩板会闪 ✗
     * 所以用自己的整数混合，而不是 {@code Math.random()}。
     */
    private static double hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);
        return (h & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
    }

    // ------------------------------------------------------------------
    //  几何与颜色工具
    // ------------------------------------------------------------------

    /**
     * 焰体在某处的<b>中心点</b>：沿 -dir 后退，并叠加一点横向摆动
     * （越靠尾越晃 —— 火焰不是一根直棍子）。
     *
     * @param wobble 摆动幅度倍率（火舌用 > 1 让它更飘）
     */
    private static Vec3 axisPoint(Vec3 dir, Vec3 right, Vec3 up, double t, double length,
                                  double radius, double age, double wobble) {
        double w1 = Math.sin(t * 5.0D + age * 0.35D) * radius * 0.30D * t * wobble;
        double w2 = Math.cos(t * 4.0D + age * 0.28D) * radius * 0.22D * t * wobble;
        return dir.scale(-length * t).add(right.scale(w1)).add(up.scale(w2));
    }

    /**
     * 焰体沿长度的粗细（1.0 = 与核心半径相同）。
     *
     * <p>参考图的形态：从核心往后<b>先略微涨宽</b>（约 15% 处最宽），
     * 然后一路<b>收细到 0</b> —— 这是"火流星"和"球"最大的区别所在。
     */
    private static double profile(double t) {
        if (t <= 0.15D) {
            return 1.00D + 0.20D * (t / 0.15D);
        }
        double k = Math.max(0.0D, (1.0D - t) / 0.85D);
        return 1.20D * Math.pow(k, 0.72D);
    }

    /**
     * 沿长度取色：白黄 → 亮橙 → 橙 → 深橙红，透明度同步衰减。
     *
     * <p>结果写进 {@code dst}（复用数组，避免每段 new 一次 —— 渲染里不该分配）。
     *
     * @param t          0 = 最热处，1 = 尾尖
     * @param alphaScale 这一层的整体透明度倍率
     */
    private static void flameColor(double t, float alphaScale, float[] dst) {
        // ⚠️ 作者 2026-10-05：「不要用仿真渲染改用像素风格」✗
        //    这里原来按 t 连续插值出无数中间色 + 透明度一路衰减（0.88 的衰减 ✗），
        //    出来就是"现代特效插件"那种观感 ✗
        //    ⇒ 改成**量化到离散色板 + 恒定不透明** ✓（色板见 PixelFlame ✓）
        //    ⚠️ alphaScale 现在只当"整根该不该画"的门 ✓（像素风没有"渐渐透掉"✗）
        PixelFlame.flat(t, dst);
        if (alphaScale <= 0.02F) {
            dst[3] = 0.0F;
        }
    }

    // ==================================================================
    //  射线链 t3「火龙术」：成形法阵 + 火焰龙头（拖尾复用上面的焰体 ✓）
    // ==================================================================

    /** 龙头各段：沿 +dir 的位置（倍半径）。⚠️ 拖尾在 -dir（见 axisPoint）⇒ 龙头朝 +dir ✓ */
    private static final double[] DRAGON_SEG_T = {-0.28D, -0.04D, 0.24D, 0.54D, 0.88D};
    /** 对应的粗细（倍半径）。 */
    private static final double[] DRAGON_SEG_R = {0.26D, 0.60D, 0.68D, 0.48D, 0.16D};

    /** 龙头的角数（每圈）—— 比焰体细分一些，棱角更像"有骨头的头" ✓。 */
    private static final int DRAGON_SIDES = 16;

    /**
     * 画一张**带贴图的四边形** ✓（交叉双片的基本单元 ✓）
     *
     * <p>⚠️ 顶点必须用 {@code vertex(Matrix4f, x, y, z)} 这个**会自己应用变换**的重载 ✗，
     * 用 {@code vertex(x, y, z)} 会被画到世界原点（我踩过 ✗）。
     * ⚠️ 该渲染格式是 NEW_ENTITY ⇒ uv / overlay / uv2 / normal 一个都不能少 ✓
     */
    /**
     * 画一个**长方体** ✓（方块龙头的积木 ✓）
     *
     * <p>局部坐标系 = (前 dir, 上 up, 右 right) ✓ —— 这样"前伸的吻"就是 +前 方向 ✓。
     *
     * <p>贴图：<b>左右两个侧面</b>用整张赤红龙头贴图 ✓（从侧面看就是那张脸 ✓），
     * 其余四个面取贴图上的一块**实色** ✓（省事又不会花 ✗）。
     *
     * <p>⚠️ 顶点必须用 {@code vertex(Matrix4f, x, y, z)}（会应用变换的那个 ✗）；
     * 该渲染格式是 NEW_ENTITY ⇒ uv / overlay / uv2 / normal 缺一不可 ✓
     */
    private static void dragonBox(VertexConsumer vc, Matrix4f pose, Vec3 dir, Vec3 up, Vec3 right,
                                  float cf, float cu, float cr,
                                  float sf, float su, float sr, float k) {
        // 8 个角（局部 → 世界 ✓）
        float f0 = (cf - sf * 0.5F) * k, f1 = (cf + sf * 0.5F) * k;
        float u0 = (cu - su * 0.5F) * k, u1 = (cu + su * 0.5F) * k;
        float r0 = (cr - sr * 0.5F) * k, r1 = (cr + sr * 0.5F) * k;
        Vec3 p000 = local(dir, up, right, f0, u0, r0);
        Vec3 p001 = local(dir, up, right, f0, u0, r1);
        Vec3 p010 = local(dir, up, right, f0, u1, r0);
        Vec3 p011 = local(dir, up, right, f0, u1, r1);
        Vec3 p100 = local(dir, up, right, f1, u0, r0);
        Vec3 p101 = local(dir, up, right, f1, u0, r1);
        Vec3 p110 = local(dir, up, right, f1, u1, r0);
        Vec3 p111 = local(dir, up, right, f1, u1, r1);
        // ★ 左右侧面：整张贴图 ✓（从侧面看就是完整的龙头脸 ✓）
        face(vc, pose, p001, p101, p111, p011, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F);
        face(vc, pose, p100, p000, p010, p110, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F);
        // 其余面：取贴图上的一块实色（0.45,0.35 ⇒ 中段赤红 ✓）
        face(vc, pose, p000, p100, p110, p010, .45F, .35F, .45F, .35F, .45F, .35F, .45F, .35F);
        face(vc, pose, p101, p001, p011, p111, .45F, .35F, .45F, .35F, .45F, .35F, .45F, .35F);
        face(vc, pose, p010, p110, p111, p011, .45F, .35F, .45F, .35F, .45F, .35F, .45F, .35F);
        face(vc, pose, p000, p001, p101, p100, .45F, .35F, .45F, .35F, .45F, .35F, .45F, .35F);
    }

    private static Vec3 local(Vec3 dir, Vec3 up, Vec3 right, double f, double u, double r) {
        return dir.scale(f).add(up.scale(u)).add(right.scale(r));
    }

    /** 一个面 = 4 个顶点（uv 两个一组 ✓）。 */
    private static void face(VertexConsumer vc, Matrix4f pose,
                             Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             float au, float av, float bu, float bv,
                             float cu, float cv, float du, float dv) {
        quadVertex(vc, pose, a, au, av);
        quadVertex(vc, pose, b, bu, bv);
        quadVertex(vc, pose, c, cu, cv);
        quadVertex(vc, pose, d, du, dv);
    }
    private static void quadVertex(VertexConsumer vc, Matrix4f pose, Vec3 p, float u, float v) {
        vc.vertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                .normal(0.0F, 0.0F, 1.0F)
                .endVertex();
    }

    /** 连接两圈、每段一个颜色的锥台（焰体的基本积木）。 */
    private static void ring(VertexConsumer out, Matrix4f pose, Vec3 right, Vec3 up,
                             Vec3 c0, double r0, Vec3 c1, double r1, float[] color, int sides) {
        float a = color[0], b = color[1], c = color[2], d = color[3];
        for (int i = 0; i < sides; i++) {
            double a0 = i * Math.PI * 2.0D / sides;
            double a1 = (i + 1) * Math.PI * 2.0D / sides;
            Vec3 p0 = c0.add(WaterGeometry.radial(right, up, a0, r0));
            Vec3 p1 = c0.add(WaterGeometry.radial(right, up, a1, r0));
            Vec3 q1 = c1.add(WaterGeometry.radial(right, up, a1, r1));
            Vec3 q0 = c1.add(WaterGeometry.radial(right, up, a0, r1));
            WaterGeometry.quad(out, pose, p0, p1, q1, q0, a, b, c, d);
        }
    }

    /** 画一颗球：沿飞行方向切 10 圈、每圈 20 段（参数化照抄水系那颗水球）。 */
    private static void sphere(VertexConsumer out, Matrix4f pose, Vec3 dir, Vec3 right, Vec3 up,
                               double radius, float r, float g, float b, float alpha) {
        for (int j = 0; j < 10; j++) {
            double a = -Math.PI / 2.0D + j * Math.PI / 10.0D;
            double c = a + Math.PI / 10.0D;
            for (int i = 0; i < 20; i++) {
                double u = i * Math.PI / 10.0D;
                double v = (i + 1) * Math.PI / 10.0D;
                Vec3 p = dir.scale(Math.sin(a) * radius).add(WaterGeometry.radial(right, up, u, Math.cos(a) * radius));
                Vec3 q = dir.scale(Math.sin(a) * radius).add(WaterGeometry.radial(right, up, v, Math.cos(a) * radius));
                Vec3 s = dir.scale(Math.sin(c) * radius).add(WaterGeometry.radial(right, up, v, Math.cos(c) * radius));
                Vec3 t = dir.scale(Math.sin(c) * radius).add(WaterGeometry.radial(right, up, u, Math.cos(c) * radius));
                WaterGeometry.quad(out, pose, p, q, s, t, r, g, b, alpha);
            }
        }
    }
}
