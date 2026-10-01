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
 *   <li><b>有点透明</b> ✓：{@link #alpha}（0.78，越小越透 ✓；可在 config 文件里实时调 ✓）</li>
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
    private static final ResourceLocation ANIM = ResourceLocation.fromNamespaceAndPath(
            "tnc", "animations/entity/light_wings.animation.json");
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(
            "tnc", "textures/entity/light_wings_bedrock.png");

    /**
     * 透明度：小一点更透 ✓（作者："翅膀要有点透明"）——
     * 可由 {@code config/tnc/light_wings_placement.json} 改，**存盘 1 秒内生效** ✓（见 TNLightWingsPlacement ✓）。
     */
    private static float alpha = 0.78F;

    /** 由 {@link TNLightWingsRenderer} 每帧按配置设一次 ✓。 */
    public static void setAlpha(float value) {
        alpha = Math.max(0.05F, Math.min(1.0F, value));
    }

    private static List<Bone> bones;
    private static boolean failed;
    private static int texW = 64;
    private static int texH = 64;

    private TNLightWingsModel() {
    }

    private static final class Cube {
        float x0, y0, z0, x1, y1, z1;
        float u, v;
        float rx, ry, rz;          // 度；绕方块自身 pivot 旋转 ✓（Bedrock 默认枢轴）
        /** 方块的旋转枢轴（Bedrock 的 {@code pivot} 字段 ✓）；没写就用 origin 角 ✓。 */
        float px, py, pz;
        boolean hasPivot;
        /**
         * ★ <b>参考方块</b>（作者用来标"后背 / 胸前"的 ✓，2026-10-01）——
         * 判据：{@code uv} 有负数（作者的指南块写的是 {@code uv:[-4,-1]} ✓）。
         * 这种方块**只给程序算位置用，不进游戏画面** ✗ —— 否则玩家背上会多两块砖 ✗✗。
         */
        boolean guide;
    }

    private static final class Bone {
        String name;
        String parent;
        float px, py, pz;
        /** 骨骼自身的旋转（Bedrock 的 {@code rotation} 字段 ✓，绕 pivot ✓）。 */
        float rx, ry, rz;
        final List<Cube> cubes = new ArrayList<>();
    }

    /**
     * geo 有没有读进来（读不进来时渲染器改用备用翅膀 ✓ —— 绝不允许"什么都没有"✗）。
     *
     * <p>注意判据是"**至少有一个方块**" ✓：文件在、骨头也在、但一个方块都没有的情况
     * （写坏了/导出错了）同样应该走备用翅膀 ✗，不能算"读进来了" ✓。
     */
    public static boolean isLoaded() {
        load();
        if (bones == null) {
            return false;
        }
        for (Bone bone : bones) {
            if (!bone.cubes.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 自检/日志用：几根骨头、几个方块 ✓（"渲染在跑但看不见"时这一行能区分模型空不空 ✓）。 */
    public static String describe() {
        load();
        if (bones == null) {
            return "geo 未读入";
        }
        int cubes = 0;
        for (Bone bone : bones) {
            for (Cube c : bone.cubes) {
                if (!c.guide) {
                    cubes++;
                }
            }
        }
        String guide = guideCount() > 0 ? "，参考块 " + guideCount() + " 个（不画 ✓）" : "";
        return bones.size() + " 骨 / " + cubes + " 方块" + guide;
    }

    /**
     * 自检用：每个方块用到的 **UV 矩形**（和 {@link #box} 的展开方式一致 ✓）＋ 它在哪根骨头、多大 ✓。
     *
     * <p>用途：拿这些矩形去贴图里数不透明像素 ⇒ 一眼看出"哪些方块采到了空白"✗
     * （2026-10-01 的"模型有点不对"就是这个：羽毛的 v 用到 42，而贴图只画了 y 0..31 ✗）。
     */
    public record CubeReport(String bone, int index, float u, float v,
                             float sizeX, float sizeY, float sizeZ, float[][] faces) {
    }

    public static List<CubeReport> cubeUvReport() {
        List<CubeReport> out = new ArrayList<>();
        if (bones == null) {
            return out;
        }
        int i = 0;
        for (Bone bone : bones) {
            for (Cube c : bone.cubes) {
                if (c.guide) {
                    continue;                    // ★ 参考方块没有 UV，不进"采样检查" ✓
                }
                float w = c.x1 - c.x0, h = c.y1 - c.y0, d = c.z1 - c.z0;
                float u = c.u, v = c.v;
                float[][] faces = {
                        {u + d, v, w, d},                 // up
                        {u + d + w, v, w, d},             // down
                        {u, v + d, d, h},                 // east
                        {u + d, v + d, w, h},             // north
                        {u + d + w, v + d, d, h},         // west
                        {u + d + w + d, v + d, w, h},     // south
                };
                out.add(new CubeReport(bone.name, i, u, v, w, h, d, faces));
                i++;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    //  作者的 waving 关键帧动画（assets/tnc/animations/entity/light_wings.animation.json ✓）
    //  ★ 2026-10-01 作者："我希望在站着的时候翅膀不动的，你只有起飞的时候就播放那个 waving" ✓
    // ------------------------------------------------------------------

    /** 一个关键帧：时间（秒）+ 相对骨骼自身 rotation 的**增量**角度（度 ✓，Bedrock 是叠加的 ✓）。 */
    private record AnimKey(float time, float[] rot) {
    }

    /** 骨头名（小写 ✓）→ 按时间排好的关键帧 ✓ */
    private static Map<String, List<AnimKey>> animBones;
    /** 动画总长（秒 ✓，用来循环 ✓）。 */
    private static float animLength = 2.0F;
    /** 动画名字（日志用 ✓）。 */
    private static String animName = "（未读入）";
    private static boolean animFailed;

    /** 动画读进来了没（读不进来就退回程序化扇动 ✓，绝不允许"什么都不动"✗）。 */
    public static boolean hasAnimation() {
        return animBones != null && !animBones.isEmpty();
    }

    /** 渲染器每帧调一次（内部只读一次文件 ✓）。 */
    public static void ensureAnimationLoaded() {
        loadAnimation();
    }

    public static String describeAnimation() {
        return hasAnimation()
                ? "waving " + animLength + "s / " + animBones.size() + " 骨"
                : "无（退回程序化扇动）";
    }

    private static void loadAnimation() {
        if (animBones != null || animFailed) {
            return;
        }
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(ANIM);
            if (res.isEmpty()) {
                animFailed = true;
                return;
            }
            try (BufferedReader reader = res.get().openAsReader()) {
                parseAnimation(reader);
            }
        } catch (Throwable t) {
            animFailed = true;
            org.apache.logging.log4j.LogManager.getLogger("TN-C/light").warn(
                    "TN-C/light: 光翼动画读取失败，改用程序化扇动：{}", t.toString());
        }
    }

    /**
     * 纯解析 ✓（不碰 Minecraft 运行时 ✓ —— 单测可以直接喂 JSON ✓）。
     *
     * <p>只认第一段动画（作者的文件里就叫 {@code waving} ✓）；每个骨头只读 {@code rotation} 轨道
     * （这份文件也只有 rotation ✓），关键帧值支持两种写法：数组 ✓ 或 {@code {"post": [...]}} ✓。
     */
    static void parseAnimation(java.io.Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        JsonObject anims = root.getAsJsonObject("animations");
        if (anims == null || anims.size() == 0) {
            animFailed = true;
            return;
        }
        String first = anims.keySet().iterator().next();
        JsonObject anim = anims.getAsJsonObject(first);
        animName = first;
        if (anim.has("animation_length")) {
            animLength = Math.max(0.05F, anim.get("animation_length").getAsFloat());
        }
        Map<String, List<AnimKey>> map = new HashMap<>();
        JsonObject bonesObj = anim.getAsJsonObject("bones");
        if (bonesObj != null) {
            for (String boneName : bonesObj.keySet()) {
                JsonObject bone = bonesObj.getAsJsonObject(boneName);
                JsonObject rot = bone.getAsJsonObject("rotation");
                if (rot == null) {
                    continue;
                }
                List<AnimKey> keys = new ArrayList<>();
                for (String timeKey : rot.keySet()) {
                    float t;
                    try {
                        t = Float.parseFloat(timeKey);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    JsonElement value = rot.get(timeKey);
                    JsonArray arr = null;
                    if (value.isJsonArray()) {
                        arr = value.getAsJsonArray();
                    } else if (value.isJsonObject()) {
                        JsonObject obj = value.getAsJsonObject();
                        if (obj.has("post") && obj.get("post").isJsonArray()) {
                            arr = obj.getAsJsonArray("post");
                        } else if (obj.has("pre") && obj.get("pre").isJsonArray()) {
                            arr = obj.getAsJsonArray("pre");
                        }
                    }
                    if (arr == null || arr.size() < 3) {
                        continue;
                    }
                    keys.add(new AnimKey(t, new float[]{arr.get(0).getAsFloat(),
                            arr.get(1).getAsFloat(), arr.get(2).getAsFloat()}));
                }
                if (!keys.isEmpty()) {
                    keys.sort(java.util.Comparator.comparingDouble(AnimKey::time));
                    map.put(boneName.toLowerCase(), keys);
                }
            }
        }
        animBones = map;
    }

    /**
     * 求某个骨头在 {@code time} 秒时的旋转增量 ✓（线性插值 ✓、到尾巴保持最后一个关键帧 ✓、
     * 超过总长就按 {@code loop} 绕回去 ✓）。
     *
     * @return {@code [x, y, z]}（度 ✓）；这根骨头没被动画就返回全 0 ✓
     */
    static float[] animationRotation(String boneName, float time) {
        if (animBones == null) {
            return new float[]{0.0F, 0.0F, 0.0F};
        }
        List<AnimKey> keys = animBones.get(boneName.toLowerCase());
        if (keys == null || keys.isEmpty()) {
            return new float[]{0.0F, 0.0F, 0.0F};
        }
        float t = time;
        if (animLength > 0.0F) {
            t = t % animLength;
            if (t < 0.0F) {
                t += animLength;
            }
        }
        AnimKey first = keys.get(0);
        if (t <= first.time()) {
            return first.rot().clone();
        }
        for (int i = 1; i < keys.size(); i++) {
            AnimKey a = keys.get(i - 1);
            AnimKey b = keys.get(i);
            if (t <= b.time()) {
                float span = b.time() - a.time();
                float f = span <= 0.0F ? 0.0F : (t - a.time()) / span;
                return new float[]{
                        a.rot()[0] + (b.rot()[0] - a.rot()[0]) * f,
                        a.rot()[1] + (b.rot()[1] - a.rot()[1]) * f,
                        a.rot()[2] + (b.rot()[2] - a.rot()[2]) * f};
            }
        }
        return keys.get(keys.size() - 1).rot().clone();
    }

    /**
     * 离线预览用：每个"面"的 4 个顶点（模型坐标系 ✓，已经过**和渲染同一份**变换 ✓）
     * ＋ 4 组 UV（0..1 ✓，UV 展开也和 {@link #box} 一模一样 ✓）。
     *
     * <p>用途：把模型**带贴图**画成 PNG ✓ —— 我这边看不到游戏画面 ✗，
     * 光靠数字/字符画判断不了"这到底像不像翅膀"✗（作者 2026-10-01："你自己看看图片这是啥"✗）。
     */
    public record PreviewFace(String bone, int cubeIndex, boolean guide, float[][] pos, float[][] uv) {
    }

    /**
     * ★ <b>旋转手性</b>（2026-10-01 作者反馈："你每个随便自己转肯定不对的" ✗ —— 这次他猜对了 ✓）。
     *
     * <p>Bedrock/Blockbench 的 {@code rotation: [x,y,z]} 与 JOML 的 {@code rotateX/Y/Z(+θ)} 方向**相反** ✗：
     * 作者这个模型的**每根羽毛都靠 Z 旋转成扇**（25°~62.5° ✓），
     * 手性反了的时候羽毛会**朝里叠成一坨**✗（作者截图里那堆散板子 ✓），
     * 取反之后才是正常的扇形排布 ✓ —— 这是拿**离线预览 A/B 对照**看出来的 ✓
     * （{@code LightWingsPreviewTest.renderRotationHandednessAb} ✓ 会画出
     * {@code build/light_wings_rot_none|plus|minus.png} 三张纯色图 ✓）。
     *
     * <p>★ 所以游戏里用 {@link #ROT_MINUS} ✓（骨骼旋转与关键帧动画**一起**取反 ✓，否则动画也会反着摆 ✗）。
     */
    static final float ROT_PLUS = 1.0F;
    static final float ROT_MINUS = -1.0F;
    /** 游戏里实际用的手性 ✓（A/B 定下来的 ✓）。 */
    static final float ROT_SIGN = ROT_MINUS;

    /** 生成预览面 ✓（{@code animated=false} 时就是站着静止的姿势 ✓）。 */
    public static List<PreviewFace> previewFaces(float animTime, boolean animated) {
        return previewFaces(animTime, animated, ROT_PLUS);
    }

    /** 带旋转手性的预览 ✓（A/B 对比用 ✓）。 */
    public static List<PreviewFace> previewFaces(float animTime, boolean animated, float rotSign) {
        List<PreviewFace> out = new ArrayList<>();
        if (bones == null) {
            return out;
        }
        List<Matrix4f> matrices = buildMatrices(new Matrix4f(), 0.0F, animTime, animated, rotSign);
        int index = 0;
        for (Bone bone : bones) {
            for (Cube c : bone.cubes) {
                Matrix4f m = c.guide ? new Matrix4f() : (index < matrices.size() ? matrices.get(index) : new Matrix4f());
                float w = c.x1 - c.x0, h = c.y1 - c.y0, d = c.z1 - c.z0;
                float u = c.u, v = c.v;
                float x0 = c.x0 / 16.0F, y0 = c.y0 / 16.0F, z0 = c.z0 / 16.0F;
                float x1 = c.x1 / 16.0F, y1 = c.y1 / 16.0F, z1 = c.z1 / 16.0F;
                // 六面：顶点顺序 = box() 里那套"朝外逆时针"✓；UV 也照 box() 的展开 ✓
                addFace(out, bone.name, index, c.guide, m, texW, texH,
                        x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, u + d, v, w, d);
                addFace(out, bone.name, index, c.guide, m, texW, texH,
                        x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, u + d + w, v, w, d);
                addFace(out, bone.name, index, c.guide, m, texW, texH,
                        x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, u, v + d, d, h);
                addFace(out, bone.name, index, c.guide, m, texW, texH,
                        x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u + d, v + d, w, h);
                addFace(out, bone.name, index, c.guide, m, texW, texH,
                        x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, u + d + w, v + d, d, h);
                addFace(out, bone.name, index, c.guide, m, texW, texH,
                        x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u + d + w + d, v + d, w, h);
                if (!c.guide) {
                    index++;
                }
            }
        }
        return out;
    }

    private static void addFace(List<PreviewFace> out, String bone, int index, boolean guide, Matrix4f m,
                                int texW, int texH,
                                float ax, float ay, float az, float bx, float by, float bz,
                                float cx, float cy, float cz, float dx, float dy, float dz,
                                float u, float v, float w, float h) {
        float[][] pos = new float[4][];
        float[][] uv = new float[4][];
        float[][] src = {{ax, ay, az}, {bx, by, bz}, {cx, cy, cz}, {dx, dy, dz}};
        for (int i = 0; i < 4; i++) {
            org.joml.Vector4f p = new org.joml.Vector4f(src[i][0], src[i][1], src[i][2], 1.0F);
            m.transform(p);
            pos[i] = new float[]{p.x, p.y, p.z};
        }
        uv[0] = new float[]{u / texW, v / texH};
        uv[1] = new float[]{(u + w) / texW, v / texH};
        uv[2] = new float[]{(u + w) / texW, (v + h) / texH};
        uv[3] = new float[]{u / texW, (v + h) / texH};
        out.add(new PreviewFace(bone, index, guide, pos, uv));
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
                parse(reader);
            }
        } catch (Throwable t) {
            failed = true;
            // ★ 读失败必须留痕 ✗（原来是静默的 ⇒ 作者只会看到"没有翅膀"，查不出原因 ✗）
            org.apache.logging.log4j.LogManager.getLogger("TN-C/light").warn(
                    "TN-C/light: 光翼 geo 读取失败，改用备用翅膀：{}", t.toString());
        }
    }

    /**
     * 纯解析 ✓ —— <b>不碰任何 Minecraft 运行时</b>（只吃一个 {@code Reader} ✓）。
     *
     * <p>为什么要单独抽出来：2026-10-01 的"模型有点不对"只能靠**离线投影成字符画**来判 ✓
     * （我这边看不到画面 ✗），而 {@code load()} 里要 {@code Minecraft.getInstance()} ✗
     * ⇒ 单测没法用 ✗。抽出来之后 {@code LightWingsProjectionTest} 就能直接喂 JSON 文本，
     * 用**和渲染完全相同的那份数学**（{@link #buildMatrices} ✓）画出来 ✓。
     */
    static void parse(java.io.Reader reader) {
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
            // ★ 骨骼自身的旋转也要读（作者的 wingright 就是 [0,180,0] ✓ 不读就画反 ✗）
            JsonArray br = bo.getAsJsonArray("rotation");
            if (br != null && br.size() == 3) {
                bone.rx = br.get(0).getAsFloat();
                bone.ry = br.get(1).getAsFloat();
                bone.rz = br.get(2).getAsFloat();
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
                            // ★ 负 uv = 作者的"参考方块"（后背/胸前指南 ✓）⇒ 不画，只用来算位置 ✓
                            c.guide = c.u < 0.0F || c.v < 0.0F;
                            // ★ 旋转枢轴：Bedrock 用方块自己的 pivot ✗（不是 origin 角 ✗）——
                            //   作者这个模型里两者差最多 7 个单位（0.44 格）⇒ 用错枢轴整片羽毛会歪掉 ✗
                            c.px = c.x0;
                            c.py = c.y0;
                            c.pz = c.z0;
                            JsonArray cp = co.getAsJsonArray("pivot");
                            if (cp != null && cp.size() == 3) {
                                c.px = cp.get(0).getAsFloat();
                                c.py = cp.get(1).getAsFloat();
                                c.pz = cp.get(2).getAsFloat();
                                c.hasPivot = true;
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

    /**
     * 在玩家背上画一对光翼 ✓。flap 单位是度（正值＝扇起 ✓）。
     *
     * <h2>★★ 2026-10-01 事故：忘了乘 {@code PoseStack} ⇒ 翅膀画在<b>世界原点</b> ✗</h2>
     * 这里原来是 {@code Matrix4f m = new Matrix4f();}（单位矩阵）从头算骨骼 ✗ ——
     * 而 {@code pose} 里装的是"玩家在哪、朝哪、背上偏移多少" ✓，**一乘不上就等于把翅膀钉在世界原点** ✗:
     * {@code vc.vertex(m, …)} 只用 m 变换顶点 ✓，玩家位置/朝向全在 {@code pose} 里 ✓
     * ⇒ 渲染器明明在跑（日志 `光翼渲染已启动（作者 geo 模型）` ✓）、顶点也提交了 ✓、
     * 剔除也修了 ✓，玩家就是**一片都看不见** ✗（因为那一对翅膀正躺在坐标 (0,0,0) 附近 ✗）。
     *
     * <p>修法：根骨骼的矩阵**以 {@code pose.last().pose()} 为起点** ✓（子骨骼从父骨骼继承 ✓，
     * 所以整棵树都带上玩家变换了 ✓）。★ 这也是"自己写 geo 渲染"最容易漏的一步 ✗ ——
     * 备用程序化翅膀那边用的是 {@code pose.last().pose()} ✓，所以它一直是对的 ✓。
     */
    public static void render(PoseStack pose, VertexConsumer vc, float flap) {
        render(pose, vc, flap, 0.0F, false);
    }

    /** 带关键帧动画的渲染 ✓（{@code animated == false} 时就是静止的默认姿势 ✓）。 */
    public static void render(PoseStack pose, VertexConsumer vc, float flap,
                             float animTime, boolean animated) {
        load();
        if (bones == null) {
            return;
        }
        List<Matrix4f> matrices = buildMatrices(pose.last().pose(), flap, animTime, animated);
        int i = 0;
        for (Bone bone : bones) {
            for (Cube c : bone.cubes) {
                if (c.guide) {
                    continue;                    // ★ 索引要和 buildMatrices 对齐 ⇒ 这里也必须跳 ✓
                }
                if (i < matrices.size()) {
                    box(vc, matrices.get(i), c);
                }
                i++;
            }
        }
    }

    /**
     * 把每个方块的世界变换算出来 ✓ —— <b>渲染与离线自检共用这一份数学</b> ✗
     * （复制一份的话两边必然慢慢跑偏 ✗，而"模型有点不对"这种问题就是靠对账才能定位 ✓）。
     *
     * @param root 玩家坐标系（位置/朝向/背上偏移 ✓）；离线自检直接传单位矩阵 ✓
     * @param flap 程序化扇动角度（度 ✓；只在"没有关键帧动画"时当备用 ✓）
     */
    static List<Matrix4f> buildMatrices(Matrix4f root, float flap) {
        return buildMatrices(root, flap, 0.0F, false);
    }

    /**
     * @param animTime 动画时间（秒 ✓）—— 只有 {@code animated == true} 时才用它 ✓
     * @param animated 是否在播 {@code waving} 关键帧动画 ✓（站着的时候传 false ⇒ 翅膀**静止** ✓，
     *                 这正是作者 2026-10-01 要的："站着的时候翅膀不动的，只有起飞的时候播放那个 waving"✓）
     */
    static List<Matrix4f> buildMatrices(Matrix4f root, float flap, float animTime, boolean animated) {
        return buildMatrices(root, flap, animTime, animated, ROT_SIGN);
    }

    /** 带旋转手性的版本 ✓（A/B 对比/自检用 ✓；游戏里用 {@link #ROT_SIGN} ✓）。 */
    static List<Matrix4f> buildMatrices(Matrix4f root, float flap, float animTime, boolean animated,
                                        float rotSign) {
        List<Matrix4f> out = new ArrayList<>();
        if (bones == null) {
            return out;
        }
        Map<String, Matrix4f> world = new HashMap<>();
        for (Bone bone : bones) {
            Matrix4f m = new Matrix4f();
            if (bone.parent != null && world.containsKey(bone.parent)) {
                m.set(world.get(bone.parent));
            } else {
                m.set(root);                     // 根骨骼：从玩家坐标系出发 ✓
            }
            // 绕骨骼 pivot：translate(p) * rot(bone) * translate(-p)
            m.translate(bone.px / 16.0F, bone.py / 16.0F, bone.pz / 16.0F);
            String n = bone.name.toLowerCase();
            if (n.equals("wingright") || n.equals("wingleft")) {
                // 备用扇动：绕 Z 摆（左翼反向 ✓）；有作者动画时 flap 是 0 ✓
                float deg = n.equals("wingleft") ? -flap : flap;
                if (deg != 0.0F) {
                    m.rotate(Axis.ZP.rotationDegrees(deg));
                }
            }
            // ★ 作者的 waving 关键帧（**叠加**在骨骼自身 rotation 上 ✓ —— Bedrock 的动画就是相对的 ✓）
            float ax = 0.0F, ay = 0.0F, az = 0.0F;
            if (animated && animBones != null) {
                float[] off = animationRotation(bone.name, animTime);
                ax = off[0];
                ay = off[1];
                az = off[2];
            }
            // ★ 骨骼自身旋转（Bedrock 的 rotation ✓）：作者的 wingright 是 [0,180,0] ⇒ 右翼镜像 ✓
            //   手性 rotSign 和方块那边保持一致 ✓（否则骨骼和羽毛会朝相反方向转 ✗）
            if (bone.rz + az != 0.0F) {
                m.rotate(Axis.ZP.rotationDegrees((bone.rz + az) * rotSign));
            }
            if (bone.ry + ay != 0.0F) {
                m.rotate(Axis.YP.rotationDegrees((bone.ry + ay) * rotSign));
            }
            if (bone.rx + ax != 0.0F) {
                m.rotate(Axis.XP.rotationDegrees((bone.rx + ax) * rotSign));
            }
            m.translate(-bone.px / 16.0F, -bone.py / 16.0F, -bone.pz / 16.0F);
            world.put(bone.name, m);

            for (Cube c : bone.cubes) {
                if (c.guide) {
                    continue;                    // ★ 参考方块（后背/胸前）不进画面 ✗
                }
                Matrix4f cube = new Matrix4f();
                cube.set(m);
                if (c.rx != 0.0F || c.ry != 0.0F || c.rz != 0.0F) {
                    // 绕**方块自己的 pivot**转（没写 pivot 才退回 origin 角 ✓）
                    cube.translate(c.px / 16.0F, c.py / 16.0F, c.pz / 16.0F);
                    if (c.rz != 0.0F) {
                        cube.rotate(Axis.ZP.rotationDegrees(c.rz * rotSign));
                    }
                    if (c.ry != 0.0F) {
                        cube.rotate(Axis.YP.rotationDegrees(c.ry * rotSign));
                    }
                    if (c.rx != 0.0F) {
                        cube.rotate(Axis.XP.rotationDegrees(c.rx * rotSign));
                    }
                    cube.translate(-c.px / 16.0F, -c.py / 16.0F, -c.pz / 16.0F);
                }
                out.add(cube);
            }
        }
        return out;
    }

    /**
     * 离线自检：所有方块的 8 个角（格 ✓，已按 {@link #buildMatrices} 变换 ✓），
     * 每条是 {@code [x, y, z, 方块序号]} ✓ —— 单测据此画字符画/查对称 ✓。不碰 Minecraft 运行时 ✓。
     */
    public static List<float[]> corners(Matrix4f root, float flap) {
        return corners(root, flap, 0.0F, false);
    }

    /** 带动画的版本 ✓（离线验证"站着不动 / 飞起来播 waving"就是用这个 ✓）。 */
    public static List<float[]> corners(Matrix4f root, float flap, float animTime, boolean animated) {
        return cornersOf(root, flap, animTime, animated, false);
    }

    /**
     * ★ 作者那两个"后背 / 胸前"参考方块的位置 ✓（模型坐标系，未做玩家变换 ✗）——
     * 单测拿它 + {@link TNLightWingsPlacement} 的当前参数，算"这套摆放有没有把参考块放到身上" ✓。
     */
    public static List<float[]> guideCorners() {
        return cornersOf(new Matrix4f(), 0.0F, 0.0F, false, true);
    }

    /** 参考方块有几个 ✓（作者用它们标身体位置 ✓）。 */
    public static int guideCount() {
        int n = 0;
        if (bones != null) {
            for (Bone b : bones) {
                for (Cube c : b.cubes) {
                    if (c.guide) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private static List<float[]> cornersOf(Matrix4f root, float flap, float animTime,
                                          boolean animated, boolean guidesOnly) {
        List<float[]> out = new ArrayList<>();
        List<Matrix4f> matrices = buildMatrices(root, flap, animTime, animated);
        int i = 0;
        for (Bone bone : bones) {
            for (Cube c : bone.cubes) {
                if (c.guide != guidesOnly) {
                    if (!c.guide) {
                        i++;                     // ★ 索引只随"会画的方块"走（和 buildMatrices 对齐 ✓）
                    }
                    continue;
                }
                Matrix4f m;
                if (guidesOnly) {
                    // 参考方块在根骨骼上、没有旋转 ⇒ 直接平移即可 ✓
                    m = new Matrix4f(root).translate(c.x0 / 16.0F, c.y0 / 16.0F, c.z0 / 16.0F);
                } else {
                    m = i < matrices.size() ? matrices.get(i) : new Matrix4f(root);
                }
                // ★ 顶点坐标要用**绝对值**（c.x0..c.x1 ✓）—— 方块矩阵里**不含**方块的 origin 平移 ✗，
                //   平移是编码在顶点坐标里的 ✓。（参考块那条路是自己拼的 root*translate(origin) ✗，
                //   所以那边才用 0..size 的局部偏移 ✓ —— 两边不能混 ✗，2026-10-01 自己踩了一次 ✗）
                boolean absolute = !guidesOnly;
                for (int cx = 0; cx < 2; cx++) {
                    for (int cy = 0; cy < 2; cy++) {
                        for (int cz = 0; cz < 2; cz++) {
                            float lx = absolute ? (cx == 0 ? c.x0 : c.x1) / 16.0F
                                    : (cx == 0 ? 0.0F : (c.x1 - c.x0) / 16.0F);
                            float ly = absolute ? (cy == 0 ? c.y0 : c.y1) / 16.0F
                                    : (cy == 0 ? 0.0F : (c.y1 - c.y0) / 16.0F);
                            float lz = absolute ? (cz == 0 ? c.z0 : c.z1) / 16.0F
                                    : (cz == 0 ? 0.0F : (c.z1 - c.z0) / 16.0F);
                            org.joml.Vector4f v = new org.joml.Vector4f(lx, ly, lz, 1.0F);
                            m.transform(v);
                            out.add(new float[]{v.x, v.y, v.z, i});
                        }
                    }
                }
                if (!c.guide) {
                    i++;
                }
            }
        }
        return out;
    }

    /**
     * 画一个方块（Bedrock 的盒子 UV 展开 ✓，UV 归一化到 0..1 ✓，贴图是独立文件不是图集 ✓）。
     *
     * <h2>★★ 2026-10-01 事故：四个面的绕序是反的 ⇒ 整对翅膀被背面剔除吃掉了 ✗</h2>
     * {@code entityTranslucentEmissive} <b>开着背面剔除</b> ✓，而绕序必须是"从外面看逆时针" ✓。
     * 原来 up / down / north / south 四个面的顶点顺序恰好都写反了 ✗ ——
     * 而作者的翅膀正是**一单位厚的薄片**：大面积的脸就是 north / south ✗✗
     * ⇒ 渲染器明明在跑（日志里 `光翼渲染已启动（作者 geo 模型）` ✓）、顶点也提交了 ✓，
     * 玩家却**一片都看不见** ✗（作者："没看见翅膀"）。
     *
     * <p>绕序速查（右手定则，cross(ab, bc) 要指向**外侧** ✓）：
     * <pre>
     *   up    (+Y)：a(x0,y1,z1) b(x1,y1,z1) c(x1,y1,z0) d(x0,y1,z0)
     *   down  (−Y)：a(x0,y0,z0) b(x1,y0,z0) c(x1,y0,z1) d(x0,y0,z1)
     *   east  (+X)：a(x1,y0,z0) b(x1,y1,z0) c(x1,y1,z1) d(x1,y0,z1)   ← 本来就对
     *   north (−Z)：a(x1,y0,z0) b(x0,y0,z0) c(x0,y1,z0) d(x1,y1,z0)
     *   west  (−X)：a(x0,y0,z1) b(x0,y1,z1) c(x0,y1,z0) d(x0,y0,z0)   ← 本来就对
     *   south (+Z)：a(x0,y0,z1) b(x1,y0,z1) c(x1,y1,z1) d(x0,y1,z1)
     * </pre>
     * 改完如果哪天又"看不见"，先查这一条 ✓（也可以临时换成不剔除的渲染类型验证 ✓）。
     */
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
        quad(vc, m, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, up);          // up    (+Y)
        quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, down);        // down  (−Y)
        quad(vc, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, east);        // east  (+X)
        quad(vc, m, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, north);       // north (−Z)
        quad(vc, m, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, west);        // west  (−X)
        quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, south);       // south (+Z)
    }

    /**
     * 画一个面 —— <b>正反两面都提交</b> ✓（见下）。
     *
     * <p>★ 翅膀本来就是"发光半透明的一片"，没有正/背面之分 ✓；而
     * {@code entityTranslucentEmissive} **开着背面剔除** ✗ ⇒ 只要绕序写反（本文件刚踩过一次 ✗）
     * 整片就消失，而且**一声不响** ✗（作者："没看见翅膀"）。所以这里正反各提交一次 ✓：
     * 绕序对不对都看得见 ✓，代价只是顶点数 ×2（整对翅膀不到 2000 个顶点 ✓）。
     */
    private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float az,
                             float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz, float[] uv) {
        float u0 = uv[0] / texW, v0 = uv[1] / texH;
        float u1 = (uv[0] + uv[2]) / texW, v1 = (uv[1] + uv[3]) / texH;
        vertex(vc, m, ax, ay, az, u0, v0);
        vertex(vc, m, bx, by, bz, u1, v0);
        vertex(vc, m, cx, cy, cz, u1, v1);
        vertex(vc, m, dx, dy, dz, u0, v1);
        // 反面：顶点顺序倒着再来一遍 ⇒ 绕序相反 ⇒ 从另一侧也画得出来 ✓
        vertex(vc, m, dx, dy, dz, u0, v1);
        vertex(vc, m, cx, cy, cz, u1, v1);
        vertex(vc, m, bx, by, bz, u1, v0);
        vertex(vc, m, ax, ay, az, u0, v0);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v) {
        vc.vertex(m, x, y, z).color(1.0F, 1.0F, 1.0F, alpha).uv(u, v)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                .normal(0.0F, 0.0F, 1.0F).endVertex();
    }
}