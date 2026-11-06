package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 逆雨：水法读条时，"雨"从地面往回下到天上。
 *
 * <p>读条的每一帧，地面先湿一圈，然后水线从地上被抽起来、往上收回天空，
 * 到读条末尾在头顶聚成一团越来越亮的积水云，最后收成一颗水珠落进法里。
 * 整个效果只有一条时间轴：{@code cycle = frac(time * 速度 + 相位)}，
 * {@code cycle} 从 0 走到 1 就是"一滴水从地面升到云里"的一辈子 ——
 * 也正是它和普通下雨唯一的区别：y 随 cycle 上涨，不随重力下掉。
 *
 * <p>几何全部即时生成，只读读条进度，不生成实体、不改伤害、不写方块；
 * 走的是和 {@link WaterCastFx} 同一个渲染阶段与同一个 {@code RenderType.lightning()}，
 * 所以加色混合下会自然发亮。
 */
final class WaterReverseRain {

    /** 读条前戏是否由逆雨接管（false 就退回旧的地面潮汐阵 + 四系法阵）。 */
    static final boolean ENABLED = true;

    private static final Vec3 UP = new Vec3(0, 1, 0);

    /** 和 `WaterCastFx` 同一套水色，别在这里另起一套。 */
    private static final float W_R = .78F, W_G = .96F, W_B = 1.0F;
    private static final float C_R = .26F, C_G = .78F, C_B = 1.0F;
    private static final float D_R = .02F, D_G = .10F, D_B = .28F;

    /** 地面被抽干时留下的一圈圈"倒着扩散"的水纹数量。 */
    private static final int LIFT_SPOTS = 6;

    private WaterReverseRain() {
    }

    // ==================================================================
    //  渲染
    // ==================================================================

