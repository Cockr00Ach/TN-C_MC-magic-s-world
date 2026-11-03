package com.tnc.tnc.warrior;

import net.minecraft.world.phys.Vec3;

/**
 * 刀光的曲线数学：Catmull-Rom 插值 + "这一段要插几个点"。
 *
 * <p>从 {@code client/WarriorTrail} 里单独拿出来，是为了能在<b>没有渲染环境</b>的情况下
 * 直接测 —— 用户抱怨的"不顺滑"里有一半是这条曲线的事（点不够密就是折线），
 * 这种数值逻辑不该只能靠进游戏看。
 */
public final class WarriorTrailCurve {

    /** 相邻两帧之间最多允许转多少度；超过就多插点。 */
    public static final double MAX_SEGMENT_DEGREES = 4.0;
    /** 一段最多插几段（防止极端角度下爆顶点）。 */
    public static final int MAX_SUBDIVISIONS = 12;

    private WarriorTrailCurve() {
    }

    /**
     * 转角 → 这一段要拆成几段。
     *
     * <p>固定插 3 个点有两个毛病：慢慢比划时浪费顶点，快到一秒两圈时又不够 ——
     * 一圈只剩十几个点，看着就是个多边形。按角度自适应就两头都合适。
     *
     * <p>超过 {@link #MAX_SEGMENT_DEGREES} × {@link #MAX_SUBDIVISIONS}（48°）之后，
     * 4° 那条保证让位给顶点数上限：再快的旋转也只拆 12 段，否则一帧转 180° 会炸出几百个顶点。
     */
    public static int subdivisions(double degrees) {
        if (!Double.isFinite(degrees) || degrees <= MAX_SEGMENT_DEGREES) {
            return 1;
        }
        int steps = (int) Math.ceil(degrees / MAX_SEGMENT_DEGREES);
        return Math.max(1, Math.min(MAX_SUBDIVISIONS, steps));
    }

    /** 两根剑身向量之间的夹角（度）→ 拆几段。剑身长度为 0（退化）时按 1 段处理。 */
    public static int subdivisions(Vec3 fromBlade, Vec3 toBlade) {
        if (fromBlade == null || toBlade == null
                || fromBlade.lengthSqr() < 1.0E-8 || toBlade.lengthSqr() < 1.0E-8) {
            return 1;
        }
        double cos = Math.max(-1.0, Math.min(1.0, fromBlade.normalize().dot(toBlade.normalize())));
        return subdivisions(Math.toDegrees(Math.acos(cos)));
    }

    /** 标准 Catmull-Rom（张力 0.5）：{@code t=0} 落在 p1、{@code t=1} 落在 p2。 */
    public static Vec3 catmull(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return p0.scale(-0.5f * t3 + t2 - 0.5f * t)
                .add(p1.scale(1.5f * t3 - 2.5f * t2 + 1f))
                .add(p2.scale(-1.5f * t3 + 2f * t2 + 0.5f * t))
                .add(p3.scale(0.5f * t3 - 0.5f * t2));
    }

    /**
     * 把一份"每段插几个点"压到总预算之内。
     *
     * <p>为什么要压：细分是按转角算的，一帧转 170° 时一段就能吃 12 个点；几十个样本叠起来
     * 能到三四百个点，乘上"两层 × 正反两面"就是几千个四边形 —— 单个玩家还好，
     * 十几个人同屏时这笔顶点开销纯属浪费（那么快的挥砍根本看不清细节）。
     *
     * <p>压法是**整体等比缩减**（每段至少留 1），而不是从尾巴上砍点：砍点会让光带长度变短、
     * 突然断开；等比缩减只是密一点/疏一点，形状不变。
     *
     * @param steps  每段原来的细分数（≥1）
     * @param budget 插值点总数上限（不含真实样本本身）
     * @return 新数组（不改入参）；本来就够小的话原样返回
     */
    public static int[] limit(int[] steps, int budget) {
        if (steps == null || steps.length == 0) {
            return steps == null ? new int[0] : steps;
        }
        int sum = 0;
        for (int n : steps) {
            sum += Math.max(1, n);
        }
        if (budget >= sum || budget <= 0) {
            return steps;
        }
        // 每段至少 1，所以先把"至少 1"的那部分扣掉，剩下的按比例分
        int spare = Math.max(0, budget - steps.length);
        int[] out = new int[steps.length];
        for (int i = 0; i < steps.length; i++) {
            int n = Math.max(1, steps[i]);
            if (n == 1 || spare == 0) {
                out[i] = 1;
                continue;
            }
            int scaled = 1 + Math.round((n - 1) * (float) spare / (float) Math.max(1, sum - steps.length));
            out[i] = Math.max(1, scaled);
        }
        return out;
    }
}
