package com.tnc.tnc.light;

/**
 * 光系施法前戏的<b>纯数值规则</b> —— 不引用任何 Minecraft / SpellEngine 类，方便直接跑 JUnit。
 *
 * <p>作者 2026-11-05 的要求：<b>「不是每次就一个法阵吗，我希望有更多的小法阵和更久的施法前戏，
 * 让我能感受到暴风雨的前夕」</b> ✓ ⇒ 光耀 / 召唤天使两条链在<b>读条期间</b>就铺开一座
 * 由「主阵 ＋ 若干小阵 ＋ 竖环」组成的仪式场，读条越久，阵越亮、越完整 ✓。
 *
 * <h2>为什么单独拆一个类</h2>
 * 几何渲染（{@code client/LightCastFx}）要读几千个顶点，不能在 JUnit 里跑 ✗；
 * 但「第几档几颗小阵 / 什么时候点亮第几颗 / 核心多大 / 释放闪多亮」这些是纯算术 ✓。
 * 拆出来之后：
 * <ul>
 *   <li>{@code LightCastFx}（客户端只负责画）和 {@code TNLightChainMechanics}（服务端铺阵）共用同一套数量；</li>
 *   <li>测试可以钉死「低档也有至少 3 颗小阵」「点亮顺序单调」「progress 0/1 两端闭合」这些不变量 ✓。</li>
 * </ul>
 *
 * <h2>观感设计（“暴风雨的前夕”）</h2>
 * <ol>
 *   <li><b>先暗后亮</b>：读条前段屏幕四周压暗（{@link #darkVignette}），像云层压下来；</li>
 *   <li><b>一点点“写”出来</b>：主阵不是整张蹦出来，而是按读条比例一段段描出来
 *       （{@link #formation}），同时小阵按顺序点亮（{@link #satelliteIgnition}）；</li>
 *   <li><b>越接近释放越亮</b>：核心/光柱/射线随 {@link #chargeGlow} 加速增亮，
 *       最后一小段还会心跳式地颤一下；</li>
 *   <li><b>释放即炸开</b>：{@link #releaseFlash} / {@link #releaseShake} 给白闪与震屏，
 *       配合服务端多铺的小阵，形成“光污染” ✓。</li>
 * </ol>
 */
public final class LightRitualSequence {

    /** 光耀链 / 召唤天使链的最高档。 */
    public static final int MAX_TIER = 5;

    /**
     * 光系三种<b>施法方式</b> —— 2026-11-05 作者：「法阵要是每个都是一样的施法方式就不好看了，
     * 低级和高级应该是有区别的」。
     *
     * <ul>
     *   <li>{@link #INSCRIPTION}（t1~t2）：<b>铭刻</b> —— 只有一张主阵 + 2~3 颗贴地小阵，
     *       没有竖环、没有屏幕压暗/心跳、没有光柱/光雨/光带；读条短，像在地上快速写完一个符。</li>
     *   <li>{@link #SANCTUARY}（t3）：<b>圣所</b> —— 主阵 + 4 颗小阵（各带一枚竖立伴生环）
     *       + 2 道绕身门环；开始有轻微暗角、一次心跳和一根细光柱。</li>
     *   <li>{@link #DESCENT}（t4~t5）：<b>天穹降临</b> —— 6~8 颗小阵、4~6 道门环、
     *       满档压暗/心跳/光柱/盘旋光带/脉冲环/光雨，才是“暴风雨的前夕”。</li>
     * </ul>
     */
    public enum Rite {
        INSCRIPTION,
        SANCTUARY,
        DESCENT
    }

    private LightRitualSequence() {
    }

    /** 档位钳到 1..5（Boss / 调试命令给 0 或 99 也不该把几何算崩）✓。 */
    public static int tier(int tier) {
        return Math.max(1, Math.min(MAX_TIER, tier));
    }

    /** 这一档用哪一种施法方式。 */
    public static Rite rite(int tier) {
        int t = tier(tier);
        if (t <= 2) {
            return Rite.INSCRIPTION;
        }
        return t == 3 ? Rite.SANCTUARY : Rite.DESCENT;
    }

    /**
     * 读条期间要同时亮几颗<b>地面小阵</b>。
     *
     * <p>低阶不是“缩小版高阶”：t1/t2 只铺 2/3 颗，贴地、紧凑；t3 起才扩到 4 颗并给每颗
     * 加竖立伴生环；t4/t5 是 6/8 颗的全阵。这样一眼就能看出“这是小法术还是大仪式”。
     */
    public static int satelliteSeals(int tier) {
        return switch (tier(tier)) {
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 4;
            case 4 -> 6;
            default -> 8;
        };
    }

