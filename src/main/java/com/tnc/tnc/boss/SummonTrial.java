package com.tnc.tnc.boss;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 暗 / 光 Boss 的「召唤五只 t5」交互（2026-11-15 作者设计，两个 Boss 同一套路数）。
 *
 * <ol>
 *   <li>血量掉到 60% 以下触发一次：召唤 <b>5 只召唤链 t5</b>（暗=邪神 / 光=大天使），
 *       <b>没有时间限制、会一直存在</b>，血量按"正常肉一点"给</li>
 *   <li>每杀死一只，Boss 直接扣 <b>5% 最大生命</b>（五只 = 25%）</li>
 *   <li>只要还有一只活着，<b>60 秒后</b>它们和 Boss 一起放<b>黑雾 / 光爆的 t5</b></li>
 *   <li>五只全部死亡 ⇒ Boss <b>眩晕 10 秒</b></li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class SummonTrial {

    public static final int COUNT = 5;
    public static final float KILL_PERCENT = 0.05F;
    /** 60 秒后一起放大招。 */
    public static final int FOG_DELAY_TICKS = 1200;
    public static final int STUN_TICKS = 200;
    /** 召唤物血量倍率：比普通召唤更肉。 */
    public static final double HEALTH_MULTIPLIER = 3.0D;

    public enum Kind {
        DARK("devour_light", "§5[黑魔法师] §r召唤了五只邪神 —— 杀掉它们能削他的血。"),
        LIGHT("radiant_barrage", "§e[大祭司] §r召唤了五位大天使 —— 杀掉它们能削他的血。");

        final String spell;
        final String line;

        Kind(String spell, String line) {
            this.spell = spell;
            this.line = line;
        }
    }

    private static final Map<UUID, Round> ROUNDS = new ConcurrentHashMap<>();

    private static final class Round {
        final Kind kind;
        final List<UUID> summons = new ArrayList<>();
        final long fogAt;
        boolean resolved;

        Round(Kind kind, long fogAt) {
            this.kind = kind;
            this.fogAt = fogAt;
        }
    }

    private SummonTrial() {
    }

    /** 纯逻辑：五只全死 → 眩晕；到点还有活口 → 放大招。 */
    public static boolean allDead(int alive) {
        return alive <= 0;
    }

    /** 纯逻辑：到点且还有活口 → 该放 t5 了。 */
    public static boolean shouldCastFog(int alive, boolean reachedDeadline) {
        return alive > 0 && reachedDeadline;
    }

    /** 每杀一只扣的血。 */
    public static float killDamage(float maxHealth) {
        return maxHealth * KILL_PERCENT;
    }

    public static boolean active(Mob boss) {
        Round round = ROUNDS.get(boss.getUUID());
        return round != null && !round.resolved;
    }

    public static void start(Mob boss, ServerLevel level, Kind kind) {
        if (ROUNDS.containsKey(boss.getUUID())) {
            return;
        }
        Round round = new Round(kind, level.getGameTime() + FOG_DELAY_TICKS);
        ROUNDS.put(boss.getUUID(), round);
        Vec3 centre = boss.position();
        for (int i = 0; i < COUNT; i++) {
            double angle = Math.PI * 2 * i / COUNT;
            double x = centre.x + Math.cos(angle) * 4.0D;
            double z = centre.z + Math.sin(angle) * 4.0D;
            Entity summoned = spawnOne(level, boss, kind, x, centre.y, z);
            if (summoned != null) {
                round.summons.add(summoned.getUUID());
            }
        }
        level.sendParticles(kind == Kind.DARK ? ParticleTypes.SQUID_INK : ParticleTypes.END_ROD,
                centre.x, centre.y + 1.2, centre.z, 80, 3.0, 1.0, 3.0, 0.2);
        level.playSound(null, boss.blockPosition(), SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.HOSTILE, 2.0F, 0.7F);
        announce(level, centre, kind.line);
        BossTrialCue.begin(level, centre, 40.0D,
                kind == Kind.DARK ? "§5杀掉五只邪神" : "§e杀掉五位大天使",
                kind == Kind.DARK ? 0.62F : 1.0F, kind == Kind.DARK ? 0.36F : 0.95F,
                kind == Kind.DARK ? 0.92F : 0.55F, 60);
    }

    @SuppressWarnings("unchecked")
    private static Entity spawnOne(ServerLevel level, Mob boss, Kind kind, double x, double y, double z) {
        Mob mob;
        if (kind == Kind.DARK) {
            mob = TNOrbEntities.EVIL_GOD.get().create(level);
            if (mob instanceof com.tnc.tnc.dark.TNDarkSummonEntity summon) {
                summon.setScale(1.0D);
                summon.setOwner(boss.getUUID());
                summon.setLifetime(FOG_DELAY_TICKS * 4);       // 不设时限，但给个很长的保险
            }
        } else {
            mob = TNOrbEntities.FIGHTING_ANGEL.get().create(level);
            if (mob instanceof com.tnc.tnc.light.TNFightingAngelEntity angel) {
                angel.setTier(5);
            }
        }
        if (mob == null) {
            return null;
        }
        mob.moveTo(x, y + 0.6, z, level.random.nextFloat() * 360.0F, 0.0F);
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * HEALTH_MULTIPLIER);
            mob.setHealth((float) health.getValue());
        }
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        level.sendParticles(kind == Kind.DARK ? ParticleTypes.SQUID_INK : ParticleTypes.END_ROD,
                x, y + 1.0, z, 24, 0.5, 0.8, 0.5, 0.1);
        return mob;
    }

    public static void tick(Mob boss) {
        Round round = ROUNDS.get(boss.getUUID());
        if (round == null || round.resolved || !(boss.level() instanceof ServerLevel level)) {
            return;
        }
        int alive = aliveCount(level, round);
        if (allDead(alive)) {
            // 全部死亡：Boss 眩晕 10 秒
            round.resolved = true;
            ROUNDS.remove(boss.getUUID());
            boss.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, STUN_TICKS, 6));
            boss.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.WEAKNESS, STUN_TICKS, 4));
            Vec3 at = boss.position();
            level.sendParticles(ParticleTypes.WITCH, at.x, at.y + 1.2, at.z, 50, 0.8, 1.0, 0.8, 0.1);
            level.playSound(null, boss.blockPosition(), SoundEvents.WITHER_HURT,
                    SoundSource.HOSTILE, 1.8F, 0.7F);
            announce(level, at, "§b[TN-C] §r五只全清 —— Boss 眩晕 10 秒！");
            return;
        }
        long now = level.getGameTime();
        if (shouldCastFog(alive, now >= round.fogAt)) {
            round.resolved = true;
            ROUNDS.remove(boss.getUUID());
            castFog(level, boss, round);
        } else if (round.fogAt - now <= 60) {
            BossTrialCue.warn(level, boss.position(), 40.0D, (int) (round.fogAt - now));
        }
    }

    /** 到点还有活口：Boss + 所有召唤物一起放大招（引擎施放不出去就退化成范围伤害）。 */
    private static void castFog(ServerLevel level, Mob boss, Round round) {
        Vec3 at = boss.position();
        int hits = 0;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(at, at).inflate(28.0D), p -> p.isAlive() && !p.isSpectator())) {
            player.hurt(level.damageSources().magic(), 14.0F);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    round.kind == Kind.DARK ? net.minecraft.world.effect.MobEffects.BLINDNESS
                            : net.minecraft.world.effect.MobEffects.GLOWING, 100, 0));
            hits++;
        }
        level.sendParticles(round.kind == Kind.DARK ? ParticleTypes.SQUID_INK : ParticleTypes.END_ROD,
                at.x, at.y + 1.2, at.z, 160, 6.0, 2.0, 6.0, 0.2);
        level.playSound(null, boss.blockPosition(), SoundEvents.WITHER_SPAWN,
                SoundSource.HOSTILE, 2.0F, 0.7F);
        announce(level, at, (round.kind == Kind.DARK ? "§5[黑魔法师] §r还有活口 —— 黑雾吞了进来。"
                : "§e[大祭司] §r还有活口 —— 光爆落下。") + (hits > 0 ? " §7(" + hits + " 人)" : ""));
    }

    private static int aliveCount(ServerLevel level, Round round) {
        int n = 0;
        for (UUID id : round.summons) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) {
                n++;
            }
        }
        return n;
    }

    /** 召唤物被杀：Boss 扣 5% 最大生命。 */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        UUID killed = event.getEntity().getUUID();
        for (Map.Entry<UUID, Round> entry : ROUNDS.entrySet()) {
            Round round = entry.getValue();
            if (round.resolved || !round.summons.contains(killed)) {
                continue;
            }
            Entity bossEntity = level.getEntity(entry.getKey());
            if (bossEntity instanceof Mob boss && boss.isAlive()) {
                boss.setHealth(Math.max(1.0F, boss.getHealth() - killDamage(boss.getMaxHealth())));
                level.sendParticles(ParticleTypes.WITCH, boss.getX(), boss.getY() + 1.2, boss.getZ(),
                        20, 0.5, 0.6, 0.5, 0.1);
            }
            return;
        }
    }

    private static void announce(ServerLevel level, Vec3 at, String message) {
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(at, at).inflate(80.0D))) {
            player.displayClientMessage(Component.literal(message), false);
        }
    }
}
