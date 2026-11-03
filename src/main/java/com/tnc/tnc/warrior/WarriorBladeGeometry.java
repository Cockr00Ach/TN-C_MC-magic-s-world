package com.tnc.tnc.warrior;

import net.minecraft.world.phys.Vec3;

/**
 * 常驻武器光效的几何：把剑身换算成世界坐标里的三个点（握把 / 中段 / 剑尖）。
 *
 * <p>为什么算在服务端：光效走 {@code ServerLevel#sendParticles} 广播，附近所有客户端
 * 看到的是同一套坐标；在客户端各算一套的话，别人眼里的剑身辉光会错位。这里只做纯
 * 向量运算、不读世界，所以能被单元测试直接钉住。
 *
 * <p>三点是"手 → 剑身 → 剑尖"同一条直线：{@code points[1]} 只是为了少算一次插值，
 * 光效按 0..1 的比例直接取这条线段上的点即可。
 */
public final class WarriorBladeGeometry {

    /** 握把相对眼睛的偏移：右手偏右、略低于眼、稍微前伸。 */
    private static final double GRIP_RIGHT = 0.34;
    private static final double GRIP_FORWARD = 0.10;
    private static final double GRIP_DROP = -0.30;

    /** 挥砍时剑身绕"右轴"向前下方压下的弧度（静止 → 收刀约 115°）。 */
    private static final double SWING_ARC = 2.0;

    private WarriorBladeGeometry() {
    }

    /**
     * 剑身长度（格）：重剑最长、锤次之、剑与斧更短。
     *
     * <p>数值跟着物品模型的第三人称缩放走（2026-11-02 整体放大 1.35～1.45 倍，
     * 见 {@code assets/tnc/models/item/*.json} 的 {@code display}），否则刀光会比剑短一截、
     * 看起来"光从剑尖前面就断了"。改模型缩放时这里要同步改。
     *
     * <p>弓返回 0 —— 弓没有剑身，调用方据此跳过整段光效。
     */
    public static double length(WarriorWeaponFamily family) {
        if (family == null) {
            return 1.05;
        }
        return switch (family) {
            case GREATSWORD -> 1.62;
            case HAMMER -> 1.42;
            case BOW -> 0.0;
            case LONGSWORD -> 1.30;
            // 长枪最长：模型本身就有 2 格多的枪杆，刀光要跟着枪尖走
            case SPEAR -> 1.75;
        };
    }

    /**
     * 算剑身三点。
     *
     * @param eye          眼睛位置（{@code player.getEyePosition()}）
     * @param yawDegrees   偏航（{@code player.getYRot()}）
     * @param pitchDegrees 俯仰（{@code player.getXRot()}，正数=低头）
     * @param swing        挥砍进度 0..1（{@code player.getAttackAnim(0)}）
     * @param length       剑身长度，见 {@link #length(WarriorWeaponFamily)}
     * @return {@code [握把, 中段, 剑尖]}
     */
    public static Vec3[] blade(Vec3 eye, float yawDegrees, float pitchDegrees, double swing, double length) {
        double yaw = Math.toRadians(yawDegrees);
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 up = new Vec3(0, 1, 0);
        double pitch = Math.toRadians(-pitchDegrees);
        Vec3 look = forward.scale(Math.cos(pitch)).add(up.scale(Math.sin(pitch)));
        // 静止时剑身朝"前上"；挥砍时绕右轴压向前下，看起来就是attackAnim 那一下劈砍
        Vec3 rest = look.scale(0.55).add(up.scale(0.55)).add(right.scale(0.12)).normalize();
        Vec3 dir = rotate(rest, right, -SWING_ARC * clamp01(swing));
        Vec3 hand = eye.add(right.scale(GRIP_RIGHT)).add(forward.scale(GRIP_FORWARD)).add(up.scale(GRIP_DROP));
        return new Vec3[]{hand, hand.add(dir.scale(length * 0.55)), hand.add(dir.scale(length))};
    }

    /** 绕任意轴旋转（罗德里格斯公式）；{@code axis} 会被归一化。 */
    public static Vec3 rotate(Vec3 v, Vec3 axis, double radians) {
        Vec3 n = axis.normalize();
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return v.scale(cos).add(n.cross(v).scale(sin)).add(n.scale(n.dot(v) * (1.0 - cos)));
    }

    static double clamp01(double value) {
        return value <= 0 ? 0 : Math.min(value, 1);
    }

    /** 剑尖到握把的距离，单元测试用它确认"剑有多长"。 */
    public static double span(Vec3[] points) {
        return points[0].distanceTo(points[2]);
    }
}
