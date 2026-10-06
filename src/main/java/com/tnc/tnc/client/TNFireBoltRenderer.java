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
        // 射线链 t3「火龙术」：成形阶段画**朝向准心的法阵**，成形后是一颗火焰龙头 ✓
        if ("fire_dragon".equals(entity.spellPath())) {
            dragon(out, pose, dir, right, up, radius, age, fade, entity.formProgress(partial));
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
        double k;
        if (t < 0.30D) {                 // 白黄 → 亮橙
            k = t / 0.30D;
            dst[0] = 1.00F;
            dst[1] = (float) (1.00D - 0.32D * k);
            dst[2] = (float) (0.82D - 0.72D * k);
        } else if (t < 0.65D) {          // 亮橙 → 橙
            k = (t - 0.30D) / 0.35D;
            dst[0] = 1.00F;
            dst[1] = (float) (0.68D - 0.30D * k);
            dst[2] = (float) (0.10D - 0.06D * k);
        } else {                         // 橙 → 深橙红
            k = (t - 0.65D) / 0.35D;
            dst[0] = (float) (1.00D - 0.16D * k);
            dst[1] = (float) (0.38D - 0.24D * k);
            dst[2] = 0.04F;
        }
        dst[3] = (float) ((1.0D - 0.88D * t) * alphaScale);
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

    private static void dragon(VertexConsumer out, Matrix4f pose, Vec3 dir, Vec3 right, Vec3 up,
                               double radius, double age, float fade, double form) {
        float[] color = new float[4];

        // ① 成形阶段：面前那个**朝向准心**的法阵 ✓
        //    作者要"由外到里一点点出现，表现出描绘的感觉"✗ ⇒ 外环先满、内环依次跟上 ✓
        if (form < 0.999D) {
            for (int i = 0; i < 3; i++) {
                double drawn = Math.max(0.0D, Math.min(1.0D, form * 1.7D - i * 0.26D));
                if (drawn <= 0.03D) {
                    continue;
                }
                double rr = radius * (1.10D + i * 0.72D);
                flameColor(0.16D + i * 0.24D, 0.60F * fade * (float) drawn, color);
                // 有厚度的细圆环（可见性比平面圆环稳 ✓）
                ring(out, pose, right, up, dir.scale(-0.05D * radius), rr * 0.93D,
                        dir.scale(0.05D * radius), rr, color, 48);
            }
        }

        // ② 龙头本体：一串锥台，整体随成形进度长大 ✓
        double head = radius * (0.35D + 0.65D * form);
        for (int i = 0; i + 1 < DRAGON_SEG_T.length; i++) {
            flameColor(0.60D - i * 0.14D, 0.88F * fade, color);
            ring(out, pose, right, up,
                    dir.scale(DRAGON_SEG_T[i] * head), DRAGON_SEG_R[i] * head,
                    dir.scale(DRAGON_SEG_T[i + 1] * head), DRAGON_SEG_R[i + 1] * head,
                    color, DRAGON_SIDES);
        }

        // ③ 下颌：往下偏一点 ⇒ 有"张嘴"的层次 ✓
        Vec3 jaw = up.scale(-0.24D * head);
        for (int i = 0; i < 2; i++) {
            double t0 = 0.12D + i * 0.30D;
            flameColor(0.72D, 0.72F * fade, color);
            ring(out, pose, right, up,
                    dir.scale(t0 * head).add(jaw), 0.28D * head,
                    dir.scale((t0 + 0.30D) * head).add(jaw), 0.17D * head,
                    color, 12);
        }

        // ④ 两只角：往脑后翘（两段，弯一点更像角 ✓）
        for (int side = -1; side <= 1; side += 2) {
            Vec3 root = dir.scale(0.16D * head).add(right.scale(side * 0.28D * head))
                    .add(up.scale(0.34D * head));
            Vec3 mid = dir.scale(-0.24D * head).add(right.scale(side * 0.38D * head))
                    .add(up.scale(0.68D * head));
            Vec3 tip = dir.scale(-0.62D * head).add(right.scale(side * 0.40D * head))
                    .add(up.scale(0.96D * head));
            flameColor(0.58D, 0.82F * fade, color);
            ring(out, pose, right, up, root, 0.12D * head, mid, 0.070D * head, color, 10);
            ring(out, pose, right, up, mid, 0.070D * head, tip, 0.012D * head, color, 10);
        }

        // ⑤ 两眼：白热的小亮点 ✓
        float[] glow = {1.0F, 0.95F, 0.66F, 0.95F * fade};
        for (int side = -1; side <= 1; side += 2) {
            Vec3 eye = dir.scale(0.42D * head).add(right.scale(side * 0.34D * head))
                    .add(up.scale(0.20D * head));
            ring(out, pose, right, up, eye.add(dir.scale(-0.06D * head)), 0.105D * head,
                    eye.add(dir.scale(0.06D * head)), 0.105D * head, glow, 10);
        }
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
