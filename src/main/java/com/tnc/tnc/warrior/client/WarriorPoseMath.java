package com.tnc.tnc.warrior.client;

import com.tnc.tnc.warrior.WarriorAnimation;

/**
 * 玩家动作的<b>纯数学</b>部分：给定动作与播放进度，算出每根骨骼该转到哪。
 *
 * <p>刻意不 import 任何 Minecraft 类型 —— 于是可以在普通 JUnit 里验证"动作真的在动、
 * 左右手镜像正确、旋身真的转满一圈"，而不需要启动客户端。{@link WarriorPose} 只负责
 * 把这些数字扣到 {@code HumanoidModel} 的骨骼上。
 *
 * <p>约定：手臂角度是<b>绝对值</b>（0 = 自然下垂，-π/2 = 向前平举）；
 * 身体/头/腿是相对原版姿势的<b>增量</b>。
 */
public final class WarriorPoseMath {

    private static final float HALF_PI = (float) (Math.PI / 2);

    private WarriorPoseMath() {
    }

    /** 一小段动作的骨骼角度。 */
    public record Pose(float rightArmX, float rightArmY, float rightArmZ,
                       float leftArmX, float leftArmY, float leftArmZ,
                       float bodyYaw, float bodyPitch, float headYaw, float headPitch,
                       float rightLegX, float leftLegX) {
    }

    /** 算出某个动作在进度 {@code t}（0..1）时、某只惯用手对应的姿势。 */
    public static Pose of(WarriorAnimation animation, float t, boolean leftHanded) {
        float x = Math.max(0, Math.min(1, t));
        Pose right = switch (animation) {
            case ULTIMATE -> spin(ease(x));
            case SWORD_CHARGE, GS_CHARGE, HM_CHARGE -> overhead(x);
            case SPEAR_CHARGE -> thrust(x);
            case SLASH_LEFT -> slash(ease(x), false, false);
            case SLASH_RIGHT -> slash(ease(x), true, false);
            case SLASH_HORIZONTAL -> slash(ease(x), false, true);
            case THRUST_DASH -> thrust(x);
            case SPIN_SLASH -> spin(ease(x));
            case OVERHEAD_SMASH -> overhead(x);
            case GUARD_BASH -> guardBash(x);
            case BOW_DRAW -> bowDraw(x);
            case EVADE -> evade(x);
            case GROUND_QUAKE -> groundQuake(x);
            // 长剑/重剑/战锤/长弓的连招优先走 Blockbench 关键帧（见 WarriorComboAnimations）；
            // 这里只是文件缺失时的退路，形状挑最接近的程序化动作。
            case SWORD_BREAKER -> slash(ease(x), false, false);
            case SWORD_SPIN -> spin(ease(x));
            case SWORD_STANCE -> guardBash(x);
            case SWORD_COUNTER -> slash(ease(x), true, true);
            case GS_SWEEP -> slash(ease(x), false, true);
            case GS_SMASH -> overhead(x);
            case GS_WALL -> guardBash(x);
            case GS_STANCE -> guardBash(x);
            case HM_CRUSH -> overhead(x);
            case HM_QUAKE -> groundQuake(x);
            case HM_CUT -> guardBash(x);
            case HM_STANCE -> guardBash(x);
            case BOW_AIM -> bowDraw(x);
            case BOW_DASH_SHOT -> bowDraw(x);
            case BOW_BACK_SHOT -> evade(x);
            case BOW_CHARGE -> bowDraw(Math.min(x,.7f));
            case SWORD_RETREAT -> evade(x);
            case SWORD_SWALLOW -> spin(ease(x));
            case GS_RIFT -> overhead(x);
            case GS_ADVANCE -> overhead(x);
            case HM_RUIN -> overhead(x);
            case HM_DRIVE -> overhead(x);
            case BOW_PIERCE -> bowDraw(x);
            case BOW_STRAFE -> bowDraw(x);
            case BOW_VOLLEY -> bowDraw(x);
            case SPEAR_BREAK -> thrust(x);
            case SPEAR_DRAGON -> thrust(x);
            case SPEAR_RETURN -> thrust(x);
            case SPEAR_GUARD -> thrust(x);
            case SPEAR_FLOW -> thrust(x);
            case SCYTHE_POUNCE -> spin(ease(x));
            case SCYTHE_REAP -> spin(ease(x));
            case SCYTHE_HOOK -> spin(ease(x));
            case SCYTHE_GUARD -> guardBash(x);
            case SCYTHE_EXECUTE -> overhead(x);
            case SCYTHE_WHIRL -> spin(ease(x));

        };
        return leftHanded ? mirror(right) : right;
    }

