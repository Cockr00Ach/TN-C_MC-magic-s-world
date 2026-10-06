package com.tnc.tnc.client;

import com.tnc.tnc.magic.fire.TNFlameDemonField;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * <b>射线链 t4「炎魔龙之怒」的巨阵渲染器</b>（作者 2026-10-05 设计）✓
 *
 * <h2>画什么</h2>
 * <ol>
 *   <li><b>地上那个大法阵</b> —— 作者要「巨大的法阵」，而且「法阵的出现应该是从外到里
 *       一点点出现，表现出描绘的感觉」✗ ⇒ 外环先亮、内环依次跟上 ✓</li>
 *   <li><b>东南西北 4 根黑曜石巨柱</b> —— 作者明确「<b>不改地形</b>」✗
 *       ⇒ 一根真方块都不放，纯画 ✓（不然打完地上多 4 根柱子，玩家还得自己拆 ✗）</li>
 *   <li><b>柱顶 4 颗太阳感圆球</b> —— 「红黄色的圆球，尽量模拟出太阳的感觉」✓
 *       （三片正交的发光圆盘叠出"球"的观感 ✓，比真画球省得多而远处看几乎一样 ✓）</li>
 *   <li><b>4 条激光</b> —— 每个圆球射向它这一 tick 锁定的目标 ✓
 *       （目标 id 来自同步字段 ✓ ⇒「多束打同一目标」在这里就是"4 条线指向同一点" ✓）</li>
 * </ol>
 *
 * <p>⚠️ 坐标一律<b>相对实体位置</b> ✗ —— {@code EntityRenderer} 的 pose 已经平移到实体位置了 ✗
 * （水球/火球渲染器都踩过这个坑 ✓）
 */
public final class TNFlameDemonRenderer extends EntityRenderer<TNFlameDemonField> {

    private static final ResourceLocation PLACEHOLDER =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");

    /** 法阵由外到里"描绘"完需要多久（tick ✓）。 */
    private static final double DRAW_TICKS = 34.0D;

    /**
     * 柱子半宽（格 ✓）
     *
     * <p>⚠️ 作者 2026-10-05 第 4 条：柱子样式要「末影岛处的方柱形式」✓
     * ⇒ 干净的四棱方柱、细一点、不带花纹 ✓（原来 0.42 加熔岩竖纹，像工业柱子 ✗）
     */
    private static final double PILLAR_HALF = 0.34D;

    /** 黑曜石色（末影岛那种近黑的紫 ✓）。 */
    private static final float OR = 0.045F;
    private static final float OG = 0.022F;
    private static final float OB = 0.075F;

    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Vec3 AXIS_X = new Vec3(1.0D, 0.0D, 0.0D);
    private static final Vec3 AXIS_Z = new Vec3(0.0D, 0.0D, 1.0D);
    private static final Vec3 ORIGIN = Vec3.ZERO;

