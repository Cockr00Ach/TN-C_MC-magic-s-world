package com.tnc.tnc.light.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 光翼**位置/大小**的可调参数 ✓ —— 放在 {@code config/tnc/light_wings_placement.json}，
 * 游戏里**改完存盘 1 秒内自动生效** ✓，不用重启、不用重装 ✗。
 *
 * <h2>为什么要有这个文件（2026-10-01）</h2>
 * 作者："出现了但是模型有点不对吧" ✗ —— 而"贴在哪、多大、朝哪"是**纯视觉**的事 ✓，
 * 我在这边看不到画面 ✗：每改一个数就要构建 → 装机 → 作者重启进游戏看一眼 ✗（一轮十分钟 ✗）。
 * 所以把这些数**挪出代码**：作者开着游戏改文件、存盘，一秒钟后画面就变了 ✓，
 * 调好之后把文件里的数值发我，我照抄成默认值 ✓（默认值仍是这套 ✓）。
 *
 * <h2>字段（都是相对玩家脚下原点的，单位＝格 ✓）</h2>
 * <table border="1">
 *   <tr><th>字段</th><th>默认</th><th>意思</th></tr>
 *   <tr><td>{@code back_y}</td><td>1.18</td><td>模型原点的高度（1.18 ≈ 肩胛）；想让翅膀整体上移就加大 ✓</td></tr>
 *   <tr><td>{@code back_z}</td><td>0.16</td><td>往**背后**推多少；负数＝推到胸前 ✗（贴反了就用这个试 ✓）</td></tr>
 *   <tr><td>{@code yaw_offset}</td><td>180</td><td>左右/正反朝向；贴反了先试 0 或 180 ✓</td></tr>
 *   <tr><td>{@code scale}</td><td>1.0</td><td>整体大小 ✓（0.5＝一半、2.0＝两倍 ✓）</td></tr>
 *   <tr><td>{@code alpha}</td><td>0.78</td><td>透明度：越小越透 ✓（作者原来要"有点透明"✓）</td></tr>
 *   <tr><td>{@code flap_speed_flying} / {@code flap_amp_flying}</td><td>0.85 / 42</td><td>飞行时的扇动速度 / 幅度（度）✓</td></tr>
 *   <tr><td>{@code flap_speed_idle} / {@code flap_amp_idle}</td><td>0.18 / 9</td><td>站着/走路时的轻微扇动 ✓</td></tr>
 * </table>
 *
 * <p>★ 文件不存在时**自己写一份默认的** ✓（第一次进游戏就会出现在 `config/tnc/` 下 ✓）。
 * 读失败/字段缺失都**保留原值**（不会把翅膀弄没 ✗），并且日志会打一行
 * {@code TN-C/light: 光翼放置参数 …} ✓ —— 调完把那行发我即可 ✓。
 */
public final class TNLightWingsPlacement {