    /**
     * 画一整层逆雨。
     *
     * @param pose   已经平移到施法者脚下的矩阵（只含平移，所以本地轴就是世界轴）
     * @param tier   法术档位 1~5
     * @param ratio  读条进度 0~1
     * @param radius 这本法术的地面半径（旧法阵用的同一个数）
     * @param time   游戏刻（含插值）
     */
    static void draw(VertexConsumer out, Matrix4f pose, int tier, float ratio, double radius,
                     double time) {
        float p = Mth.clamp(ratio, 0F, 1F);
        float charge = chargeGlow(p);
        double R = Math.max(.6D, radius);
        double height = columnHeightFor(tier);

        // 1. 地面：先湿一圈。越接近放出来，水被抽得越干、地面越暗。
        double wet = R * (1.15D + .15D * charge);
        WaterGeometry.disk(out, pose, new Vec3(0, .028, 0), UP, wet,
                D_R, D_G, D_B, .26F * (.45F + .55F * charge), 40);
        WaterGeometry.ringColor(out, pose, new Vec3(0, .045, 0), UP, wet, .020,
                C_R, C_G, C_B, .16F + .22F * charge, 48);

        // 2. 起点：几处水纹在"倒着收"——普通落水是往外扩，这里往外扩的反方向收，
        //    看起来就像水面被提着往中间收拢、再被抽走。
        for (int k = 0; k < LIFT_SPOTS; k++) {
            double angle = k * Math.PI * 2D / LIFT_SPOTS + hash(k, 11) * .9D;
            double spot = R * (.30D + .62D * hash(k, 17));
            double cycle = frac(time * speedOf(k, 23) + hash(k, 29));
            double shrink = spot * (.55D + .40D * cycle);
            Vec3 at = new Vec3(Math.cos(angle) * spot, .05D, Math.sin(angle) * spot);
            float a = (1F - (float) cycle) * (.10F + .16F * charge);
            WaterGeometry.ringColor(out, pose, at, UP, shrink, .012 + .012 * (1 - cycle),
                    C_R, C_G, C_B, a, 20);
            // 每一圈还带一颗离地的小水珠，圈一收它就往上走。
            WaterGeometry.droplet(out, pose, at.add(0, .06 + .30 * cycle, 0),
                    .028 + .020 * (1 - cycle), time * .8 + k, W_R, W_G, W_B, a * 1.4F);
            // 离地那一下的小水冠：五根朝上的短刺，看着像水被"拔"起来，而不是溅起来。
            for (int s = 0; s < 5; s++) {
                double sa = s * Math.PI * 2D / 5D + time * .6D + k;
                double lift = .10 + .34 * (1 - cycle);
                Vec3 from = at.add(Math.cos(sa) * .07, .02, Math.sin(sa) * .07);
                streak(out, pose, from, from.add(0, lift, 0), .010, a * 1.5F);
            }
        }

        // 3. 主体：水线往天上走。cycle 越大越高、越淡、越往中间收（被云吸进去）。
        int drops = dropCountFor(tier);
        for (int i = 0; i < drops; i++) {
            double seed = hash(i, 7);
            double speed = .55D + .45D * hash(i, 13);
            double cycle = frac(time * .045D * speed + hash(i, 31) + phaseOf(i, seed));
            double y = dropHeight(cycle, height);
            double angle = hash(i, 37) * Math.PI * 2D + cycle * .55D * (i % 2 == 0 ? 1 : -1);
            double reach = R * (.30D + .95D * hash(i, 41)) * dropRadiusFactor(cycle);
            Vec3 foot = new Vec3(Math.cos(angle) * reach, .04D + y, Math.sin(angle) * reach);
            double len = (.34D + .55D * speed) * (1D - .38D * cycle);
            float a = dropAlpha(cycle) * (.30F + .46F * charge);
            if (a < .004F) {
                continue;
            }
            Vec3 tip = foot.add(0, len, 0);
            double width = .011 + .007 * speed;
            // 水线本身压暗，只有最上面那一小截亮成一颗"头"。
            // 这一颗头是关键：静止画面里看方向全靠它，不然逆雨和普通下雨长得一模一样。
            streak(out, pose, foot, tip, width, a * .55F);
            streak(out, pose, tip.add(0, -len * .30D, 0), tip, width * 1.35D,
                    Math.min(1F, a * 1.85F));
        }

        // 4. 终点：头顶那团积水云。先是薄薄一片，越读越厚、越亮，最后收成一颗珠子。
        double cloudY = height;
        double cloudR = R * (.50D + .35D * charge);
        float cloudA = .09F + .23F * charge;
        // 不是一张硬边圆盘——那看着像飞碟——用十来个大小不一的软水团堆起来，再压一颗亮芯。
        for (int k = 0; k < 10; k++) {
            double pa = k * Math.PI * 2D / 10D + time * .25D;
            double pr = cloudR * (.30D + .42D * hash(k, 61));
            Vec3 puff = new Vec3(Math.cos(pa) * pr, cloudY + (hash(k, 67) - .5D) * .40D,
                    Math.sin(pa) * pr);
            WaterGeometry.disk(out, pose, puff, UP, cloudR * (.26D + .16D * hash(k, 71)),
                    C_R, C_G, C_B, cloudA * .60F, 18);
        }
        WaterGeometry.disk(out, pose, new Vec3(0, cloudY, 0), UP, cloudR * .58D,
                W_R, W_G, W_B, cloudA * .85F, 26);
        for (int arm = 0; arm < 3; arm++) {
            double spin = time * .55D + arm * Math.PI * 2D / 3D;
            Vec3 a0 = new Vec3(Math.cos(spin) * cloudR * .30D, cloudY - .02D,
                    Math.sin(spin) * cloudR * .30D);
            Vec3 a1 = new Vec3(Math.cos(spin + 1.25D) * cloudR * .92D, cloudY + .04D,
                    Math.sin(spin + 1.25D) * cloudR * .92D);
            streak(out, pose, a0, a1, .018, cloudA * 1.9F);
        }
        // 云底下垂下来的几缕水：被云吸进去的最后一段，正好把"下面那些线"和"上面那团云"缝起来。
        for (int k = 0; k < 6; k++) {
            double ka = k * Math.PI * 2D / 6D + time * .18D;
            double kr = cloudR * (.45D + .40D * hash(k, 53));
            double drop = .55D + .85D * (1D - charge);
            Vec3 top = new Vec3(Math.cos(ka) * kr, cloudY - .03D, Math.sin(ka) * kr);
            streak(out, pose, top.add(0, -drop, 0), top, .013, cloudA * 1.5F);
        }
        // 末尾那一下：云收成一颗水珠，往下压（读条要放出去了）。
        if (p > .72F) {
            float tail = smooth((p - .72F) / .28F);
            double bead = cloudR * (.85D - .55D * tail);
            double beadY = cloudY - .55D * tail;
            WaterGeometry.droplet(out, pose, new Vec3(0, beadY, 0),
                    .18D + .16D * tail, time * 1.1D, W_R, W_G, W_B, .35F + .45F * tail);
            WaterGeometry.ringColor(out, pose, new Vec3(0, beadY - .12D, 0), UP, bead * 1.5D,
                    .014, W_R, W_G, W_B, .22F * tail, 28);
        }
    }

