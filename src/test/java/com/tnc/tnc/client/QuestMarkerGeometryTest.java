package com.tnc.tnc.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 寻人点 HUD 的投影几何。
 *
 * <p>这些断言都是**能手算**的（朝南/朝西/朝北、低头 45°），写错一项就会红：
 * 旧实现把屏幕左右镜像了、边缘箭头也只有朝南时才对，见
 * {@code work/verify_marker_math.py} 的对照表。
 */
class QuestMarkerGeometryTest {

    private static final double EPS = 1.0e-9D;

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final double FOV = 70.0D;

    // ---------------------------------------------------------------- 摄像机基向量
    // 原版：yaw 0 = 朝南(+Z)，90 = 朝西(-X)；pitch 为正 = 低头。

    @Test
    void facingSouth_westIsOnTheRight() {
        // 站在原点朝南：右手边是西(-X)。
        QuestMarkerGeometry.CameraSpace space =
                QuestMarkerGeometry.cameraSpace(-5.0D, 0.0D, 10.0D, 0.0D, 0.0D);
        assertEquals(5.0D, space.right(), EPS);
        assertEquals(0.0D, space.up(), EPS);
        assertEquals(10.0D, space.depth(), EPS);
    }

    @Test
    void facingWest_targetAheadIsCentred() {
        QuestMarkerGeometry.CameraSpace space =
                QuestMarkerGeometry.cameraSpace(-10.0D, 0.0D, 0.0D, 90.0D, 0.0D);
        assertEquals(0.0D, space.right(), EPS);
        assertEquals(10.0D, space.depth(), EPS);
    }

    @Test
    void facingWest_northIsOnTheRight() {
        // 朝西时右手边是北(-Z)。目标在左前 45°（-10/-10），应在右半屏。
        QuestMarkerGeometry.CameraSpace space =
                QuestMarkerGeometry.cameraSpace(-10.0D, 0.0D, -10.0D, 90.0D, 0.0D);
        assertTrue(space.right() > 0.0D, "朝西时北侧目标必须在右手边");
        assertEquals(10.0D, space.depth(), EPS);
    }

    @Test
    void facingNorth_eastIsOnTheRight() {
        QuestMarkerGeometry.CameraSpace space =
                QuestMarkerGeometry.cameraSpace(10.0D, 0.0D, -10.0D, 180.0D, 0.0D);
        assertTrue(space.right() > 0.0D, "朝北时东侧目标必须在右手边");
    }

    // ---------------------------------------------------------------- 屏幕投影

    @Test
    void projectsTargetAheadToScreenCentre() {
        QuestMarkerGeometry.ScreenPoint point = QuestMarkerGeometry.project(
                QuestMarkerGeometry.cameraSpace(0.0D, 0.0D, 10.0D, 0.0D, 0.0D),
                FOV, WIDTH, HEIGHT);
        assertEquals(WIDTH / 2.0D, point.x(), 1.0e-6D);
        assertEquals(HEIGHT / 2.0D, point.y(), 1.0e-6D);
    }

    @Test
    void projectsRightHandTargetToTheRightHalf() {
        // 朝南、目标在西侧 5 格、前方 10 格 —— 屏幕上是右半屏。
        // 旧实现会算到 x≈186（左半屏），这条断言正是它的回归测试。
        QuestMarkerGeometry.ScreenPoint point = QuestMarkerGeometry.project(
                QuestMarkerGeometry.cameraSpace(-5.0D, 0.0D, 10.0D, 0.0D, 0.0D),
                FOV, WIDTH, HEIGHT);
        assertTrue(point.x() > WIDTH / 2.0D,
                "右手边的目标必须画在屏幕右半边，实际 x=" + point.x());
        assertEquals(HEIGHT / 2.0D, point.y(), 1.0e-6D);
    }

    @Test
    void lookingDownPutsAnEyeLevelTargetAboveTheCentre() {
        // 低头 45° 看正前方的同高度目标：它应该出现在屏幕上半部分。
        double scale = (HEIGHT / 2.0D) / Math.tan(Math.toRadians(FOV) / 2.0D);
        QuestMarkerGeometry.ScreenPoint point = QuestMarkerGeometry.project(
                QuestMarkerGeometry.cameraSpace(0.0D, 0.0D, 10.0D, 0.0D, 45.0D),
                FOV, WIDTH, HEIGHT);
        assertEquals(WIDTH / 2.0D, point.x(), 1.0e-6D);
        assertEquals(HEIGHT / 2.0D - scale, point.y(), 1.0e-6D);
    }

    @Test
    void lookingDownKeepsSideTargetsAtTheirRealHeight() {
        // 朝西 + 低头 45°，正前方的目标：屏幕高度由"上下分量"决定，
        // 旧实现把 dx 对上下分量的贡献漏掉了，会算成 y=300（水平线）。
        QuestMarkerGeometry.ScreenPoint point = QuestMarkerGeometry.project(
                QuestMarkerGeometry.cameraSpace(-10.0D, 0.0D, 0.0D, 90.0D, 45.0D),
                FOV, WIDTH, HEIGHT);
        assertTrue(point.y() < 0.0D,
                "低头 45° 时正前方的同高度目标应被推到画面顶部，实际 y=" + point.y());
    }

    // ---------------------------------------------------------------- 边缘箭头

    @Test
    void edgeArrow_facingSouth_aheadPointsUp() {
        assertArrow(0.0D, -1.0D,
                QuestMarkerGeometry.cameraSpace(0.0D, 0.0D, 10.0D, 0.0D, 0.0D));
    }

    @Test
    void edgeArrow_facingWest_aheadPointsUp() {
        // 旧实现这里得到 (0, +1) —— 箭头朝下，正好相反。
        assertArrow(0.0D, -1.0D,
                QuestMarkerGeometry.cameraSpace(-10.0D, 0.0D, 0.0D, 90.0D, 0.0D));
    }

    @Test
    void edgeArrow_facingWest_rightPointsRight() {
        assertArrow(1.0D, 0.0D,
                QuestMarkerGeometry.cameraSpace(0.0D, 0.0D, -10.0D, 90.0D, 0.0D));
    }

    @Test
    void edgeArrow_facingWest_leftPointsLeft() {
        assertArrow(-1.0D, 0.0D,
                QuestMarkerGeometry.cameraSpace(0.0D, 0.0D, 10.0D, 90.0D, 0.0D));
    }

    @Test
    void edgeArrow_behindPointsDown() {
        // 目标正后方：箭头落在屏幕下缘（提示"转身"），而不是镜像到上方。
        assertArrow(0.0D, 1.0D,
                QuestMarkerGeometry.cameraSpace(10.0D, 0.0D, 0.0D, 90.0D, 0.0D));
    }

    private static void assertArrow(double expectedX, double expectedY,
                                    QuestMarkerGeometry.CameraSpace space) {
        double[] dir = QuestMarkerGeometry.edgeDirection(space);
        assertEquals(expectedX, dir[0], 1.0e-6D);
        assertEquals(expectedY, dir[1], 1.0e-6D);
        assertEquals(1.0D, Math.hypot(dir[0], dir[1]), 1.0e-6D, "箭头方向必须是单位向量");
    }
}