    /** 模型原点相对玩家脚下原点的高度（格）。1.18 ≈ 肩胛位置 ✓。 */
    public static float backY = 1.18F;
    /**
     * 沿**模型 +Z**（＝玩家<b>胸前</b>方向 ✓）推多远 ✓。
     *
     * <h2>★★ 2026-10-01 修正：朝向反了（作者："翅膀位置还是不对啊"）</h2>
     * 作者在模型里加了两个参考方块，标"**后背**"（z 1..4 单位）与"**胸前**"（z 4..7 单位）✓
     * ⇒ 模型空间里**身体在 +Z 一侧** ✓，也就是 <b>+Z ＝ 胸口方向</b>（Bedrock 玩家模型的标准朝向 ✓，
     * 同一套里 +X ＝ 玩家的左手 ✓）。
     *
     * <p>而原来是 {@code yawOffset = 180} ✗（把 +Z 当成了"背后"）⇒ 那两个参考块会被推到
     * **角色背后 0.22~0.60 格**悬空 ✗（作者一眼就看出"位置不对"✗），翅膀也跟着落在错的一侧 ✗。
     *
     * <p>现在的取值：{@code yawOffset = 0}（+Z = 胸前 ✓）＋ {@code backZ = -0.25}
     * ＝ **模型原点落在身体中心往后 0.25 格**（躯干背面就在中心后 0.125 格 ✓）
     * ⇒ 翅膀（z 0..2 单位 = 0..0.125 格）正好**贴在背面上** ✓，
     * 而作者那两个参考块则正好**套在身上** ✓（它们的中心在 +0.25 ⇒ 世界坐标 0 ＝ 身体中心 ✓）。
     */
    public static float backZ = -0.25F;
    /** 朝向偏移（度）✓ —— 0 ＝ 模型 +Z 对准玩家胸前 ✓（由作者 2026-10-01 的参考块定 ✓）。 */
    public static float yawOffset = 0.0F;
    /** 整体缩放 ✓。 */
    public static float scale = 1.0F;
    /** 透明度（越小越透 ✓）。 */
    public static float alpha = 0.78F;
    /** 飞行时的扇动速度与幅度（度）✓ —— **只在没有关键帧动画时当备用** ✓。 */
    public static float flapSpeedFlying = 0.85F;
    public static float flapAmpFlying = 42.0F;
    /**
     * 站着/走路时的轻微扇动 ✓ —— ★ 默认 **0 ⇒ 完全静止** ✓
     * （作者 2026-10-01："我希望在站着的时候翅膀不动的"✓；想让站姿也轻轻摆就把幅度调大 ✓）。
     */
    public static float flapSpeedIdle = 0.18F;
    public static float flapAmpIdle = 0.0F;
    /**
     * ★ 作者 2026-10-01："你只有起飞的时候就播放那个 waving" ✓
     * ⇒ 飞起来播 {@code waving} 关键帧动画（默认开 ✓），站着不播（默认关 ✓）。
     */
    public static boolean wavingWhenFlying = true;
    public static boolean wavingWhenIdle = false;
    /** 动画播放速度倍率（1.0 = 原速 ✓）。 */
    public static float animationSpeed = 1.0F;
    /** 竖直速度带来的仰角系数（只在飞的时候生效 ✓；设 0 就完全不仰 ✓）。 */
    public static float pitchFactor = 0.3F;

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/light");

    private static final String DIR = "config/tnc";
    private static final String NAME = "light_wings_placement.json";

    /** 上次检查文件的时间（毫秒）与当时的 mtime ✓ —— 一秒查一次，改动即时生效 ✓。 */
    private static long lastCheck;
    private static long lastModified = -1L;
    private static boolean warned;

    private TNLightWingsPlacement() {
    }

