package com.tnc.tnc.magic;

/**
 * ★ <b>龙卷风的强力吸力</b>（2026-11-13 作者：「龙卷风加上强力吸力，四格以下的敌人会在其中卷到飞起」）✓
 *
 * <h2>和以前的区别</h2>
 * 以前 {@code suckIn} 每 10 tick 给一个很小的拽力（0.32 × 距离衰减），而且只给 0.06 的上升 ✗ ——
 * 敌人是"被慢慢拖过来"，不会真的飞起来 ✗。
 *
 * <p>现在对<b>四格以内</b>的敌人每 tick 直接<b>改写速度</b>：
 * <ul>
 *   <li><b>切向</b>：绕着风眼转（速度和龙卷风自转同向）✓</li>
 *   <li><b>径向</b>：往风眼收，但留一个最小半径（不然全挤在轴心上抖）✓</li>
 *   <li><b>垂直</b>：一路往上升，升到风柱上段就保持（"卷到飞起" ✓）</li>
 *   <li>速度封顶，免得 t5 把怪甩出一百格 ✗；同时清 {@code fallDistance} ⇒ 掉下来不摔死 ✓</li>
 * </ul>
 *
 * <p>纯数学（不碰 MC 类）⇒ 单元测试直接钉住"边缘吸得更狠、会往上飞、速度有上限" ✓。
 */
public final class TornadoSwirlRules {

    /** 强力吸力的半径（格）✓ —— 作者说的"四格"。 */
    public static final double LIFT_RADIUS = 4.0D;

    /** 切向速度（格/tick）✓ —— 决定转得多快。 */
    public static final double SWIRL_SPEED = 0.62D;

    /** 往风眼收的最小 / 最大速度（格/tick）✓。 */
    public static final double INWARD_MIN = 0.12D;
    public static final double INWARD_MAX = 0.50D;

    /** 上升速度（格/tick）✓。 */
    public static final double RISE_SPEED = 0.45D;

    /** 已经到顶之后往下压一点（不让人一直往上飘出视野）✓。 */
    public static final double HOLD_SPEED = -0.04D;

    /** 速度上限（格/tick）✓。 */
    public static final double MAX_SPEED = 1.5D;

    /** 绕圈的最小半径（格）✓ —— 太贴轴心会"卡住不动"。 */
    public static final double ORBIT_FLOOR = 0.8D;

    /** 升到风柱的这个比例高度就算"到顶"✓。 */
    public static final double LIFT_TOP_RATIO = 0.55D;

    private TornadoSwirlRules() {
    }

    /** 这一档的切向速度 ✓（档越高转得越快）。 */
    public static double swirlSpeed(int tier) {
        return SWIRL_SPEED + 0.05D * (Math.max(3, Math.min(5, tier)) - 3);
    }

    /**
     * 往风眼收的速度 ✓ —— <b>边缘更狠</b>（越远吸力越大），
     * 靠近轴心时只剩一个很小的收力（保证不会飞出去，又不会抖）✓。
     *
     * @param distance 水平距离（格）
     */
    public static double inwardSpeed(double distance) {
        double t = Math.min(1.0D, Math.max(0.0D, distance / LIFT_RADIUS));
        return INWARD_MIN + (INWARD_MAX - INWARD_MIN) * t;
    }

    /** 升到顶的高度（格，相对风眼脚下的 y）✓。 */
    public static double liftTop(double tornadoHeight) {
        return Math.max(1.5D, tornadoHeight * LIFT_TOP_RATIO);
    }

    /**
     * 算出这一 tick 应该给敌人的速度 ✓（单位：格/tick，世界坐标）。
     *
     * @param dx            敌人相对风眼轴的 dx
     * @param dz            敌人相对风眼轴的 dz
     * @param heightAbove   敌人现在的 y 相对风眼脚下有多高（格）
     * @param tornadoHeight 风柱总高（格）
     * @param tier          档位（3..5）
     * @return {@code {vx, vy, vz}}，已按 {@link #MAX_SPEED} 封顶
     */
    public static double[] velocity(double dx, double dz, double heightAbove,
                                    double tornadoHeight, int tier) {
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0E-4D) {
            // 正好在轴心上：给一个随机方向的最小切向，免得原地抖
            return new double[]{swirlSpeed(tier), RISE_SPEED, 0.0D};
        }
        double ux = dx / distance;
        double uz = dz / distance;
        // 切向 = 把径向逆时针转 90°（和渲染器的 -spin 同向）✓
        double tangentX = -uz;
        double tangentZ = ux;
        double swirl = swirlSpeed(tier);
        double inward = inwardSpeed(distance);
        // 已经进了最小 orbit 半径就不再往里收（否则会穿过轴心来回抖）✓
        if (distance <= ORBIT_FLOOR) {
            inward = INWARD_MIN * 0.5D;
        }
        double vx = tangentX * swirl - ux * inward;
        double vz = tangentZ * swirl - uz * inward;
        double vy = heightAbove < liftTop(tornadoHeight) ? RISE_SPEED : HOLD_SPEED;
        return clampSpeed(new double[]{vx, vy, vz});
    }

    /** 速度封顶 ✓（超过 {@link #MAX_SPEED} 就按比例缩）。 */
    public static double[] clampSpeed(double[] v) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len <= MAX_SPEED || len < 1.0E-9D) {
            return v;
        }
        double k = MAX_SPEED / len;
        return new double[]{v[0] * k, v[1] * k, v[2] * k};
    }

    /** 这个敌人算不算"在强力吸力范围里"（水平 ≤ 4 格、且没高过风柱太多）✓。 */
    public static boolean inside(double dx, double dz, double heightAbove, double tornadoHeight) {
        if (dx * dx + dz * dz > LIFT_RADIUS * LIFT_RADIUS) {
            return false;
        }
        return heightAbove > -1.0D && heightAbove < tornadoHeight + 1.0D;
    }
}
