package com.tnc.tnc.casino.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 赌场机器的自绘几何工具箱 —— <b>不碰资源包</b> ✓（照 {@code light/client/TNDragonModel} 那条已验证的路线）。
 *
 * <h2>为什么不用 {@code models/block/*.json}（三条硬理由，都有实证 ✗）</h2>
 * <ol>
 *   <li>顶点必须在 <b>−16..32</b>（≈2 格）—— 2 格宽的机器会被加载器直接拒绝 ✗
 *       （实证：{@code block/gamble_table.json} extents {@code 0..64} ⇒
 *       {@code Failed to load model} ⇒ 那张赌博桌在游戏里从没显示过）；</li>
 *   <li>整合包里优先级更高的资源包遮住了 {@code tnc:models/**} ✗（同一条日志）；</li>
 *   <li>原版 element <b>只能绕单轴转固定角度</b> ✗ —— 滚轮/转盘要逐帧任意角度 ✓。</li>
 * </ol>
 *
 * <h2>坐标系约定（和 {@code PastureGeometry} / {@code GearModels} 一致 ✓）</h2>
 * 一个"格"= 1.0 ✓（不是原版模型的 16 ✓）。本类内部一律按<b>方块坐标</b>给数
 * （{@code 0..2} 是 2 格 ✓），{@code pos} 给的是<b>格子里的偏移</b>（{@code 0..1} ✓）。
 *
 * <h2>面朝向约定（★ 这张表是验算过的 ✗，别凭感觉改）</h2>
 * 绕序按"从外面看逆时针" ✓；法线 + 每面明暗写死在 {@link #FACES} 里 ✓。
 * 暗一档是为了让纯色几何看得出立体感 ✓（正式贴图接进来后这层明暗仍然有用 ✓）。
 */
public final class TNCasinoGeometry {

    private TNCasinoGeometry() {
    }

    /** 一个面的明暗系数（顶面最亮、底面最暗 ✓ —— 与 {@code TNLightWingsModel} 同一套观感）。 */
    private static final float SHADE_UP = 1.00F;
    private static final float SHADE_DOWN = 0.55F;
    private static final float SHADE_X = 0.78F;
    private static final float SHADE_Z = 0.90F;

    /**
     * 纯色几何要发的那个 uv ✓ —— <b>恒为 (0,0)</b>，专采 {@link #white()} 那张图的 (0,0) 纯白像素，
     * 于是"贴图 × 顶点色"的结果正好等于顶点色 ✓。
     *
     * <h2>★★ 这一行不是可有可无的 ✗ —— 漏了它整局游戏直接崩（2026-10-08 实机惨案）</h2>
     * 原来这里写着"<b>有意不发 uv</b>：默认取 {@code (0,0)}" ✗ ——
     * <b>这条注释是错的</b> ✓，而且错得很贵：
     * <ul>
     *   <li>{@code VertexConsumer} 的"没调用就取默认值"只对 <b>{@code VertexConsumer}</b> 那几个
     *       {@code default} 方法成立 ✗；而 {@code MultiBufferSource.getBuffer(...)} 在正常情况下
     *       交回来的就是 <b>{@code BufferBuilder} 本体</b> ✓，它<b>重写</b>了这些方法并做<b>逐元素校验</b> ✗；</li>
     *   <li>{@code BufferBuilder} 里有个"下一个该填的元素"游标（{@code m_85656_} / {@code elementsToFill} ✓）：
     *       每个 {@code color/uv/overlayCoords/uv2/normal} 都<b>先比对自己是不是"当前该填的那个元素"</b>，
     *       不是就<b>静默直接 return</b> ✗ —— 于是少发一个元素时<b>不报错、不提示</b> ✗；</li>
     *   <li>直到 {@code endVertex()}（{@code m_5752_}）发现游标没走完，抛
     *       {@code IllegalStateException: Not filled all elements of the vertex} ✗。</li>
     * </ul>
     * 而赌场机器绑的是 {@code RenderType.entityCutoutNoCull} ⇒ 顶点格式是<b>实体的那套
     * （position + color + <b>uv0</b> + overlay + uv2 + normal ✓）</b>，
     * 原来恰好漏的就是 <b>uv0</b> ✗ ⇒ 一旦世界里放下老虎机/轮盘/赌博桌，
     * 方块实体渲染器一跑就抛异常 ⇒ <b>客户端直接崩（不是卡顿，是崩）</b> ✗。
     *
     * <p><b>实证</b>：{@code crash-2026-10-08_20.47.58-client.txt}
     * {@code Description: Rendering Block Entity} ·
     * {@code IllegalStateException: Not filled all elements of the vertex} ·
     * {@code at ...TNCasinoGeometry.box(TNCasinoGeometry.java:94)} ·
     * {@code at ...TNSlotMachineRenderer.render(TNSlotMachineRenderer.java:164)}。
     *
     * <p><b>为什么全仓只有这一处踩到</b> ✓（不是巧合，是格式对不上）：
     * 本类是全仓<b>唯一</b>把 {@code POSITION_COLOR} 顶点喂给<b>实体格式</b>缓冲的地方 ✗。
     * 其余自绘全走 {@code POSITION_COLOR} 缓冲（{@code WaterRenderTypes.GEOMETRY} /
     * {@code RenderType.lightning()} ⇒ {@code WaterGeometry.vertex} 与
     * {@code PortalRitualRenderer.vertex} 同样不发 uv，但它们的格式里<b>压根没有 uv 这个元素</b> ⇒ 合法 ✓）；
     * 而所有走实体格式的地方（{@code TNDragonModel} / {@code TNMagicCircleRenderer} /
     * {@code TNLightningStrikeRenderer} / {@code TNFireBoltRenderer} / {@code TNLightBeamRenderer} /
     * {@code TNLightWingsModel} ✓）<b>本来就都发了 {@code .uv(...)}</b> ✓ —— 就这里漏了 ✗。
     * <b>结论</b>：换渲染类型（{@code RenderType}）＝换顶点格式，顶点那几个元素<b>一个都不能省</b> ✗。
     */
    private static final float UV_ZERO = 0.0F;

    /** 六面：绕序 + 法线 + 明暗 ✓。顺序 up/down/east/west/south/north（与 {@code TNLightWingsModel.box} 逐面核对过 ✓）。 */
    private static final Face[] FACES = {
            new Face(new float[][]{{0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}}, new Vector3f(0, 1, 0), SHADE_UP),
            new Face(new float[][]{{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}}, new Vector3f(0, -1, 0), SHADE_DOWN),
            new Face(new float[][]{{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}}, new Vector3f(1, 0, 0), SHADE_X),
            new Face(new float[][]{{0, 0, 1}, {0, 1, 1}, {0, 1, 0}, {0, 0, 0}}, new Vector3f(-1, 0, 0), SHADE_X),
            new Face(new float[][]{{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}}, new Vector3f(0, 0, 1), SHADE_Z),
            new Face(new float[][]{{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}}, new Vector3f(0, 0, -1), SHADE_Z),
    };

    private record Face(float[][] corners, Vector3f normal, float shade) {
    }

    /**
     * 画一个长方体 ✓（按方块坐标给数：{@code w/h/d} 是三条边长，不是"到哪" ✗）。
     *
     * @param pose  当前姿态 ✓（旋转/平移已经压在里面了 ✓）
     * @param buffer 顶点缓冲 ✓
     * @param pos   长方体最小角（格子内的偏移 0..1 ✓）
     * @param size  三条边长（以格为单位 ✓）
     * @param rgb   基色 ✓（会被逐面明暗乘一下 ✓）
     * @param light 打包光照 ✓
     */
    public static void box(PoseStack pose, VertexConsumer buffer, Vector3f pos, Vector3f size,
                           int rgb, int light) {
        box(pose, buffer, pos, size, rgb, light, 1.0F);
    }

    /** 同上，{@code shade} 是整体再压一档（做凹槽/阴影用 ✓）。 */
    public static void box(PoseStack pose, VertexConsumer buffer, Vector3f pos, Vector3f size,
                           int rgb, int light, float shade) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float baseR = ((rgb >> 16) & 0xFF) / 255.0F;
        float baseG = ((rgb >> 8) & 0xFF) / 255.0F;
        float baseB = (rgb & 0xFF) / 255.0F;
        float x0 = pos.x, y0 = pos.y, z0 = pos.z;
        float x1 = pos.x + size.x, y1 = pos.y + size.y, z1 = pos.z + size.z;
        for (Face face : FACES) {
            float k = face.shade() * shade;
            for (float[] corner : face.corners()) {
                float x = corner[0] == 0 ? x0 : x1;
                float y = corner[1] == 0 ? y0 : y1;
                float z = corner[2] == 0 ? z0 : z1;
                // ★ 【必须发 uv】✓ —— 见下方 UV_ZERO 的长注释：漏了它 = 每帧崩游戏 ✗
                buffer.vertex(m, x, y, z)
                        .color(baseR * k, baseG * k, baseB * k, 1.0F)
                        .uv(UV_ZERO, UV_ZERO)
                        .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                        .uv2(light)
                        .normal(n, face.normal().x, face.normal().y, face.normal().z)
                        .endVertex();
            }
        }
    }

    /**
     * 画一个<b>绕自己的 Y 轴转了 {@code spinDegrees}</b> 的长方体 ✓ ——
     * 转盘那几片扇形、盘上的小球、滚动的骰子全靠它 ✓。
     *
     * <p>{@code center} 给的是盒子<b>中心</b> ✓（不是最小角 ✗）—— 因为"绕自己转"必须
     * 先平移到中心、转、再退回原点画 ✓。
     */
    public static void boxRotated(PoseStack pose, VertexConsumer buffer, Vector3f center, Vector3f size,
                                  float spinDegrees, int rgb, int light) {
        boxRotated(pose, buffer, center, size, spinDegrees, rgb, light, 1.0F);
    }

    /** 同上；{@code shade} 是整体再压一档（做凹槽/阴影 ✓）。 */
    public static void boxRotated(PoseStack pose, VertexConsumer buffer, Vector3f center, Vector3f size,
                                  float spinDegrees, int rgb, int light, float shade) {
        pose.pushPose();
        pose.translate(center.x, center.y, center.z);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spinDegrees));
        box(pose, buffer, new Vector3f(-size.x * 0.5F, -size.y * 0.5F, -size.z * 0.5F),
                size, rgb, light, shade);
        pose.popPose();
    }

    /** 便捷重载：直接给两个角 ✓（比"偏移 + 边长"更贴近建模时的思路 ✓）。 */
    public static void box(PoseStack pose, VertexConsumer buffer,
                           float x0, float y0, float z0, float x1, float y1, float z1,
                           int rgb, int light) {
        box(pose, buffer, new Vector3f(x0, y0, z0), new Vector3f(x1 - x0, y1 - y0, z1 - z0), rgb, light);
    }

    // ------------------------------------------------------------------
    //  ★ 圆柱 / 圆环 —— "转盘看起来是个盘、滚轮看起来是个筒"全靠这一节 ✗
    // ------------------------------------------------------------------

    /**
     * 把一个箱子放到"极坐标"位置 ✓ —— 先绕 Y 转 {@code angleDegrees}，
     * 再沿<b>转完之后的 −Z 方向</b>推出去 {@code distance} ✓，最后画一个尺寸为 {@code size} 的箱子 ✓。
     *
     * <h2>为什么需要它（★ 这就是"片 vs 筒"的分水岭 ✗）</h2>
     * 自绘只有盒子 ✗、没有圆柱 ✓。想让东西"看起来是圆的"，只能<b>用若干平板围一圈</b> ✓：
     * 每块板离轴心 {@code 内切半径} 远、面朝外 ✓。本方法就是"摆第 i 块板"的那一步 ✓。
     *
     * <p>角度 0° 时板子在 <b>−Z 方向</b>（即机器正面 ✓）—— 与整机"正面朝 −Z"的约定一致 ✓。
     *
     * @param center 轴心（方块坐标 ✓）
     * @param size 板的尺寸（宽 × 高 × 厚 ✓）
     * @param distance 板中心离轴心多远 ✓（多边形内切半径 ✓）
     * @param angleDegrees 绕 Y 的角度 ✓
     */
    public static void boxPolar(PoseStack pose, VertexConsumer buffer, Vector3f center, Vector3f size,
                                float distance, float angleDegrees, int rgb, int light) {
        boxPolar(pose, buffer, center, size, distance, angleDegrees, rgb, light, 1.0F);
    }

    /** 同上；{@code shade} 是整体再压一档（做凹槽/阴影 ✓）。 */
    public static void boxPolar(PoseStack pose, VertexConsumer buffer, Vector3f center, Vector3f size,
                                float distance, float angleDegrees, int rgb, int light, float shade) {
        pose.pushPose();
        pose.translate(center.x, center.y, center.z);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angleDegrees));
        pose.translate(0.0F, 0.0F, -distance);
        box(pose, buffer, new Vector3f(-size.x * 0.5F, -size.y * 0.5F, -size.z * 0.5F),
                size, rgb, light, shade);
        pose.popPose();
    }

    /**
     * 一个正 {@code segments} 边形围成的"圆柱侧面" ✓。
     *
     * <p>★ <b>用内切半径 {@code R·cos(π/n)} 摆板心</b> ✗ —— 这样相邻板的棱才正好相接 ✓；
     * 若用外接半径 R 摆，板会往外凸着、彼此露出缝 ✓。板宽取弦长 {@code 2·R·sin(π/n)} ✓。
     *
     * @param axis 轴心（方块坐标 ✓）
     * @param radius <b>看起来的</b>半径 ✓（外接半径 ✓）
     * @param height 筒高 ✓
     * @param thickness 板厚 ✓
     * @param segments 段数 ✓（12 已足够圆 ✓；越大越圆但顶点线性增长 ✗）
     * @param phaseDegrees 整体相位偏移 ✓（用来表达"停在哪一格" ✓）
     * @param rgb 统一颜色 ✓（{@code colorAt} 非空时被忽略 ✓）
     * @param colorAt 第 i 段用什么颜色 ✓（传 {@code null} = 统一用 {@code rgb} ✓）
     */
    public static void cylinder(PoseStack pose, VertexConsumer buffer, Vector3f axis, float radius,
                                float height, float thickness, int segments, float phaseDegrees,
                                int rgb, int light, java.util.function.IntFunction<Integer> colorAt) {
        double step = 2.0 * Math.PI / segments;
        float apothem = (float) (radius * Math.cos(step / 2.0));
        float chord = (float) (2.0 * radius * Math.sin(step / 2.0));
        for (int i = 0; i < segments; i++) {
            float angle = phaseDegrees + 360.0F * i / segments;
            int color = colorAt == null ? rgb : colorAt.apply(i);
            boxPolar(pose, buffer, axis, new Vector3f(chord * 1.03F, height, thickness),
                    apothem, angle, color, light);
        }
    }

    /**
     * 往一个圆上均匀摆 {@code count} 个小方块 ✓ —— 袋位标记 / 盘心铆钉 / 刻度都用它 ✓。
     */
    public static void studs(PoseStack pose, VertexConsumer buffer, Vector3f axis, float outer,
                             float size, float height, int count, float phaseDegrees,
                             int rgb, int light) {
        for (int i = 0; i < count; i++) {
            float angle = phaseDegrees + 360.0F * i / count;
            boxPolar(pose, buffer, axis, new Vector3f(size, height, size), outer, angle, rgb, light);
        }
    }

    // ------------------------------------------------------------------
    //  纯色几何要绑的那张"白图"
    // ------------------------------------------------------------------
    /**
     * 纯色自绘必须给管线绑一张贴图 ✗，而我们要的是"顶点颜色是什么就画什么" ✓。
     *
     * <h2>★ 用原版自带的纯白 ＋ 一个 UV 都不发</h2>
     * <ul>
     *   <li>原版管线对"没写 UV 的顶点"默认取 {@code (0,0)} ⇒ <b>所有顶点都采同一个像素</b> ✓；</li>
     *   <li>于是只要那张图的 {@code (0,0)} 是纯白，输出颜色就<b>正好等于</b>顶点颜色 ✓，
     *       不需要 UV 图集、不需要分格、不可能采错格子 ✓。</li>
     * </ul>
     *
     * <h2>⚠️★ 这里踩过一次大坑（2026-10-08，导致游戏卡死 ✗）</h2>
     * 第一版是<b>自己在代码里注册一张 DynamicTexture</b>，而且注册动作写在
     * <b>渲染的时候</b>（{@code white()} 里判断 "{@code manager.getTexture(...) == null} 就注册" ✓）。
     * 结果：解析不到 ⇒ 抛 {@code FileNotFoundException} ⇒ 每帧都抛 ⇒
     * 配合 {@code azurelibarmor} 的 {@code AnimatableTexture} mixin（它把
     * {@code TextureManager.register} 包了一层）⇒ <b>放下机器的一瞬间开始每帧刷异常，游戏直接卡死</b> ✗
     * （实证：{@code logs/debug.log} 里
     * {@code Failed to load texture: tnc:dynamic/casino_white.png} ＋
     * {@code at ...TNCasinoGeometry.registerWhite(TNCasinoGeometry.java:164)} ✓）。
     *
     * <p><b>教训两条</b> ✗：
     * ① <b>绝不在渲染路径里注册贴图</b> ✓ —— 渲染只该读、不该改状态 ✓；
     * ② 用<b>已经在资源里、确定存在</b>的图 ✓ —— 原版就有一张现成的纯白：
     * {@code minecraft:textures/misc/white.png}（已核 1.20.1 客户端 jar：<b>4×4 / colortype 2 /
     * 像素 (0,0) = [255,255,255,255]</b> ✓）。
     *
     * <p>⚠️ 更正一条<b>之前写错的</b>笔记 ✗：早先这里写着"原版没有 {@code textures/misc/white.png}" ——
     * <b>这是错的</b> ✓（当时只列了 {@code missingno}/{@code pumpkinblur}/{@code vignette}/{@code forcefield}，
     * 漏看了 {@code white.png} ✓）。已按实际 jar 内容改正 ✓。
     */
    private static final net.minecraft.resources.ResourceLocation WHITE =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");

    /**
     * 取那张纯白 ✓ —— <b>纯查表、零副作用</b> ✗：不在渲染路径里注册任何东西 ✓
     * （第一版就是在这里注册才把游戏搞卡的 ✗）。
     */
    public static net.minecraft.resources.ResourceLocation white() {
        return WHITE;
    }

    /**
     * 朝向 → 绕 Y 的度数 ✓。
     *
     * <h2>推导（别凭感觉 ✗）</h2>
     * 模型在<b>没转</b>的时候正面朝 <b>−Z</b>（即 {@code NORTH} ✓，与原版方块模型的约定同源 ✓）。
     * 要把"正面"从 NORTH 转到 {@code facing}，绕 Y 逆时针转 {@code (facing.toYRot() - 180)} 度 ✓
     * —— 于是 NORTH ⇒ 0° ✓（不动）、SOUTH ⇒ 180° ✓、WEST ⇒ 90° ✓、EAST ⇒ 270° ✓。
     */
    public static float yawFor(Direction facing) {
        return switch (facing) {
            case NORTH -> 0.0F;
            case EAST -> 270.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }

    /** 把 {@code 0..1} 的格内偏移转成方块坐标（2 格的模型用 ✓）。 */
    public static Vector3f at(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }

    public static Vector3f size(float w, float h, float d) {
        return new Vector3f(w, h, d);
    }

    /** 一串盒子（调试用：把机器几何列出来核对位置 ✓）。 */
    public static List<String> describe(List<float[]> boxes) {
        List<String> out = new ArrayList<>(boxes.size());
        for (float[] b : boxes) {
            out.add(String.format("(%.3f,%.3f,%.3f)-(%.3f,%.3f,%.3f)", b[0], b[1], b[2], b[3], b[4], b[5]));
        }
        return out;
    }
}
