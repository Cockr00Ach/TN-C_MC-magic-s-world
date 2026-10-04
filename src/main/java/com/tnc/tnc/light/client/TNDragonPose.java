package com.tnc.tnc.light.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.light.TNDragonEntity;

/**
 * ★ 光龙 / 暗龙共用的<b>朝向修正</b> ✓（作者 2026-10-04："黑龙和光龙都是倒着飞" ✗ ＋
 * "怎么模型一出来闪一下就消失了" ✗）。
 *
 * <h2>为什么"倒着飞"（根因，反汇编 {@code libs/geckolib-4.8.4.jar} 得到 ✓）</h2>
 * {@code GeoEntityRenderer.actuallyRender} 里是这样取朝向的：
 * <pre>
 *   LivingEntity living = animatable instanceof LivingEntity ? (LivingEntity) animatable : null;
 *   float yaw = living == null ? 0.0F : Mth.lerp(partialTick, living.yRotO, living.getYRot());
 *   applyRotations(animatable, poseStack, ageInTicks, yaw, partialTick);
 * </pre>
 * 而 {@code applyRotations} 里是 {@code poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw))} ✓。
 * 我们的龙是 {@code TNDragonEntity extends Entity} ✗（投射物，不是生物 ✓）⇒
 * <b>GeckoLib 读到的 yaw 恒为 0</b> ✗ ⇒ 模型被钉死在"绕 Y 转 180°"这一个姿态上 ✗，
 * 于是不管实体朝哪儿飞、模型永远盯着同一个方向 ⇒ 实机就是**倒着飞 / 横着飘** ✗。
 * 光龙和暗龙是**同一个实体类** ✓ ⇒ 两条一起中招 ✓。
 *
 * <h2>所以这里怎么补</h2>
 * 实体那边（{@link TNDragonEntity#renderYaw(float)} / {@link TNDragonEntity#renderPitch(float)}）
 * 已经把"这一帧模型该用的角度"算好了 ✓，这里只负责按这个顺序压进矩阵 ✓：
 * <ol>
 *   <li>绕 Y 转 <b>{@link #MODEL_YAW_OFFSET}（180°）</b> —— 把 GeckoLib 那一下抵消 ✓；
 *       （它默认是给"鼻子朝 +Z"的模型用的，我们这条东方龙的鼻子在 −Z ✓ ——
 *        这一点由 {@code tools/check_dragon_charge_facing.py} 从 geo 里现量 ✓）；</li>
 *   <li>再绕 Y 转 <b>{@code renderYaw}</b> —— 真正的飞行方向 ✓
 *       （{@code renderYaw = 180 − yRot} ✓，整条推导在那个方法上 ✓，改之前先跑那个脚本 ✓）；</li>
 *   <li>最后绕 X 转 <b>{@code -renderPitch}</b> —— GeckoLib 的 {@code applyRotations}
 *       <b>完全不处理俯仰</b> ✗（只管 Y + 死亡/睡觉 ✓），而 MC 的 {@code xRot} 是"低头为正"、
 *       模型里是"抬头为正" ✓ ⇒ 取负号 ✓，t3/t4/t5 朝准星俯冲时才真的"跟着准星点头" ✓。</li>
 * </ol>
 *
 * <p>★ 这个 180 / 符号组合是 {@code tools/check_dragon_charge_facing.py} 把
 * 东南西北 × 俯仰 −45…+45 全跑通 4/4 之后才写死的 ✓ —— 这种地方"看着差不多"就是反 180° ✗
 * （作者报的"倒着飞"正是这么来的 ✓），要动就先跑那个脚本 ✓。
 *
 * <p>★ 千万别顺手去改实体的 {@code yRot} 来"看着对" ✗ —— 它是公开状态
 * （存档 / 别的实体读它 / 以后的寻路都用 ✓），为了迁就一个渲染库的默认值去污染它，
 * 以后一定有人再踩一次 ✓（天使那次就是栽在这上面 ✓）。
 *
 * <p>这个类是给<b>两个</b>渲染器调用的 ✓（{@code TNDragonRenderer} 和
 * {@code dark/client/TNDarkDragonRenderer} ✓）—— 抄成两份迟早改岔 ✗。
 */
public final class TNDragonPose {

    /** GeckoLib 给非生物实体硬塞的那个角度 ✗（{@code 180 - 0} ✓）—— 所以先原样补一个 180° 抵消掉 ✓。 */
    public static final float MODEL_YAW_OFFSET = 180.0F;

    /**
     * ★ 排查开关 ✓：加 JVM 参数 {@code -Dtnc.dragon.debug=true} 启动游戏后，
     * 每条龙**前 5 秒**每 20 tick 会往日志里打一行自己的位置 / 朝向 / 距离 / 还在不在 ✓。
     *
     * <p>为什么留这个：作者报的"闪一下就消失"✗ 和"倒着飞"✗ 都**只能在实机里看** ✓，
     * 而这两件事的成因（非生物实体 yaw 恒为 0 / 大模型被视锥剔掉 / 出生点在地形里自爆 ✗）
     * 光看代码互相长得一模一样 ✗。开着这个开关跑一次就能一眼分出来是**没渲染**还是**已经被 discard** ✓。
     * 默认关闭 ✓、一行日志而已 ✓。
     */
    private static final boolean DEBUG = Boolean.getBoolean("tnc.dragon.debug");

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/dragon");

    private TNDragonPose() {
    }

    /**
     * 把龙的模型摆正 ✓（在 GeckoLib 那一下之后**再**补，所以是"抵消 + 重新定向"✓）。
     *
     * @param partialTick 用来插值角度 ✓ —— 不然每 tick 朝向会一跳一跳 ✗
     */
    public static void fixFacing(PoseStack poseStack, TNDragonEntity animatable, float partialTick) {
        poseStack.mulPose(Axis.YP.rotationDegrees(MODEL_YAW_OFFSET));
        poseStack.mulPose(Axis.YP.rotationDegrees(animatable.renderYaw(partialTick)));
        poseStack.mulPose(Axis.XP.rotationDegrees(-animatable.renderPitch(partialTick)));
        debug(animatable);
    }

    /** {@code -Dtnc.dragon.debug=true} 才打 ✓（见 {@link #DEBUG} ✓）。 */
    private static void debug(TNDragonEntity animatable) {
        if (!DEBUG || animatable.tickCount > 100 || animatable.tickCount % 20 != 0) {
            return;
        }
        var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        LOGGER.info("TN-C/dragon(render): t={} pos=({},{},{}) yaw={} pitch={} scale={} camDist={} removed={}",
                animatable.tickCount,
                String.format(java.util.Locale.ROOT, "%.2f", animatable.getX()),
                String.format(java.util.Locale.ROOT, "%.2f", animatable.getY()),
                String.format(java.util.Locale.ROOT, "%.2f", animatable.getZ()),
                String.format(java.util.Locale.ROOT, "%.1f", animatable.renderYaw(0.0F)),
                String.format(java.util.Locale.ROOT, "%.1f", animatable.getXRot()),
                String.format(java.util.Locale.ROOT, "%.2f", animatable.scale()),
                String.format(java.util.Locale.ROOT, "%.1f",
                        Math.sqrt(animatable.distanceToSqr(camera.getPosition()))),
                animatable.isRemoved());
    }
}
