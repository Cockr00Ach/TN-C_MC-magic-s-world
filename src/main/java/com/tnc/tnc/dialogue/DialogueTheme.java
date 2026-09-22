package com.tnc.tnc.dialogue;

import java.util.Map;

/**
 * 对话框的**配色主题**（用户 2026-09-22：「跟不同人对话，视觉对话框是不一样的」）。
 *
 * <h2>为什么要有它</h2>
 * 之前对话框的配色是写死在 {@code DialogueScreen} 里的金色描边，所有 NPC 一个样。
 * 但这是一个**群像**开场：酒馆老板、铁匠、骑士、管书的 —— 玩家一眼能分辨"谁在说话"
 * 会让这几场戏清楚很多。对话框颜色是最省事、也最直接的那种"身份提示"。
 *
 * <h2>主题怎么定的（关键：不写死在 Java 的对话逻辑里）</h2>
 * <ol>
 *   <li>剧本 id 的前缀即是主题名：{@code tnc:zhuangquerang_second} → 主题 {@code zhuangquerang}。
 *       所以**加一个新 NPC 的配色不用改任何逻辑**，只要在这里注册一行；</li>
 *   <li>服务端在 {@link DialogueLoader} 解析剧本时就把它填进 {@link DialogueScript#theme()}，
 *       随剧本一起发给客户端（配色是"这条剧本的属性"，不是客户端自己猜的）。</li>
 * </ol>
 *
 * <p>想加主题：往 {@link #THEMES} 里加一行；想让某个 NPC 用它：往 {@link #NPC_THEMES} 加一行。
 * 两处都找不到就落到 {@link #DEFAULT}（金色，也是 Self 用的，等于现在的样子）。
 */
public final class DialogueTheme {

    /**
     * 一套配色。
     *
     * @param key     主题名（= 剧本 id 的前缀）
     * @param panel   面板底色（含透明度，{@code 0xAARRGGBB}）
     * @param border  描边颜色
     * @param name    说话人名字的颜色
     * @param text    正文颜色
     * @param hint    "继续"三角/提示的颜色（一般跟描边同色）
     */
    public record Palette(String key, int panel, int border, int name, int text, int hint) {
    }

    /** 默认 = 金色。Self 用的就是它，所以"没配色的 NPC"外观和以前完全一样。 */
    public static final Palette DEFAULT = new Palette("gold",
            0xC8101014, 0xFFD8A657, 0xFFD8A657, 0xFFF2F2F2, 0xFFD8A657);

    /** 蓝调 —— 给庄鹊让（她的人设是"管书的/魔法协会"，蓝白最贴）。 */
    public static final Palette BLUE = new Palette("blue",
            0xC80A1526, 0xFF6FA8DC, 0xFF9CC7F0, 0xFFEAF3FF, 0xFF6FA8DC);

    private static final Map<String, Palette> THEMES = Map.of(
            DEFAULT.key(), DEFAULT,
            BLUE.key(), BLUE);

    /**
     * NPC（皮肤名）→ 主题名。
     *
     * <p>没登记的 NPC 走 {@link #DEFAULT}。★ 想给某个人换色，只改这一行。
     */
    private static final Map<String, String> NPC_THEMES = Map.of(
            "zhuangquerang", "blue");

    private DialogueTheme() {
    }

    /**
     * 按**剧本 id 的前缀**取配色 —— 前缀就是 NPC 的皮肤名
     * （{@code zhuangquerang_second} → {@code zhuangquerang}）。
     *
     * <p>取不到就回落到 NPC 表，再取不到就用默认金色。
     */
    public static Palette paletteOf(String scriptPath) {
        if (scriptPath == null || scriptPath.isEmpty()) {
            return DEFAULT;
        }
        int cut = scriptPath.indexOf('_');
        String npc = cut < 0 ? scriptPath : scriptPath.substring(0, cut);
        return forNpc(npc);
    }

    /** 按 NPC 皮肤名取主题。 */
    public static Palette forNpc(String npc) {
        if (npc == null) {
            return DEFAULT;
        }
        Palette direct = THEMES.get(npc);          // 前缀本身就是主题名（如 blue）
        if (direct != null) {
            return direct;
        }
        String themeKey = NPC_THEMES.get(npc);     // 否则查 NPC → 主题 的登记表
        return themeKey == null ? DEFAULT : THEMES.getOrDefault(themeKey, DEFAULT);
    }

    /** 按主题名取（诊断用）。 */
    public static Palette byName(String themeKey) {
        return themeKey == null ? DEFAULT : THEMES.getOrDefault(themeKey, DEFAULT);
    }
}