    /**
     * 读条期间绕在身边的<b>竖环</b>（像铁魔法高阶法术那种立起来的法阵）。
     *
     * <p>低阶 <b>0 道</b> —— 这是和“圣所/天穹”最直观的结构差别：低阶只有地面阵，
     * 高阶才在身周立起一道道门环。
     */
    public static int gateRings(int tier) {
        return switch (tier(tier)) {
            case 1, 2 -> 0;
            case 3 -> 2;
            case 4 -> 4;
            default -> 6;
        };
    }

    /** 释放后真正留在场上的<b>竖立光环</b>数量（服务端铺阵用，和客户端门环同一套档位逻辑）。 */
    public static int uprightHalos(int tier) {
        return switch (tier(tier)) {
            case 1, 2 -> 0;
            case 3 -> 1;
            case 4 -> 2;
            default -> 3;
        };
    }

    /** 主阵地面上向外射的<b>光芒</b>条数：低阶 6/8 道，高阶才像日冕（18/24 道）。 */
    public static int groundRays(int tier) {
        return switch (tier(tier)) {
            case 1 -> 6;
            case 2 -> 8;
            case 3 -> 12;
            case 4 -> 18;
            default -> 24;
        };
    }

    /** 外圈符文刻痕数：低阶只刻 6/8 道，高阶 18/24 道。 */
    public static int outerRunes(int tier) {
        return groundRays(tier);
    }

    /**
     * 主阵的“成形比例”：0 = 还没开始，1 = 完整。
     *
     * <p>用 smoothstep 而不是线性 —— 线性会像进度条，smoothstep 两头慢、中间快，
     * 更像“笔尖在阵上走” ✓。
     */
    public static float formation(float progress) {
        return smooth(clamp(progress));
    }

    /**
     * 第 {@code index} 颗小阵在当前读条比例下的点亮程度（0 = 暗，1 = 全亮）。
     *
     * <p>点亮不是一起亮 ✓：第一颗从 6% 开始，最后一颗要到约 56% 才起步，
     * 每颗再花 18% 的读条淡入 —— 这样读条时能清楚看到“一颗一颗被点亮”的顺序感，
     * 也就是“暴风雨前夕”的节奏 ✓。
     */
    public static float satelliteIgnition(int tier, int index, float progress) {
        int count = Math.max(1, satelliteSeals(tier));
        int i = Math.max(0, Math.min(count - 1, index));
        float start = 0.06F + 0.50F * (i / (float) Math.max(1, count - 1));
        return smooth(clamp((clamp(progress) - start) / 0.18F));
    }

    /** 核心（头顶那颗“太阳种子”）的大小系数：读条开始 0.35、结束 1.0。 */
    public static float coreScale(float progress) {
        return 0.35F + 0.65F * formation(progress);
    }

    /**
     * 屏幕/核心的“过曝”强度：前段几乎为 0，最后 40% 快速抬起来。
     *
     * <p>用 {@code p^1.7} 而不是线性 —— 线性会让整段读条都在亮，反而没有“压轴”感 ✓。
     */
    public static float chargeGlow(float progress) {
        float p = clamp(progress);
        return (float) Math.pow(p, 1.7D);
    }

    /**
     * 这一档允许的<b>屏幕演出强度</b>（0..1）。
     *
     * <p>低阶 <b>0</b>：不压暗、不过曝、不震屏 —— 读条结束只是阵亮一下；
     * 中阶 0.45：轻微压暗与一次心跳；高阶 0.8/1.0：完整的“暴风雨前夕”。
     */
    public static float screenFx(int tier) {
        return switch (tier(tier)) {
            case 1, 2 -> 0.0F;
            case 3 -> 0.45F;
            case 4 -> 0.80F;
            default -> 1.0F;
        };
    }

    /** 核心是否需要一根从地面顶到头顶的<b>光柱</b>（低阶只留一颗小光种，不竖柱）。 */
    public static boolean hasCorePillar(int tier) {
        return tier(tier) >= 3;
    }

    /** 小阵是否带<b>竖立伴生环</b>（低阶只有贴地小阵）。 */
    public static boolean hasUprightCompanions(int tier) {
        return tier(tier) >= 3;
    }

