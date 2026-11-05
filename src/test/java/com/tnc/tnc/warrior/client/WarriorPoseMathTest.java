package com.tnc.tnc.warrior.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tnc.tnc.warrior.WarriorAnimation;
import org.junit.jupiter.api.Test;

/**
 * 玩家动作的纯数学验证：不启动客户端，也能证明"每个动作真的在动、左右手镜像正确、
 * 旋身真的转满一圈、拉弓会松手"。
 *
 * <p>这一层挡住的是"动画注册了但姿势是常量"这类问题 —— 那种 bug 在游戏里表现为
 * 角色举着武器一动不动，靠肉眼很容易漏。
 */
class WarriorPoseMathTest {

    private static final float LIMIT = (float) (Math.PI * 1.05);

    @Test
    void everyAnimationProducesFiniteBoundedAngles() {
        for (WarriorAnimation animation : WarriorAnimation.values()) {
            for (float t = 0; t <= 1; t += 0.1f) {
                assertFinite(animation, t, WarriorPoseMath.of(animation, t, false));
                assertFinite(animation, t, WarriorPoseMath.of(animation, t, true));
            }
        }
    }

    private static void assertFinite(WarriorAnimation animation, float t, WarriorPoseMath.Pose pose) {
        float[] values = {pose.rightArmX(), pose.rightArmY(), pose.rightArmZ(),
                pose.leftArmX(), pose.leftArmY(), pose.leftArmZ(),
                pose.bodyYaw(), pose.bodyPitch(), pose.headYaw(), pose.headPitch(),
                pose.rightLegX(), pose.leftLegX()};
        for (float value : values) {
            assertTrue(Float.isFinite(value), animation + " 在 t=" + t + " 出现了非法角度");
            assertTrue(Math.abs(value) <= LIMIT + 7.0f,
                    animation + " 在 t=" + t + " 的角度过大：" + value);
        }
    }

    @Test
    void everyAnimationActuallyMovesSomeBone() {
        for (WarriorAnimation animation : WarriorAnimation.values()) {
            WarriorPoseMath.Pose start = WarriorPoseMath.of(animation, 0.05f, false);
            float maxDelta = 0;
            for (float t = 0.25f; t <= 1.0f; t += 0.25f) {
                WarriorPoseMath.Pose pose = WarriorPoseMath.of(animation, t, false);
                maxDelta = Math.max(maxDelta, delta(start, pose));
            }
            assertTrue(maxDelta > 0.2f,
                    animation + " 的骨骼几乎没动（maxDelta=" + maxDelta + "），动作等于没做");
        }
    }

    private static float delta(WarriorPoseMath.Pose a, WarriorPoseMath.Pose b) {
        return Math.max(
                Math.max(Math.abs(a.rightArmX() - b.rightArmX()), Math.abs(a.rightArmY() - b.rightArmY())),
                Math.max(Math.max(Math.abs(a.leftArmX() - b.leftArmX()), Math.abs(a.bodyYaw() - b.bodyYaw())),
                        Math.max(Math.abs(a.bodyPitch() - b.bodyPitch()),
                                Math.max(Math.abs(a.rightLegX() - b.rightLegX()),
                                        Math.abs(a.headPitch() - b.headPitch())))));
    }

    @Test
    void spinTurnsAFullCircle() {
        WarriorPoseMath.Pose end = WarriorPoseMath.of(WarriorAnimation.SPIN_SLASH, 1f, false);
        assertEquals(Math.PI * 2, end.bodyYaw(), 0.02, "旋身斩必须真的转满 360°");
    }

    @Test
    void leftHandedPoseMirrorsTheMainArm() {
        WarriorPoseMath.Pose right = WarriorPoseMath.of(WarriorAnimation.SLASH_LEFT, 0.5f, false);
        WarriorPoseMath.Pose left = WarriorPoseMath.of(WarriorAnimation.SLASH_LEFT, 0.5f, true);
        assertEquals(right.rightArmX(), left.leftArmX(), 1e-4, "左手玩家的主手动作应换到左臂");
        assertEquals(-right.rightArmY(), left.leftArmY(), 1e-4, "左右镜像时 Y 轴要取反");
        assertEquals(-right.bodyYaw(), left.bodyYaw(), 1e-4, "身体转向也要镜像");
    }

    @Test
    void bowReleasesTheDrawArm() {
        WarriorPoseMath.Pose drawn = WarriorPoseMath.of(WarriorAnimation.BOW_DRAW, 0.6f, false);
        WarriorPoseMath.Pose released = WarriorPoseMath.of(WarriorAnimation.BOW_DRAW, 0.95f, false);
        assertTrue(Math.abs(released.rightArmY()) < Math.abs(drawn.rightArmY()),
                "拉弓后半段要松手回弹：drawn=" + drawn.rightArmY() + ", released=" + released.rightArmY());
    }
}
