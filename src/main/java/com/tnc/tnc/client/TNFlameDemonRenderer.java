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
    private static final double PILLAR_HALF = 0.50D;   // 作者：柱子"从一个方块改为 4 个块"✗ ⇒ 截面 1×1 ✓

    /** 柱高（格 ✓）—— 作者要 4 个块 ✗。 */
    private static final double PILLAR_HEIGHT = 6.0D;   // 作者：柱高 6 格 ✗

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

        // ★ 2026-10-05：作者「t4/t5 中的法阵有问题，不好修就把火球链的法阵拿来用」✓
        //   ⇒ 直接**复用火球链那座**（TNSigilRenderer，照作者参考图画的那版 ✓）
        //     自己手搓的那套 drawSigil 已删除 ✗（两套法阵叠在一起就是"有问题"的来源 ✗）
        // 出场渐显（照 TNSigilRenderer 的写法 ✓，免得"啪"地凭空出现 ✗）
        float fade = (float) Math.min(1.0D, (field.tickCount + partial) / 8.0D);
        TNSigilRenderer.drawSigil(out, pose, Vec3.ZERO, 15.0D, true, age, fade);  // 作者：t4 法阵 15 格 ✗（伤害域 = 它 ✓）
        for (int i = 0; i < 4; i++) {
            // ⚠️ orbPosition() 给的是**世界坐标** ✓ ⇒ 减掉实体位置换成相对坐标 ✗
            Vec3 rel = field.orbPosition(i).subtract(field.position());
            drawPillar(stack, buffers, rel, age);
            // ⚠️ 作者 2026-10-05：「柱顶的光球不要用光滑的几何体，用粒子球」✓
            //   ⇒ 那 3 片正交圆盘**已删除** ✗；光球改由 TNFlameDemonField.tick()
            //      每 tick 在 orbPosition 处撒**染色粒子球** ✓（服务端发 ⇒ 所有玩家可见 ✓）
            // （这条光线现在由 TNFlameDemonField 每 tick 撒**粒子流** ✓，渲染器不再画管 ✗）
        }
    }

    // ------------------------------------------------------------------
    //  ① 地上那个大法阵（由外到里"描绘"）
    // ------------------------------------------------------------------


    // ------------------------------------------------------------------
    //  ② 黑曜石巨柱（纯视觉）
    // ------------------------------------------------------------------

    private static void drawPillar(PoseStack stack, MultiBufferSource buffers, Vec3 at, double age) {
        // ⚠️ 作者 2026-10-05：「柱子高度 6 格，截面改成 2x2，并且**给柱子加上黑曜石的材质**」✓
        //   ⇒ 不再用"自画四边形 + 单色"✗，改成**直接渲染原版黑曜石方块** ✓：
        //     贴图、光照、明暗全是原版的 ✓（用 renderSingleBlock ✓）
        //   ⚠️ 为什么这次 renderSingleBlock 能行 ✗：黑曜石是**普通方块**✓，
        //     有真正的方块模型 ✓；上次那个龙头骷髅是 BlockEntityRenderer 画的 ✗，
        //     方块模型是空的 ⇒ 什么都画不出来 ✓
        //   堆法：3 块（每块缩放到 2×2×2 ✓）⇒ 合起来正好 **2×2×6 格** ✓
        double rise = Math.min(1.0D, age / 12.0D);      // 柱脚从地里"升起来"（前 12 tick ✓）
        if (rise <= 0.02D) {
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.world.level.block.state.BlockState obsidian =
                net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState();
        // 半宽 1 格 ⇒ 截面 2×2 ✓（缩放在下面做 ✓）
        // ⚠️ 作者 2026-10-05：「柱高 **10**」✓ ⇒ 摞 **5 块 × 每块 2 格** = 10 格 ✓
        //    （用 5×2 而不是 3×3.33 ：竖直方向不拉伸材质 ✓，黑曜石纹理保持原样 ✓）
        for (int k = 0; k < 5; k++) {
            stack.pushPose();
            // 从地面往上摞：第 k 块的中心高度 = k × 2 × rise ✓
            stack.translate(at.x - 1.0D, k * 2.0D * rise, at.z - 1.0D);
            // 水平 2 倍 ⇒ 2 格宽 ✓；竖直 2 倍再乘 rise ⇒ 从地里长出来 ✓
            stack.scale(2.0F, (float) (2.0D * rise), 2.0F);
            mc.getBlockRenderer().renderSingleBlock(obsidian, stack, buffers,
                    net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            stack.popPose();
        }
    }

    // ------------------------------------------------------------------
    //  ③ 柱顶那颗"太阳"（三片正交发光圆盘 + 一圈火环）
    // ------------------------------------------------------------------


    // ------------------------------------------------------------------
    //  ④ 激光：从圆球射向这一条线当前锁定的目标
    // ------------------------------------------------------------------

}
