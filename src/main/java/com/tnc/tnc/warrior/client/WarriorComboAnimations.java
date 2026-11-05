package com.tnc.tnc.warrior.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tnc.tnc.warrior.WarriorAnimation;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 连招的<b>骨骼关键帧</b>（Bedrock 动画格式，Blockbench 直接导出）。
 *
 * <p>动作文件由美术在 Blockbench 里做，四把近战/远程武器各一份：
 * {@code assets/tnc/animations/entity/战士连招/长剑连招.json}、{@code 重剑连招.json}、
 * {@code 战锤连招.json}、{@code 长弓连招.json}。每条动画按 6 根骨骼（{@code bone} 根 /
 * {@code head} / 两条臂 / 两条腿）给"时间 → 角度"关键帧，单位是<b>度</b>、时间是<b>秒</b>。
 * 这里只做两件事：解析、按时间线性采样；真正的"把角度扣到 {@code HumanoidModel} 上"
 * 在 {@link WarriorPose}。
 *
 * <p><b>为什么不用 {@code ResourceLocation} 读</b>：资源路径里有中文（{@code 战士连招/长剑连招.json}），
 * 而 {@code ResourceLocation} 只允许 {@code [a-z0-9/._-]}，构造就会抛异常。所以这里直接按
 * classpath 读 jar 里的条目 —— 开发环境和打包后的 jar 都成立。
 *
 * <p><b>position 关键帧故意忽略</b>：文件里的位移（例如旋身斩的 {@code [0,0,-60]}）是"模型整体前冲"，
 * 但服务端已经把玩家真的推过去了（{@code dash}），再叠一次会看到人被推两次。
 *
 * <p>纯 Java（不 import 任何 Minecraft 类型），所以能在普通 JUnit 里验证解析与采样。
 */
public final class WarriorComboAnimations {

    /** 动作名 → 动画枚举。名字就是 Blockbench 里的动画名，改名字要同步改这里。 */
    private static final Map<String, WarriorAnimation> BY_CLIP = Map.ofEntries(
            Map.entry("长剑QZE", WarriorAnimation.SWORD_BREAKER),
            Map.entry("长剑QEQZZZ", WarriorAnimation.SWORD_SPIN),
            Map.entry("长剑QE长Z", WarriorAnimation.SWORD_STANCE),
            Map.entry("长剑ZZQQEE", WarriorAnimation.SWORD_COUNTER),
            Map.entry("重剑ZQE", WarriorAnimation.GS_SWEEP),
            Map.entry("重剑QQZ", WarriorAnimation.GS_SMASH),
            Map.entry("重剑EQZ", WarriorAnimation.GS_WALL),
            Map.entry("重剑QEE", WarriorAnimation.GS_STANCE),
            Map.entry("锤QZZ", WarriorAnimation.HM_CRUSH),
            Map.entry("锤EEZ", WarriorAnimation.HM_QUAKE),
            Map.entry("锤ZQQ", WarriorAnimation.HM_CUT),
            Map.entry("锤QEQ", WarriorAnimation.HM_STANCE),
            Map.entry("弓QZ", WarriorAnimation.BOW_AIM),
            Map.entry("弓EQ", WarriorAnimation.BOW_DASH_SHOT),
            Map.entry("弓ZE", WarriorAnimation.BOW_BACK_SHOT),
            Map.entry("SWORD_RETREAT", WarriorAnimation.SWORD_RETREAT),
            Map.entry("SWORD_SWALLOW", WarriorAnimation.SWORD_SWALLOW),
            Map.entry("GS_RIFT", WarriorAnimation.GS_RIFT),
            Map.entry("GS_ADVANCE", WarriorAnimation.GS_ADVANCE),
            Map.entry("HM_RUIN", WarriorAnimation.HM_RUIN),
            Map.entry("HM_DRIVE", WarriorAnimation.HM_DRIVE),
            Map.entry("BOW_PIERCE", WarriorAnimation.BOW_PIERCE),
            Map.entry("BOW_STRAFE", WarriorAnimation.BOW_STRAFE),
            Map.entry("BOW_VOLLEY", WarriorAnimation.BOW_VOLLEY),
            Map.entry("SPEAR_BREAK", WarriorAnimation.SPEAR_BREAK),
            Map.entry("SPEAR_DRAGON", WarriorAnimation.SPEAR_DRAGON),
            Map.entry("SPEAR_RETURN", WarriorAnimation.SPEAR_RETURN),
            Map.entry("SPEAR_GUARD", WarriorAnimation.SPEAR_GUARD),
            Map.entry("SPEAR_FLOW", WarriorAnimation.SPEAR_FLOW),
            Map.entry("SCYTHE_POUNCE", WarriorAnimation.SCYTHE_POUNCE),
            Map.entry("SCYTHE_REAP", WarriorAnimation.SCYTHE_REAP),
            Map.entry("SCYTHE_HOOK", WarriorAnimation.SCYTHE_HOOK),
            Map.entry("SCYTHE_GUARD", WarriorAnimation.SCYTHE_GUARD),
            Map.entry("SCYTHE_EXECUTE", WarriorAnimation.SCYTHE_EXECUTE),
            Map.entry("SCYTHE_WHIRL", WarriorAnimation.SCYTHE_WHIRL),
            Map.entry("BOW_CHARGE", WarriorAnimation.BOW_CHARGE),
            Map.entry("SWORD_CHARGE", WarriorAnimation.SWORD_CHARGE),
            Map.entry("GS_CHARGE", WarriorAnimation.GS_CHARGE),
            Map.entry("HM_CHARGE", WarriorAnimation.HM_CHARGE),
            Map.entry("SPEAR_CHARGE", WarriorAnimation.SPEAR_CHARGE));

