package com.tnc.tnc.magic.dark;

/**
 * ★ <b>黑暗衍的「施法前戏」规则</b>（2026-11-05：作者「优化一下衍 boss 的施法 … 衍是有黑暗元素的」）。
 *
 * <p>照光法 / 雷法那套仪式场的结构来做，但**材料全换成暗系**：
 * 紫黑环、往里勾的影子爪、黑雾、中心暗核 —— 不做雷锲、不铺雷网 ✓（作者明确"不要雷锲"）。
 *
 * <p>这个类<b>不碰 Minecraft</b>（纯数值），方便写单测守门；服务器端 {@code YanDarkBossEntity}
 * 按它排程，客户端 {@code DarkSealRenderer} 按它画几何。
 *
 * <p>四档 = boss 的四个技能，越狠的前戏越长 ✓：
 * <ol>
 *   <li>{@code WHISPER} 低语（闪电移位）：最短，2 颗小阵，没有门环；</li>
 *   <li>{@code VEIL} 幕（雷暴）：中短，3 颗小阵，2 道门环；</li>
 *   <li>{@code ECLIPSE} 蚀（天打五雷轰）：长，5 颗小阵，3 道门环；</li>
 *   <li>{@code SOVEREIGN} 君临（神在投篮）：最长，7 颗小阵，5 道门环。</li>
 * </ol>
 */
public final class DarkRitualSequence {

    /** 四档仪式（和 boss 的四个技能一一对应，见 {@link #riteFor(int)}）。 */
    public enum Rite {
        WHISPER,
        VEIL,
        ECLIPSE,
        SOVEREIGN
    }

    /** 档数（1..4）。 */
    public static final int MAX_TIER = 4;

    private DarkRitualSequence() {
    }

    /** 技能编号 → 仪式。0 雷暴 / 1 天打五雷轰 / 2 神在投篮 / 3 闪电移位（和 boss 的 switch 一致 ✓）。 */
    public static Rite riteFor(int kind) {
        return switch (kind) {
            case 0 -> Rite.VEIL;
            case 1 -> Rite.ECLIPSE;
            case 2 -> Rite.SOVEREIGN;
            default -> Rite.WHISPER;
        };
    }

    /** 仪式 → 档位（1..4），给 {@code theme} 之类的维度对齐用。 */
    public static int tier(Rite rite) {
        return switch (rite) {
            case WHISPER -> 1;
            case VEIL -> 2;
            case ECLIPSE -> 3;
            case SOVEREIGN -> 4;
        };
    }

    /** 主阵样式：低两档用小阵（13），高两档用大阵（14）。 */
    public static int mainStyle(Rite rite) {
        return switch (rite) {
            case WHISPER, VEIL -> 13;
            case ECLIPSE, SOVEREIGN -> 14;
        };
    }

    /** 主阵半径（格）：小技能 7 / 10，大招 13 / 17。 */
    public static double mainRadius(Rite rite) {
        return switch (rite) {
            case WHISPER -> 7.0D;
            case VEIL -> 10.0D;
            case ECLIPSE -> 13.0D;
            case SOVEREIGN -> 17.0D;
        };
    }

    /** 前戏时长（tick）：这段时间里只有阵在亮、招式还没出手 ✓（给玩家反应时间）。 */
    public static int telegraphTicks(Rite rite) {
        return switch (rite) {
            case WHISPER -> 18;
            case VEIL -> 24;
            case ECLIPSE -> 32;
            case SOVEREIGN -> 42;
        };
    }

    /** 卫星小阵数量：2 / 3 / 5 / 7。 */
    public static int satelliteSeals(Rite rite) {
        return switch (rite) {
            case WHISPER -> 2;
            case VEIL -> 3;
            case ECLIPSE -> 5;
            case SOVEREIGN -> 7;
        };
    }

    /** 立起来的门环数量：0 / 2 / 3 / 5（低语只有脚下，不立环 ✓）。 */
    public static int gateRings(Rite rite) {
        return switch (rite) {
            case WHISPER -> 0;
            case VEIL -> 2;
            case ECLIPSE -> 3;
            case SOVEREIGN -> 5;
        };
    }

    /**
     * 第 {@code index} 颗小阵在第几 tick 点亮（前戏的 10% → 55%，一颗颗推进 ✓）。
     *
     * @param count 小阵总数（&gt; 1）
     */
    public static int ignitionTick(Rite rite, int index, int count) {
        int t = telegraphTicks(rite);
        double f = count <= 1 ? 0.10D : 0.10D + 0.45D * index / (count - 1);
        return (int) Math.round(t * f);
    }

    /** 第 {@code index} 道门环在第几 tick 立起来（前戏的 45% → 85%）。 */
    public static int gateTick(Rite rite, int index, int count) {
        int t = telegraphTicks(rite);
        double f = count <= 1 ? 0.45D : 0.45D + 0.40D * index / (count - 1);
        return (int) Math.round(t * f);
    }

    /** 卫星小阵的半径（格）：0.9 / 1.2 / 1.5 / 1.8。 */
    public static double satelliteSize(Rite rite) {
        return switch (rite) {
            case WHISPER -> 0.90D;
            case VEIL -> 1.20D;
            case ECLIPSE -> 1.50D;
            case SOVEREIGN -> 1.80D;
        };
    }

    /** 卫星小阵的轨道半径系数（× 主阵半径）：内圈 0.40 / 外圈 0.72 交替 ✓。 */
    public static double satelliteOrbitFraction(int index) {
        return (index % 2 == 0) ? 0.40D : 0.72D;
    }

    /** 卫星小阵的角度（弧度），按黄金角散开，避免每次都一样 ✓。 */
    public static double satelliteAngle(int index) {
        return 2.399963229728653D * index;
    }

    /** 门环到主阵中心的距离（格）：3.2 / 4.0 / 5.0。 */
    public static double gateRadius(Rite rite) {
        return switch (rite) {
            case WHISPER, VEIL -> 3.2D;
            case ECLIPSE -> 4.0D;
            case SOVEREIGN -> 5.0D;
        };
    }

    /** 前戏开头那一团黑雾的粒子数（0 档不喷）。 */
    public static int mistBurst(Rite rite) {
        return switch (rite) {
            case WHISPER -> 26;
            case VEIL -> 42;
            case ECLIPSE -> 64;
            case SOVEREIGN -> 90;
        };
    }

    /** 镜头压暗强度（0..1）：只有大招才明显压 ✓。 */
    public static float screenDim(Rite rite) {
        return switch (rite) {
            case WHISPER -> 0.0F;
            case VEIL -> 0.18F;
            case ECLIPSE -> 0.36F;
            case SOVEREIGN -> 0.55F;
        };
    }

    /** 出手那一下的震屏强度。 */
    public static float screenShake(Rite rite) {
        return switch (rite) {
            case WHISPER -> 0.35F;
            case VEIL -> 0.80F;
            case ECLIPSE -> 1.60F;
            case SOVEREIGN -> 2.40F;
        };
    }

    /** 阵整体存在时长 = 前戏 ＋ 余韵（tick）；出手后阵还留一会儿才散 ✓。 */
    public static int circleLife(Rite rite) {
        return telegraphTicks(rite) + 40;
    }
}
