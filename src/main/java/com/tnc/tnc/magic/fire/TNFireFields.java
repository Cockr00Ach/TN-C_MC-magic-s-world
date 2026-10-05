package com.tnc.tnc.magic.fire;

import com.tnc.tnc.TNMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * 火球链里<b>不是"朝准星扔火球"</b>的那两档的入口：熔岳天倾（t4）与炎葬（t5）。
 *
 * <p>为什么单独一个类：{@code TNFireBoltEntity.cast} 只处理"扔出去的火球"，
 * 而这两档是<b>法阵</b>（一个落在选定目标头顶、一个以自己为中心），形态完全不同。
 * 约定和别处一样：<b>不是本链的法术返回 false</b>，派发处可以无脑全调一遍 ✓
 */
public final class TNFireFields {

    private TNFireFields() {
    }

    /**
     * @return 真的放出去了才 true
     */
    public static boolean cast(ServerPlayer player, ResourceLocation spellId) {
        if (player == null || spellId == null || !spellId.getNamespace().equals(TNMod.MODID)) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        switch (spellId.getPath()) {
            case "molten_skyfall" -> {
                // 作者 2026-10-05：法阵要落在**选定目标**头上，而且**生成后固定不动**
                TNSkyfallEntity.cast(level, player, skyfallAnchor(player));
                return true;
            }
            case "meteor_fall" -> {
                // 作者 2026-10-05：地上先张开一座巨大法阵，随后天上一颗大陨石砸下来。
                // 法阵钉在**目标脚下的地面**上，不跟着谁走 ✓
                TNMeteorFallEntity.cast(level, player, groundAnchor(player));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * 熔岳天倾的法阵该落在哪 —— 作者 2026-10-05：「在<b>选定目标头上</b>生成」。
     *
     * <p>找目标的顺序（都是"看着谁就打谁"的直觉）：
     * <ol>
     *   <li>沿视线做<b>实体扫掠</b>（线段 × 包围盒，取最近的一个）→ 落在它头顶：
     *       最常用的情形 ✓</li>
     *   <li>没打到实体 → 看<b>方块落点</b>：眼睛瞄哪块地就落哪块地上方</li>
     *   <li>连方块都没有（对着天空）→ 退回<b>自己头顶</b>，
     *       免得"放了但什么都没发生" ✗</li>
     * </ol>
     *
     * <p>找到之后统一抬高 {@link FireSpellRules#SKYFALL_HEIGHT} 格 —— 法阵是在"头上"。
     * 法阵本身<b>不会再移动</b>（位置只在 {@code TNSkyfallEntity.cast} 时定一次）✓
     */
    private static Vec3 skyfallAnchor(ServerPlayer player) {
        return aimPoint(player, FireSpellRules.SKYFALL_CAST_RANGE);
    }

    /**
     * 陨星坠（t5）的瞄准点 —— 和熔岳天倾同一套选点，只是射程更大。
     *
     * <p>⚠️ 作者 2026-10-05 要求「保持高档强于低档，不要出现低档高于高档」✗，
     * 而 t5 原来射程 24 比 t4 的 64 还近 ✗ —— 所以分开成两个常量 ✓
     */
    private static Vec3 groundAim(ServerPlayer player) {
        return aimPoint(player, FireSpellRules.METEOR_CAST_RANGE);
    }

    /** 共用的选点逻辑：沿视线 实体 → 方块 → 自己（见上面那条顺序说明）。 */
    private static Vec3 aimPoint(ServerPlayer player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(reach));

        // 1) 沿视线找最近的实体（和火球用同一套"线段 × 包围盒"扫掠，不自己另发明）
        LivingEntity hit = null;
        double closest = reach * reach;
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(eye, end).inflate(1.0D), t -> FireSpellRules.hittable(player, t))) {
            Optional<Vec3> point = target.getBoundingBox().inflate(0.3D).clip(eye, end);
            if (point.isPresent() && point.get().distanceToSqr(eye) < closest) {
                closest = point.get().distanceToSqr(eye);
                hit = target;
            }
        }
        if (hit != null) {
            return hit.position().add(0.0D, FireSpellRules.SKYFALL_HEIGHT, 0.0D);
        }

        // 2) 没打到实体：看准星打在哪个方块上
        BlockHitResult block = player.level().clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            return block.getLocation().add(0.0D, FireSpellRules.SKYFALL_HEIGHT, 0.0D);
        }

        // 3) 对着天空：退回自己头顶
        return player.position().add(0.0D, FireSpellRules.SKYFALL_HEIGHT, 0.0D);
    }

    /**
     * 陨星坠的法阵该铺在哪 —— 作者 2026-10-05：「地上出现一个巨大的法阵」。
     *
     * <p>和 {@link #skyfallAnchor} 的区别：那座在"<b>头上</b>"，这座要铺在<b>地面</b>上。
     * 所以先复用 {@code skyfallAnchor} 找出"你瞄的是哪"，再<b>往下找一层地板</b>，
     * 让法阵平平地贴地 ✓（往下最多找 8 格，找不到就用瞄准点本身）
     */
    private static Vec3 groundAnchor(ServerPlayer player) {
        // ⚠️ groundAim 给的是「头顶」（已经 +SKYFALL_HEIGHT）—— 必须先减回来再往下找地板 ✗
        //    作者 2026-10-05 实测「为什么 t5 的法阵会生成在目标上方」就是这个：
        //    以前只往下找 8 格，而高度是 12 ⇒ 净效果停在目标上方 4 格 ✗
        Vec3 aim = groundAim(player).subtract(0.0D, FireSpellRules.SKYFALL_HEIGHT, 0.0D);
        // 往下找地板给足 32 格（法阵本来就该铺在地上，多找一点更稳 ✓）
        BlockHitResult floor = player.level().clip(new ClipContext(aim,
                aim.subtract(0.0D, 32.0D, 0.0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 at = floor.getType() == HitResult.Type.MISS ? aim : floor.getLocation();
        return new Vec3(at.x, at.y + 0.06D, at.z);
    }

    /** 这个法术是不是由本类负责（命令/自检用）。 */
    public static boolean handles(String path) {
        return "molten_skyfall".equals(path) || "meteor_fall".equals(path);
    }
}
