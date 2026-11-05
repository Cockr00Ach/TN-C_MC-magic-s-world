package com.tnc.tnc.client.dragon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.client.lightning.LightningTheme;
import com.tnc.tnc.magic.LightningDragonPath;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * <b>雷龙的模型</b> —— 全部由方块拼出来（"尽管堆砌模型块数"），再用电弧把轮廓勾一遍。
 *
 * <h2>这条龙为什么"没有身体"</h2>
 * 躯干故意<b>不画皮</b>：只有
 * <ol>
 *   <li>一条闪电脊线（每两节脊椎之间一道锯齿电弧）；</li>
 *   <li>一节节悬浮的脊椎骨块（22 节，越往尾越细）；</li>
 *   <li>左右两侧的肋骨弧（从脊椎甩出去又收回来）；</li>
 *   <li>沿着脊背的鬃刺。</li>
 * </ol>
 * 肋骨之间是完全空的 ⇒ 从侧面能一眼看穿到对面 ✓，远看就是"一团电勾出来的龙" ✓。
 *
 * <h2>头的细节（都独立可动）</h2>
 * 头骨 / 颅顶 / 眉骨 / 颊甲（3 对）/ 吻 / 鼻 / 上唇 / 下颚（可张合）/ 上下牙（各 6~7 对）/
 * 眼（带光晕）/ 三对龙角（每对 7 节、会颤）/ 两根长龙须（各 18 节、按正弦飘）/ 耳鳍（3 对）。
 *
 * <h2>姿态</h2>
 * 龙的每一节位置直接由 {@link LightningDragonPath} 给出（世界坐标，相对落点）；
 * 每节再按"指向龙头"的方向与"螺旋外法线"算一个正交基，于是方块会顺着曲线转 ✓。
 * 头的朝向 = 水平飞行方向（不是正上方 ✗ —— 龙是昂着头往上窜的，不是火箭）。
 *
 * <p>全部顶点都是即时生成的（约 2000 个四边形/帧），不读模型文件、不依赖 GeckoLib ✓。
 */
public final class LightningDragonGeometry {

    /** 龙身节数（每节都会画脊椎骨块，越大越"堆料" ✓）。 */
    private static final int SEGMENTS = 22;

    /** 一个正交基（原点 ＋ 三个单位轴），所有部件都靠它在世界里摆姿势 ✓。 */
    private record Frame(Vector3f o, Vector3f x, Vector3f y, Vector3f z) {
    }

    private LightningDragonGeometry() {
    }

    /**
     * 画整条龙。
     *
     * @param shapeSeed 形状种子（同步过的 ✓，每 2 tick 换一次让电弧"爬动"）
     * @param age       破土之后过了多少 tick（可以是小数 = 插值 ✓）
     * @param scale     体型倍数（1.0 = 约 45 格长）
     * @param camX/camY/camZ 相机在<b>实体原点坐标系</b>里的位置
     * @return 龙头在世界（实体原点坐标系）里的位置 —— 渲染器拿去画"云底光斑"用
     */
    public static Vector3f dragon(VertexConsumer vc, Matrix4f m,
                                  long shapeSeed, float age, double scale, int theme, float alpha,
                                  double camX, double camY, double camZ) {
        // ---- 1. 脊线：按**高度**取点（每节比龙头低 1.95 格）⇒ 龙长恒定 ≈ 43 格 ✓；
        //         高度不够的节 clamp 到 0 ⇒ 还埋在地里，正是"破土而出"的那几 tick ✓ ----
        Vector3f[] spine = new Vector3f[SEGMENTS + 1];
        double headY = LightningDragonPath.rise(age, scale);
        double step = LightningDragonPath.SEGMENT_SPACING * Math.max(0.4D, scale);
        for (int i = 0; i <= SEGMENTS; i++) {
            double y = Math.max(0.0D, headY - i * step);
            double[] p = LightningDragonPath.pointAtHeight(y, scale);
            spine[i] = new Vector3f((float) p[0], (float) p[1], (float) p[2]);
        }
        return body(vc, m, spine, shapeSeed, age, scale, theme, alpha, camX, camY, camZ);
    }

