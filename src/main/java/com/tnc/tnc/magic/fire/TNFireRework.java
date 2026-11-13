package com.tnc.tnc.magic.fire;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNShockwaveEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 火法重做：<b>点火链</b>（攒热）与 <b>引爆链</b>（花热）。
 *
 * <h2>两条链的关系</h2>
 * 点火链单发伤害很低，作用是把热量堆上去；引爆链自己的伤害几乎全部来自
 * <b>目标身上已有的热量</b>（{@link FireHeat}）。所以低阶玩家用「火星 → 爆燃」
 * 就能打出完整循环，高阶只是把攒和放到更大范围 ✓
 *
 * <h2>为什么同一个类管 10 个法术</h2>
 * 它们共用同一套"命中/范围/结算"工具，拆开写会重复十遍 ✗。
 * 约定和别的火系链一样：<b>不是本链返回 false</b>，派发处可以无脑全调一遍 ✓
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class TNFireRework {

    /** 点火链（原射线链的 5 个 id）。 */
    private static final Set<String> IGNITE = Set.of(
            "sun_ray", "blast_ray", "fire_dragon", "flame_demon_wrath", "solar_judgment");

    /** 引爆链（原火球链的 5 个 id）。 */
    private static final Set<String> DETONATE = Set.of(
            "fireball", "great_fireball", "lava_fireball", "molten_skyfall", "meteor_fall");

    /** 延时爆炸（火种、熔核）：不新建实体，靠一条服务端队列结算，最省。 */
    private record Pending(ServerLevel level, Vec3 at, long tick, float damage, UUID caster,
                           float heat, double radius, boolean consumeTargets) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private TNFireRework() {
    }

    public static boolean handles(ResourceLocation spellId) {
        String path = spellId.getPath();
        return IGNITE.contains(path) || DETONATE.contains(path);
    }

    /** 派发入口。不是本链返回 false。 */
    public static boolean cast(ServerPlayer player, ResourceLocation spellId) {
        String path = spellId.getPath();
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        switch (path) {
            case "sun_ray" -> spark(player, level);
            case "blast_ray" -> emberTrail(player, level);
            case "fire_dragon" -> fireWall(player, level);
            case "flame_demon_wrath" -> infernoRing(player, level);
            case "solar_judgment" -> skyfire(player, level);
            case "fireball" -> detonate(player, level, 2.2F, false);
            case "great_fireball" -> detonate(player, level, 2.5F, true);
            case "lava_fireball" -> seed(player, level);
            case "molten_skyfall" -> moltenCore(player, level);
            case "meteor_fall" -> martyrBurst(player, level);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ================================================================ 点火链

    /** t1 火星：短锥，伤害很低，热量 +12。 */
    private static void spark(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        int hits = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(eye, eye.add(look.scale(7.0D))).inflate(2.0D),
                t -> t != player && FireSpellRules.hittable(player, t))) {
            Vec3 to = e.position().subtract(eye).normalize();
            if (to.dot(look) < 0.55) {
                continue;
            }
            e.hurt(level.damageSources().onFire(), 4.0F);
            e.setSecondsOnFire(3);
            FireHeat.add(e, player, 12.0F);
            hits++;
        }
        Vec3 tip = eye.add(look.scale(4.0D));
        level.sendParticles(ParticleTypes.FLAME, tip.x, tip.y, tip.z, 16, 0.6, 0.6, 0.6, 0.08);
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT,
                SoundSource.PLAYERS, 0.7F, hits > 0 ? 1.35F : 1.1F);
    }

    /** t2 燃痕：沿视线铺一条火带（几块短命的熔岩地）。 */
    private static void emberTrail(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle();
        for (int i = 1; i <= 4; i++) {
            Vec3 at = eye.add(dir.scale(i * 3.0D));
            Vec3 ground = TNFireHands.groundUnder(level, at);
            TNLavaFieldEntity.spawn(level, ground, player, 3.0F, 1.6D, 50);
            level.sendParticles(ParticleTypes.FLAME, ground.x, ground.y + 0.3, ground.z,
                    10, 0.5, 0.2, 0.5, 0.05);
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(eye, eye.add(dir.scale(13.0D))).inflate(2.5D),
                t -> t != player && FireSpellRules.hittable(player, t))) {
            e.hurt(level.damageSources().onFire(), 6.0F);
            FireHeat.add(e, player, 10.0F);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    /** t3 焚风：前方炸开一堵火墙，把敌人推着烧。 */
    private static void fireWall(ServerPlayer player, ServerLevel level) {
        Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(8.0D));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(at, at).inflate(6.0D),
                t -> t != player && FireSpellRules.hittable(player, t))) {
            Vec3 push = e.position().subtract(at).normalize().scale(0.85D).add(0, 0.35, 0);
            e.setDeltaMovement(e.getDeltaMovement().add(push));
            e.hurtMarked = true;
            e.hurt(level.damageSources().onFire(), 8.0F);
            e.setSecondsOnFire(4);
            FireHeat.add(e, player, 14.0F);
        }
        TNShockwaveEntity.blast(level, at, 6.0D, FireSpellRules.SHAKE_T3, player, 1.0F);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 80, 3.0, 1.5, 3.0, 0.15);
    }

    /** t4 炎狱：以目标点为心，2 秒内四圈火环往外扩，每圈都叠热。 */
    private static void infernoRing(ServerPlayer player, ServerLevel level) {
        Vec3 center = aimPoint(player, level, 26.0D);
        for (int i = 0; i < 4; i++) {
            schedule(level, center, i * 10L + 5, 5.0F, player, 10.0F, 3.0D + i * 1.6D, false);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS, 1.2F, 0.8F);
    }

    /** t5 天火：24 格内所有敌人一次注入大量热量并点燃。 */
    private static void skyfire(ServerPlayer player, ServerLevel level) {
        Vec3 center = player.position();
        int n = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(24.0D),
                t -> t != player && FireSpellRules.hittable(player, t))) {
            FireHeat.add(e, player, 45.0F);
            e.setSecondsOnFire(8);
            e.hurt(level.damageSources().onFire(), 10.0F);
            n++;
        }
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 6, center.z, 200, 12, 8, 12, 0.1);
        level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 1, center.z, 60, 8, 1, 8, 0.2);
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, 1.0F, 1.4F);
        if (n == 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§c[TN-C] §7天火落空了（24 格内没有敌人）"), true);
        }
    }

    // ================================================================ 引爆链

    /** t1/t2 爆燃：把目标身上的热量一次花掉。t2 会向周围传一半。 */
    private static void detonate(ServerPlayer player, ServerLevel level, float rate, boolean chain) {
        LivingEntity target = aim(player, level, 26.0D);
        if (target == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§c[TN-C] §7没瞄到目标"), true);
            return;
        }
        float heat = FireHeat.consume(target);
        float damage = Math.max(8.0F, heat * rate);
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
        target.hurt(level.damageSources().onFire(), damage);
        target.setSecondsOnFire(5);
        explodeFx(level, at, 2.5D + (chain ? 1.0D : 0.0D), damage * 0.25F, player);
        if (chain) {
            float share = heat * 0.5F;
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(at, at).inflate(6.0D),
                    t -> t != target && t != player && FireSpellRules.hittable(player, t))) {
                FireHeat.add(other, player, share);
                other.hurt(level.damageSources().onFire(), damage * 0.35F);
            }
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                String.format("§6[TN-C] §r爆燃 §c%.0f §7伤害 §8(热量 %.0f)", damage, heat)), true);
    }

    /** t3 火种：先埋一颗（+15 热），3 秒后自己炸；伤害按埋下时的热量算。 */
    private static void seed(ServerPlayer player, ServerLevel level) {
        LivingEntity target = aim(player, level, 26.0D);
        Vec3 at = target != null ? target.position() : aimPoint(player, level, 20.0D);
        float heat = target != null ? FireHeat.of(target) : 0.0F;
        if (target != null) {
            FireHeat.add(target, player, 15.0F);
        }
        schedule(level, at, 60L, (heat + 25.0F) * 2.4F, player, 0.0F, 3.0D, false);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS, 0.9F, 0.6F);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§6[TN-C] §r火种已埋下 §7（3 秒后引爆）"), true);
    }

    /** t4 熔核：把热量压成三颗熔岩核，0.4 秒一颗地砸下去。 */
    private static void moltenCore(ServerPlayer player, ServerLevel level) {
        LivingEntity target = aim(player, level, 32.0D);
        Vec3 at = target != null ? target.position() : aimPoint(player, level, 24.0D);
        float heat = target != null ? FireHeat.consume(target) : 0.0F;
        for (int i = 0; i < 3; i++) {
            Vec3 offset = at.add((i - 1) * 2.2D, 0, (i % 2 == 0 ? 1.6D : -1.6D));
            schedule(level, offset, i * 8L + 4, Math.max(10.0F, heat * 0.9F), player, 0.0F, 3.2D, false);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT,
                SoundSource.PLAYERS, 1.2F, 0.5F);
    }

    /** t5 殉爆：24 格内所有带热的敌人一起引爆，自己的血也算燃料（越少越疼）。 */
    private static void martyrBurst(ServerPlayer player, ServerLevel level) {
        Vec3 center = player.position();
        float selfFuel = 1.0F + (1.0F - Math.max(0.0F, player.getHealth() / player.getMaxHealth())) * 1.5F;
        int n = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(24.0D),
                t -> t != player && FireSpellRules.hittable(player, t))) {
            float heat = FireHeat.consume(e);
            if (heat <= 0.0F) {
                continue;
            }
            e.hurt(level.damageSources().onFire(), heat * 2.6F * selfFuel);
            explodeFx(level, e.position(), 2.6D, heat * 0.3F, player);
            n++;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 2.0F, 0.65F);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                String.format("§6[TN-C] §r殉爆 §c%d §7个目标 §8(自身燃料 ×%.2f)", n, selfFuel)), true);
    }

    // ================================================================ 工具

    private static void explodeFx(ServerLevel level, Vec3 at, double radius, float shake, ServerPlayer caster) {
        TNShockwaveEntity.blast(level, at, radius, shake, caster, 1.0F);
        TNLavaFieldEntity.spawn(level, TNFireHands.groundUnder(level, at), caster,
                Math.max(2.0F, shake * 2.0F), Math.max(1.2D, radius * 0.6D), 60);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.2, 0.2, 0.2, 0.0);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 40, radius * 0.4, radius * 0.3, radius * 0.4, 0.2);
    }

    private static void schedule(ServerLevel level, Vec3 at, long delay, float damage, ServerPlayer caster,
                                 float heat, double radius, boolean consume) {
        long when = level.getGameTime() + delay;
        PENDING.add(new Pending(level, at, when, damage, caster.getUUID(), heat, radius, consume));
    }

    /** 延时队列：到点就炸，顺便给范围内的敌人叠热。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        var it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.level().getGameTime() < p.tick()) {
                continue;
            }
            it.remove();
            ServerPlayer caster = server.getPlayerList().getPlayer(p.caster());
            for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class,
                    new AABB(p.at(), p.at()).inflate(p.radius() + 1.0D),
                    t -> FireSpellRules.hittable(caster, t))) {
                float falloff = (float) Math.max(0.3, 1.0 - e.position().distanceTo(p.at()) / (p.radius() + 1.0D));
                e.hurt(p.level().damageSources().onFire(), p.damage() * falloff);
                e.setSecondsOnFire(4);
                FireHeat.add(e, caster, p.heat());
            }
            explodeFx(p.level(), p.at(), p.radius(), (float) (2.0 + p.radius()), caster);
        }
    }

    /** 视线命中点（打不到方块就取最远 24 格）。 */
    private static Vec3 aimPoint(ServerPlayer player, ServerLevel level, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                ? hit.getLocation() : end;
    }

    /** 沿视线找最近的生物。 */
    private static LivingEntity aim(ServerPlayer player, ServerLevel level, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(eye, end).inflate(1.2D),
                t -> t != player && FireSpellRules.hittable(player, t))) {
            var hit = e.getBoundingBox().inflate(0.35D).clip(eye, end);
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
}
