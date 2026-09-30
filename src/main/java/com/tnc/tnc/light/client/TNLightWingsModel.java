package com.tnc.tnc.light.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 极简 Bedrock geo 渲染器 —— 只画作者的 {@code light_wings.geo.json}（不做动画关键帧 ✗）。
 *
 * <h2>为什么自己写而不是用 GeckoLib</h2>
 * GeckoLib 的 {@code GeoEntityRenderer} 是给"实体"用的 ✗，而这是**挂在玩家背上的一层** ✓ ——
 * 引 GeckoLib 到玩家渲染层要绕一大圈 ✓；这里只需要：读 geo → 按父子骨骼变换 → 画六个面 ✓，一百多行就够 ✓。
 *
 * <h2>作者要的两点</h2>
 * <ul>
 *   <li><b>有点透明</b> ✓：{@link #ALPHA}（0.78，越小越透 ✓）</li>
 *   <li><b>自发光</b> ✓：{@code entityTranslucentEmissive} ＋ 全亮度（不随环境变暗 ✓）</li>
 * </ul>
 *
 * <h2>扇动</h2>
 * 按名字找两根"翅膀根"骨骼（{@code wingright} / {@code wingLeft} ✓），绕 Z 轴按 {@code flap} 摆动 ✓，
 * 羽片子骨骼由父子关系自动跟随 ✓ —— 所以"飞的时候会动"不需要关键帧 ✓（以后要播你的 {@code waving} 再说 ✓）。
 */
public final class TNLightWingsModel {

    private static final ResourceLocation GEO = ResourceLocation.fromNamespaceAndPath(
            "tnc", "geo/entity/light_wings.geo.json");
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(
            "tnc", "textures/entity/light_wings_bedrock.png");

    /** 透明度：小一点更透 ✓（作者："翅膀要有点透明"） */
    private static final float ALPHA = 0.78F;

    private static List<Bone> bones;
    private static boolean failed;
    private static int texW = 64;
    private static int texH = 64;

    private TNLightWingsModel() {
    }

    private static final class Cube {
        float x0, y0, z0, x1, y1, z1;
        float u, v;
        float rx, ry, rz;          // 度；绕方块自身 from 点旋转 ✓（Bedrock 默认枢轴）
    }

    private static final class Bone {
        String name;
        String parent;
        float px, py, pz;
        final List<Cube> cubes = new ArrayList<>();
    }

    private static void load() {
        if (bones != null || failed) {
            return;
        }
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(GEO);
            if (res.isEmpty()) {
                failed = true;
                return;
            }
            try (BufferedReader reader = res.get().openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject geo = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
                JsonObject desc = geo.getAsJsonObject("description");
                if (desc.has("texture_width")) {
                    texW = desc.get("texture_width").getAsInt();
                }
                if (desc.has("texture_height")) {
                    texH = desc.get("texture_height").getAsInt();
                }
                List<Bone> list = new ArrayList<>();
                for (JsonElement be : geo.getAsJsonArray("bones")) {
                    JsonObject bo = be.getAsJsonObject();
                    Bone bone = new Bone();
                    bone.name = bo.get("name").getAsString();
                    bone.parent = bo.has("parent") ? bo.get("parent").getAsString() : null;
                    JsonArray p = bo.getAsJsonArray("pivot");
                    if (p != null) {
                        bone.px = p.get(0).getAsFloat();
                        bone.py = p.get(1).getAsFloat();
                        bone.pz = p.get(2).getAsFloat();
                    }
                    if (bo.has("cubes")) {
                        for (JsonElement ce : bo.getAsJsonArray("cubes")) {
                            JsonObject co = ce.getAsJsonObject();
                            Cube c = new Cube();
                            JsonArray f = co.getAsJsonArray("origin");
                            JsonArray s = co.getAsJsonArray("size");
                            c.x0 = f.get(0).getAsFloat();
                            c.y0 = f.get(1).getAsFloat();
                            c.z0 = f.get(2).getAsFloat();
                            c.x1 = c.x0 + s.get(0).getAsFloat();
                            c.y1 = c.y0 + s.get(1).getAsFloat();
                            c.z1 = c.z0 + s.get(2).getAsFloat();
                            JsonArray uv = co.getAsJsonArray("uv");
                            if (uv != null) {
                                c.u = uv.get(0).getAsFloat();
                                c.v = uv.get(1).getAsFloat();
                            }
                            if (co.has("rotation")) {
                                JsonArray r = co.getAsJsonArray("rotation");
                                if (r != null && r.size() == 3) {
                                    c.rx = r.get(0).getAsFloat();
                                    c.ry = r.get(1).getAsFloat();
                                    c.rz = r.get(2).getAsFloat();
                                }
                            }
                            bone.cubes.add(c);
                        }
                    }
                    list.add(bone);
                }
                bones = list;
            }
        } catch (Throwable t) {
            failed = true;
        }
    }

    /** 在玩家背上画一对光翼 ✓。flap 单位是度（正值＝扇起 ✓）。 */
    public static void render(PoseStack pose, VertexConsumer vc, float flap) {
        load();
        if (bones == null) {
            return;
        }
        Map<String, Matrix4f> world = new HashMap<>();
        for (Bone bone : bones) {
            Matrix4f m = new Matrix4f();
            if (bone.parent != null && world.containsKey(bone.parent)) {
                m.set(world.get(bone.parent));
            }
            // 绕骨骼 pivot：translate(p) * rot(bone) * translate(-p)
            m.translate(bone.px / 16.0F, bone.py / 16.0F, bone.pz / 16.0F);
            String n = bone.name.toLowerCase();
            if (n.equals("wingright") || n.equals("wingleft")) {
                // 两根翅膀根：绕 Z 扇动（左翼反向 ✓）
                float deg = n.equals("wingleft") ? -flap : flap;
                m.rotate(Axis.ZP.rotationDegrees(deg));
            }
            m.translate(-bone.px / 16.0F, -bone.py / 16.0F, -bone.pz / 16.0F);
            world.put(bone.name, m);

            for (Cube c : bone.cubes) {
                Matrix4f cube = new Matrix4f();
                cube.set(m);
                if (c.rx != 0.0F || c.ry != 0.0F || c.rz != 0.0F) {
                    cube.translate(c.x0 / 16.0F, c.y0 / 16.0F, c.z0 / 16.0F);
                    if (c.rz != 0.0F) {
                        cube.rotate(Axis.ZP.rotationDegrees(c.rz));
                    }
                    if (c.ry != 0.0F) {
                        cube.rotate(Axis.YP.rotationDegrees(c.ry));
                    }
                    if (c.rx != 0.0F) {
                        cube.rotate(Axis.XP.rotationDegrees(c.rx));
                    }
                    cube.translate(-c.x0 / 16.0F, -c.y0 / 16.0F, -c.z0 / 16.0F);
                }
                box(vc, cube, c);
            }
        }
    }

    /** 画一个方块（Bedrock 的盒子 UV 展开 ✓，UV 归一化到 0..1 ✓，贴图是独立文件不是图集 ✓）。 */
    private static void box(VertexConsumer vc, Matrix4f m, Cube c) {
        float w = c.x1 - c.x0, h = c.y1 - c.y0, d = c.z1 - c.z0;
        float u = c.u, v = c.v;
        float[] up = {u + d, v, w, d};
        float[] down = {u + d + w, v, w, d};
        float[] east = {u, v + d, d, h};
        float[] north = {u + d, v + d, w, h};
        float[] west = {u + d + w, v + d, d, h};
        float[] south = {u + d + w + d, v + d, w, h};
        float x0 = c.x0 / 16.0F, y0 = c.y0 / 16.0F, z0 = c.z0 / 16.0F;
        float x1 = c.x1 / 16.0F, y1 = c.y1 / 16.0F, z1 = c.z1 / 16.0F;
        quad(vc, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, up);          // up
        quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, down);        // down
        quad(vc, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, east);        // east
        quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, north);       // north
        quad(vc, m, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, west);        // west
        quad(vc, m, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, south);       // south
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float az,
                             float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz, float[] uv) {
        float u0 = uv[0] / texW, v0 = uv[1] / texH;
        float u1 = (uv[0] + uv[2]) / texW, v1 = (uv[1] + uv[3]) / texH;
        vc.vertex(m, ax, ay, az).color(1.0F, 1.0F, 1.0F, ALPHA).uv(u0, v0)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                .normal(0.0F, 0.0F, 1.0F).endVertex();
        vc.vertex(m, bx, by, bz).color(1.0F, 1.0F, 1.0F, ALPHA).uv(u1, v0)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                .normal(0.0F, 0.0F, 1.0F).endVertex();
        vc.vertex(m, cx, cy, cz).color(1.0F, 1.0F, 1.0F, ALPHA).uv(u1, v1)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                .normal(0.0F, 0.0F, 1.0F).endVertex();
        vc.vertex(m, dx, dy, dz).color(1.0F, 1.0F, 1.0F, ALPHA).uv(u0, v1)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                .normal(0.0F, 0.0F, 1.0F).endVertex();
    }
}