    /**
     * 每把武器一组 classpath 路径：先认美术的原始文件名，再认一个纯 ASCII 的备用名。
     * 组内没读到就跳过这一组，其它武器照常加载 —— 少一份文件只影响那一把武器的动作。
     */
    private static final String[][] FILES = {
            {"/assets/tnc/animations/entity/战士连招/长剑连招.json",
             "/assets/tnc/animations/entity/sword_combo.json"},
            {"/assets/tnc/animations/entity/战士连招/重剑连招.json",
             "/assets/tnc/animations/entity/greatsword_combo.json"},
            {"/assets/tnc/animations/entity/战士连招/战锤连招.json",
             "/assets/tnc/animations/entity/hammer_combo.json"},
            {"/assets/tnc/animations/entity/战士连招/长弓连招.json",
             "/assets/tnc/animations/entity/bow_combo.json"},
            {"/assets/tnc/animations/entity/warrior_performance.json"},
            {"/assets/tnc/animations/entity/traditional_martial_arts.json"}};

    private static final float[] ZERO = {0f, 0f, 0f};

    /** 另一种读取方式：classpath 读不到时用它（生产环境由客户端注册，走 Forge 的 ModFile）。 */
    @FunctionalInterface
    public interface Opener {
        InputStream open(String path) throws IOException;
    }

    private static Map<String, Clip> loaded;
    private static Opener fallback;

    private WarriorComboAnimations() {
    }

    /** 一根骨骼的关键帧：时间（秒，升序）+ 对应的角度（度，xyz）。 */
    public record Track(float[] times, float[][] rotation) {

        public Track {
            if (times.length != rotation.length) {
                throw new IllegalArgumentException("关键帧时间与角度数量不一致");
            }
        }

        /** 线性采样；超出首尾关键帧时取端值。 */
        public float[] sample(float seconds) {
            int count = times.length;
            if (count == 0) {
                return ZERO.clone();
            }
            if (seconds <= times[0]) {
                return rotation[0].clone();
            }
            if (seconds >= times[count - 1]) {
                return rotation[count - 1].clone();
            }
            int next = 1;
            while (next < count && times[next] < seconds) {
                next++;
            }
            float from = times[next - 1];
            float to = times[next];
            float span = to - from;
            float f = span <= 1e-6f ? 1f : (seconds - from) / span;
            float[] a = rotation[next - 1];
            float[] b = rotation[next];
            return new float[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
        }
    }

    /** 一条完整动作：名字、时长（秒）、骨骼表。 */
    public record Clip(String name, float lengthSeconds, Map<String, Track> bones) {

        /** 某根骨骼在某一时刻的角度（度）；没动的骨骼返回 0。 */
        public float[] rotation(String bone, float seconds) {
            Track track = bones.get(bone);
            return track == null ? ZERO.clone() : track.sample(seconds);
        }

        /** 这条动作有没有动过某根骨骼。 */
        public boolean moves(String bone) {
            return bones.containsKey(bone);
        }
    }