    /** 每帧都会调（内部一秒只查一次文件 ✓，开销可忽略 ✓）。 */
    public static void tick() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 1000L) {
            return;
        }
        lastCheck = now;
        try {
            Path file = file();
            if (!Files.exists(file)) {
                writeDefault(file);
                return;
            }
            long modified = Files.getLastModifiedTime(file).toMillis();
            if (modified == lastModified) {
                return;
            }
            lastModified = modified;
            load(file);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                LOGGER.warn("TN-C/light: 光翼放置参数读取失败（继续用当前值）：{}", t.toString());
            }
        }
    }

    /** 供日志/自检用的一行摘要 ✓。 */
    public static String describe() {
        return String.format(java.util.Locale.ROOT,
                "back_y=%.3f back_z=%.3f yaw=%.1f scale=%.3f alpha=%.2f "
                        + "waving_fly=%s waving_idle=%s anim_speed=%.2f pitch=%.2f "
                        + "flap_fly=%.2f/%.1f flap_idle=%.2f/%.1f",
                backY, backZ, yawOffset, scale, alpha,
                wavingWhenFlying, wavingWhenIdle, animationSpeed, pitchFactor,
                flapSpeedFlying, flapAmpFlying, flapSpeedIdle, flapAmpIdle);
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(DIR).resolve(NAME);
    }

    /** 第一次进游戏时写一份带说明的默认文件 ✓（作者直接改这个文件就行 ✓）。 */
    private static void writeDefault(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        String text = """
                {
                  "_说明": "光翼在玩家背上的位置/大小/动作 —— 改完存盘, 1 秒内自动生效(不用重启游戏)。调好把日志里那行『光翼放置参数』发我, 我抄成默认值。",
                  "_字段说明": {
                    "back_y": "模型原点的高度(格), 1.18≈肩胛; 想整体上移就加大",
                    "back_z": "沿模型+Z(胸前方向)推多远; -0.25=原点在身体中心往后0.25格(贴背)",
                    "yaw_offset": "朝向(度), 0=模型+Z对准胸前(按作者的参考块定); 贴反了就试 180",
                    "scale": "整体大小, 0.5=一半, 2.0=两倍",
                    "alpha": "透明度, 越小越透",
                    "waving_when_flying": "飞的时候播作者的 waving 关键帧动画(true/false)",
                    "waving_when_idle": "站着的时候也播动画(默认 false = 站着完全不动)",
                    "animation_speed": "动画速度倍率, 1.0=原速",
                    "pitch_factor": "飞行时按竖直速度仰头的幅度(0=完全不仰)",
                    "flap_speed_flying/flap_amp_flying": "★备用★ 没读到动画时才用的程序化扇动(飞行)",
                    "flap_speed_idle/flap_amp_idle": "★备用★ 站着的扇动幅度, 默认 0 = 静止"
                  },
                  "back_y": 1.18,
                  "back_z": -0.25,
                  "yaw_offset": 0.0,
                  "scale": 1.0,
                  "alpha": 0.78,
                  "waving_when_flying": true,
                  "waving_when_idle": false,
                  "animation_speed": 1.0,
                  "pitch_factor": 0.3,
                  "flap_speed_flying": 0.85,
                  "flap_amp_flying": 42.0,
                  "flap_speed_idle": 0.18,
                  "flap_amp_idle": 0.0
                }
                """;
        Files.writeString(file, text, StandardCharsets.UTF_8);
        lastModified = Files.getLastModifiedTime(file).toMillis();
        LOGGER.info("TN-C/light: 已写出默认光翼放置参数 {}（{}）", file, describe());
    }

    private static void load(Path file) throws Exception {
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        backY = read(root, "back_y", backY, -4.0F, 6.0F);
        backZ = read(root, "back_z", backZ, -3.0F, 3.0F);
        yawOffset = read(root, "yaw_offset", yawOffset, -360.0F, 360.0F);
        scale = read(root, "scale", scale, 0.05F, 8.0F);
        alpha = read(root, "alpha", alpha, 0.05F, 1.0F);
        flapSpeedFlying = read(root, "flap_speed_flying", flapSpeedFlying, 0.0F, 4.0F);
        flapAmpFlying = read(root, "flap_amp_flying", flapAmpFlying, 0.0F, 90.0F);
        flapSpeedIdle = read(root, "flap_speed_idle", flapSpeedIdle, 0.0F, 4.0F);
        flapAmpIdle = read(root, "flap_amp_idle", flapAmpIdle, 0.0F, 90.0F);
        wavingWhenFlying = readBool(root, "waving_when_flying", wavingWhenFlying);
        wavingWhenIdle = readBool(root, "waving_when_idle", wavingWhenIdle);
        animationSpeed = read(root, "animation_speed", animationSpeed, 0.05F, 6.0F);
        pitchFactor = read(root, "pitch_factor", pitchFactor, 0.0F, 3.0F);
        LOGGER.info("TN-C/light: 光翼放置参数 {}", describe());
    }

    /** 读一个开关 ✓：缺字段保留原值 ✓，写错类型也保留原值 ✓。 */
    private static boolean readBool(JsonObject root, String key, boolean fallback) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return value.getAsBoolean();
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** 读一个数：缺字段就保留原值 ✓，超范围就夹住 ✓（永远不会把翅膀弄没 ✗）。 */
    private static float read(JsonObject root, String key, float fallback, float min, float max) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            float v = value.getAsFloat();
            if (Float.isNaN(v) || Float.isInfinite(v)) {
                return fallback;
            }
            return Math.max(min, Math.min(max, v));
        } catch (Throwable t) {
            return fallback;
        }
    }
}
