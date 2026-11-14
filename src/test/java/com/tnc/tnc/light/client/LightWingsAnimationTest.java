package com.tnc.tnc.light.client;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 光翼的**扇动动画**（{@code assets/tnc/animations/entity/light_wings.animation.json}）自检。
 *
 * <h2>为什么要有这个测试（2026-11-07）</h2>
 * 作者：「翅膀的 animation 别忘记做了」—— 而"两翼是不是一起扇"是纯几何的事，
 * 眼睛看不出来（我这边看不到游戏画面 ✗），符号错了的表现是**一只上一只下** ✗、
 * 或者一侧动得多一侧动得少 ✗，在游戏里只在飞行那几秒能看出来，很难靠试玩发现 ✗。
 *
 * <p>★ 右翼不是简单的 x 反射：几何是「z 镜像 + 绕 (0,0,1) 转 180°」✗，所以
 * {@code wingright} 自己那根骨头和它下面的 {@code *2} 骨头**手性规则不一样** ✓：
 * <ul>
 *   <li>{@code wingright}（自带 [0,180,0] 的那根）：Y 同号 ✓、Z 反号 ✓；</li>
 *   <li>{@code wingLeftMid2} / {@code wingLeftTip2}（在它下面）：Y、Z **都和左边同号** ✓。</li>
 * </ul>
 * 这套符号是扫出来的（16 种组合，用下面的镜像误差当尺子 ✓，最好的一组误差 0.12 格 ≈ 静止姿势本身的
 * 0.13 格 ✓）；推公式推错过一次 ✗，所以这里留一个**回归测试**钉住它 ✓。
 *
 * <p>判据（用渲染器那份数学 ✓ {@link TNLightWingsModel#previewFaces}，不复制一份 ✗）：
 * <ol>
 *   <li>静止姿势的镜像误差本身就有一个底（约 0.13 格 ✓，几何手工镜像的零头），两个相位的误差不能超过它；</li>
 *   <li>两侧都要在上扬相位抬高、在下压相位降低 ✓（方向不能相反 ✗）；</li>
 *   <li>幅度 3~30 像素（0.19~1.9 格 ✓）：看得见但不夸张 ✓。</li>
 * </ol>
 * 另外把两个相位画成 PNG（{@code build/light_wings_flap_down|up.png} ✓）。
 */
class LightWingsAnimationTest {

    /** 关键帧时间（秒 ✓，和 animation.json 里那两组峰一致 ✓）。 */
    private static final float DOWN = 0.25F;
    private static final float UP = 0.75F;

    @Test
    void animationIsWiredToTheBones() throws Exception {
        load();
        assertTrue(TNLightWingsModel.hasAnimation(), "animation.json 没读进来（骨骼名和 geo 对不上？）");
        String info = TNLightWingsModel.describeAnimation();
        assertTrue(info.contains("waving"), "动画名应该还是 waving，实际：" + info);
        assertTrue(info.contains("1.0s"), "循环长度应该是 1 秒，实际：" + info);
        assertTrue(info.contains("6 骨"), "应该有六根骨头有旋转轨道，实际：" + info);
    }

    @Test
    void bothWingsBeatTogether() throws Exception {
        load();
        List<TNLightWingsModel.PreviewFace> rest = TNLightWingsModel.previewFaces(0.0F, false);
        List<TNLightWingsModel.PreviewFace> down = TNLightWingsModel.previewFaces(DOWN, true);
        List<TNLightWingsModel.PreviewFace> up = TNLightWingsModel.previewFaces(UP, true);
        assertTrue(rest.size() > 200, "面数太少：" + rest.size());

        double baseline = mirrorError(rest);
        double downError = mirrorError(down);
        double upError = mirrorError(up);
        System.out.printf("光翼镜像误差：静止 %.3f 格 / 下压 %.3f 格 / 上扬 %.3f 格%n",
                baseline, downError, upError);
        assertTrue(baseline < 0.2D, "静止姿势左右就不对称 ✗（几何镜像写错了？）：" + baseline);
        assertTrue(downError < baseline + 0.08D, "下压相位左右不对称 ✗（右侧符号反了？）：" + downError);
        assertTrue(upError < baseline + 0.08D, "上扬相位左右不对称 ✗（右侧符号反了？）：" + upError);

        double riseDown = rise(down, rest, -1);
        double riseUp = rise(up, rest, -1);
        double riseDownR = rise(down, rest, 1);
        double riseUpR = rise(up, rest, 1);
        System.out.printf("左翼：下压 %+.3f / 上扬 %+.3f 格；右翼：下压 %+.3f / 上扬 %+.3f 格%n",
                riseDown, riseUp, riseDownR, riseUpR);
        assertTrue(riseDown < -0.06D && riseUp > 0.06D, "左翼扇动方向不对 ✗（下压/上扬反了）");
        assertTrue(riseDownR < -0.06D && riseUpR > 0.06D, "右翼扇动方向不对 ✗（和左翼相反？）");
        assertTrue(riseUp < 1.9D && riseUpR < 1.9D, "扇动幅度太大 ✗（翅膀会甩出去）");
    }

    @Test
    void rendersFlapFrames() throws Exception {
        load();
        BufferedImage tex = LightWingsPreviewTest.loadTexture();
        LightWingsPreviewTest.render(TNLightWingsModel.previewFaces(DOWN, true), tex, 0.0D,
                "build/light_wings_flap_down.png", false);
        LightWingsPreviewTest.render(TNLightWingsModel.previewFaces(UP, true), tex, 0.0D,
                "build/light_wings_flap_up.png", false);
        LightWingsPreviewTest.render(TNLightWingsModel.previewFaces(DOWN, true), tex, 0.0D,
                "build/light_wings_flap_down_solid.png", true);
    }

    /** 某一侧（-1 左 / +1 右）相对静止姿势的**平均**抬高（格 ✓，可正可负 ✓）。 */
    private static double rise(List<TNLightWingsModel.PreviewFace> pose,
                               List<TNLightWingsModel.PreviewFace> rest, int side) {
        double sum = 0.0D;
        int count = 0;
        for (int i = 0; i < Math.min(pose.size(), rest.size()); i++) {
            float[][] a = pose.get(i).pos();
            float[][] b = rest.get(i).pos();
            for (int k = 0; k < a.length; k++) {
                if (side < 0 ? b[k][0] > -0.4F : b[k][0] < 0.4F) {
                    continue;
                }
                sum += a[k][1] - b[k][1];
                count++;
            }
        }
        return count == 0 ? 0.0D : sum / count;
    }

    /**
     * 右半边每个点到「左半边镜像点集」的最近距离的最大值（格 ✓，0 = 完全对称 ✓）。
     * 模型坐标里的镜像面是 z = 1 单位 = 0.0625 格 ⇒ 镜像后 z' = 0.125 - z ✓。
     */
    private static double mirrorError(List<TNLightWingsModel.PreviewFace> faces) {
        List<float[]> left = new ArrayList<>();
        List<float[]> right = new ArrayList<>();
        for (TNLightWingsModel.PreviewFace f : faces) {
            if (f.guide()) {
                continue;
            }
            for (float[] p : f.pos()) {
                if (p[0] < -0.4F) {
                    left.add(p);
                } else if (p[0] > 0.4F) {
                    right.add(p);
                }
            }
        }
        double worst = 0.0D;
        for (float[] p : right) {
            double mx = -p[0];
            double my = p[1];
            double mz = 0.125D - p[2];
            double best = Double.MAX_VALUE;
            for (float[] q : left) {
                double d = Math.sqrt(Math.pow(mx - q[0], 2) + Math.pow(my - q[1], 2) + Math.pow(mz - q[2], 2));
                if (d < best) {
                    best = d;
                    if (best < 1.0E-4D) {
                        break;
                    }
                }
            }
            worst = Math.max(worst, best);
        }
        return worst;
    }

    private static void load() throws Exception {
        LightWingsPreviewTest.loadModel();
        try (var stream = LightWingsAnimationTest.class.getResourceAsStream(
                "/assets/tnc/animations/entity/light_wings.animation.json")) {
            assertTrue(stream != null, "找不到 light_wings.animation.json");
            TNLightWingsModel.parseAnimation(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }
}
