package com.tnc.tnc.magic.wind;

import com.tnc.tnc.magic.TNWindMechanics;
import com.tnc.tnc.magic.TNWindSpiritEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 风灵的一次出手：先<b>向内吸</b>，再放出 {@code shots(tier)} 把"风武器"✓
 *
 * <h2>2026-11-11 作者「风灵再改改」—— 出手方式按档位分开</h2>
 * <table border="1">
 *   <tr><th>档</th><th>t1 / t2</th><th>t3 / t4</th><th>t5</th></tr>
 *   <tr><td>每次打几个目标</td><td>1</td><td><b>2</b></td><td><b>3</b></td></tr>
 *   <tr><td>风环齐射</td><td>—</td><td>—</td><td>每 <b>5</b> 次出手一轮（<b>4</b> 连发）</td></tr>
 * </table>
 *
 * <p>多发时单发伤害按 {@code 1/√n} 分摊（见 {@link WindArsenalRules#shotDamageFactor(int)}）⇒
 * 三发总伤害是单发的 1.73 倍：升档有感觉、但不至于断层 ✓。
 *
 * <p>吸力原来"只有效果没有画面"✗；现在每个被吸的目标身上都会甩一串往风灵飞的云粒子
 * （{@link #inwardStreak}）⇒ 看得出"是这只风灵在拽" ✓。
 */
public final class WindArsenal {

    private static final Map<LivingEntity, List<WeakReference<WindWeaponEntity>>> OWNED = new WeakHashMap<>();

    private WindArsenal() {
    }

    public static double attackAge(TNWindSpiritEntity e, float partial) {
        return e.level().getGameTime() + partial - e.windAttackTime();
    }

    public static int form(TNWindSpiritEntity e) {
        return e.windWeaponForm();
    }

    public static Vec3 direction(TNWindSpiritEntity e) {
        return e.windAttackDirection();
    }

    public static WindWeaponEntity attack(TNWindSpiritEntity spirit, LivingEntity owner) {
        if (!(spirit.level() instanceof ServerLevel level) || !owner.isAlive() || owner.level() != level) {
            return null;
        }
        int tier = spirit.tier();
        double radius = WindArsenalRules.radius(tier);
        List<LivingEntity> enemies = level.getEntitiesOfClass(LivingEntity.class,
                spirit.getBoundingBox().inflate(radius),
                e -> e != owner && !(e instanceof Player) && e.isAlive() && !e.isSpectator()
                        && !e.isAlliedTo(owner));
        enemies.removeIf(e -> e.distanceToSqr(spirit) > radius * radius || !owner.hasLineOfSight(e));
        enemies.sort(Comparator.comparingDouble(e -> e.distanceToSqr(spirit)));
        if (enemies.isEmpty()) {
            return null;
        }

        Vec3 centre = spirit.position().add(0, 0.25, 0);
        // ① 吸：把附近敌人往风灵这边拽（最多 12 个）✓
        for (int i = 0; i < Math.min(12, enemies.size()); i++) {
            LivingEntity enemy = enemies.get(i);
            Vec3 delta = centre.subtract(enemy.getBoundingBox().getCenter());
            double force = (0.08 + tier * 0.012) * (1 - Math.min(1, delta.length() / radius));
            Vec3 motion = enemy.getDeltaMovement().add(delta.normalize().scale(force));
            if (motion.lengthSqr() > 0.55 * 0.55) {
                motion = motion.normalize().scale(0.55);
            }
            enemy.setDeltaMovement(motion);
            enemy.hurtMarked = true;
            inwardStreak(level, enemy, centre);
        }

        List<WeakReference<WindWeaponEntity>> mine = OWNED.computeIfAbsent(owner, k -> new ArrayList<>());
        mine.removeIf(ref -> {
            WindWeaponEntity weapon = ref.get();
            return weapon == null || !weapon.isAlive();
        });
        if (mine.size() >= WindArsenalRules.MAX_PROJECTILES) {
            return null;
        }

        long cycle = level.getGameTime() / Math.max(1, WindArsenalRules.zapInterval());
        boolean volley = WindArsenalRules.volleyEvery(tier) > 0
                && cycle % WindArsenalRules.volleyEvery(tier) == 0;
        int shots = WindArsenalRules.shots(tier);
        if (volley) {
            shots = Math.max(shots, WindArsenalRules.volleyShots(tier));
        }
        float damage = 4 * TNWindMechanics.damageMultiplierForTier(tier)
                * WindArsenalRules.shotDamageFactor(tier)
                * (volley ? 0.75F : 1.0F);

        List<LivingEntity> targets = pickTargets(enemies, shots);
        WindWeaponEntity first = null;
        int firstForm = 0;
        Vec3 firstAim = new Vec3(0, 0, 1);
        for (int i = 0; i < targets.size(); i++) {
            if (mine.size() >= WindArsenalRules.MAX_PROJECTILES) {
                break;
            }
            LivingEntity target = targets.get(i);
            Vec3 aim = target.getBoundingBox().getCenter().subtract(centre).normalize();
            if (aim.lengthSqr() < 0.01) {
                aim = new Vec3(0, 0, 1);
            }
            int form = WindArsenalRules.weapon(tier, spirit.slot() + i, cycle);
            WindWeaponEntity weapon = WindArsenalRegistration.WEAPON.create(level);
            if (weapon == null) {
                break;
            }
            Vec3 at = centre.add(aim.scale(0.6));
            weapon.configure(owner, at, aim, form, tier, damage);
            if (!level.addFreshEntity(weapon)) {
                break;
            }
            mine.add(new WeakReference<>(weapon));
            if (first == null) {
                first = weapon;
                firstForm = form;
                firstAim = aim;
            }
        }
        if (first == null) {
            return null;
        }
        spirit.markWindAttack(firstForm, firstAim);

        // ② 齐射那一轮：在风灵身上炸一圈风环 ⇒ "所有风灵一起出手"要看得出来 ✓
        if (volley) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                    spirit.getX(), spirit.getY() + 0.4D, spirit.getZ(), 6, 0.35D, 0.2D, 0.35D, 0.0D);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    spirit.getX(), spirit.getY() + 0.4D, spirit.getZ(), 10, 0.4D, 0.25D, 0.4D, 0.05D);
        }
        return first;
    }

    /**
     * 从候选里挑最多 {@code shots} 个<b>彼此拉开</b>的目标 ✓ ——
     * 不然几发会糊在同一只身上，看着像只打了一发 ✗。
     */
    private static List<LivingEntity> pickTargets(List<LivingEntity> enemies, int shots) {
        int want = Math.max(1, shots);
        List<LivingEntity> picked = new ArrayList<>(want);
        for (LivingEntity candidate : enemies) {
            boolean tooClose = false;
            for (LivingEntity chosen : picked) {
                if (chosen.distanceToSqr(candidate) < 1.5D * 1.5D) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose && enemies.size() > want) {
                continue;
            }
            picked.add(candidate);
            if (picked.size() >= want) {
                break;
            }
        }
        if (picked.isEmpty()) {
            picked.add(enemies.get(0));
        }
        return picked;
    }

    /** 被吸的目标身上甩一串往风灵飞的云粒（成本很低）✓。 */
    private static void inwardStreak(ServerLevel level, LivingEntity enemy, Vec3 centre) {
        Vec3 from = enemy.getBoundingBox().getCenter();
        Vec3 delta = centre.subtract(from);
        if (delta.lengthSqr() < 1.0E-4D) {
            return;
        }
        for (int k = 1; k <= 3; k++) {
            double t = k / 4.0D;
            Vec3 at = from.add(delta.scale(t));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    at.x, at.y, at.z, 1, 0.02D, 0.02D, 0.02D, 0.02D);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                from.x, from.y, from.z, 2, 0.1D, 0.1D, 0.1D, 0.0D);
    }
}
