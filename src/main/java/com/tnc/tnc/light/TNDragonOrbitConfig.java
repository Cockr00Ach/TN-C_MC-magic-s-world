package com.tnc.tnc.light;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * <b>光龙 t2「三条小龙绕着自己转」的可调参数</b> ✓ —— {@code config/tnc/dragon_orbit.json}，
 * 改完存盘、<b>下一次放 t2 就生效</b> ✓（不用重启、不用重装 ✓）。
 *
 * <h2>为什么要挪出来（作者 2026-10-03）</h2>
 * 作者："<b>环绕飞龙有点卡顿，并且应该是三条龙头对尾绕成一个圆接在一起</b>" ✓ ——
 * "环多大、转多快、往哪边转"这些都是**一眼就能看出来**的数 ✓，我在这边看不到画面 ✗：
 * 每调一个数都要 构建 → 装机 → 作者重启进游戏看一眼 ✗。所以照 {@code TNLightWingsPlacement}
 * 那一套，把数挪进配置文件 ✓（那个是**客户端**读的 ✓，这个是**服务端**读的 ⇒ 用
 * {@link FMLPaths#CONFIGDIR} 而不是 {@code Minecraft.getInstance()} ✗）。
 *
 * <h2>字段</h2>
 * <table border="1">
 *   <tr><th>字段</th><th>默认</th><th>意思</th></tr>
 *   <tr><td>{@code direction}</td><td>1</td><td>往哪边转（+1 / -1）✓ —— <b>三条龙的"肚皮"朝外拐</b>就把这个取反 ✓</td></tr>
 *   <tr><td>{@code radius_factor}</td><td>1.0</td><td>环半径倍率；<b>1.0 = 三条正好首尾相接围成一个整圆</b> ✓（大于 1 就会拉开缝 ✓）</td></tr>
 *   <tr><td>{@code height}</td><td>1.2</td><td>环心比主人**脚底**高多少格 ✓</td></tr>
 *   <tr><td>{@code deg_per_tick}</td><td>3.0</td><td>每秒转多少度（20 tick = 1 秒）✓ 3.0 ≈ 2 秒一圈 ✓</td></tr>
 *   <tr><td>{@code ticks}</td><td>400</td><td>转多久（tick）✓ 400 = 20 秒 ✓（和"光龙鳞甲"的 buff 一样长 ✓）</td></tr>
 *   <tr><td>{@code count}</td><td>3</td><td>几条龙 ✓（数量变了环的"首尾相接"角度也跟着变 ✓）</td></tr>
 * </table>
 *
 * <p>★ 文件不存在时**自己写一份默认的** ✓；读失败 / 字段缺失都保留原值 ✓（不会把环绕弄没 ✗），
 * 并且日志会打一行 {@code TN-C/light: 光龙环绕参数 …} ✓ —— 调好把那行发我即可 ✓。
 */
public final class TNDragonOrbitConfig {

    /** 旋转方向：+1 / -1 ✓（三条龙拐向中心的那一侧就是对的 ✓ 见类注释 ✓）。 */
    public static int direction = 1;
    /**
     * 环半径倍率 ✓ —— 1.0 是"三条龙首尾正好接上"的几何值（半径 ≈ 3.3 格 ✓）。
     *
     * <p>★ 作者 2026-10-04："我希望是龙绕着一个圆环绕，现在的实机效果是三条弯弯的龙在那转，
     * 根本不是圆" ✗ ⇒ 现在**不再把身体掰弯** ✗、龙直着飞 ✓，所以环要**明显更大**才看得出是圆 ✓；
     * 默认给 2.5（半径 ≈ 8.2 格，直径 ≈ 16 格 ✓）—— 这就是"绕着一个圆环飞"该有的样子 ✓。
     */
    public static double radiusFactor = 2.5D;
    /** 环心相对主人脚底的高度（格 ✓）。 */
    public static double height = 1.2D;
    /** 每秒转多少度（度/tick ✓）。 */
    public static double degPerTick = 3.0D;
    /** 转多久（tick ✓）。 */
    public static int ticks = 400;
    /** 几条龙 ✓。 */
    public static int count = 3;

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/light");

    private static final String DIR = "tnc";
    private static final String NAME = "dragon_orbit.json";

    private TNDragonOrbitConfig() {
    }

    /**
     * 每次放 t2 的时候调一次 ✓（就一个几行的本地文件，开销可忽略 ✓；
     * 这样**不用**再挂一个每帧检查的钩子 ✓）。
     */
    public static void reload() {
        try {
            Path file = FMLPaths.CONFIGDIR.get().resolve(DIR).resolve(NAME);
            if (!Files.exists(file)) {
                writeDefault(file);
                return;
            }
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            direction = readSign(root, "direction", direction);
            radiusFactor = read(root, "radius_factor", radiusFactor, 0.05D, 8.0D);
            height = read(root, "height", height, -8.0D, 16.0D);
            degPerTick = read(root, "deg_per_tick", degPerTick, 0.05D, 60.0D);
            ticks = (int) read(root, "ticks", ticks, 20.0D, 24000.0D);
            count = (int) read(root, "count", count, 1.0D, 12.0D);
            LOGGER.info("TN-C/light: 光龙环绕参数 {}", describe());
        } catch (Throwable t) {
            LOGGER.warn("TN-C/light: 光龙环绕参数读取失败（继续用当前值）：{}", t.toString());
        }
    }

    /** 供日志/自检用的一行摘要 ✓。 */
    public static String describe() {
        return String.format(java.util.Locale.ROOT,
                "direction=%d radius_factor=%.3f height=%.2f deg_per_tick=%.2f ticks=%d count=%d",
                direction, radiusFactor, height, degPerTick, ticks, count);
    }

    /** 第一次放 t2 时写一份带说明的默认文件 ✓（作者直接改这个文件就行 ✓）。 */
    private static void writeDefault(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        String text = """
                {
                  "_说明": "光龙 t2「三条小龙绕着自己转」的参数 —— 改完存盘, 下一次放 t2 生效(不用重启)。调好把日志里那行『光龙环绕参数』发我, 我抄成默认值。",
                  "_字段说明": {
                    "direction": "转的方向(+1/-1); 三条龙的肚皮朝外拐(不是拐向中心)就把它取反",
                    "radius_factor": "环半径倍率; 1.0 = 三条龙首尾正好相接围成一个整圆(拉开缝就调大)",
                    "height": "环心比主人脚底高多少格",
                    "deg_per_tick": "每秒转多少度(20 tick = 1 秒); 3.0 约 2 秒一圈",
                    "ticks": "转多久(tick); 400 = 20 秒(和光龙鳞甲 buff 一样长)",
                    "count": "几条龙(数量变了, 首尾相接的角度会自动跟着变)"
                  },
                  "direction": 1,
                  "radius_factor": 1.0,
                  "height": 1.2,
                  "deg_per_tick": 3.0,
                  "ticks": 400,
                  "count": 3
                }
                """;
        Files.writeString(file, text, StandardCharsets.UTF_8);
        LOGGER.info("TN-C/light: 已写出默认光龙环绕参数 {}（{}）", file, describe());
    }

    /** 读方向 ✓：只能 ±1 ✓（写别的值也当成 ±1 ✓）。 */
    private static int readSign(JsonObject root, String key, int fallback) {
        double v = read(root, key, fallback, -1.0D, 1.0D);
        return v < 0.0D ? -1 : 1;
    }

    /** 读一个数：缺字段就保留原值 ✓，超范围就夹住 ✓。 */
    private static double read(JsonObject root, String key, double fallback, double min, double max) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            double v = value.getAsDouble();
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                return fallback;
            }
            return Math.max(min, Math.min(max, v));
        } catch (Throwable t) {
            return fallback;
        }
    }
}