    public TNFlameDemonRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TNFlameDemonField entity) {
        return PLACEHOLDER;
    }

    @Override
    public boolean shouldRender(TNFlameDemonField field, Frustum frustum, double x, double y, double z) {
        return field.distanceToSqr(x, y, z) < 160.0D * 160.0D;
    }

    @Override
    public void render(TNFlameDemonField field, float yaw, float partial, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        VertexConsumer out = buffers.getBuffer(WaterRenderTypes.geometry());
        Matrix4f pose = stack.last().pose();

        double age = field.tickCount + partial;
        double radius = field.radius();
        if (radius < 0.5D) {
            return;
        }

        drawSigil(out, pose, radius, age, field.life());
        for (int i = 0; i < 4; i++) {
            // ⚠️ orbPosition() 给的是**世界坐标** ✓ ⇒ 减掉实体位置换成相对坐标 ✗
            Vec3 rel = field.orbPosition(i).subtract(field.position());
            drawPillar(out, pose, rel, age);
            drawSun(out, pose, rel, age, i);
            drawLaser(out, pose, field, rel, i);
        }
    }

    // ------------------------------------------------------------------
    //  ① 地上那个大法阵（由外到里"描绘"）
    // ------------------------------------------------------------------

    private static void drawSigil(VertexConsumer out, Matrix4f pose, double radius, double age, int life) {
        // ⚠️ 作者 2026-10-05：不要仿真渲染 ✗ ⇒ 不再"渐渐淡出" ✓，最后 3 tick 整块收掉 ✓
        if (age > life - 3) {
            return;
        }
        // 地面底光（一整块实色 ✓）
        WaterGeometry.disk(out, pose, ORIGIN, UP, radius, 0.62F, 0.24F, 0.05F, 1.0F);
        // ⚠️ 作者要"从外到里一点点出现"✗ ⇒ 每环有自己的出现时刻：越靠外越早 ✓
        for (int i = 0; i < 5; i++) {
            double r = radius * (1.0D - i * 0.17D);
            double appear = Math.min(1.0D, Math.max(0.0D, age / DRAW_TICKS - i * 0.16D));
            if (appear <= 0.01D) {
                continue;
            }
            // 像素风：一圈要么**整圈画出来**、要么不画 ✓（不再半透明渐变 ✗）
            // 外环先满、内环依次跟上 ⇒ 仍然是"由外到里描绘"的感觉 ✓
            if (appear < 0.5D) {
                continue;
            }
            WaterGeometry.ring(out, pose, ORIGIN, UP, r, Math.max(0.09D, 0.13D - i * 0.010D),
                    age * 0.012D, 1.0F);
        }
        // ★ 放射状辐条 + 六芒星（作者 2026-10-05 第 7 条：「法阵不清晰」✗）
        //   根因：光靠几个同心圆，在暗色地面上就是几圈**虚影** ✗，看不出是个法阵 ✗
        //   ⇒ 加 8 根辐条 + 一个六芒星 ✓ —— 这两个结构一出来，法阵立刻立住了 ✓
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4.0D + age * 0.004D;
            Vec3 outer = WaterGeometry.radial(AXIS_X, AXIS_Z, a, radius * 0.98D);
            Vec3 inner = WaterGeometry.radial(AXIS_X, AXIS_Z, a, radius * 0.30D);
            if (k % 2 == 0) {
                WaterGeometry.tube(out, pose, inner, outer, 0.055D, 1.0F, 0.66F, 0.16F, 1.0F);
            }
        }
        for (int k = 0; k < 6; k++) {
            Vec3 p0 = WaterGeometry.radial(AXIS_X, AXIS_Z, k * Math.PI / 3.0D, radius * 0.62D);
            Vec3 p1 = WaterGeometry.radial(AXIS_X, AXIS_Z, (k + 2) * Math.PI / 3.0D, radius * 0.62D);
            WaterGeometry.tube(out, pose, p0, p1, 0.045D, 1.0F, 0.80F, 0.26F, 1.0F);
        }

        // 阵中心那点热核 ✓
        if (age / DRAW_TICKS > 0.7D) {
            WaterGeometry.disk(out, pose, ORIGIN, UP, radius * 0.22D, 1.0F, 0.72F, 0.22F, 1.0F);
        }
    }

    // ------------------------------------------------------------------
    //  ② 黑曜石巨柱（纯视觉）
    // ------------------------------------------------------------------

    private static void drawPillar(VertexConsumer out, Matrix4f pose, Vec3 at, double age) {
        // 柱脚从地里"升起来"（前 12 tick ✓，作者要"生起"✓）
        double rise = Math.min(1.0D, age / 12.0D);
        double height = at.y * rise;
        if (height < 0.2D) {
            return;
        }
        Vec3[] corner = {
                new Vec3(at.x - PILLAR_HALF, 0.0D, at.z - PILLAR_HALF),
                new Vec3(at.x + PILLAR_HALF, 0.0D, at.z - PILLAR_HALF),
                new Vec3(at.x + PILLAR_HALF, 0.0D, at.z + PILLAR_HALF),
                new Vec3(at.x - PILLAR_HALF, 0.0D, at.z + PILLAR_HALF)};
        for (int i = 0; i < 4; i++) {
            Vec3 p0 = corner[i];
            Vec3 p1 = corner[(i + 1) % 4];
            Vec3 q0 = new Vec3(p0.x, height, p0.z);
            Vec3 q1 = new Vec3(p1.x, height, p1.z);
            // 两面都画（免得从里面看是空的 ✗）
            WaterGeometry.quad(out, pose, p0, p1, q1, q0, OR, OG, OB, 1.0F);
            WaterGeometry.quad(out, pose, q0, q1, p1, p0, OR, OG, OB, 1.0F);
        }
        // 柱顶面
        WaterGeometry.quad(out, pose,
                new Vec3(at.x - PILLAR_HALF, height, at.z - PILLAR_HALF),
                new Vec3(at.x + PILLAR_HALF, height, at.z - PILLAR_HALF),
                new Vec3(at.x + PILLAR_HALF, height, at.z + PILLAR_HALF),
                new Vec3(at.x - PILLAR_HALF, height, at.z + PILLAR_HALF),
                0.16F, 0.09F, 0.20F, 1.0F);
        // ⚠️ 作者第 4 条要"末影岛的方柱形式"✓ ⇒ **不再画熔岩竖纹** ✗
        //    末影岛那几根就是干干净净一根黑紫方柱 ✓，加了纹路反而像工业设施 ✗
        // 柱顶一圈亮边（像素风：一条实色棱 ✓）
        WaterGeometry.quad(out, pose,
                new Vec3(at.x - PILLAR_HALF, height, at.z - PILLAR_HALF),
                new Vec3(at.x + PILLAR_HALF, height, at.z - PILLAR_HALF),
                new Vec3(at.x + PILLAR_HALF, height, at.z + PILLAR_HALF),
                new Vec3(at.x - PILLAR_HALF, height, at.z + PILLAR_HALF),
                0.30F, 0.16F, 0.42F, 1.0F);
    }

    // ------------------------------------------------------------------
    //  ③ 柱顶那颗"太阳"（三片正交发光圆盘 + 一圈火环）
    // ------------------------------------------------------------------

    private static void drawSun(VertexConsumer out, Matrix4f pose, Vec3 at, double age, int index) {
        float pulse = (float) (0.92D + 0.08D * Math.sin(age * 0.22D + index));
        double r = 0.85D * pulse;
        // 外层暗红（三片正交 ⇒ 从任何角度看都是一团光 ✓）
        for (Vec3 axis : new Vec3[]{UP, AXIS_X, AXIS_Z}) {
            WaterGeometry.disk(out, pose, at, axis, r * 1.35D, 1.00F, 0.34F, 0.05F, 1.0F);
        }
        // 内层亮黄（"太阳"的感觉就靠这一层 ✓）
        for (Vec3 axis : new Vec3[]{UP, AXIS_X, AXIS_Z}) {
            WaterGeometry.disk(out, pose, at, axis, r * 0.72D, 1.00F, 0.86F, 0.32F, 1.0F);
        }
        // 绕着飞的火环（作者要"圆球附近有火焰粒子飞舞"✓ —— 几何环比真粒子更省 ✓）
        WaterGeometry.ring(out, pose, at, UP, r * 1.9D, 0.13D, age * 0.05D + index, 1.0F);
    }

    // ------------------------------------------------------------------
    //  ④ 激光：从圆球射向这一条线当前锁定的目标
    // ------------------------------------------------------------------

    private static void drawLaser(VertexConsumer out, Matrix4f pose, TNFlameDemonField field,
                                  Vec3 orb, int index) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        int id = field.orbTargetId(index);
        if (id < 0) {
            return;                                        // 这条线这一 tick 没锁到东西 ✓
        }
        Entity target = minecraft.level.getEntity(id);
        if (target == null) {
            return;
        }
        Vec3 to = target.getPosition(1.0F).add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                .subtract(field.position());
        // 外焰壳 + 白热芯：两层管，看着才像"激光"而不是一根橙棍 ✗
        WaterGeometry.tube(out, pose, orb, to, 0.17D, 1.00F, 0.30F, 0.05F, 1.0F);
        WaterGeometry.tube(out, pose, orb, to, 0.075D, 1.00F, 0.92F, 0.62F, 1.0F);
    }
}