    /** 解析一份 Blockbench 动画文件。 */
    public static Map<String, Clip> parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject animations = root.getAsJsonObject("animations");
        Map<String, Clip> out = new LinkedHashMap<>();
        if (animations == null) {
            return out;
        }
        for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
            JsonObject body = entry.getValue().getAsJsonObject();
            float length = body.has("animation_length") ? body.get("animation_length").getAsFloat() : 1f;
            Map<String, Track> bones = new LinkedHashMap<>();
            JsonObject boneMap = body.getAsJsonObject("bones");
            if (boneMap != null) {
                for (Map.Entry<String, JsonElement> bone : boneMap.entrySet()) {
                    JsonObject keys = bone.getValue().getAsJsonObject().getAsJsonObject("rotation");
                    if (keys == null) {
                        continue;
                    }
                    TreeMap<Float, float[]> frames = new TreeMap<>();
                    for (Map.Entry<String, JsonElement> key : keys.entrySet()) {
                        frames.put(Float.parseFloat(key.getKey()), vector(key.getValue()));
                    }
                    float[] times = new float[frames.size()];
                    float[][] values = new float[frames.size()][];
                    int index = 0;
                    for (Map.Entry<Float, float[]> frame : frames.entrySet()) {
                        times[index] = frame.getKey();
                        values[index] = frame.getValue();
                        index++;
                    }
                    bones.put(bone.getKey(), new Track(times, values));
                }
            }
            out.put(entry.getKey(), new Clip(entry.getKey(), length, Map.copyOf(bones)));
        }
        return out;
    }

    private static float[] vector(JsonElement element) {
        JsonArray array = element.getAsJsonArray();
        return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    /** 某个动作枚举对应的骨骼动画；没有对应文件/没加载到就返回 {@code null}（客户端回落到程序化姿势）。 */
    public static Clip clip(WarriorAnimation animation) {
        if (animation == null) {
            return null;
        }
        Map<String, Clip> all = all();
        for (Map.Entry<String, WarriorAnimation> mapping : BY_CLIP.entrySet()) {
            if (mapping.getValue() == animation) {
                return all.get(mapping.getKey());
            }
        }
        return null;
    }

    public static Clip clip(WarriorAnimation animation,String weaponId) {
        String key=com.tnc.tnc.warrior.WarriorMartialArts.clipKey(animation,weaponId);
        Clip variant=key==null?null:all().get(key);
        return variant==null?clip(animation):variant;
    }

    /** 已加载的全部动作（首次调用时读文件，失败就记住空表，不每帧重试）。 */
    public static Map<String, Clip> all() {
        if (loaded == null) {
            loaded = load();
        }
        return loaded;
    }

    /** 测试/重载用：忘掉缓存。 */
    static void forget() {
        loaded = null;
    }

    /**
     * 注册备用读取方式并清空缓存。
     *
     * <p>为什么要备用：开发环境里 {@code getResourceAsStream} 直接读 {@code build/resources/main}，
     * 一定成功；但正式客户端里 mod 是由 Forge 的 {@code SecureJar}/{@code ModFile} 挂上去的，
     * 中文条目名经 classloader 偶有读不到的情况（读不到就只剩程序化姿势，看起来像"动画没生效"）。
     * 客户端启动时注册一个走 {@code ModFile#findResource} 的兜底，两条路都试一遍。
     */
    public static void setFallback(Opener opener) {
        fallback = opener;
        loaded = null;
    }

    private static Map<String, Clip> load() {
        Map<String, Clip> all = new LinkedHashMap<>();
        for (String[] group : FILES) {
            for (String path : group) {
                String json = read(path);
                if (json == null) {
                    continue;
                }
                Map<String, Clip> parsed = parse(json);
                if (!parsed.isEmpty()) {
                    all.putAll(parsed);
                    break;
                }
            }
        }
        return all.isEmpty() ? new HashMap<>() : Map.copyOf(all);
    }

    private static String read(String path) {
        try (InputStream stream = open(path)) {
            if (stream == null) {
                return null;
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return null;
        }
    }

    /** 先走 classpath，再走客户端注册的兜底；两条都失败才返回 {@code null}。 */
    private static InputStream open(String path) {
        InputStream stream = WarriorComboAnimations.class.getResourceAsStream(path);
        if (stream != null || fallback == null) {
            return stream;
        }
        try {
            return fallback.open(path);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }
}
