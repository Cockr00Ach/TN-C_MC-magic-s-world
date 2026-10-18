package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.fire.TNBurnAuraEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * 燃烧链起手时「围着自己的那团火」（配套实体 {@link TNBurnAuraEntity}）。
 *
 * <p>作者 2026-10-11：「动画从火焰粒子变为出现火焰围绕，高等级法术的火焰规模大于低等级的，
 * <b>采用真实建模</b>」；2026-10-18 追加：「接着丰富燃烧链的释放特效，用真实建模」。
 * ⇒ 这里从头到尾都是<b>带贴图的几何体</b>，一颗 {@code ParticleTypes.FLAME} 都不用 ✗。
 *
 * <h2>七层构成（从下往上、从里往外）</h2>
 * <ol>
 *   <li><b>地面火斑</b>：贴地的一圈小火舌 + 底下的余烬光片，让"脚下也在烧"；</li>
 *   <li><b>主火圈</b>：{@code lobes} 簇火，每簇是 <b>3 片 60° 交角 × 3 段</b> 的锥形火舌
 *       （宽度向上收掉 85%、火尖按 t² 往外弯）—— 三片交角 ≠ 两片十字：从任何角度看都是<b>一团</b>，
 *       不会转个身就变成一张纸 ✗；</li>
 *   <li><b>外焰壳</b>：比主火圈更外、更矮、更透的一层，<b>反向自转</b> ⇒ 火焰有前后层次、有厚度；</li>
 *   <li><b>内芯</b>：贴身处几簇更矮更亮的火，视线穿过外焰能看到，"人被包住"；</li>
 *   <li><b>上升火柱</b>：t3 起多出来的一根「火焰绕着人往上烧」的柱子 —— 一圈往上爬的火苗
 *       + 两条对缠的螺旋火带，柱高烧过头顶，越高越细越淡。作者 2026-10-18：
 *       「燃烧链的特效重复度过高，要求 t3 后面的法术有火焰围绕人物上升的效果」；</li>
 *   <li><b>火星</b>：几何体做的小火片往上飘（按 age 取模循环，相位由序号算死 ⇒ 不闪）；</li>
 *   <li><b>两圈冲击环</b>：起手第 0~5 tick 一圈往外撑开的亮环；<b>释放</b>最后 4 tick 再来一圈更大的
 *       + 整团火膨胀 —— "放出去"那一下才有动作。</li>
 * </ol>
 *
 * <h2>档位表（5 档，规模逐档变大；t3 起多一根上升火柱）</h2>
 * <pre>
 *   档  主火簇  圈半径  火苗高  火苗宽  外焰壳  内芯   火柱(苗数/柱高/圈数)  火星
 *   1     5     0.56    0.80    0.34     -      -           -              3
 *   2     7     0.67    0.97    0.39     5      -           -              6
 *   3     9     0.79    1.15    0.43     7      3    10 / 1.85 / 0.85       9
 *   4    11     0.90    1.32    0.47     9      5    14 / 2.37 / 1.70      12
 *   5    13     1.02    1.50    0.52    11      7    18 / 2.90 / 2.55      15
 * </pre>
 *
 * <h2>为什么分两个 {@link RenderType}</h2>
 * <ul>
 *   <li>硬边火苗（主火/内芯/地面火舌）走 {@code entityCutoutNoCull}：贴图是像素画、边缘是硬边，
 *       cutout 保留硬边且没有半透明排序问题；{@code NoCull} 是因为三片交角里总有背朝镜头的，
 *       剔除掉火就少一半 ✗。</li>
 *   <li>需要<b>渐隐</b>的部分（外焰壳、火舌、火星、冲击环）走 {@code entityTranslucent}：
 *       cutout 不混合、顶点 alpha 等于没用 ✗，只有半透明管线才吃 alpha ✓。
 *       亮度一律 {@link LightTexture#FULL_BRIGHT}（火自发光，洞里/夜里都亮）。</li>
 * </ul>
 */
public final class TNBurnAuraRenderer extends EntityRenderer<TNBurnAuraEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/burn_flame.png");

    /** 硬边火苗的顶点 alpha（cutout 不吃 alpha，写 1 免得被 alpha test 剔掉）。 */
    private static final float A = 1.0F;

    /** 释放爆发持续几 tick（从 life 前 4 tick 开始）。 */
    private static final double BURST_TICKS = 4.0D;

    public TNBurnAuraRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TNBurnAuraEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(TNBurnAuraEntity entity, float yaw, float partial, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        Matrix4f pose = stack.last().pose();

        double age = entity.tickCount + partial;
        int tier = Math.max(1, Math.min(5, entity.tier()));
        double life = Math.max(1, entity.life());

        // 释放那一下的爆发进度：<0 表示还没到释放点
        double burst = age > life - BURST_TICKS ? (age - (life - BURST_TICKS)) / BURST_TICKS : -1.0D;
        // 整团火在释放时膨胀一点，收尾 30% 再慢慢缩回去
        double grow = (burst >= 0.0D ? 1.0D + 0.35D * burst : 1.0D)
                * (age > life - 1.0D ? 0.85D + 0.15D * (life - age) : 1.0D);

        int lobes = 3 + tier * 2;                 // 5 / 7 / 9 / 11 / 13
        double ringR = (0.44D + tier * 0.115D) * grow;   // 0.555 .. 1.015（再乘膨胀）
        double baseH = (0.62D + tier * 0.175D) * grow;   // 0.795 .. 1.495
        double baseW = 0.30D + tier * 0.043D;            // 0.343 .. 0.515
        double spin = age * 0.045D;

        // ① 地面火斑（贴地，圈比主火略大一点，看着火是从地上烧起来的）
        groundFire(solid, glow, pose, age, tier, lobes, ringR, baseW);

        // ② 主火圈
        for (int k = 0; k < lobes; k++) {
            double a = spin + k * Math.PI * 2.0D / lobes;
            // 每簇各自呼吸：两个不同频率叠加，免得整圈火整齐地一起跳（像呼吸灯一样假）✗
            double flicker = 0.82D + 0.18D * Math.sin(age * 0.55D + k * 1.9D)
                    + 0.06D * Math.sin(age * 1.31D + k * 0.7D);
            // 高低错落：挨着的两簇别一样高，不然像一圈栅栏 ✗
            double varH = 0.78D + 0.44D * ((k * 7) % 5) / 4.0D;
            double h = baseH * flicker * varH;
            double w = baseW * (0.90D + 0.16D * Math.sin(age * 0.41D + k * 2.3D));
            // 火苗往外歪一点：直上直下像一圈蜡烛 ✗
            double lean = ringR * 0.16D;
            flame(solid, pose, a, ringR, w, h, lean, A);
        }

        // ③ 外焰壳（t2 起）：更外、更矮、更透、反向自转 ⇒ 火焰有厚度
        if (tier >= 2) {
            int shell = tier * 2 + 1;             // 5 / 7 / 9 / 11
            double shellR = ringR * 1.30D;
            for (int k = 0; k < shell; k++) {
                double a = -spin * 0.72D + k * Math.PI * 2.0D / shell + 0.31D;
                double flicker = 0.84D + 0.20D * Math.sin(age * 0.38D + k * 1.3D);
                flame(glow, pose, a, shellR, baseW * 1.18D,
                        baseH * 0.70D * flicker, shellR * 0.14D, 0.42F);
            }
        }

        // ④ 内芯（t3 起）：贴身处更矮更亮的几簇
        if (tier >= 3) {
            int inner = (tier - 1) * 2 - 1;       // 3 / 5 / 7
            double innerR = ringR * 0.40D;
            for (int k = 0; k < inner; k++) {
                double a = -spin * 1.6D + k * Math.PI * 2.0D / inner + 0.6D;
                double flicker = 0.86D + 0.20D * Math.sin(age * 0.67D + k * 2.1D);
                flame(solid, pose, a, innerR, baseW * 0.82D, baseH * 0.62D * flicker, 0.0D, A);
            }
        }

        // ⑤ 上升火柱（t3 起）：火苗绕着身体往上爬，越高越细越淡
        //    作者 2026-10-18：「燃烧链的特效重复度过高，要求 t3 后面的法术有火焰围绕人物上升的效果」✓
        if (tier >= 3) {
            risingColumn(solid, glow, pose, age, tier, baseH, grow);
        }

        // ⑥ 火星（几何体小片，往上飘）
        sparks(glow, pose, age, tier, ringR, baseH);

        // ⑦ 起手爆闪（0~5 tick）+ 释放爆环
        if (age < 5.0D) {
            shockRing(glow, pose, ringR * (0.55D + 1.65D * (age / 5.0D)),
                    (float) (0.92D - 0.72D * (age / 5.0D)), 0.10D);
        }
        if (burst >= 0.0D) {
            shockRing(glow, pose, ringR * (1.05D + 1.45D * burst),
                    (float) (0.95D * (1.0D - burst)), 0.16D);
            // 释放瞬间再从地上顶起一圈短火，把"喷出去"的方向感补上
            int risers = 6 + tier * 2;
            for (int k = 0; k < risers; k++) {
                double a = spin * 1.4D + k * Math.PI * 2.0D / risers;
                flame(glow, pose, a, ringR * (1.02D + 0.42D * burst),
                        baseW * 0.9D, baseH * (0.55D + 0.75D * burst) * (1.0D - 0.4D * burst),
                        ringR * 0.30D, (float) (0.55D * (1.0D - burst)));
            }
        }
    }

    // ------------------------------------------------------------------ ① 地面火斑

    /**
     * 脚下一圈贴地的火：每个方位一层"余烬光片"（半透明，贴地）+ 一小簇矮火。
     *
     * <p>光片用半透明管线、矮火用 cutout —— 两种管线混着贴地，才不会看起来像一张塑料膜 ✗。
     */
    private static void groundFire(VertexConsumer solid, VertexConsumer glow, Matrix4f pose,
                                   double age, int tier, int lobes, double ringR, double baseW) {
        double gr = ringR * 1.06D;
        int n = lobes + 4;
        for (int k = 0; k < n; k++) {
            double a = -age * 0.018D + k * Math.PI * 2.0D / n;
            double pulse = 0.55D + 0.45D * Math.sin(age * 0.9D + k * 2.6D);
            // 余烬光片：贴地的一个小方片，用贴图下半段（橙红），径向铺开
            flatPatch(glow, pose, a, gr * (0.72D + 0.22D * pulse), baseW * 1.5D,
                    baseW * 1.5D, (float) (0.30D + 0.22D * pulse));
            // 矮火：从地面舔起来的一小撮
            double fh = 0.20D + 0.10D * pulse + tier * 0.012D;
            flame(solid, pose, a, gr, baseW * 0.62D, fh, gr * 0.10D, A);
        }
    }

    /** 贴地的一个小方片（水平），中心在 (cos a, 0.02, sin a) * r。 */
    private static void flatPatch(VertexConsumer out, Matrix4f pose, double a, double r,
                                  double wRad, double wTan, float alpha) {
        double cx = Math.cos(a) * r;
        double cz = Math.sin(a) * r;
        double rx = Math.cos(a), rz = Math.sin(a);
        double tx = -rz, tz = rx;
        double y = 0.02D;
        quadUV(out, pose,
                cx - rx * wRad - tx * wTan, y, cz - rz * wRad - tz * wTan,
                cx + rx * wRad - tx * wTan, y, cz + rz * wRad - tz * wTan,
                cx + rx * wRad + tx * wTan, y, cz + rz * wRad + tz * wTan,
                cx - rx * wRad + tx * wTan, y, cz - rz * wRad + tz * wTan,
                0.05F, 0.92F, 0.95F, 0.70F, 1.0F, 1.0F, 1.0F, alpha);
    }

    // ------------------------------------------------------------------ ② 火苗本体

    /**
     * 一簇火 = <b>3 片 60° 交角 × 3 段</b> 的锥形火舌。
     *
     * <p>每段：宽度按 {@code 1 - 0.85 * t^0.7} 收细、火尖按 {@code lean * t²} 往外弯，
     * 于是整簇火是"宽底 → 收尖 → 尖端外翘"的形状，不是一块竖着的方片 ✗。
     *
     * @param a     在圈上的方位角
     * @param ringR 距身体中轴的距离
     * @param lean  火尖往外偏移的距离（0 = 笔直向上）
     */
    private static void flame(VertexConsumer out, Matrix4f pose, double a, double ringR,
                              double w, double h, double lean, float alpha) {
        flameAt(out, pose, a, ringR, w, h, lean, 0.0D, 1.0F, 1.0F, 1.0F, alpha);
    }

    /**
     * 同 {@link #flame}，但可以<b>从 y 高度起始</b>（火柱里往上爬的火苗）并<b>带一个色偏</b>
     * （越高档越白热 ⇒ 五档一眼能分出强弱，不是同一团火换个大小 ✗）。
     *
     * @param yBase 这簇火的根部高度（0 = 地面）
     * @param tr    颜色乘数（贴图本身是白芯橙边，乘上去就偏色）
     */
    private static void flameAt(VertexConsumer out, Matrix4f pose, double a, double ringR,
                                double w, double h, double lean, double yBase,
                                float tr, float tg, float tb, float alpha) {
        double cx = Math.cos(a) * ringR;
        double cz = Math.sin(a) * ringR;
        double rx = Math.cos(a);
        double rz = Math.sin(a);
        double tx = -rz;
        double tz = rx;

        for (int plane = 0; plane < 3; plane++) {
            double pa = plane * Math.PI / 3.0D;
            double ux = tx * Math.cos(pa) + rx * Math.sin(pa);
            double uz = tz * Math.cos(pa) + rz * Math.sin(pa);
            for (int seg = 0; seg < 3; seg++) {
                double t0 = seg / 3.0D;
                double t1 = (seg + 1) / 3.0D;
                double hw0 = w * 0.5D * taper(t0);
                double hw1 = w * 0.5D * taper(t1);
                double y0 = yBase + h * t0;
                double y1 = yBase + h * t1;
                double o0 = lean * t0 * t0;
                double o1 = lean * t1 * t1;
                // v 从 1（火根）到 0（火尖），按段取对应的一小截贴图
                float v0 = (float) (1.0D - 0.98D * t0);
                float v1 = (float) (1.0D - 0.98D * t1);
                quadUV(out, pose,
                        cx + ux * -hw0 + rx * o0, y0, cz + uz * -hw0 + rz * o0,
                        cx + ux * hw0 + rx * o0, y0, cz + uz * hw0 + rz * o0,
                        cx + ux * hw1 + rx * o1, y1, cz + uz * hw1 + rz * o1,
                        cx + ux * -hw1 + rx * o1, y1, cz + uz * -hw1 + rz * o1,
                        0.04F, v0, 0.96F, v1, tr, tg, tb, alpha);
            }
        }
    }

    /** 宽度收细曲线：根部 1.0、尖端 0.15（留一点尖，别收成零宽——零宽会变成看不见的细线）。 */
    private static double taper(double t) {
        return 1.0D - 0.85D * Math.pow(t, 0.7D);
    }

    // ------------------------------------------------------------------ ⑤ 上升火柱

    /**
     * t3 起的「火焰绕着人往上烧」：<b>一圈往上爬的火苗</b> + <b>两条对缠的螺旋火带</b>。
     *
     * <p>作者 2026-10-18：「燃烧链的特效重复度过高，要求 t3 后面的法术有火焰围绕人物上升的效果」。
     * ⇒ 五档不再只是"同一团火放大"，t3 起多出这根<b>会往上走的火柱</b>：
     * <ul>
     *   <li>柱高 {@code 1.85 / 2.37 / 2.90} 格 —— 玩家 1.8 格，t4/t5 的火会烧过头顶；</li>
     *   <li>火苗数 {@code 10 / 14 / 18}，每根按自己的相位从脚边爬到柱顶再"被空气吃掉"（越高越细越淡）；</li>
     *   <li>整根柱子缓慢自转、同时越往上越往内收，绕的圈数 {@code 0.85 / 1.70 / 2.55} 圈；</li>
     *   <li>配色往白热推（t3 橙 → t5 白亮）⇒ 一眼看出档次。</li>
     * </ul>
     */
    private static void risingColumn(VertexConsumer solid, VertexConsumer glow, Matrix4f pose,
                                     double age, int tier, double baseH, double grow) {
        int strips = 10 + (tier - 3) * 4;                       // 10 / 14 / 18
        double colH = (baseH * 1.55D + (tier - 3) * 0.55D) * grow;   // ≈1.85 / 2.37 / 2.90
        double colR = (0.42D + tier * 0.055D) * grow;           // ≈0.59 / 0.64 / 0.70
        double turns = 0.85D + (tier - 3) * 0.85D;              // 0.85 / 1.70 / 2.55 圈
        // 越高档越白热：t5 是"白亮"，t3 还是橙红
        float tr = 1.0F;
        float tg = tier >= 5 ? 0.96F : (tier >= 4 ? 0.86F : 0.78F);
        float tb = tier >= 5 ? 0.80F : (tier >= 4 ? 0.52F : 0.34F);

        // ① 往上爬的火苗
        for (int i = 0; i < strips; i++) {
            // 每根火苗自己的爬升进度：0 = 脚边、1 = 柱顶，越过柱顶就重来（所以一直在循环上升）
            double phase = (age * 0.030D + i / (double) strips) % 1.0D;
            double ang = age * 0.055D + phase * turns * Math.PI * 2.0D + i * 0.79D;
            double wobble = 0.86D + 0.14D * Math.sin(age * 0.62D + i * 2.1D);
            double w = (0.19D + 0.09D * (1.0D - phase)) * wobble;
            double h = (0.40D + 0.34D * (1.0D - phase)) * wobble;
            double r = colR * (0.92D + 0.14D * Math.sin(phase * 3.4D + i * 1.1D));
            // 越往上越淡：火苗是被空气吃掉的，不是被截断的 ✗
            float alpha = (float) (0.95D * Math.min(1.0D, (1.0D - phase) * 2.6D));
            flameAt(solid, pose, ang, r, w, h, colR * 0.22D, phase * colH, tr, tg, tb, alpha);
        }

        // ② 对缠的螺旋火带：单根火苗只是"往上飘"，加两条带子才看得出来在"绕"
        int strands = tier >= 5 ? 3 : 2;
        int segs = 18;
        for (int s = 0; s < strands; s++) {
            double base = age * 0.055D + s * Math.PI * 2.0D / strands;
            for (int k = 0; k < segs; k++) {
                double t0 = k / (double) segs;
                double t1 = (k + 1) / (double) segs;
                double a0 = base + t0 * turns * Math.PI * 2.0D;
                double a1 = base + t1 * turns * Math.PI * 2.0D;
                double y0 = t0 * colH;
                double y1 = t1 * colH;
                double hw0 = 0.085D * (1.0D - 0.78D * t0);
                double hw1 = 0.085D * (1.0D - 0.78D * t1);
                double ux0 = -Math.sin(a0), uz0 = Math.cos(a0);
                double ux1 = -Math.sin(a1), uz1 = Math.cos(a1);
                double r0 = colR * (1.00D + 0.10D * t0);
                double r1 = colR * (1.00D + 0.10D * t1);
                // 顶端渐隐：火舌往上是被"烧掉"的
                float aA = (float) (0.80D * (1.0D - 0.92D * t0));
                float aB = (float) (0.80D * (1.0D - 0.92D * t1));
                quadGrad(glow, pose,
                        Math.cos(a0) * r0 + ux0 * -hw0, y0, Math.sin(a0) * r0 + uz0 * -hw0,
                        Math.cos(a0) * r0 + ux0 * hw0, y0, Math.sin(a0) * r0 + uz0 * hw0,
                        Math.cos(a1) * r1 + ux1 * hw1, y1, Math.sin(a1) * r1 + uz1 * hw1,
                        Math.cos(a1) * r1 + ux1 * -hw1, y1, Math.sin(a1) * r1 + uz1 * -hw1,
                        0.30F, 0.88F, 0.72F, 0.30F, aA, aB);
            }
        }
    }

    // ------------------------------------------------------------------ ⑥ 火星

    /** 往上飘的小火片：相位由序号算死（不用随机），所以每一颗都稳定地循环上升。 */
    private static void sparks(VertexConsumer out, Matrix4f pose, double age, int tier,
                               double ringR, double baseH) {
        int n = tier * 3;
        for (int i = 0; i < n; i++) {
            double phase = (age * 0.49D + i * 0.37D) % 1.0D;
            double y = baseH * (0.25D + 1.55D * phase);
            double a = i * 2.399D + age * 0.06D;
            double r = ringR * (0.62D + 0.30D * Math.sin(age * 0.2D + i * 1.7D));
            double hw = 0.026D * (1.0D - 0.5D * phase);
            float alpha = (float) (0.95D * (1.0D - phase * 0.85D));
            // 两片小十字，免得转到侧面就没了
            double ux = -Math.sin(a), uz = Math.cos(a);
            double cx = Math.cos(a) * r, cz = Math.sin(a) * r;
            quadUV(out, pose,
                    cx + ux * -hw, y, cz + uz * -hw,
                    cx + ux * hw, y, cz + uz * hw,
                    cx + ux * hw, y + hw * 4.2D, cz + uz * hw,
                    cx + ux * -hw, y + hw * 4.2D, cz + uz * -hw,
                    0.42F, 0.86F, 0.58F, 0.40F, 1.0F, 0.92F, 0.62F, alpha);
            quadUV(out, pose,
                    cx + Math.cos(a) * -hw, y, cz + Math.sin(a) * -hw,
                    cx + Math.cos(a) * hw, y, cz + Math.sin(a) * hw,
                    cx + Math.cos(a) * hw, y + hw * 4.2D, cz + Math.sin(a) * hw,
                    cx + Math.cos(a) * -hw, y + hw * 4.2D, cz + Math.sin(a) * -hw,
                    0.42F, 0.86F, 0.58F, 0.40F, 1.0F, 0.92F, 0.62F, alpha);
        }
    }

    // ------------------------------------------------------------------ ⑦ 冲击环

    /** 贴地往外的亮环：一段段四边形拼成的一圈，半径越大越淡。 */
    private static void shockRing(VertexConsumer out, Matrix4f pose, double radius,
                                  float alpha, double width) {
        if (alpha <= 0.02F || radius <= 0.05D) {
            return;
        }
        int segs = 28;
        for (int i = 0; i < segs; i++) {
            double a0 = i * Math.PI * 2.0D / segs;
            double a1 = (i + 1) * Math.PI * 2.0D / segs;
            double y = 0.035D;
            quadUV(out, pose,
                    Math.cos(a0) * radius, y, Math.sin(a0) * radius,
                    Math.cos(a1) * radius, y, Math.sin(a1) * radius,
                    Math.cos(a1) * (radius + width), y, Math.sin(a1) * (radius + width),
                    Math.cos(a0) * (radius + width), y, Math.sin(a0) * (radius + width),
                    0.10F, 0.95F, 0.90F, 0.80F, 1.0F, 0.76F, 0.34F, alpha);
        }
    }

    // ------------------------------------------------------------------ 顶点工具

    /** 一个四边形：四角按顺序给，UV 取整块贴图，颜色/透明度统一。 */
    private static void quadUV(VertexConsumer out, Matrix4f pose,
                               double x0, double y0, double z0,
                               double x1, double y1, double z1,
                               double x2, double y2, double z2,
                               double x3, double y3, double z3,
                               float u0, float v0, float u1, float v1,
                               float r, float g, float b, float a) {
        vert(out, pose, x0, y0, z0, u0, v0, r, g, b, a);
        vert(out, pose, x1, y1, z1, u1, v0, r, g, b, a);
        vert(out, pose, x2, y2, z2, u1, v1, r, g, b, a);
        vert(out, pose, x3, y3, z3, u0, v1, r, g, b, a);
    }

    /** 一个四边形，底部/顶部各自 alpha（火舌往上渐隐用）。 */
    private static void quadGrad(VertexConsumer out, Matrix4f pose,
                                 double x0, double y0, double z0,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 double x3, double y3, double z3,
                                 float u0, float v0, float u1, float v1,
                                 float aBottom, float aTop) {
        vert(out, pose, x0, y0, z0, u0, v0, 1.0F, 1.0F, 1.0F, aBottom);
        vert(out, pose, x1, y1, z1, u1, v0, 1.0F, 1.0F, 1.0F, aBottom);
        vert(out, pose, x2, y2, z2, u1, v1, 1.0F, 1.0F, 1.0F, aTop);
        vert(out, pose, x3, y3, z3, u0, v1, 1.0F, 1.0F, 1.0F, aTop);
    }

    private static void vert(VertexConsumer out, Matrix4f pose,
                             double x, double y, double z, float u, float v,
                             float r, float g, float b, float a) {
        out.vertex(pose, (float) x, (float) y, (float) z)
                .color(r, g, b, a).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F).endVertex();
    }
}