    /** 一条水线：两片交叉的窄四边形，任何方向看都不会变成一条看不见的线。 */
    private static void streak(VertexConsumer out, Matrix4f pose, Vec3 a, Vec3 b, double halfWidth,
                               float alpha) {
        Vec3 axis = b.subtract(a);
        if (axis.lengthSqr() < 1e-9) {
            return;
        }
        axis = axis.normalize();
        Vec3 across = axis.cross(new Vec3(1, 0, 0));
        if (across.lengthSqr() < 1e-6) {
            across = axis.cross(new Vec3(0, 0, 1));
        }
        across = across.normalize().scale(halfWidth);
        Vec3 across2 = axis.cross(across);
        if (across2.lengthSqr() < 1e-9) {
            return;
        }
        across2 = across2.normalize().scale(halfWidth);
        WaterGeometry.quad(out, pose, a.subtract(across), a.add(across), b.add(across),
                b.subtract(across), W_R, W_G, W_B, alpha);
        WaterGeometry.quad(out, pose, a.subtract(across2), a.add(across2), b.add(across2),
                b.subtract(across2), C_R, C_G, C_B, alpha * .70F);
    }

    // ==================================================================
    //  曲线（也是测试直接钉的东西）
    // ==================================================================

    /** 档位越高，被抽起来的水柱越高：t1 5.7 格，t5 9.9 格（比一栋房子还高，但不至于糊到镜头里）。 */
    static double columnHeightFor(int tier) {
        int t = Mth.clamp(tier, 1, 5);
        return 4.6D + 1.05D * t;
    }

    /** 水线的条数：t1 144 条，t5 280 条（每滴两片交叉四边形，再加一截亮头）。 */
    static int dropCountFor(int tier) {
        int t = Mth.clamp(tier, 1, 5);
        return 110 + 34 * t;
    }

    /** 一滴水的一生：cycle 0 在地面，1 在云里——**往上**。 */
    static double dropHeight(double cycle, double height) {
        return Mth.clamp(cycle, 0D, 1D) * height;
    }

    /** 越往上越淡（被云吸掉），地面上最实。 */
    static float dropAlpha(double cycle) {
        double c = Mth.clamp(cycle, 0D, 1D);
        return (float) Math.pow(1D - c, .55D);
    }

    /** 越往上越往中间收：底部 1.0，顶部 0.72。 */
    static double dropRadiusFactor(double cycle) {
        return 1D - .28D * Mth.clamp(cycle, 0D, 1D);
    }

    /** 读条推进本身也把水往上"提"（读得越久，雨越大）。 */
    static float chargeGlow(float ratio) {
        float p = Mth.clamp(ratio, 0F, 1F);
        return smooth(Mth.clamp(p / .82F, 0F, 1F));
    }

    // ==================================================================
    //  工具
    // ==================================================================

    private static double speedOf(int index, long salt) {
        return .85D + .55D * hash(index, salt);
    }

    /** 让每条水线错开出发，不然它们会排成一条线一起冲上去。 */
    private static double phaseOf(int index, double seed) {
        return seed * .65D;
    }

    private static double frac(double value) {
        double f = value - Math.floor(value);
        return f < 0 ? f + 1 : f;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    /** 稳定的每颗种子随机数：同一个 i 每一帧都拿到同一个值，水线才不会乱跳。 */
    private static double hash(int index, long salt) {
        long x = index * 0x9E3779B97F4A7C15L + salt * 0xBF58476D1CE4E5B9L;
        x ^= x >>> 30;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 27;
        x *= 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return (x >>> 11) * 0x1.0p-53;
    }
}
