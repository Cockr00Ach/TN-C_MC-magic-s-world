package com.tnc.tnc.magic.fire;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.light.TNLightBeamEntity;
import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * <b>火系「射线链」的入口</b>（作者 2026-10-05 的全新设计，顶替旧的 {@code *_fire_ray}）。
 *
 * <h2>为什么走 Java 派发，而不是引擎的 SPAWN</h2>
 * <p>射线需要带上「形态（火焰 ✓）」「焚身等级（t1~t3 = I、t4~t5 = II ✓）」这些参数 ✗，
 * 而引擎的 SPAWN 动作<b>只能指定实体类型、带不了参数</b> ✗
 * （{@code TNShockwaveEntity} 的注释里也记过这个限制 ✓）。
 * 所以火系射线由这里自己 spawn ✓ —— 和火球链同一个约定：
 * <b>不是本链的法术返回 false</b>，派发处可以无脑全调一遍 ✓。
 *
 * <h2>索敌</h2>
 * <p>作者 2026-10-05：「就在施法时加一个索敌」✓ ——
 * 这里在**施法瞬间**沿视线找最近的、火系能打中的生物 ✓；
 * 之后射线自己每 tick 限速拐向它（光柱本来就有这套 ✓，所以"看得见在追"✓）。
 */
public final class TNFireRays {

    private TNFireRays() {
    }

    /** 射线存在多久（tick）—— 十来 tick 够看清"一条射线打过去" ✓。 */
    private static final int RAY_LIFE_TICKS = 12;

    /** 索敌距离的额外余量（格）—— 比射程多找一点，免得差半格就锁不到 ✓。 */
    private static final double ACQUIRE_SLACK = 1.5D;

    /**
     * @return 真的放出去了才 true
     */
    public static boolean cast(ServerPlayer player, ResourceLocation spellId) {
        if (player == null || spellId == null || !spellId.getNamespace().equals(TNMod.MODID)) {
            return false;
        }
        String path = spellId.getPath();
        FireSpellRules.Ray spec = FireSpellRules.ray(path);
        if (spec == null) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }

        LivingEntity target = acquire(player, spec.range());
        float damage = FireSpellRules.rayDamage(spec, FireSpellRules.power(player));

        TNLightBeamEntity beam = new TNLightBeamEntity(TNOrbEntities.LIGHT_BEAM.get(), level);
        // 出生朝向 = 视线方向 ✓（configure 故意不立刻对准目标 ⇒ 之后每 tick 拐过去，看得见索敌 ✓）
        beam.setYRot(player.getYRot());
        beam.setXRot(player.getXRot());
        beam.setPos(player.getEyePosition());
        beam.configure(player, spec.radius(), spec.range(), damage,
                RAY_LIFE_TICKS, TNLightBeamEntity.STYLE_FIRE, target);
        // 火焰形态：焚身等级 + t2 命中额外小范围爆炸 ✓
        beam.configureFire(spec.scorchLevel(), "blast_ray".equals(path));
        level.addFreshEntity(beam);
        return true;
    }

    /** 这个法术是不是由本类负责（命令/自检用）。 */
    public static boolean handles(String path) {
        return FireSpellRules.isRay(path);
    }

    /**
     * 施法瞬间的<b>索敌</b>：沿视线找最近的、火系能打中的生物 ✓（没有就 null ✓）。
     *
     * <p>判据和火球/射线完全一致（同一个 {@link FireSpellRules#hittable}）✓
     * ⇒ 锁到谁就能打到谁 ✓
     */
    private static LivingEntity acquire(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range + ACQUIRE_SLACK));
        LivingEntity best = null;
        double closest = Double.MAX_VALUE;
        for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(eye, end).inflate(1.0D), t -> FireSpellRules.hittable(player, t))) {
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.3D).clip(eye, end);
            if (hit.isPresent() && hit.get().distanceToSqr(eye) < closest) {
                closest = hit.get().distanceToSqr(eye);
                best = candidate;
            }
        }
        return best;
    }
}