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
    /** 往背后推的距离（格）；负数＝推到胸前 ✓。 */
    public static float backZ = 0.16F;
    /** 朝向偏移（度）✓。 */
    public static float yawOffset = 180.0F;
    /** 整体缩放 ✓。 */
    public static float scale = 1.0F;
    /** 透明度（越小越透 ✓）。 */
    public static float alpha = 0.78F;
    /** 飞行时的扇动速度与幅度（度）✓。 */
    public static float flapSpeedFlying = 0.85F;
    public static float flapAmpFlying = 42.0F;
    /** 站着/走路时的轻微扇动 ✓。 */
    public static float flapSpeedIdle = 0.18F;
    public static float flapAmpIdle = 9.0F;

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
                        + "flap_fly=%.2f/%.1f flap_idle=%.2f/%.1f",
                backY, backZ, yawOffset, scale, alpha,
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
                  "_说明": "光翼在玩家背上的位置/大小 —— 改完存盘, 1 秒内自动生效(不用重启游戏)。调好把日志里那行『光翼放置参数』发我, 我抄成默认值。",
                  "_字段说明": {
                    "back_y": "模型原点的高度(格), 1.18≈肩胛; 想整体上移就加大",
                    "back_z": "往背后推多少(格), 负数=推到胸前(贴反了先试这个)",
                    "yaw_offset": "朝向(度), 贴反了先试 0 或 180",
                    "scale": "整体大小, 0.5=一半, 2.0=两倍",
                    "alpha": "透明度, 越小越透",
                    "flap_speed_flying/flap_amp_flying": "飞行时扇动速度/幅度(度)",
                    "flap_speed_idle/flap_amp_idle": "站着走路时的轻微扇动"
                  },
                  "back_y": 1.18,
                  "back_z": 0.16,
                  "yaw_offset": 180.0,
                  "scale": 1.0,
                  "alpha": 0.78,
                  "flap_speed_flying": 0.85,
                  "flap_amp_flying": 42.0,
                  "flap_speed_idle": 0.18,
                  "flap_amp_idle": 9.0
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
        LOGGER.info("TN-C/light: 光翼放置参数 {}", describe());
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
