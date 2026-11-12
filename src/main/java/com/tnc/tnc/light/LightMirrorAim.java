package com.tnc.tnc.light;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 「光镜要能瞄准」✓ —— 作者 2026-11-07：
 * <i>"那个镜子要把它弄成可以瞄准的东西嘞，不然光线 t4t5 砸不到它身上"</i>。
 *
 * <h2>为什么原来砸不到</h2>
 * 分光这件事本身是通的 ✓：光柱每 tick 都会用 {@link LightMirrorRules#intersects} 对光镜的
 * {@link TNLightMirrorEntity#opticalBounds()} 做几何判定 ✓（t1~t3 从眼睛直射，只要准星对上就一定穿过 ✓）。
 * 砸不到的是 **t4/t5**：它们从天上往下落 ✗，落点是 {@code aimPoint()}（准星打到的方块面 / 高度图）
 * ⇒ 浮在空中的光镜**不在那条竖线上** ✗，柱子从旁边一米落下去 ✗。
 *
 * <h2>现在怎么瞄</h2>
 * 施法时先按准星挑一面**自己的、活着的**光镜 ✓：
 * <ol>
 *   <li>视线锥（{@link #COS_LIMIT} ≈ 20° ✓）内的才算"瞄着" ✓；</li>
 *   <li>用 {@link TNLightMirrorEntity#opticalBounds()} 外扩 {@link #PADDING} 当靶子 ✓
 *       （光镜是一块薄片子，按 1.8×2.4×1.8 的"光路盒子"算才瞄得舒服 ✓ ——
 *       光柱分光用的也是这个盒子 ✓，瞄准和判定用同一个盒子才不会"看着瞄上了却没分光" ✗）；</li>
 *   <li>沿视线**最先碰到**的那面 ✓（挡在后面的不算 ✗）；</li>
 *   <li>再补一次方块遮挡 ✓（中间有墙就不该穿墙瞄 ✓）。</li>
 * </ol>
 * 挑到了 ⇒ {@code castSky} 把天阵开在**光镜正上方**、只落**一根**柱子（t5 本来会撒五根 ✓ ——
 * 五根柱子各分六道会把场面糊掉 ✗）。
 */
public final class LightMirrorAim {

    /** 瞄光镜的距离（格 ✓）；和光线 t4/t5 的 reach 一个量级 ✓。 */
    public static final double REACH = 48.0D;

    /** 视线锥：准星两侧各约 20° 都算"瞄着它" ✓（光镜是薄片，锥太小会很难对上 ✗）。 */
    public static final double COS_LIMIT = Math.cos(Math.toRadians(20.0D));

    /** 靶子盒子外扩（格 ✓）：让"大概对着"也算中 ✓。 */
    public static final double PADDING = 0.45D;

    private LightMirrorAim() {
    }

    /**
     * 玩家准星瞄着的那面光镜 ✓；没有就 {@code null} ✓。
     *
     * <p>只认**自己的**镜子 ✓ —— 别人的镜子不该被你的天阵点亮 ✗（分光那侧也是这么判的 ✓）。
     */
    public static TNLightMirrorEntity pick(Level level, LivingEntity caster) {
        if (level == null || caster == null) {
            return null;
        }
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        List<TNLightMirrorEntity> owned = new ArrayList<>();
        List<AABB> boxes = new ArrayList<>();
        for (TNLightMirrorEntity mirror : level.getEntitiesOfClass(TNLightMirrorEntity.class,
                caster.getBoundingBox().inflate(REACH))) {
            if (!mirror.isAlive() || !mirror.belongsTo(caster)) {
                continue;
            }
            owned.add(mirror);
            boxes.add(mirror.opticalBounds());
        }
        int index = nearest(eye, look, boxes, REACH, COS_LIMIT, PADDING);
        if (index < 0) {
            return null;
        }
        TNLightMirrorEntity mirror = owned.get(index);
        // 方块遮挡：眼睛到镜子中心的直线上有碰撞体就不算瞄准 ✓（隔着墙瞄不到 ✓）
        BlockHitResult blocked = level.clip(new ClipContext(eye, mirror.center(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (blocked.getType() != HitResult.Type.MISS
                && blocked.getLocation().distanceToSqr(eye) < eye.distanceToSqr(mirror.center()) - 0.25D) {
            return null;
        }
        return mirror;
    }

    /**
     * 纯几何：沿视线最先碰到的那个盒子 ✓（{@code -1} = 一个都没碰到 ✓）。
     *
     * <p>只碰 {@link AABB} / {@link Vec3} ⇒ **单测能直接跑** ✓（不用起一个 Level ✓）。
     *
     * @param eye     眼睛位置
     * @param look    视线方向（不必归一化 ✓）
     * @param boxes   候选靶子（模型坐标系 ✓，会被 {@code padding} 外扩 ✓）
     * @param reach   最远距离（格 ✓）
     * @param cosLimit 视线锥的余弦阈值（1 = 必须正对 ✓）
     * @param padding 靶子外扩（格 ✓）
     */
    public static int nearest(Vec3 eye, Vec3 look, List<AABB> boxes, double reach, double cosLimit, double padding) {
        if (eye == null || look == null || boxes == null || boxes.isEmpty()) {
            return -1;
        }
        double length = look.length();
        if (length < 1.0E-6D) {
            return -1;
        }
        Vec3 unit = look.scale(1.0D / length);
        Vec3 end = eye.add(unit.scale(reach));
        double bestDistance = Double.MAX_VALUE;
        int best = -1;
        for (int i = 0; i < boxes.size(); i++) {
            AABB box = boxes.get(i);
            if (box == null) {
                continue;
            }
            AABB padded = box.inflate(padding);
            Vec3 toCenter = padded.getCenter().subtract(eye);
            double distance = toCenter.length();
            if (distance > reach + padded.getSize()) {
                continue;
            }
            if (distance > 1.0E-6D && unit.dot(toCenter.scale(1.0D / distance)) < cosLimit) {
                continue;
            }
            Optional<Vec3> hit = padded.clip(eye, end);
            if (hit.isEmpty()) {
                continue;
            }
            double along = hit.get().distanceTo(eye);
            if (along < bestDistance) {
                bestDistance = along;
                best = i;
            }
        }
        return best;
    }
}