    /** 斜劈/横扫：手臂从后上方抡到前下方，身体跟着转。 */
    private static Pose slash(float e, boolean reverse, boolean horizontal) {
        float armX;
        float armY;
        float armZ;
        if (horizontal) {
            armX = -HALF_PI + 0.12f * e;
            armY = -1.25f + 2.5f * e;
            armZ = 0f;
        } else {
            armX = -2.35f + 1.95f * e;
            armY = reverse ? 0.75f - 1.35f * e : -0.55f + 0.95f * e;
            armZ = reverse ? -0.45f + 0.55f * e : 0.35f - 0.5f * e;
        }
        float bodyYaw = horizontal ? -0.35f + 0.7f * e : (reverse ? 0.26f - 0.44f * e : -0.26f + 0.44f * e);
        float headYaw = horizontal ? 0.18f * e : -0.1f * e;
        return new Pose(armX, armY, armZ, 0.25f * e, 0f, 0f, bodyYaw, 0f, headYaw, 0f, 0f, 0f);
    }

    /** 突进刺：身体前倾、主手前刺，前腿弓步。 */
    private static Pose thrust(float t) {
        float punch = (float) Math.sin(Math.PI * Math.min(1, t * 1.25));
        return new Pose(-HALF_PI - 0.12f + 0.22f * punch, 0f, 0f,
                0f, 0f, 0f,
                0f, 0.22f * punch, 0f, -0.12f * punch,
                0.45f * punch, -0.35f * punch);
    }

    /** 旋身：身体真转一圈，双臂平举展开。 */
    private static Pose spin(float e) {
        return new Pose(-HALF_PI, 0f, 1.15f,
                -HALF_PI, 0f, -1.15f,
                (float) (Math.PI * 2 * e), 0.12f * (float) Math.sin(Math.PI * e), 0f, 0f, 0f, 0f);
    }

    /** 过顶重劈：双手举过头再砸下。 */
    private static Pose overhead(float t) {
        float e = ease(t);
        float lift = (float) Math.sin(Math.PI * Math.min(1, t * 1.4));
        return new Pose(-2.75f + 2.3f * e, -0.15f, 0.15f,
                -2.75f + 2.3f * e, 0.15f, -0.15f,
                0f, 0.28f * lift, 0f, 0.12f * lift, 0f, 0f);
    }

    /** 守势：副手横挡在前，主手收在身侧再顶出去。 */
    private static Pose guardBash(float t) {
        float punch = (float) Math.sin(Math.PI * Math.min(1, t * 1.5));
        return new Pose(-0.55f - 1.05f * punch, 0.35f, 0.2f,
                -1.75f, 0.55f, -0.75f,
                -0.22f + 0.3f * punch, 0.1f * punch, 0f, 0f,
                0.16f * punch, -0.12f * punch);
    }

    /** 弓：副手前推、主手后拉；后 1/3 松手回弹。 */
    private static Pose bowDraw(float t) {
        float draw = Math.min(1, t / 0.72f);
        float release = t > 0.72f ? (t - 0.72f) / 0.28f : 0;
        float drawYaw = 0.85f * draw;
        float drawX = -HALF_PI - 0.05f;
        if (release > 0) {
            drawYaw *= Math.max(0, 1 - release * 2.5f);
            drawX += 0.35f * release;
        }
        return new Pose(drawX, drawYaw, -0.25f * draw,
                -HALF_PI + 0.05f, 0f, 0f,
                -0.18f * draw, 0f, 0f, 0f, 0f, 0f);
    }

    /** 闪避：身体侧倾下蹲、双腿收拢。 */
    private static Pose evade(float t) {
        float env = (float) Math.sin(Math.PI * t);
        return new Pose(-0.5f, 0f, 0.5f * env,
                -0.5f, 0f, -0.5f * env,
                0.35f * env, 0.7f * env, 0f, 0.25f * env,
                0.85f * env, 0.55f * env);
    }

    /** 震地：双手举高后随身体下砸，屈膝。 */
    private static Pose groundQuake(float t) {
        float e = ease(t);
        float env = (float) Math.sin(Math.PI * t);
        return new Pose(-2.6f + 2.5f * e, 0f, 0.1f,
                -2.6f + 2.5f * e, 0f, -0.1f,
                0f, 0.45f * env, 0f, 0f,
                0.4f * env, 0.4f * env);
    }

    /**
     * 左手玩家镜像：交换左右臂，并把绕竖轴的旋转（y）与张开的旋转（z）取反。
     * 前后/上下（x）不取反 —— 抬手的角度左右手是一致的。
     */
    private static Pose mirror(Pose p) {
        return new Pose(p.leftArmX(), -p.leftArmY(), -p.leftArmZ(),
                p.rightArmX(), -p.rightArmY(), -p.rightArmZ(),
                -p.bodyYaw(), p.bodyPitch(), -p.headYaw(), p.headPitch(),
                p.leftLegX(), p.rightLegX());
    }

    /** 平滑 0→1，起收都不突跳。 */
    static float ease(float t) {
        float x = Math.max(0, Math.min(1, t));
        return x * x * (3 - 2 * x);
    }
}