    /**
     * ★ <b>盘在施法者身上的龙</b>（作者 2026-11-05："登神链我希望施法之后持续时间内会有
     * 我们的雷龙缠绕着身上一直游走"）。
     *
     * <p>和 {@link #dragon} 用<b>同一套骨头</b>，只是脊线换成一条<b>螺旋</b>：
     * 头在最上面、尾在最下面，绕身体转若干圈；{@code spin} 随时间增加 ⇒
     * 整条龙绕人慢慢游动 ✓（"游走"就靠它）。
     *
     * <p>★★ 2026-11-05 第二批反馈（作者："太大只了，没有绕在身上的感觉"）：
     * 原来半径 1.57 格、绕 2 圈，看着像<b>一条大蛇在外面兜圈</b>✗。
     * 现在半径收到 ~1.1 格、绕 <b>3.5 圈</b>（圈数由<b>脊线长度</b>反算，见下）⇒
     * 贴着身体一圈圈缠上去 ✓。
     *
     * <p><b>圈数怎么来的</b>：龙身 22 节、每节 {@code SEGMENT_SPACING × 体型} 格，
     * 所以脊线总长 {@code L = SEGMENT_SPACING × 体型 × SEGMENTS} 是<b>定死的</b>。
     * 螺旋长度 ≈ √((2πr·turns)² + height²) ⇒ 反解
     * {@code turns = √(L² − height²) / (2πr)} —— 这样任何体型都不会把骨头挤扁/拉长 ✓。
     *
     * @param radius 螺旋半径（格；≈ 1.0 刚好贴着人转、又不穿模）
     * @param height 从头到尾的竖直跨度（格；≈ 2.4~3.1，头顶略高于人）
     * @param spin   螺旋整体相位（渲染器传 {@code age * 0.11} ⇒ 一直在游 ✓）
     */
    public static Vector3f coil(VertexConsumer vc, Matrix4f m, long shapeSeed, float age, double scale,
                                int theme, float alpha, double camX, double camY, double camZ,
                                double radius, double height, double spin) {
        double S = Math.max(0.4D, scale);
        // 脊线总长（和 dragon() 里取点用的步长完全一致 ✓）
        double length = LightningDragonPath.SEGMENT_SPACING * S * SEGMENTS;
        double around = Math.sqrt(Math.max(1.0D, length * length - height * height));
        double turns = around / (Math.PI * 2.0D * Math.max(0.25D, radius));

        Vector3f[] spine = new Vector3f[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            double t = i / (double) SEGMENTS;                  // 0 = 头，1 = 尾
            double ang = spin - t * Math.PI * 2.0D * turns;
            // 尾巴收一点（盘成锥形，看着像"缠紧"✓），同时随时间轻轻鼓一下（呼吸感 ✓）
            double r = radius * (1.0D - 0.22D * t) * (1.0D + 0.06D * Math.sin(age * 0.09D + t * 4.0D));
            double y = height * (1.0D - t) + 0.25D + 0.14D * Math.sin(age * 0.11D + t * 6.0D);
            spine[i] = new Vector3f((float) (Math.cos(ang) * r), (float) y, (float) (Math.sin(ang) * r));
        }
        return body(vc, m, spine, shapeSeed, age, scale, theme, alpha, camX, camY, camZ);
    }

