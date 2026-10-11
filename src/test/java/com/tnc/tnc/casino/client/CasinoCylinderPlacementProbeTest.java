package com.tnc.tnc.casino.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 {@code cylinder} / {@code boxPolar} / {@code studs} 的**实际顶点位置**抓出来，
 * 直接印成文本 ✓ —— 用来回答"几何到底画在哪、画没画出来"✗。
 *
 * <p>这是一个**取证用**的测试 ✓（不是断言型的闸门 ✓）：
 * 作者报"老虎机没看到东西在转、转盘桌没看见转盘" ✗，
 * 而静态柜体看得到 ✓ ⇒ 说明问题出在**动态那几层**（圆筒/圆环）✓。
 * 本测试把那一层的顶点坐标全部打出来 ✓，看它有没有落到机器外面 / 缩成一点 ✓。
 *
 * <p>跑法：{@code gradlew test --tests "*CasinoCylinderPlacementProbeTest*" -i} ✓
 */
class CasinoCylinderPlacementProbeTest {

    /** 只记顶点位置，不校验元素（校验由 CasinoGeometryVertexFormatTest 负责 ✓）。 */
    private static final class PositionRecorder implements VertexConsumer {
        final List<float[]> positions = new ArrayList<>();

        @Override public VertexConsumer vertex(double x, double y, double z) {
            positions.add(new float[]{(float) x, (float) y, (float) z});
            return this;
        }

        @Override public VertexConsumer vertex(Matrix4f matrix, float x, float y, float z) {
            var v = new Vector3f(x, y, z).mulPosition(matrix);
            positions.add(new float[]{v.x, v.y, v.z});
            return this;
        }

        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() { }
        @Override public void defaultColor(int r, int g, int b, int a) { }
        @Override public void unsetDefaultColor() { }
    }

    private static void report(String label, PositionRecorder rec) {
        if (rec.positions.isEmpty()) {
            System.out.println("[PROBE] " + label + " -> NO VERTICES AT ALL (!!)");
            return;
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (float[] p : rec.positions) {
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
            minZ = Math.min(minZ, p[2]); maxZ = Math.max(maxZ, p[2]);
        }
        System.out.printf("[PROBE] %-26s verts=%4d  x %.3f..%.3f  y %.3f..%.3f  z %.3f..%.3f%n",
                label, rec.positions.size(), minX, maxX, minY, maxY, minZ, maxZ);
    }

    @Test
    void printWhereTheCylinderAndRingsLand() {
        // ---- ① 老虎机的一只滚筒（照 TNSlotMachineRenderer 的真实参数 ✓）----
        //   REEL_X[0]=0.28  REEL_Y=1.30  REEL_R=0.105  REEL_H=0.30  z=(FRONT+0.86)/2+0.06
        Vector3f reelAxis = new Vector3f(0.28F, 1.30F, (0.06F + 0.86F) * 0.5F + 0.06F);
        PositionRecorder reel = new PositionRecorder();
        TNCasinoGeometry.cylinder(new PoseStack(), reel, reelAxis, 0.105F, 0.30F, 0.02F, 12, 0.0F,
                CasinoPalette.DRUM, 0xF000F0, null);
        report("slot reel (cylinder)", reel);

        // ---- ② 转盘的号码环（POCKET_R=0.42，37 格 ×2 段 ✓）----
        Vector3f wheelAxis = new Vector3f(0.86F, 0.0F, 1.00F);
        PositionRecorder pockets = new PositionRecorder();
        TNCasinoGeometry.cylinder(new PoseStack(), pockets, wheelAxis, 0.42F, 0.022F, 0.10F,
                37 * 2, 0.0F, CasinoPalette.CARD_FACE, 0xF000F0, i -> 0xA32824);
        report("roulette pocket ring", pockets);

        // ---- ③ 挡珠棱（一圈小柱 ✓）----
        PositionRecorder studs = new PositionRecorder();
        TNCasinoGeometry.studs(new PoseStack(), studs, wheelAxis, 0.48F, 0.028F, 0.055F,
                37, 0.0F, CasinoPalette.BRASS, 0xF000F0);
        report("roulette stud ring", studs);

        // ---- ④ 单块板：确认 boxPolar 没把箱子转没 ✓ ----
        PositionRecorder one = new PositionRecorder();
        TNCasinoGeometry.boxPolar(new PoseStack(), one, wheelAxis, new Vector3f(0.1F, 0.02F, 0.02F),
                0.4F, 0.0F, CasinoPalette.BRASS, 0xF000F0);
        report("single boxPolar", one);

        // ---- ⑤ 有个 pose 先转 90° 的情况（盘在转的时候 ✓）----
        PoseStack spun = new PoseStack();
        spun.mulPose(com.mojang.math.Axis.YP.rotationDegrees(137.0F));
        PositionRecorder spunRec = new PositionRecorder();
        TNCasinoGeometry.cylinder(spun, spunRec, reelAxis, 0.105F, 0.30F, 0.02F, 12, 0.0F,
                CasinoPalette.DRUM, 0xF000F0, null);
        report("reel under 137deg pose", spunRec);
    }
}