    /** 是否有盘旋光带 / 脉冲环（中阶起）。 */
    public static boolean hasRibbons(int tier) {
        return tier(tier) >= 3;
    }

    /** 是否下<b>光雨</b>（只有 t4/t5 的“天穹”才下）。 */
    public static boolean hasLightRain(int tier) {
        return tier(tier) >= 4;
    }

    /**
     * 读条前段的暗角强度（0..1）：云层压下来的“暴风雨前夕”感。
     *
     * <p>只在读条前 55% 出现，之后让位给白光 ✓ —— 先暗后亮，反差才够大 ✓；
     * 低阶直接是 0（没有“天象”，只有地上一个符）。
     */
    public static float darkVignette(int tier, float progress) {
        float fx = screenFx(tier);
        if (fx <= 0.0F) {
            return 0.0F;
        }
        float p = clamp(progress);
        float v = clamp((0.55F - p) / 0.55F);
        return smooth(v) * 0.85F * fx;
    }

    /**
     * 释放瞬间的<b>白闪</b>强度（给 {@code TNSpellClientVisuals.flash} 的 0..1）。
     * 低阶只是“亮一下”（0.20/0.26），高阶才接近满屏（0.58/0.70）✓。
     */
    public static float releaseFlash(int tier) {
        return switch (tier(tier)) {
            case 1 -> 0.20F;
            case 2 -> 0.26F;
            case 3 -> 0.42F;
            case 4 -> 0.58F;
            default -> 0.70F;
        };
    }

    /** 释放瞬间的<b>震屏</b>强度（度）。低阶 0，中阶一次轻震，高阶才明显 ✓。 */
    public static float releaseShake(int tier) {
        return switch (tier(tier)) {
            case 1 -> 0.0F;
            case 2 -> 0.3F;
            case 3 -> 1.4F;
            case 4 -> 2.6F;
            default -> 3.8F;
        };
    }

    /**
     * 读条到后期的心跳震屏周期（tick）：越接近释放越密。
     * 返回 0 表示这一档没有心跳，或还没到该震的阶段（低阶全程 0）✓。
     */
    public static int heartbeatTicks(int tier, float progress) {
        if (screenFx(tier) <= 0.0F) {
            return 0;
        }
        float p = clamp(progress);
        if (p < 0.45F) {
            return 0;
        }
        if (p < 0.65F) {
            return 14;
        }
        if (p < 0.85F) {
            return 9;
        }
        return 5;
    }

    /** 心跳震屏的强度（度）：按 screenFx 缩放，低阶恒 0 ✓。 */
    public static float heartbeatShake(int tier, float progress) {
        float fx = screenFx(tier);
        if (fx <= 0.0F) {
            return 0.0F;
        }
        float p = clamp(progress);
        return p < 0.45F ? 0.0F : (0.18F + 0.85F * p * p) * fx;
    }

    /**
     * 第 {@code index} 颗小阵的轨道半径比例（相对主阵半径）——
     * 0.42 / 0.66 两圈交替，读条越到后面越往内收一点点 ✓。
     */
    public static double satelliteOrbitFraction(int index) {
        double base = (index & 1) == 0 ? 0.42D : 0.66D;
        return base;
    }

    /** 小阵自身半径比例（相对主阵半径）：低档小一点，高档稍大但不会糊成一片 ✓。 */
    public static double satelliteSizeFraction(int tier) {
        return 0.052D + 0.006D * tier(tier);
    }

    /** 小阵在轨道上的角度（均匀分布 + 交错半格，避免两圈正好对齐）✓。 */
    public static double satelliteAngle(int tier, int index) {
        int count = Math.max(1, satelliteSeals(tier));
        double half = (index & 1) == 0 ? 0.0D : Math.PI / count;
        return index * Math.PI * 2.0D / count + half;
    }

    /**
     * 第 {@code index} 颗小阵的“呼吸”相位 —— 用编号错开，
     * 每颗的脉动不在同一帧，看起来才像一圈各自活着的阵 ✓。
     */
    public static double satellitePulsePhase(int index) {
        return index * 1.7D;
    }

    // ---- 纯算术小工具（测试也直接用） ----

    public static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    /** 经典 smoothstep：两端斜率为 0，不会“啪”地开始/结束 ✓。 */
    public static float smooth(float t) {
        float x = clamp(t);
        return x * x * (3.0F - 2.0F * x);
    }
}
