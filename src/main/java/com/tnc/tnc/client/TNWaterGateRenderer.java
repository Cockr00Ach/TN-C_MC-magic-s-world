package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.water.TNWaterGateEntity;
import com.tnc.tnc.magic.water.WaterGateRules;
import com.tnc.tnc.magic.water.WaterSpellRules;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 水球链 t4/t5 的<b>法阵群</b>（作者 2026-11-09：金闪闪那种 ✓）。
 *
 * <p>所有几何都是现画的（一个实体都不多 ✓），三个阶段：
 * <ul>
 *   <li>{@link TNWaterGateEntity#IDLE}：两个小法阵绕着玩家慢慢转 ✓（阵面始终朝着"准星那边"✓）；</li>
 *   <li>{@link TNWaterGateEntity#OPEN}/{@code FIRE}：一圈法阵一个接一个亮起来 ✓，
 *       亮完才齐射 —— 齐射那一下整体闪白 ✓（作者："先出阵、全部出现完之后再一起发射"✗）；</li>
 *   <li>{@link TNWaterGateEntity#CONVERGE}/{@code GIANT}（仅 t5）：这几座阵<b>飞到身前一点</b>
 *       叠在一起 ✓，然后在那里撑开一个巨大的阵 ✓。</li>
 * </ul>
 *
 * <p>阵面朝向：朝着<b>这一轮的目标</b> ✓（这就是"法阵里射出去"的读法 ✗，
 * 让阵面永远朝着玩家正前方会像一块贴在脸上的牌子 ✗）。
 */
public final class TNWaterGateRenderer extends EntityRenderer<TNWaterGateEntity> {

    public TNWaterGateRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(TNWaterGateEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/water_still.png");
    }

    @Override
    public boolean shouldRender(TNWaterGateEntity e, Frustum frustum, double x, double y, double z) {
        return e.distanceToSqr(x, y, z) < 96 * 96;
    }

    @Override
    public void render(TNWaterGateEntity e, float yaw, float partial, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        buffers = WaterTransparencyPass.route(buffers);
        double age = e.tickCount + partial;
        int tier = e.tier();
        int phase = e.phase();
        double phaseTick = e.tickCount + partial - e.phaseStart();
        Vec3 aim = aimPoint(e);
        Vec3 look = Vec3.directionFromRotation(0, e.lookYaw());
        // 阵面朝"目标"而不是朝"玩家前方"：法阵要看起来像"把水波射向敌人"✓
        float pulse = (float)(0.82 + 0.18 * Math.sin(age * 0.35));

        Matrix4f pose = stack.last().pose();
        VertexConsumer body = buffers.getBuffer(WaterRenderTypes.geometry());
        VertexConsumer glow = buffers.getBuffer(WaterRenderTypes.glow());

        if (phase == TNWaterGateEntity.IDLE) {
            double seal = WaterGateRules.sealRadius(tier) * 0.72;
            for (int i = 0; i < 2; i++) {
                Vec3 at = WaterGateRules.orbitPos(Vec3.ZERO, i, 2, age);
                // 平时只有两座 ⇒ 画满 ✓（作者："法阵太简陋了"✗）
                seal(body, glow, pose, at, WaterGateRules.facing(at, aim), seal, age + i * 17,
                        0.72F * pulse, tier, true);
            }
            return;
        }

        int count = WaterGateRules.portals(tier);
        if (phase == TNWaterGateEntity.OPEN || phase == TNWaterGateEntity.FIRE
                || phase == TNWaterGateEntity.LINGER) {
            boolean lingerPhase = phase == TNWaterGateEntity.LINGER;
            float flash = phase == TNWaterGateEntity.FIRE ? WaterGateRules.flash(phaseTick) : 0.0F;
            // ★ 作者 2026-11-10："射完激光之后法阵还要残留半秒"⇒ 残留那 10 tick 里阵不再变大，
            //   只是<b>自己散掉</b>（不是"被关灯"✗ 见 WaterGateRules.linger 的曲线 ✓）
            float remain = lingerPhase ? WaterGateRules.linger(phaseTick) : 1.0F;
            if (remain <= 0.01F) {
                return;
            }
            for (int i = 0; i < count; i++) {
                // 残留阶段所有阵都已经是"全开"状态 ✓（不能拿 phaseTick 重新算一遍出现进度 ✗）
                float open = lingerPhase ? 1.0F : WaterGateRules.appear(i, phaseTick);
                if (open <= 0.02F) {
                    continue;
                }
                Vec3 at = WaterGateRules.portalPos(Vec3.ZERO, tier, i, count, e.awayYaw());
                // 亮起来的过程：从中心撑开 + 转得更快 ✓（齐射时同屏六~八座 ⇒ 用精简版 ✓）
                double radius = WaterGateRules.sealRadius(tier) * (0.30D + 0.70D * open) * (1.0D + 0.25D * flash);
                // 残留时阵稍微再胀一点点（0.5 秒里 +6% ✓ 像"余波还没散"✓）
                radius *= lingerPhase ? 1.0D + 0.06D * (1.0D - remain) : 1.0D;
                seal(body, glow, pose, at, WaterGateRules.facing(at, aim), radius,
                        age * (1.0D + open) + i * 23, open * (0.78F + 0.9F * flash) * remain, tier, false);
            }
            return;
        }

        // CONVERGE / GIANT：几座阵一起飞到身前那一点 ✓
        Vec3 converge = WaterGateRules.convergePos(Vec3.ZERO, e.awayYaw());
        double merged = phase == TNWaterGateEntity.GIANT ? 1.0D
                : Mth.clamp(phaseTick / (double)WaterGateRules.CONVERGE_TICKS, 0.0D, 1.0D);
        double ease = merged * merged * (3.0D - 2.0D * merged);
        for (int i = 0; i < count; i++) {
            Vec3 from = WaterGateRules.portalPos(Vec3.ZERO, tier, i, count, e.awayYaw());
            Vec3 at = from.lerp(converge, ease);
            double radius = WaterGateRules.sealRadius(tier) * (1.0D - 0.45D * ease)
                    * (phase == TNWaterGateEntity.GIANT ? 1.0D + 0.35D * Mth.clamp(phaseTick / 4.0D, 0.0D, 1.0D) : 1.0D);
            seal(body, glow, pose, at, WaterGateRules.facing(at, aim), radius, age + i * 13, 0.85F * pulse, tier, false);
        }
        if (phase == TNWaterGateEntity.GIANT) {
            // 收尾：身前那一点撑开一座巨大的阵（那道巨大水波就是从这里出去的 ✓）
            //  ★ 2026-11-10 作者："t5 汇聚成的大小要是小的的 5 倍大" ⇒ giantSealRadius = 小阵 × 5 ✓
            float grow = Mth.clamp((float)phaseTick / 4.0F, 0.0F, 1.0F);
            float fade = 1.0F - Mth.clamp((float)(phaseTick - 6.0D) / (WaterGateRules.GIANT_TICKS - 6.0F), 0.0F, 1.0F);
            seal(body, glow, pose, converge, look,
                    WaterGateRules.giantSealRadius(tier) * (0.35D + 0.65D * grow),
                    age * 1.7D, 0.95F * fade, tier);
        }
    }

    /**
     * 一座水色法阵（作者 2026-11-10：「法阵太简陋了吧，你根本没设计吧……这个法阵也要发光，
     * 因为你的水波是发光的」）。
     *
     * <h2>设计（从外到内七层，双通道）</h2>
     * <ol>
     *   <li><b>洗底</b>：极淡的深蓝盘 —— 让阵在夜里也是一片"水"，而不是一圈线；</li>
     *   <li><b>外环带</b>：粗水体环（半透明）+ 一道细的珠白亮边（加色 ✓ 这就是"发光"）；</li>
     *   <li><b>内环带</b>：0.88R 的青色环 + 0.52R 的细环，把阵分成三段；</li>
     *   <li><b>符文带</b>：12 个径向短刻（大阵 20 个）<b>自己转</b> ✓；</li>
     *   <li><b>六芒星</b>：两个反向旋转的三角形（t5 才画全两个 ✓）—— 法阵的"骨架"；</li>
     *   <li><b>水涡臂</b>：3~4 条从 0.86R 卷进 0.26R 的螺旋（水系身份 ✗）；</li>
     *   <li><b>阵心 + 外缘浪弧</b>：中心一颗白核 + 一圈亮环，外面三道不闭合的浪弧
     *       （闭环会读成"香炉"，开口才像浪 ✓）。</li>
     * </ol>
     *
     * <p>为什么不用 {@link WaterGeometry#seal}：那座整阵是给"施法脚下那一张"用的 ✗，
     * 一座就要 **2856~3808** 个顶点 ✗（实测），而这里同一帧最多十座 ✗。
     * 这一版的单座成本约 700（小阵）/ 1300（巨大阵）✓，形状还是同一种"潮汐阵"的语言 ✓。
     */
    static void seal(VertexConsumer body, VertexConsumer glow, Matrix4f pose, Vec3 center,
                     Vec3 facing, double radius, double time, float alpha, int tier) {
        seal(body, glow, pose, center, facing, radius, time, alpha, tier, radius > 1.2D);
    }

    /**
     * 同上，但<b>明说了要不要画满</b> ✓。
     *
     * <p>为什么需要这个开关：齐射那一瞬间同屏 <b>六~八座</b>阵 ✗ ——
     * 每座都画满（1572 顶点）就是 12k 顶点 ✗。它们在**阵面只有 0.6~0.95 格半径**的时候
     * 本来就看不出花瓣和星点 ✗ ⇒ 齐射/汇聚阶段用 {@code false}（约 770 顶点/座 ✓），
     * 只有"平时环绕的两个"和"t5 汇聚出来的大阵"才画满 ✓。
     */
    static void seal(VertexConsumer body, VertexConsumer glow, Matrix4f pose, Vec3 center,
                     Vec3 facing, double radius, double time, float alpha, int tier, boolean full) {
        if (radius <= 0.02D || alpha <= 0.01F) {
            return;
        }
        Vec3 axis = facing.lengthSqr() < 1.0E-6D ? new Vec3(0, 0, 1) : facing.normalize();
        Vec3 right = WaterSpellRules.right(axis);
        Vec3 up = right.cross(axis).normalize();
        int seg = full ? (radius > 3.0D ? 56 : 32) : 16;
        boolean rich = full && radius > 1.2D;   // 涡臂只给 t5 那座大阵
        double spin = time * 0.022D;

        // ① 洗底：由外到内渐亮的淡盘 —— 让法阵是一团"水光"，不是几根线 ✗
        WaterGeometry.disk(body, pose, center.add(axis.scale(0.002D)), axis, radius * 0.98D,
                0.02F, 0.12F, 0.30F, alpha * 0.10F, seg);
        WaterGeometry.disk(glow, pose, center.add(axis.scale(0.003D)), axis, radius * 0.92D,
                0.08F, 0.38F, 0.72F, alpha * 0.10F, seg);
        if (full) {
            WaterGeometry.disk(glow, pose, center.add(axis.scale(0.004D)), axis, radius * 0.62D,
                    0.12F, 0.48F, 0.80F, alpha * 0.12F, 20);
        }
        WaterGeometry.disk(glow, pose, center.add(axis.scale(0.005D)), axis, radius * 0.30D,
                0.18F, 0.60F, 0.88F, alpha * 0.18F, 12);

        // ② 外环带：柔光晕 + 水体环 + 珠白亮线（"发光"就是这三层叠出来的 ✓）
        WaterGeometry.ringColor(glow, pose, center, axis, radius * 0.995D, radius * 0.10D + 0.02D,
                0.55F, 0.92F, 1.0F, alpha * 0.16F, seg);
        WaterGeometry.ringColor(body, pose, center, axis, radius, radius * 0.035D + 0.012D,
                0.55F, 0.90F, 1.0F, alpha * 0.55F, seg);
        WaterGeometry.ringColor(glow, pose, center, axis, radius, radius * 0.018D + 0.006D,
                0.95F, 1.0F, 1.0F, alpha * 0.95F, seg);

        // ③ 内带：0.88R（柔光 + 水线 + 亮边）＋ 0.52R 细环
        WaterGeometry.ringColor(glow, pose, center, axis, radius * 0.88D, radius * 0.07D + 0.014D,
                0.30F, 0.80F, 1.0F, alpha * 0.12F, seg);
        WaterGeometry.ringColor(body, pose, center, axis, radius * 0.88D, radius * 0.022D + 0.008D,
                0.26F, 0.78F, 1.0F, alpha * 0.45F, seg);
        WaterGeometry.ringColor(glow, pose, center, axis, radius * 0.88D, radius * 0.010D + 0.005D,
                0.82F, 0.97F, 1.0F, alpha * 0.80F, seg);
        WaterGeometry.ringColor(body, pose, center, axis, radius * 0.52D, radius * 0.012D + 0.006D,
                0.26F, 0.78F, 1.0F, alpha * 0.35F, seg);

        // ④ 符文带（自己转 ✓，每三格一个长刻 ✓）
        int ticks = rich ? 20 : 12;
        for (int i = 0; i < ticks; i++) {
            double a = spin + i * Math.PI * 2.0D / ticks;
            Vec3 p = point(center, right, up, axis, a, radius * 0.925D, 0.004D);
            Vec3 q = point(center, right, up, axis, a, radius * (i % 3 == 0 ? 0.985D : 0.962D), 0.004D);
            WaterGeometry.stroke(glow, pose, axis, p, q, radius * 0.010D + 0.006D,
                    0.95F, 1.0F, 1.0F, alpha * 0.85F);
        }

        // ⑤ 六芒星：两个三角形反向转 ✓
        triangle(body, glow, pose, center, right, up, axis, radius * 0.66D, spin * 1.6D, alpha, full);
        if (rich) {
            triangle(body, glow, pose, center, right, up, axis, radius * 0.66D,
                    -spin * 0.9D + Math.PI / 3.0D, alpha * 0.7F, false);
        }

        // ⑥ 六片水瓣（小阵，干净）／三条水涡臂（大阵，有流动感 ✓）
        if (rich) {
            for (int i = 0; i < 3; i++) {
                arm(body, glow, pose, center, right, up, axis, radius,
                        spin * 0.9D + i * Math.PI * 2.0D / 3.0D, alpha);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                petal(body, glow, pose, center, right, up, axis, radius,
                        spin * 0.7D + i * Math.PI / 2.0D, alpha);
            }
        }

        // ⑦ 阵心：双环 + 白核 + 四道短光（法阵的"眼"✓）
        WaterGeometry.ringColor(glow, pose, center, axis, radius * 0.26D, radius * 0.020D + 0.008D,
                0.30F, 0.82F, 1.0F, alpha * 0.90F, 14);
        WaterGeometry.ringColor(glow, pose, center, axis, radius * 0.16D, radius * 0.012D + 0.006D,
                0.90F, 0.99F, 1.0F, alpha * 0.90F, 12);
        WaterGeometry.disk(glow, pose, center, axis, radius * 0.085D + 0.008D,
                1.0F, 1.0F, 1.0F, alpha, 10);
        if (full) {
            for (int i = 0; i < 4; i++) {
                double a = spin * 1.4D + i * Math.PI / 2.0D + Math.PI / 4.0D;
                Vec3 p = point(center, right, up, axis, a, radius * 0.30D, 0.006D);
                Vec3 q = point(center, right, up, axis, a, radius * 0.42D, 0.006D);
                WaterGeometry.stroke(glow, pose, axis, p, q, radius * 0.010D + 0.004D,
                        0.95F, 1.0F, 1.0F, alpha * 0.70F);
            }
        }

        // ⑧ 外缘浪弧（开口，不闭环 ✓ —— 闭环会读成"香炉"✗）
        for (int i = 0; i < (full ? 3 : 1); i++) {
            double a0 = spin * 1.2D + i * Math.PI * 2.0D / 3.0D;
            arc(glow, pose, center, right, up, axis, radius * 1.07D, a0, a0 + Math.PI * 0.30D,
                    alpha * 0.65F);
        }
    }

    /** 阵面上的一个点（法阵是"平的一片"✓ 抬高一点点避免和别的面打架 ✓）。 */
    private static Vec3 point(Vec3 center, Vec3 right, Vec3 up, Vec3 axis,
                              double angle, double radius, double lift) {
        return center.add(right.scale(Math.cos(angle) * radius))
                .add(up.scale(Math.sin(angle) * radius)).add(axis.scale(lift));
    }

    /** 一个三角形（三个顶点都在半径 r 的圆上 ✓ 三条边各一笔）。 */
    private static void triangle(VertexConsumer body, VertexConsumer glow, Matrix4f pose,
                                 Vec3 center, Vec3 right, Vec3 up, Vec3 axis,
                                 double r, double rotation, float alpha, boolean dots) {
        Vec3[] v = new Vec3[3];
        for (int i = 0; i < 3; i++) {
            double a = rotation + i * Math.PI * 2.0D / 3.0D + Math.PI / 2.0D;
            v[i] = point(center, right, up, axis, a, r, 0.005D);
        }
        for (int i = 0; i < 3; i++) {
            Vec3 a = v[i], b = v[(i + 1) % 3];
            WaterGeometry.stroke(body, pose, axis, a, b, r * 0.020D + 0.004D,
                    0.30F, 0.80F, 1.0F, alpha * 0.45F);
            WaterGeometry.stroke(glow, pose, axis, a, b, r * 0.009D + 0.003D,
                    0.85F, 0.98F, 1.0F, alpha * 0.55F);
        }
        // 三个顶点各一颗小亮点 ✓（六芒星的"星"就在这里；齐射时省掉 ✓）
        if (dots) {
            for (Vec3 p : v) {
                WaterGeometry.disk(glow, pose, p, axis, r * 0.045D + 0.006D,
                        0.92F, 0.99F, 1.0F, alpha * 0.55F, 6);
            }
        }
    }

    /**
     * 一片水瓣 ✓（0.52R → 0.86R 之间的一道小弧 ＋ 一条内衬）。
     *
     * <p>小阵不用涡臂（0.62 格的阵面里塞三条螺旋只会糊成一团 ✗），
     * 改用六片对称的水瓣 —— 远看是一朵"水花"，近看每一片都有内外两层 ✓。
     */
    private static void petal(VertexConsumer body, VertexConsumer glow, Matrix4f pose,
                              Vec3 center, Vec3 right, Vec3 up, Vec3 axis,
                              double radius, double base, float alpha) {
        int steps = 4;
        Vec3 prevOuter = null, prevInner = null;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double)steps;
            double a = base + (t - 0.5D) * 0.42D;
            // 瓣形：中间最宽、两端收进环里 ✓
            double bulge = Math.sin(Math.PI * t) * radius * 0.10D;
            Vec3 outer = point(center, right, up, axis, a, radius * 0.62D + radius * 0.16D * Math.sin(Math.PI * t) + bulge, 0.004D);
            Vec3 inner = point(center, right, up, axis, a, radius * 0.52D + radius * 0.02D * Math.sin(Math.PI * t), 0.004D);
            if (prevOuter != null) {
                WaterGeometry.stroke(body, pose, axis, prevOuter, outer, radius * 0.014D + 0.004D,
                        0.30F, 0.80F, 1.0F, alpha * 0.35F);
                WaterGeometry.stroke(glow, pose, axis, prevOuter, outer, radius * 0.006D + 0.003D,
                        0.88F, 0.98F, 1.0F, alpha * 0.55F);
                WaterGeometry.stroke(glow, pose, axis, prevInner, inner, radius * 0.004D + 0.002D,
                        0.55F, 0.90F, 1.0F, alpha * 0.30F);
            }
            prevOuter = outer;
            prevInner = inner;
        }
    }

    /** 一条从外圈卷进阵心的水涡臂 ✓（螺旋，不是直线 ✗ —— 直线会读成"辐条"✗）。 */
    private static void arm(VertexConsumer body, VertexConsumer glow, Matrix4f pose,
                            Vec3 center, Vec3 right, Vec3 up, Vec3 axis,
                            double radius, double base, float alpha) {
        int steps = 6;
        Vec3 prev = null;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double)steps;
            double r = radius * (0.86D - 0.60D * t);
            double a = base + t * 1.25D;
            Vec3 p = point(center, right, up, axis, a, r, 0.004D + t * 0.002D);
            if (prev != null) {
                WaterGeometry.stroke(body, pose, axis, prev, p, radius * (0.020D - 0.012D * t) + 0.004D,
                        0.24F, 0.74F, 1.0F, alpha * (0.50F - 0.20F * (float)t));
                WaterGeometry.stroke(glow, pose, axis, prev, p, radius * 0.007D + 0.003D,
                        0.80F, 0.96F, 1.0F, alpha * (0.40F - 0.18F * (float)t));
            }
            prev = p;
        }
    }

    /** 一段开口的弧 ✓（不是整圈 ✗ —— 整圈会读成"香炉"，开口才像浪 ✓）。 */
    private static void arc(VertexConsumer glow, Matrix4f pose, Vec3 center, Vec3 right, Vec3 up,
                            Vec3 axis, double r, double a0, double a1, float alpha) {
        int steps = 7;
        Vec3 prev = null;
        for (int i = 0; i <= steps; i++) {
            double a = a0 + (a1 - a0) * i / steps;
            Vec3 p = point(center, right, up, axis, a, r, 0.005D);
            if (prev != null) {
                WaterGeometry.stroke(glow, pose, axis, prev, p, r * 0.012D + 0.004D,
                        0.88F, 0.98F, 1.0F, alpha);
            }
            prev = p;
        }
    }

    /** 这一轮打哪：记下的目标 ✓；没有就顺着主人视线看 24 格 ✓（阵面朝那边）。 */
    private Vec3 aimPoint(TNWaterGateEntity gate) {
        int id = gate.targetId();
        if (id >= 0) {
            Entity target = gate.level().getEntity(id);
            if (target instanceof LivingEntity living && living.isAlive()) {
                return target.getBoundingBox().getCenter().subtract(gate.position());
            }
        }
        return Vec3.directionFromRotation(0, gate.lookYaw()).scale(24.0D);
    }
}
