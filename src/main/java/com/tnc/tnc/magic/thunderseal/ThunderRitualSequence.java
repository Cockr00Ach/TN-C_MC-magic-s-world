package com.tnc.tnc.magic.thunderseal;

/**
 * ★ <b>雷锲链的施法前戏（仪式阵）规则</b> —— 2026-11-05 第三批：
 * 作者「参考一下光法的施法前戏和法阵，照猫画虎一下」。
 *
 * <p>光系那套（{@code LightRitualSequence}）讲的是同一件事：<b>读条期间不是一张阵，
 * 而是一座"主阵 ＋ 若干小阵 ＋ 竖环"的仪式场，读条越久越完整</b>。
 * 雷锲链没有读条条（它是瞬发陷阱），但它自己有一段 {@code charge}（蓄雷 25~37 tick），
 * 正好拿来当"前戏"：蓄雷这段时间里，小阵一颗一颗点亮、门环一道道立起来 ✓。
 *
 * <h2>三档施法方式（和光系的 INSCRIPTION / SANCTUARY / DESCENT 一一对应）</h2>
 * <ul>
 *   <li>{@link Rite#BRAND}（t1~t2）：<b>烙印</b> —— 一张低阶雷阵 ＋ 2/3 颗贴地小阵，
 *       没有门环、不压屏；蓄雷结束只是阵亮一下；</li>
 *   <li>{@link Rite#CAGE}（t3）：<b>雷牢</b> —— 标准雷阵 ＋ 4 颗小阵 ＋ 2 道门环，
 *       开始有前戏的"心跳"（每次脉冲亮一次笼子）；</li>
 *   <li>{@link Rite#TRIBUNAL}（t4~t5）：<b>霆狱</b> —— 高阶雷阵（外环＋刻痕＋电须）＋ 6/8 颗小阵
 *       ＋ 4/6 道门环，蓄雷期间整座仪式场最亮。</li>
 * </ul>
 *
 * <p>纯算术，不引用任何 Minecraft 类 ⇒ 可以直接跑 JUnit（和光系同一条约定 ✓）。
 */
public final class ThunderRitualSequence {

    public static final int MAX_TIER = 5;

    public enum Rite {
        BRAND,
        CAGE,
        TRIBUNAL
    }

    private ThunderRitualSequence() {
    }

    public static int tier(int tier) {
        return Math.max(1, Math.min(MAX_TIER, tier));
    }

    public static Rite rite(int tier) {
        int t = tier(tier);
        if (t <= 2) {
            return Rite.BRAND;
        }
        return t == 3 ? Rite.CAGE : Rite.TRIBUNAL;
    }

    /** 主阵样式：0 = 低阶（11 号）、1 = 标准（7 号，带雷网）、2 = 高阶（12 号）✓ */
    public static int mainStyleStep(int tier) {
        return switch (tier(tier)) {
            case 1, 2 -> 0;
            case 3 -> 1;
            default -> 2;
        };
    }

    /** 蓄雷期间一颗一颗点亮的地面小阵。 */
    public static int satelliteSeals(int tier) {
        return switch (tier(tier)) {
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 4;
            case 4 -> 6;
            default -> 8;
        };
    }

    /** 绕在封印周围的竖立门环（低阶 0 道 —— 结构上就和中高阶不一样 ✓）。 */
    public static int gateRings(int tier) {
        return switch (tier(tier)) {
            case 1, 2 -> 0;
            case 3 -> 2;
            case 4 -> 4;
            default -> 6;
        };
    }

    /**
     * 第 {@code index} 颗小阵在第几 tick 亮起：全部落在蓄雷期（{@code charge}）之内，
     * 第一颗 6%、最后一颗约 56% —— 和光系的 {@code satelliteIgnition} 同一个节奏 ✓。
     */
    public static int ignitionTick(int tier, int index, int charge) {
        int count = Math.max(1, satelliteSeals(tier));
        int i = Math.max(0, Math.min(count - 1, index));
        double at = 0.06D + 0.50D * (i / (double) Math.max(1, count - 1));
        return Math.max(1, (int) Math.round(Math.max(1, charge) * at));
    }

    /** 门环比小阵晚一点立起来（蓄雷过半才开始一道道开）✓ */
    public static int gateTick(int tier, int index, int charge) {
        int count = Math.max(1, gateRings(tier));
        int i = Math.max(0, Math.min(count - 1, index));
        double at = 0.45D + 0.45D * (i / (double) Math.max(1, count - 1));
        return Math.max(1, (int) Math.round(Math.max(1, charge) * at));
    }

    /** 小阵自身半径（格）：0.9~1.6，档位越高稍大 ✓ */
    public static double satelliteSize(int tier) {
        return 0.85D + 0.13D * tier(tier);
    }

    /** 小阵轨道半径比例（相对主阵）：两圈交替 ✓ */
    public static double satelliteOrbitFraction(int index) {
        return (index & 1) == 0 ? 0.42D : 0.66D;
    }

    /** 小阵角度：均匀分布 ＋ 奇偶错半格 ✓ */
    public static double satelliteAngle(int tier, int index) {
        int count = Math.max(1, satelliteSeals(tier));
        double half = (index & 1) == 0 ? 0.0D : Math.PI / count;
        return index * Math.PI * 2.0D / count + half;
    }

    /** 门环半径（格） */
    public static double gateRadius(int tier) {
        return 2.4D + 0.12D * tier(tier);
    }

    /** 屏幕演出强度（雷锲比光系克制：低阶 0，t3 起才有） */
    public static float screenFx(int tier) {
        return switch (tier(tier)) {
            case 1, 2 -> 0.0F;
            case 3 -> 0.35F;
            case 4 -> 0.70F;
            default -> 1.0F;
        };
    }

    /** 立顶那一下的白闪（给 {@code TNSpellClientVisuals.pulseFlash}） */
    public static float releaseFlash(int tier) {
        return switch (tier(tier)) {
            case 1 -> 0.14F;
            case 2 -> 0.20F;
            case 3 -> 0.34F;
            case 4 -> 0.50F;
            default -> 0.62F;
        };
    }

    /** 立顶那一下的震屏（度） */
    public static float releaseShake(int tier) {
        return switch (tier(tier)) {
            case 1 -> 0.0F;
            case 2 -> 0.2F;
            case 3 -> 1.2F;
            case 4 -> 2.2F;
            default -> 3.2F;
        };
    }
}
