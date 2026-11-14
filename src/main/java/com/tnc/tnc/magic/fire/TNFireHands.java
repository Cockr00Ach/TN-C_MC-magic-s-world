package com.tnc.tnc.magic.fire;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * 火手链的入口：从敌人脚下伸出一只燃烧的手，抓住、捏爆。
 *
 * <p>约定和其它火系链一样：<b>不是本链的法术返回 false</b>，派发处可以无脑全调一遍 ✓
 *
 * <h2>为什么手从"地面"出来而不是从目标脚下 3 格</h2>
 * 手是从地里长出来的东西，**必须接在地面上**才不会像浮空贴图；
 * 目标在天上时也一样 —— 手在地面，只是手臂会一路烧上天（见 {@code FireHandRenderer#pillar}）。
 */
public final class TNFireHands {

    /** 五个档：id → 档位（伤害/大小/火柱都由这个数推）。 */
    private static final Map<String, Integer> TIERS = Map.of(
            "flame_grip", 1,
            "blaze_clutch", 2,
            "molten_crush", 3,
            "inferno_clench", 4,
            "pyre_god_hand", 5);

    /** 索敌距离（格）。 */
    private static final double RANGE = 42.0D;

    private TNFireHands() {
    }

    public static int tierOf(ResourceLocation spellId) {
        return TIERS.getOrDefault(spellId.getPath(), 0);
    }

    /** 派发入口。不是本链返回 false。 */
    public static boolean cast(ServerPlayer player, ResourceLocation spellId) {
        int tier = tierOf(spellId);
        if (tier <= 0 || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        LivingEntity target = aim(player, RANGE);
        boolean aerial = target != null && !target.onGround() && target.getY() > player.getY() + 2.0D;

        Vec3 under;
        if (target != null) {
            under = groundUnder(level, target.position());
        } else {
            under = groundUnder(level, player.position().add(player.getLookAngle().scale(6.0D)));
        }

        float heat = target != null ? FireHeat.of(target) : 0.0F;
        TNFlameHandEntity.spawn(level, player, target, under, tier, heat, aerial);
        // 起手先给一小把热量：让"抓—捏—爆"这条链自己也参与热量循环
        if (target != null) {
            FireHeat.add(target, player, 6.0F * tier);
        }
        return true;
    }

    /** 沿视线找最近的生物；没有就打方块落点（手照样出来，只是抓空气）。 */
    private static LivingEntity aim(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(eye, end).inflate(1.4D), t -> t != player && FireSpellRules.hittable(player, t))) {
            var hit = e.getBoundingBox().inflate(0.4D).clip(eye, end);
            if (hit.isEmpty()) {
                continue;
            }
            double d = hit.get().distanceTo(eye);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /** 从某个点往下找地面；找不到就退到那个点下方 1.5 格。 */
    public static Vec3 groundUnder(ServerLevel level, Vec3 at) {
        BlockHitResult hit = level.clip(new ClipContext(at.add(0, 4, 0), at.add(0, -12, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            return new Vec3(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
        }
        return new Vec3(at.x, at.y - 1.5D, at.z);
    }
}
