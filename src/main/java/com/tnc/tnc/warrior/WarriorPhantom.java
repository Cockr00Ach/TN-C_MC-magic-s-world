package com.tnc.tnc.warrior;

import net.minecraft.server.level.ServerPlayer;

/**
 * 虚影：磨砺越高，挥出去的那道影子比手里的真剑大多少。
 *
 * <p>它同时是<b>两件事</b>的系数：
 * <ul>
 *   <li><b>看得见</b>：客户端把那把武器再画一遍（原模型 + 透明度），握把对齐真剑，
 *       长度按这里的倍率放大 —— 见 {@code client/WarriorPhantomRenderer}；</li>
 *   <li><b>摸得到</b>：连招的判定半径也乘同一个倍率 —— 见 {@code WarriorComboService#melee}。
 *       磨砺堆起来手就更长，这是给战士补攻击范围的唯一一条成长线。</li>
 * </ul>
 *
 * <p>倍率按"当前天赋档的磨砺上限"归一化，所以换天赋档（上限变大）时不会突然缩水：
 * 0 磨砺 = 1.00 倍（虚影和真剑等大），练满 = {@value #MAX_GROWTH} 加成（1.90 倍）。
 *
 * <p>2026-11-03 用户要求"变大的倍率加一倍"：{@code MAX_GROWTH} 从 0.45 提到 0.90。
 * 注意它同时是<b>判定半径</b>的系数（见 {@link com.tnc.tnc.warrior.WarriorComboService}），
 * 所以练满时连招半径也从 +45% 变成 +90% —— 这是"虚影看得见多大、就摸得到多远"的必然结果。
 */
public final class WarriorPhantom {

    /** 练满时虚影比真剑大多少（2026-11-03 由 0.45 加倍到 0.90）。 */
    public static final double MAX_GROWTH = 0.90;

    private WarriorPhantom() {
    }

    /** 0..1 的成长比例（按磨砺 / 上限）。 */
    public static double growth(int points, int cap) {
        if (cap <= 0) {
            return 0.0;
        }
        double ratio = Math.max(0, Math.min(points, cap)) / (double) cap;
        return MAX_GROWTH * ratio;
    }

    /** 虚影倍率：1.00（等大）~ 1.45。 */
    public static double scale(int points, int cap) {
        return 1.0 + growth(points, cap);
    }

    public static double scale(ServerPlayer player) {
        if (player == null) {
            return 1.0;
        }
        TemperingData data = TemperingData.get(player.server);
        return scale(data.points(player.getUUID()), data.cap(player.getUUID()));
    }
}
