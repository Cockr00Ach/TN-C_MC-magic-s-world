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

import java.util.List;
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
     * <b>索敌的"瞄准锥"（度 ✓）</b> —— 精确射线打不中时，在这个锥里找最贴准心的生物 ✓。
     *
     * <p>对齐光柱那套索敌的 35° 锥 ✓（作者 2026-10-01 定下来的手感 ✓）
     */
    private static final double AIM_CONE_DEGREES = 35.0D;

    /**
     * @return 真的放出去了才 true
     */
    public static boolean cast(ServerPlayer player, ResourceLocation spellId) {
        if (player == null || spellId == null || !spellId.getNamespace().equals(TNMod.MODID)) {
            return false;
        }
        String path = spellId.getPath();
        // t3 火龙术不是光柱（它会飞出去 ✓）⇒ 单独走投射物那条路 ✓
        if ("fire_dragon".equals(path)) {
            return castDragon(player, path);
        }
        // t4 炎魔龙之怒：目标脚下的巨阵（不是光柱 ✓）
        if ("flame_demon_wrath".equals(path)) {
            return castFlameDemon(player);
        }
        // t5 太阳の审判：目标下方的巨阵 + 正上方的太阳光球 ✓
        if ("solar_judgment".equals(path)) {
            return castSolarJudgment(player);
        }
        FireSpellRules.Ray spec = FireSpellRules.ray(path);
        if (spec == null) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }

        // ⚠️ 作者 2026-10-05 第 2 条：「t1 和 t2 中的射线是一段长度有限的线条，
        //    你可以理解为是长条状的火球」✗ ⇒ **不再用光柱** ✗
        //    （光柱是贴在施法者眼睛上的一条光 ⇒ 看不出"被射出去" ✗）
        //    改成会飞出去的**条状投射物** ✓：从手前出发、穿透生物、撞方块停下 ✓
        LivingEntity target = acquire(player, spec.range());
        FireSpellRules.Bolt bolt = new FireSpellRules.Bolt(
                spec.coefficient(), spec.range(), spec.radius(), 1,
                spec.scorchLevel() >= 2, false,
                "blast_ray".equals(path) ? FireSpellRules.RAY_BLAST_RADIUS : 0.0D, 0.0F);
        Vec3 look = player.getLookAngle().normalize();
        // ★ 出手点改到**手前**（作者说"看不出射线被射出"✗ —— 从眼睛里射当然看不出 ✗）
        Vec3 from = player.getEyePosition().add(look.scale(1.45D)).subtract(0.0D, 0.30D, 0.0D);
        TNFireBoltEntity shot = new TNFireBoltEntity(TNOrbEntities.FIRE_BOLT.get(), level);
        shot.configure(player, bolt, path, from, look.scale(TNFireBoltEntity.LAUNCH_SPEED));
        if (target != null) {
            shot.lockTarget(target);                 // ★ 索敌 ✓
        }
        level.addFreshEntity(shot);
        return true;
    }

    /**
     * t3 <b>火龙术</b>：在面前生成一颗朝准心的龙头，然后冲出去 ✓
     *
     * <p>「面前的法阵 + 龙头逐渐成形」交给 {@code TNFireBoltEntity} 的**成形阶段**表现 ✓
     * （作者要的是"前摇结束时冲出去"✗ —— 引擎前摇期间我们没有钩子 ✗，
     * 所以把这 15 tick 的成形放在释放之后 ✓，观感一致 ✓）
     */
    private static boolean castDragon(ServerPlayer player, String path) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        LivingEntity target = acquire(player, FireSpellRules.DRAGON_BOLT.range());
        Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(1.2D))
                .subtract(0.0D, 0.25D, 0.0D);
        Vec3 velocity = player.getLookAngle().normalize()
                .scale(TNFireBoltEntity.LAUNCH_SPEED);
        TNFireBoltEntity dragon = new TNFireBoltEntity(TNOrbEntities.FIRE_BOLT.get(), level);
        dragon.configure(player, FireSpellRules.DRAGON_BOLT, "fire_dragon", from, velocity);
        if (target != null) {
            // 索敌：先朝目标出手 ✓，之后每 tick 还会**限速拐**过去 ✓（见实体里的追踪 ✓）
            dragon.setDeltaMovement(target.getEyePosition().subtract(from).normalize()
                    .scale(TNFireBoltEntity.LAUNCH_SPEED));
            dragon.lockTarget(target);
        }
        level.addFreshEntity(dragon);
        return true;
    }

    /**
     * t4 <b>炎魔龙之怒</b>：在目标脚下放一个 8 秒的巨阵 ✓
     *
     * <p>落点：优先落在**锁定目标脚下** ✓；没锁到就落在准星指到的地面 ✓
     * （和熔岳天倾同一个思路 —— 作者要的是"目标脚下" ✗，不是"在自己脚下" ✗）
     */
    private static boolean castFlameDemon(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        LivingEntity target = acquire(player, FireSpellRules.T4_FIELD_RADIUS + 22.0D);
        Vec3 at = target != null
                ? new Vec3(target.getX(), target.getY(), target.getZ())
                : FireSpellRules.groundBelow(player, FireSpellRules.T4_FIELD_RADIUS + 22.0D);
        float beam = FireSpellRules.power(player) * BASE_T4_COEFFICIENT * FireSpellRules.BASE_DAMAGE
                / FireSpellRules.T4_BEAM_DIVISOR;
        TNFlameDemonField.spawn(level, player, FireSpellRules.T4_FIELD_RADIUS,
                FireSpellRules.T4_FIELD_TICKS, beam, FireSpellRules.SHAKE_T4_RAY, at);
        return true;
    }

    /**
     * t5 <b>太阳の审判</b>：目标下方的 10 秒巨阵 + 正上方太阳光球 ✓
     *
     * <p>⚠️ 收场会**真的**破坏方块（直径 32、除基岩外 ✓）—— 作者 2026-10-05 明确要求 ✓
     */
    private static boolean castSolarJudgment(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        LivingEntity target = acquire(player, FireSpellRules.T5_FIELD_RADIUS + 22.0D);
        Vec3 at = target != null
                ? new Vec3(target.getX(), target.getY(), target.getZ())
                : FireSpellRules.groundBelow(player, FireSpellRules.T5_FIELD_RADIUS + 22.0D);
        float total = FireSpellRules.power(player) * FireSpellRules.T5_COEFFICIENT
                * FireSpellRules.BASE_DAMAGE;
        TNSolarJudgmentField.spawn(level, player, FireSpellRules.T5_FIELD_RADIUS,
                FireSpellRules.T5_FIELD_TICKS,
                total / FireSpellRules.T5_BEAM_DIVISOR,     // 单次光线 ✓
                total,                                       // 收场爆炸（和单次光线"等同"的口径见类注释 ✓）
                FireSpellRules.SHAKE_T5_RAY, at);
        return true;
    }

    /** t4 的伤害系数（和 id 表分开写，免得改一处漏一处 ✗）。 */
    private static final float BASE_T4_COEFFICIENT = 5.0F;

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
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = eye.add(look.scale(range + ACQUIRE_SLACK));
        List<LivingEntity> near = player.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(eye, end).inflate(1.0D), t -> FireSpellRules.hittable(player, t));
        if (near.isEmpty()) {
            return null;
        }
        // ① 先按引擎那套：**沿视线精确射线**，取最近的命中 ✓
        LivingEntity exact = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity candidate : near) {
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.3D).clip(eye, end);
            if (hit.isPresent() && hit.get().distanceToSqr(eye) < bestDist) {
                bestDist = hit.get().distanceToSqr(eye);
                exact = candidate;
            }
        }
        if (exact != null) {
            return exact;
        }
        // ⚠️ 作者 2026-10-05：「把锥形追踪删了」✓
        //    原来这里还有一层 35° 瞄准锥兜底 ✗ —— 它会让"瞄偏一点也能锁上"✓，
        //    但也会出现"锁上了旁边的怪、射线拐过去"✗ 的观感 ✗
        //    ⇒ 删掉 ✓，现在**只认准心那条线** ✓（和引擎的 CURSOR 同一个判据 ✓）
        return null;
    }
}