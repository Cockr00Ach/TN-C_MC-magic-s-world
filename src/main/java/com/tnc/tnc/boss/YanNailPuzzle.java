package com.tnc.tnc.boss;

import com.tnc.tnc.TNMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
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
 * 衍的<b>雷锲</b>交互（2026-11-15 作者设计）。
 *
 * <h2>玩法</h2>
 * <ol>
 *   <li>衍在脚下召唤 <b>6 枚雷锲</b>，并进入<b>无敌</b>（打它不掉血、只弹火花）</li>
 *   <li>玩家有 <b>15 秒</b>把 6 枚雷锲全打碎</li>
 *   <li>全碎 ⇒ 一道雷劈在衍身上，<b>扣 10% 最大生命</b>（无视无敌）</li>
 *   <li>没碎完 ⇒ 衍把剩下的雷锲全部收回去，<b>对 24 格内所有玩家落雷</b></li>
 * </ol>
 *
 * <h2>为什么无敌做成"查询"而不是 {@code setInvulnerable}</h2>
 * 原版的 invulnerable 会连"处决/剧情伤害"一起挡掉，还会让血条看起来像卡住 ✗。
 * 这里改成在 {@code YanDarkBossEntity.hurt} 里问一句 {@link #isActive}，
 * 只挡普通伤害，谜题自己结算时用 {@code setHealth} 直接扣 ✓
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class YanNailPuzzle {

    /** 6 枚。 */
    public static final int NAILS = 6;
    /** 15 秒。 */
    public static final int WINDOW_TICKS = 300;
    /** 成功时扣多少比例的血。 */
    public static final float SUCCESS_PERCENT = 0.10F;
    /** 失败落雷的范围（格）。 */
    public static final double FAIL_RADIUS = 24.0D;
    /** 落雷伤害。 */
    public static final float FAIL_DAMAGE = 14.0F;

    /** 衍的 UUID → 这一轮的状态。 */
    private static final Map<UUID, Round> ROUNDS = new ConcurrentHashMap<>();

    private static final class Round {
        final long deadline;
        final List<UUID> nails = new ArrayList<>();
        boolean resolved;

        Round(long deadline) {
            this.deadline = deadline;
        }
    }

    private YanNailPuzzle() {
    }

    /** 纯逻辑：还有几枚没碎 + 有没有超时 → 成功了吗（单测直接钉这条）。 */
    public static boolean succeeded(int alive, boolean timedOut) {
        return alive <= 0 && !timedOut;
    }

    /** 这位衍现在是不是"雷锲阶段"（无敌）。 */
    public static boolean isActive(YanDarkBossEntity boss) {
        Round round = ROUNDS.get(boss.getUUID());
        return round != null && !round.resolved;
    }

    /** 开始一轮：召唤 6 枚雷锲。 */
    public static void start(YanDarkBossEntity boss, ServerLevel level) {
        if (ROUNDS.containsKey(boss.getUUID())) {
            return;
        }
        Round round = new Round(level.getGameTime() + WINDOW_TICKS);
        ROUNDS.put(boss.getUUID(), round);
        Vec3 center = boss.position();
        for (int i = 0; i < NAILS; i++) {
            double angle = Math.PI * 2 * i / NAILS + 0.35;
            double r = 4.5D;
            Vec3 at = center.add(Math.cos(angle) * r, 0.0D, Math.sin(angle) * r);
            var nail = TNThunderNailEntity.spawn(level, boss, groundUnder(level, at));
            round.nails.add(nail.getUUID());
        }
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 1.5, center.z, 4, 0.5, 0.5, 0.5, 0.0);
        level.playSound(null, boss.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.HOSTILE, 2.0F, 0.9F);
        com.tnc.tnc.boss.BossTrialCue.begin(level, boss.position(), 24.0D, "§d击碎六枚雷锲",
                0.72F, 0.45F, 1.0F, WINDOW_TICKS);
        announce(level, boss, "§6[衍] §r六枚雷锲已经插下 §7—— 15 秒内全部击碎，否则他会把雷还给你们。");
    }

    /** 每 tick 由衍的 aiStep 调用：超时就判失败。 */
    public static void tick(YanDarkBossEntity boss) {
        Round round = ROUNDS.get(boss.getUUID());
        if (round == null || round.resolved || !(boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (!boss.isAlive()) {
            cleanup(level, boss, round);
            return;
        }
        // 玩家把雷锲打光了：nail 的 shatter 会调 onNailDestroyed，这里兜底
        int alive = aliveNails(level, round);
        if (alive == 0) {
            succeed(level, boss, round);
            return;
        }
        com.tnc.tnc.boss.BossTrialCue.warn(level, boss.position(), 24.0D,
                (int) (round.deadline - level.getGameTime()));
        if (level.getGameTime() >= round.deadline) {
            fail(level, boss, round);
        }
    }

    /** 某一枚被打碎。 */
    public static void onNailDestroyed(ServerLevel level, UUID bossId) {
        Round round = ROUNDS.get(bossId);
        if (round == null || round.resolved) {
            return;
        }
        Entity bossEntity = level.getEntity(bossId);
        if (!(bossEntity instanceof YanDarkBossEntity boss)) {
            ROUNDS.remove(bossId);
            return;
        }
        if (aliveNails(level, round) == 0) {
            succeed(level, boss, round);
        }
    }

    /** 成功：雷劈衍，扣 10% 最大生命（无视无敌）。 */
    private static void succeed(ServerLevel level, YanDarkBossEntity boss, Round round) {
        round.resolved = true;
        cleanup(level, boss, round);
        float damage = boss.getMaxHealth() * SUCCESS_PERCENT;
        boss.setHealth(Math.max(1.0F, boss.getHealth() - damage));
        Vec3 at = boss.position();
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1.5, at.z, 6, 0.3, 0.4, 0.3, 0.0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.2, at.z, 60, 0.8, 1.2, 0.8, 0.3);
        level.playSound(null, boss.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.HOSTILE, 2.2F, 1.25F);
        boss.hurtTime = 10;
        announce(level, boss, "§b[衍] §r六枚皆碎 —— 雷罚落在他自己身上！ §7(" + Math.round(damage) + " 点)");
    }

    /** 失败：收回雷锲，对范围内所有玩家落雷。 */
    private static void fail(ServerLevel level, YanDarkBossEntity boss, Round round) {
        round.resolved = true;
        cleanup(level, boss, round);
        Vec3 at = boss.position();
        int hits = 0;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(at, at).inflate(FAIL_RADIUS), p -> p.isAlive() && !p.isSpectator())) {
            player.hurt(level.damageSources().lightningBolt(), FAIL_DAMAGE);
            level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.2, player.getZ(),
                    3, 0.2, 0.3, 0.2, 0.0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(),
                    30, 0.5, 0.8, 0.5, 0.25);
            level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.HOSTILE, 1.6F, 1.0F);
            hits++;
        }
        level.playSound(null, boss.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.HOSTILE, 2.4F, 0.7F);
        announce(level, boss, "§4[衍] §r你们没打碎它们 —— 雷锲回来了。" + (hits > 0 ? " §7(" + hits + " 人中雷)" : ""));
    }

    /** 删掉剩下的雷锲并清掉这一轮。 */
    private static void cleanup(ServerLevel level, YanDarkBossEntity boss, Round round) {
        for (UUID id : round.nails) {
            Entity nail = level.getEntity(id);
            if (nail != null && nail.isAlive()) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, nail.getX(), nail.getY() + 0.8, nail.getZ(),
                        10, 0.3, 0.4, 0.3, 0.1);
                nail.discard();
            }
        }
        ROUNDS.remove(boss.getUUID());
    }

    public static void abandon(ServerLevel level, YanDarkBossEntity boss) {
        Round round = ROUNDS.get(boss.getUUID());
        if (round != null) cleanup(level, boss, round);
    }

    private static int aliveNails(ServerLevel level, Round round) {
        int n = 0;
        for (UUID id : round.nails) {
            Entity nail = level.getEntity(id);
            if (nail != null && nail.isAlive()) {
                n++;
            }
        }
        return n;
    }

    private static Vec3 groundUnder(ServerLevel level, Vec3 at) {
        var hit = level.clip(new net.minecraft.world.level.ClipContext(at.add(0, 3, 0), at.add(0, -6, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, null));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return Vec3.atBottomCenterOf(hit.getBlockPos().above());
        }
        return at;
    }

    private static void announce(ServerLevel level, YanDarkBossEntity boss, String message) {
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(boss.position(), boss.position()).inflate(48.0D))) {
            player.displayClientMessage(Component.literal(message), false);
        }
    }

    /** 衍死了：把这一轮收干净，别留下悬空的雷锲。 */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof YanDarkBossEntity boss
                && boss.level() instanceof ServerLevel level) {
            Round round = ROUNDS.remove(boss.getUUID());
            if (round != null) {
                cleanup(level, boss, round);
            }
        }
    }
}