    /** 拿到脊线之后的一切（骨节 / 电枢 / 肋骨 / 鬃刺 / 头 / 四爪 / 尾鳍）✓ 两种脊线共用。 */
    private static Vector3f body(VertexConsumer vc, Matrix4f m, Vector3f[] spine, long shapeSeed, float age,
                                 double scale, int theme, float alpha,
                                 double camX, double camY, double camZ) {
        float S = (float) Math.max(0.4D, scale);
        float[] mid = LightningTheme.mid(theme);
        float[] edge = LightningTheme.edge(theme);
        float[] white = LightningTheme.WHITE;


        // ---- 2. 每节的正交基：y = 指向龙头，x = 骨节右侧，z = 背面 ----
        Frame[] frames = new Frame[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            Vector3f up = (i == 0 ? new Vector3f(spine[0]).sub(spine[1])
                    : new Vector3f(spine[i - 1]).sub(spine[Math.min(SEGMENTS, i + 1)]));
            if (up.lengthSquared() < 1.0E-6F) {
                up.set(0.0F, 1.0F, 0.0F);
            }
            up.normalize();
            Vector3f radial = new Vector3f(spine[i].x, 0.0F, spine[i].z);
            if (radial.lengthSquared() < 1.0E-4F) {
                radial.set(1.0F, 0.0F, 0.0F);
            }
            radial.normalize();
            Vector3f side = new Vector3f(up).cross(radial, new Vector3f());
            if (side.lengthSquared() < 1.0E-4F) {
                side.set(1.0F, 0.0F, 0.0F);
            }
            side.normalize();
            // 背面 = 螺旋外侧（蛇盘起来时背朝外 ✓）
            Vector3f back = new Vector3f(side).cross(up, new Vector3f());
            if (back.lengthSquared() < 1.0E-5F) {
                // 退化（脊线正好沿 X 轴）时给一个确定的背面 —— 不能 normalize 出 NaN ✗，
                // NaN 顶点会污染整段 buffer、整条龙直接消失 ✗（显卡侧不会报错，只会什么都不画）
                back.set(0.0F, 0.0F, 1.0F);
            }
            back.normalize();
            frames[i] = new Frame(spine[i], side, up, back);
        }

        // ---- 3. 脊椎骨块（"没有身体"，但只要骨节）----
        for (int i = 0; i <= SEGMENTS; i++) {
            float k = 1.0F - i / (float) (SEGMENTS + 3);        // 越往尾越细
            float wobble = 0.16F * Mth.sin(age * 0.22F + i * 0.6F);
            box(vc, m, frames[i], 0.0D, 0.0D, wobble,
                    0.62F * k * S, 0.42F * k * S, 0.62F * k * S,
                    mid, alpha * 0.40F);
            // 骨节上的"电枢"：一小块纯白，让骨节有亮点 ✓
            box(vc, m, frames[i], 0.0D, 0.0D, wobble,
                    0.26F * k * S, 0.18F * k * S, 0.26F * k * S,
                    white, alpha * 0.55F);
        }

        // ---- 4. 闪电脊线：相邻两节之间一道锯齿电弧 ----
        for (int i = 0; i < SEGMENTS; i++) {
            LightningBoltGeometry.arc(vc, m,
                    spine[i].x, spine[i].y, spine[i].z,
                    spine[i + 1].x, spine[i + 1].y, spine[i + 1].z,
                    shapeSeed + i * 71L, 0.13F * S, alpha * 0.85F, theme, camX, camY, camZ);
        }
        // 两侧各一条"游离"的长电弧，让轮廓更毛 ✓
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < SEGMENTS; i += 2) {
                Vector3f a = at(frames[i], s * 1.25D * S, 0.0D, 0.0D);
                Vector3f b = at(frames[Math.min(SEGMENTS, i + 2)], s * 1.25D * S, 0.0D, 0.0D);
                LightningBoltGeometry.arc(vc, m, a.x, a.y, a.z, b.x, b.y, b.z,
                        shapeSeed + 900L + i * 13L + s, 0.05F * S, alpha * 0.38F, theme,
                        camX, camY, camZ);
            }
        }

        // ---- 5. 肋骨：从脊椎甩出去、再收回来的电弧（左右各一遍）----
        for (int i = 2; i <= SEGMENTS - 2; i += 2) {
            float k = 1.0F - i / (float) (SEGMENTS + 2);
            for (int s = -1; s <= 1; s += 2) {
                double out = (2.6D + 1.2D * Mth.sin(age * 0.12F + i * 0.7F)) * k * S;
                Vector3f start = at(frames[i], s * 0.5D * S, 0.0D, 0.0D);
                Vector3f midPt = at(frames[i], s * out, -0.55D * S, 1.15D * S);
                Vector3f end = at(frames[i], s * 0.35D * S, -1.05D * S, 1.55D * S);
                LightningBoltGeometry.arc(vc, m, start.x, start.y, start.z,
                        midPt.x, midPt.y, midPt.z, shapeSeed + i * 31L + s, 0.075F * S,
                        alpha * 0.62F, theme, camX, camY, camZ);
                LightningBoltGeometry.arc(vc, m, midPt.x, midPt.y, midPt.z,
                        end.x, end.y, end.z, shapeSeed + i * 37L + s, 0.055F * S,
                        alpha * 0.45F, theme, camX, camY, camZ);
                // 肋骨末端的小骨节：让"收回来"有个落点 ✓
                box(vc, m, frames[i], s * 0.35D * S, -1.05D * S, 1.55D * S,
                        0.14F * k * S, 0.14F * k * S, 0.14F * k * S, edge, alpha * 0.55F);
            }
        }

        // ---- 6. 鬃刺（背脊上一排，随风摆）----
        for (int i = 1; i <= 10; i++) {
            float k = 1.0F - i * 0.06F;
            double sway = 0.25D * Mth.sin(age * 0.18F + i * 0.9F);
            Frame f = frames[i];
            Vector3f base = at(f, 0.0D, 0.0D, 0.55D * S);
            Vector3f dir = new Vector3f(f.z).mul(0.85F).add(new Vector3f(f.y).mul(0.45F))
                    .add(new Vector3f(f.x).mul((float) sway)).normalize();
            float len = (1.9F - i * 0.09F) * S;
            Frame spike = frameWithY(base, dir);
            box(vc, m, spike, 0.0D, len * 0.45F, 0.0D,
                    0.10F * k * S, len * 0.5F, 0.30F * k * S, edge, alpha * 0.50F);
            // 刺尖再拉一道电
            LightningBoltGeometry.arc(vc, m, base.x, base.y, base.z,
                    base.x + dir.x * len, base.y + dir.y * len, base.z + dir.z * len,
                    shapeSeed + 400L + i, 0.035F * S, alpha * 0.55F, theme, camX, camY, camZ);
        }

        // ---- 7. 头 ----
        Frame head = headFrame(age, spine, frames, S);
        drawHead(vc, m, head, age, S, theme, alpha, shapeSeed, camX, camY, camZ, mid, edge, white);

        // ---- 8. 四爪（会随飞行摆动）----
        int[] legAt = {4, 7, SEGMENTS - 7, SEGMENTS - 4};
        for (int li = 0; li < legAt.length; li++) {
            int side = li % 2 == 0 ? -1 : 1;
            drawLeg(vc, m, frames[legAt[li]], side, li, age, S, alpha, mid, white);
        }

        // ---- 9. 尾鳍（最后 3 节上张开的扇骨）----
        Vector3f tail = spine[SEGMENTS];
        Frame tf = frames[SEGMENTS];
        for (int i = -2; i <= 2; i++) {
            Frame fin = frameWithY(at(tf, 0.0D, -0.4D * S, 0.2D * S),
                    new Vector3f(tf.y).mul(-0.55F).add(new Vector3f(tf.x).mul(i * 0.42F))
                            .add(new Vector3f(tf.z).mul(0.55F)).normalize());
            float len = (2.2F - Math.abs(i) * 0.28F) * S;
            box(vc, m, fin, 0.0D, len * 0.5F, 0.0D,
                    0.09F * S, len * 0.5F, 0.42F * S, edge, alpha * 0.42F);
        }
        LightningBoltGeometry.arc(vc, m, tail.x, tail.y, tail.z,
                tail.x + tf.y.x * -2.5F * S, tail.y + tf.y.y * -2.5F * S, tail.z + tf.y.z * -2.5F * S,
                shapeSeed + 777L, 0.09F * S, alpha * 0.65F, theme, camX, camY, camZ);

        return spine[0];
    }

    // ------------------------------------------------------------------
    //  头（最堆料的部分）
    // ------------------------------------------------------------------

    /** 头的正交基：y 顺着脖子、z 是"水平前进方向"（龙是昂着头往前窜的 ✓）。 */
    private static Frame headFrame(float age, Vector3f[] spine, Frame[] frames, float S) {
        Vector3f up = frames[0].y;
        Vector3f fwd;
        if (spine.length > 2) {
            fwd = new Vector3f(spine[0]).sub(spine[2]);
        } else {
            fwd = new Vector3f(0.0F, 0.4F, 0.9F);
        }
        fwd.y = 0.0F;                                  // 先取水平分量
        if (fwd.lengthSquared() < 1.0E-4F) {
            fwd.set(0.0F, 0.0F, 1.0F);
        }
        fwd.normalize();
        Vector3f right = new Vector3f(up).cross(fwd, new Vector3f());
        if (right.lengthSquared() < 1.0E-4F) {
            right.set(1.0F, 0.0F, 0.0F);
        }
        right.normalize();
        fwd = new Vector3f(right).cross(up, new Vector3f()).normalize();

        // 头部的摆动：左右摆 yaw + 抬头/低头 pitch（两条正弦不同频 ⇒ 不像节拍器 ✓）
        double yaw = Math.toRadians(11.0D * Mth.sin(age * 0.11F) + 4.0D * Mth.sin(age * 0.31F));
        double pitch = Math.toRadians(-8.0D + 7.0D * Mth.sin(age * 0.077F + 1.1F));
        Vector3f up2 = rotate(up, right, pitch);
        Vector3f fwd2 = rotate(fwd, right, pitch);
        Vector3f right2 = rotate(right, up2, yaw);
        fwd2 = rotate(fwd2, up2, yaw);
        return new Frame(spine[0], right2, up2, fwd2);
    }

    private static void drawHead(VertexConsumer vc, Matrix4f m, Frame h, float age, float S,
                                 int theme, float alpha, long seed,
                                 double camX, double camY, double camZ,
                                 float[] mid, float[] edge, float[] white) {
        // 头骨 / 颅顶 / 眉骨 / 颊甲
        box(vc, m, h, 0.0D, 0.10D * S, 0.35D * S, 1.02F * S, 0.82F * S, 1.32F * S, mid, alpha * 0.50F);
        box(vc, m, h, 0.0D, 0.74D * S, 0.10D * S, 0.92F * S, 0.30F * S, 1.05F * S, edge, alpha * 0.55F);
        box(vc, m, h, 0.0D, 0.54D * S, 1.02D * S, 1.12F * S, 0.22F * S, 0.46F * S, white, alpha * 0.62F);
        for (int s = -1; s <= 1; s += 2) {
            box(vc, m, h, s * 1.02D * S, 0.02D * S, 0.52D * S,
                    0.26F * S, 0.55F * S, 0.88F * S, edge, alpha * 0.46F);
        }
        // 吻 / 鼻 / 上唇
        box(vc, m, h, 0.0D, 0.02D * S, 1.72D * S, 0.76F * S, 0.50F * S, 0.95F * S, mid, alpha * 0.55F);
        box(vc, m, h, 0.0D, 0.14D * S, 2.52D * S, 0.58F * S, 0.36F * S, 0.30F * S, edge, alpha * 0.60F);
        box(vc, m, h, 0.0D, -0.36D * S, 1.92D * S, 0.70F * S, 0.15F * S, 0.86F * S, edge, alpha * 0.50F);
        for (int s = -1; s <= 1; s += 2) {
            box(vc, m, h, s * 0.28D * S, 0.28D * S, 2.68D * S,
                    0.14F * S, 0.11F * S, 0.11F * S, white, alpha * 0.85F);
        }
        // 上牙：沿吻两侧各 7 颗
        for (int s = -1; s <= 1; s += 2) {
            for (int k = 0; k < 7; k++) {
                double z = (1.35D + k * 0.19D) * S;
                box(vc, m, h, s * (0.62D - k * 0.02D) * S, -0.47D * S, z,
                        0.075F * S, 0.17F * S, 0.055F * S, white, alpha * 0.88F);
            }
        }
        // 下颚（绕铰链张合）
        double jaw = Math.toRadians(9.0D + 20.0D * Math.max(0.0D, Mth.sin(age * 0.085F + 0.4F)));
        Frame pivot = new Frame(at(h, 0.0D, -0.55D * S, 0.30D * S), h.x, h.y, h.z);
        Frame j = rotateFrame(pivot, h.x, jaw);
        box(vc, m, j, 0.0D, -0.42D * S, 1.18D * S, 0.78F * S, 0.28F * S, 1.25F * S, mid, alpha * 0.55F);
        box(vc, m, j, 0.0D, -0.66D * S, 0.78D * S, 0.52F * S, 0.22F * S, 0.62F * S, edge, alpha * 0.45F);
        for (int s = -1; s <= 1; s += 2) {
            for (int k = 0; k < 6; k++) {
                double z = (1.25D + k * 0.18D) * S;
                box(vc, m, j, s * (0.56D - k * 0.02D) * S, -0.18D * S, z,
                        0.065F * S, 0.15F * S, 0.05F * S, white, alpha * 0.85F);
            }
            // 颚下的两根短须
            box(vc, m, j, s * 0.66D * S, -0.62D * S, 1.55D * S,
                    0.09F * S, 0.09F * S, 0.52F * S, edge, alpha * 0.40F);
        }

        // 眼：白核 ＋ 外圈光晕（远看两点亮，近看有神 ✓）
        for (int s = -1; s <= 1; s += 2) {
            box(vc, m, h, s * 0.90D * S, 0.42D * S, 1.02D * S,
                    0.34F * S, 0.28F * S, 0.42F * S, mid, alpha * 0.30F);
            box(vc, m, h, s * 0.94D * S, 0.42D * S, 1.06D * S,
                    0.22F * S, 0.17F * S, 0.28F * S, white, alpha * 0.95F);
        }

        // 三对龙角：每对 7 节，越往外越细，还会颤 ✓
        for (int s = -1; s <= 1; s += 2) {
            for (int horn = 0; horn < 3; horn++) {
                Vector3f p = at(h, s * (0.70D + horn * 0.18D) * S, (0.72D - horn * 0.08D) * S,
                        (-0.05D - horn * 0.30D) * S);
                Vector3f dir = new Vector3f(s * (0.62F - horn * 0.10F), 0.78F - horn * 0.12F,
                        -0.55F - horn * 0.10F).normalize();
                Vector3f prev = new Vector3f(p);
                for (int k = 0; k < 7; k++) {
                    double bend = 0.11D * Mth.sin(age * 0.19F + horn * 1.7F + k * 0.55F + s);
                    dir = rotate(dir, new Vector3f(0.0F, 0.0F, 1.0F), bend);
                    dir = rotate(dir, h.y, 0.05D * s);
                    dir.normalize();
                    float step = (0.62F - k * 0.045F) * S;
                    Vector3f next = new Vector3f(p).add(new Vector3f(dir).mul(step));
                    Frame hf = frameWithY(new Vector3f(p).add(next).mul(0.5F), dir);
                    float thick = (0.24F - k * 0.028F) * S;
                    box(vc, m, hf, 0.0D, 0.0D, 0.0D, thick, step * 0.6F, thick,
                            k < 3 ? white : edge, alpha * (0.62F - k * 0.05F));
                    p = next;
                }
                // 角尖拖一道电弧（"雷电勾线"✓）
                LightningBoltGeometry.arc(vc, m, prev.x, prev.y, prev.z, p.x, p.y, p.z,
                        seed + 1200L + horn * 17L + s * 3L, 0.04F * S, alpha * 0.60F, theme,
                        camX, camY, camZ);
            }
        }

        // 两根长龙须：18 节，按两条不同频率的正弦飘 ✓（"什么龙须都得会动"）
        for (int s = -1; s <= 1; s += 2) {
            Vector3f p = at(h, s * 0.60D * S, -0.06D * S, 2.30D * S);
            Vector3f dir = new Vector3f(s * 0.60F, 0.10F, 0.86F).normalize();
            Vector3f prev = new Vector3f(p);
            for (int k = 0; k < 18; k++) {
                double sway = 0.10D * Math.sin(age * 0.16D + k * 0.52D + s * 0.8D)
                        + 0.05D * Math.sin(age * 0.37D + k * 0.21D);
                dir = rotate(dir, h.y, sway);
                dir = rotate(dir, h.x, 0.06D * Math.sin(age * 0.12D + k * 0.33D) * s);
                dir.normalize();
                float step = 0.56F * S;
                Vector3f next = new Vector3f(p).add(new Vector3f(dir).mul(step));
                Frame wf = frameWithY(new Vector3f(p).add(next).mul(0.5F), dir);
                float thick = (0.085F - k * 0.0032F) * S;
                box(vc, m, wf, 0.0D, 0.0D, 0.0D, thick, step * 0.55F, thick,
                        k % 3 == 0 ? white : edge, alpha * 0.70F);
                // 须子本身也是一条电（每隔两节连一段）
                if (k % 2 == 0) {
                    LightningBoltGeometry.arc(vc, m, prev.x, prev.y, prev.z, next.x, next.y, next.z,
                            seed + 2000L + k * 19L + s, 0.028F * S, alpha * 0.55F, theme,
                            camX, camY, camZ);
                    prev = new Vector3f(next);
                }
                p = next;
            }
        }

        // 耳鳍 / 颊鳍：三片扇形薄板（会随头摆）
        for (int s = -1; s <= 1; s += 2) {
            for (int k = 0; k < 3; k++) {
                double a = 0.55D + k * 0.42D + 0.10D * Mth.sin(age * 0.14F + k);
                Vector3f dir = new Vector3f(h.x).mul((float) (s * Math.cos(a)))
                        .add(new Vector3f(h.z).mul((float) (-Math.sin(a))))
                        .add(new Vector3f(h.y).mul(0.18F)).normalize();
                float len = (1.05F - k * 0.16F) * S;
                Frame fin = frameWithY(at(h, s * 1.05D * S, 0.28D * S, 0.15D * S), dir);
                box(vc, m, fin, 0.0D, len * 0.5F, 0.0D, 0.06F * S, len * 0.5F, 0.42F * S,
                        edge, alpha * 0.34F);
            }
        }
    }

    // ------------------------------------------------------------------
    //  四爪
    // ------------------------------------------------------------------

    private static void drawLeg(VertexConsumer vc, Matrix4f m, Frame body, int side, int index,
                                float age, float S, float alpha, float[] mid, float[] white) {
        double swing = 0.22D * Mth.sin((float) (age * 0.09D + index * 1.3D));
        Vector3f shoulder = at(body, side * 0.75D * S, 0.0D, 0.0D);
        Vector3f upperDir = new Vector3f(body.x).mul((float) (side * 0.80D))
                .add(new Vector3f(body.y).mul((float) (-0.45D + swing * 0.4D)))
                .add(new Vector3f(body.z).mul((float) (0.35D + swing))).normalize();
        float upperLen = 1.55F * S;
        Vector3f knee = new Vector3f(shoulder).add(new Vector3f(upperDir).mul(upperLen));
        Frame uf = frameWithY(new Vector3f(shoulder).add(knee).mul(0.5F), upperDir);
        box(vc, m, uf, 0.0D, 0.0D, 0.0D, 0.24F * S, upperLen * 0.55F, 0.24F * S, mid, alpha * 0.46F);

        Vector3f lowerDir = new Vector3f(body.x).mul((float) (side * 0.30D))
                .add(new Vector3f(body.y).mul((float) (-0.85D - swing * 0.3D)))
                .add(new Vector3f(body.z).mul((float) (0.55D + swing))).normalize();
        float lowerLen = 1.35F * S;
        Vector3f foot = new Vector3f(knee).add(new Vector3f(lowerDir).mul(lowerLen));
        Frame lf = frameWithY(new Vector3f(knee).add(foot).mul(0.5F), lowerDir);
        box(vc, m, lf, 0.0D, 0.0D, 0.0D, 0.19F * S, lowerLen * 0.55F, 0.19F * S, mid, alpha * 0.42F);

        // 三根爪：张开、纯白（"龙爪"的辨识度全靠它 ✓）
        for (int c = -1; c <= 1; c++) {
            Vector3f clawDir = new Vector3f(lowerDir).mul(0.65F)
                    .add(new Vector3f(body.x).mul(c * 0.55F))
                    .add(new Vector3f(body.z).mul(0.55F)).normalize();
            float len = (0.85F - Math.abs(c) * 0.14F) * S;
            Frame cf = frameWithY(new Vector3f(foot).add(new Vector3f(clawDir).mul(len * 0.5F)), clawDir);
            box(vc, m, cf, 0.0D, 0.0D, 0.0D, 0.075F * S, len * 0.5F, 0.075F * S, white, alpha * 0.80F);
        }
    }

    // ------------------------------------------------------------------
    //  方块 / 向量工具
    // ------------------------------------------------------------------

    /** 画一个<b>有朝向的方块</b>（6 个面，顶面最亮、底面最暗 —— 不用光照也有体积感 ✓）。 */
    private static void box(VertexConsumer vc, Matrix4f m, Frame f,
                            double cx, double cy, double cz,
                            float hx, float hy, float hz,
                            float[] color, float alpha) {
        if (alpha <= 0.004F || hx <= 0.0F || hy <= 0.0F || hz <= 0.0F) {
            return;
        }
        Vector3f c = at(f, cx, cy, cz);
        Vector3f x = new Vector3f(f.x).mul(hx);
        Vector3f y = new Vector3f(f.y).mul(hy);
        Vector3f z = new Vector3f(f.z).mul(hz);
        // 8 个角：0..7，位序 (x,y,z) 的负/正组合
        Vector3f[] p = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            float sx = (i & 1) == 0 ? -1.0F : 1.0F;
            float sy = (i & 2) == 0 ? -1.0F : 1.0F;
            float sz = (i & 4) == 0 ? -1.0F : 1.0F;
            p[i] = new Vector3f(c).add(new Vector3f(x).mul(sx))
                    .add(new Vector3f(y).mul(sy)).add(new Vector3f(z).mul(sz));
        }
        // -Z / +Z / -Y / +Y / -X / +X
        face(vc, m, p[0], p[3], p[2], p[1], color, alpha * 0.82F);
        face(vc, m, p[4], p[5], p[6], p[7], color, alpha * 1.00F);
        face(vc, m, p[0], p[1], p[5], p[4], color, alpha * 0.58F);
        face(vc, m, p[3], p[7], p[6], p[2], color, alpha * 1.10F);
        face(vc, m, p[0], p[4], p[7], p[3], color, alpha * 0.76F);
        face(vc, m, p[1], p[2], p[6], p[5], color, alpha * 0.90F);
    }

    private static void face(VertexConsumer vc, Matrix4f m,
                             Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                             float[] color, float alpha) {
        vertex(vc, m, a, color, alpha);
        vertex(vc, m, b, color, alpha);
        vertex(vc, m, c, color, alpha);
        vertex(vc, m, d, color, alpha);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vector3f p, float[] color, float alpha) {
        vc.vertex(m, p.x, p.y, p.z)
                .color(color[0], color[1], color[2], Math.min(1.0F, alpha))
                .uv(0.5F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    /** 以 {@code origin} 为原点、{@code yAxis} 为 y 轴的正交基（x/z 自动补出来 ✓）。 */
    private static Frame frameWithY(Vector3f origin, Vector3f yAxis) {
        Vector3f y = new Vector3f(yAxis).normalize();
        Vector3f helper = Math.abs(y.y) > 0.92F ? new Vector3f(0.0F, 0.0F, 1.0F) : new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f x = new Vector3f(helper).cross(y, new Vector3f());
        if (x.lengthSquared() < 1.0E-5F) {
            x.set(1.0F, 0.0F, 0.0F);
        }
        x.normalize();
        Vector3f z = new Vector3f(x).cross(y, new Vector3f()).normalize();
        return new Frame(origin, x, y, z);
    }

    /** 基础坐标：{@code o + x·bx + y·by + z·bz}。 */
    private static Vector3f at(Frame f, double x, double y, double z) {
        return new Vector3f(f.o)
                .add(new Vector3f(f.x).mul((float) x))
                .add(new Vector3f(f.y).mul((float) y))
                .add(new Vector3f(f.z).mul((float) z));
    }

    /** 绕任意轴旋转一个向量（罗德里格斯公式）✓。 */
    private static Vector3f rotate(Vector3f v, Vector3f axis, double ang) {
        float c = (float) Math.cos(ang);
        float s = (float) Math.sin(ang);
        Vector3f k = new Vector3f(axis).normalize();
        float dot = v.dot(k);
        Vector3f cross = new Vector3f(k).cross(v, new Vector3f());
        return new Vector3f(v).mul(c)
                .add(cross.mul(s))
                .add(new Vector3f(k).mul(dot * (1.0F - c)));
    }

    /** 整个基绕某个轴转（下颚张合就是这么做的 ✓）。 */
    private static Frame rotateFrame(Frame f, Vector3f axis, double ang) {
        return new Frame(f.o,
                rotate(f.x, axis, ang),
                rotate(f.y, axis, ang),
                rotate(f.z, axis, ang));
    }
}
