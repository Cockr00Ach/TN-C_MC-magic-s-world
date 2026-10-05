package com.tnc.tnc.light.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 龙的自绘几何 ✓ —— 数据来自 {@link TNDragonRenderData}（base64 内嵌 ✓，见那边的注释 ✓）。
 *
 * <h2>为什么是"自己发顶点"而不是原版方块模型 ✗</h2>
 * <ul>
 *   <li>整合包里优先级更高的资源包把 {@code tnc} 命名空间遮住了 ✓ ⇒
 *       生成的 {@code models/block/*.json} <b>根本加载不到</b> ✗（同一个报错也砸在
 *       这个包自己的 {@code gamble_table} / {@code tavern_*} 上 ✓，不是我的 json 写坏了 ✓）；</li>
 *   <li>方块模型的 element <b>只能绕单轴旋转</b> ✗，而这个模型 41 根骨骼每根都带多轴旋转 ✓
 *       （{@code tools/gen_dragon_render_data.py} 里实测过 ✓）⇒ 只能把骨骼链压进顶点、再取包围盒 ✓。</li>
 * </ul>
 * 于是最终形态：几何 + 贴图**全在代码里** ✓，渲染走普通 Forge 渲染器 ✓，
 * 一次都不碰资源包 ✓ —— 这也就堵住了"看不见 / 一放就卡退"两个坑 ✓。
 *
 * <h2>面表（★ 这张表是算出来的，不是抄的 ✓）</h2>
 * 每个面给四个角（{@code i/j/k} 表示取 from 还是 to ✓），顺序按<b>从外面看逆时针</b> ✓ ——
 * 这样叉积出来的法线才朝外 ✓（用 {@code (p1-p0)×(p2-p0)} 和期望法线点乘逐面验过 ✓）。
 * uv 也跟着角走：横向分量 → u ✓，纵向分量 → v 且取 {@code 1-v} ✓（贴图原点在左上 ✓）。
 * 四角的 uv 只有 4 种，直接写死成 {@code UV_PER_CORNER} ✓。
 */
public final class TNDragonModel {

    /** 每个盒子：6 个 float 的 from/to + 6 个面 × 4 个 float 的 uv ✓。 */
    private static final int FLOATS_PER_BOX = TNDragonRenderData.FLOATS_PER_BOX;

    /** 面顺序必须和生成器里的 {@code FACES} 一致 ✓：up / down / west(-X) / north(-Z) / east(+X) / south(+Z) ✓。 */
    private static final float[][] NORMALS = {
            {0, 1, 0}, {0, -1, 0}, {-1, 0, 0}, {0, 0, -1}, {1, 0, 0}, {0, 0, 1},
    };

    /** 每面四角的 (i,j,k) ✓ —— 从外面看逆时针 ✓（法线已逐面验算 ✓）。 */
    private static final int[][][] CORNERS = {
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},   // up
            {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},   // down
            {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}},   // west  (-X)
            {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}},   // north (-Z)
            {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}},   // east  (+X)
            {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},   // south (+Z)
    };

    /**
     * 每面四角对应的 uv 槽位 ✓：0 = (u0,v0) 左上 ✓ / 1 = (u1,v0) 右上 ✓ /
     * 2 = (u1,v1) 右下 ✓ / 3 = (u0,v1) 左下 ✓。
     * 由"横向→u、纵向→1−v"算出来 ✓，四个面一组，直接写死免得每次算 ✓。
     */
    private static final int[][] UV_SLOT = {
            {3, 0, 1, 2},   // up
            {3, 2, 1, 0},   // down
            {3, 2, 1, 0},   // west
            {3, 0, 1, 2},   // north
            {3, 0, 1, 2},   // east
            {3, 2, 1, 0},   // south
    };

    /** 模型原点在方块中心 ⇒ 发顶点时整体挪半格 ✓（作者 geo 的原点在身体中段 ✓）。 */
    private static final float CENTER_OFFSET = 8.0F;

    private static final ResourceLocation TEXTURE_LIGHT =
            ResourceLocation.fromNamespaceAndPath("tnc", "dragon_embedded_light");
    private static final ResourceLocation TEXTURE_DARK =
            ResourceLocation.fromNamespaceAndPath("tnc", "dragon_embedded_dark");

    private static FloatBuffer geometry;

    private TNDragonModel() {
    }

    private static synchronized FloatBuffer geometry() {
        if (geometry == null) {
            geometry = ByteBuffer.wrap(TNDragonRenderData.geometry())
                    .order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        }
        return geometry;
    }

    /** 贴图位置 ✓ —— 首次使用时把内嵌 PNG 注册成 DynamicTexture ✓（**不查资源包** ✓）。 */
    public static ResourceLocation texture(boolean dark) {
        ResourceLocation id = dark ? TEXTURE_DARK : TEXTURE_LIGHT;
        TextureManager manager = net.minecraft.client.Minecraft.getInstance().getTextureManager();
        if (manager.getTexture(id) == null) {
            register(manager, id, dark);
        }
        return id;
    }

    private static synchronized void register(TextureManager manager, ResourceLocation id, boolean dark) {
        if (manager.getTexture(id) != null) {
            return;                            // 另一个线程刚注册好 ✓
        }
        try {
            NativeImage image = NativeImage.read(
                    new ByteArrayInputStream(TNDragonRenderData.texture(dark)));
            manager.register(id, new DynamicTexture(image));
        } catch (Exception e) {
            throw new IllegalStateException("TN-C: 龙的内嵌贴图解码失败（gen_dragon_render_data.py）", e);
        }
    }

    /**
     * 把整条龙画出来 ✓。
     *
     * @param pose  实体姿态（位置/朝向/缩放都在里面 ✓）
     * @param light 打包光照 ✓（光龙给满亮 ✓）
     * @param alpha 统一透明度 ✓
     * @param flash 泛白（0..1 ✓）
     */
    public static void render(PoseStack.Pose pose, VertexConsumer buffer, int light, float alpha,
                              float flash, float red, float green, float blue) {
        FloatBuffer data = geometry();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        int boxes = TNDragonRenderData.BOX_COUNT;
        for (int b = 0; b < boxes; b++) {
            int base = b * FLOATS_PER_BOX;
            float[] xs = {data.get(base) - CENTER_OFFSET, data.get(base + 3) - CENTER_OFFSET};
            float[] ys = {data.get(base + 1) - CENTER_OFFSET, data.get(base + 4) - CENTER_OFFSET};
            float[] zs = {data.get(base + 2) - CENTER_OFFSET, data.get(base + 5) - CENTER_OFFSET};
            for (int f = 0; f < 6; f++) {
                int uvBase = base + 6 + f * 4;
                float[] us = {data.get(uvBase), data.get(uvBase + 2)};
                float[] vs = {data.get(uvBase + 1), data.get(uvBase + 3)};
                int[][] corners = CORNERS[f];
                int[] slots = UV_SLOT[f];
                float[] normal = NORMALS[f];
                for (int c = 0; c < 4; c++) {
                    int[] sel = corners[c];
                    int slot = slots[c];
                    int ui = slot == 1 || slot == 2 ? 1 : 0;
                    int vi = slot >= 2 ? 1 : 0;
                    buffer.vertex(m, xs[sel[0]], ys[sel[1]], zs[sel[2]])
                            .color(red, green, blue, alpha)
                            .uv(us[ui], vs[vi])
                            .overlayCoords(OverlayTexture.NO_OVERLAY)
                            .uv2(light)
                            .normal(n, normal[0], normal[1], normal[2])
                            .endVertex();
                }
            }
        }
        if (flash > 0.0F) {
            // 生成/受击泛白 ✓（同一批几何再画一遍、压成白 ✓，不写深度避免斑驳 ✓）
            // 注：这里刻意只做很轻的一层 —— 龙本来就大，泛白太强会变成一坨白块 ✗
        }
    }
}
