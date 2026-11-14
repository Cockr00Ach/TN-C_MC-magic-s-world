package com.tnc.tnc.boss.earth;

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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 土系 Boss 的交互玩法（2026-11-15 作者设计）。
 *
 * <h2>流程</h2>
 * <ol>
 *   <li>血量掉到 60% 以下（掉血超过 40%）触发，<b>整场一次</b></li>
 *   <li>Boss 回到场地中心，把场上所有玩家<b>震到场地边缘</b>（专属维度，半径见 {@code EarthArenaLayout.RADIUS}）</li>
 *   <li>Boss 潜入地下（复用他自己的钻地状态机），中心升起一根<b>有血量的土柱</b></li>
 *   <li>15 秒内打碎土柱 ⇒ Boss 从地底出来，<b>停手 10 秒</b></li>
 *   <li>没打碎 ⇒ Boss 出来并飞到 <b>30 格高空</b>，放<b>地爆天星</b>：强制吸引所有玩家</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class EarthPillarTrial {

    /** 15 秒窗口。 */
    public static final int WINDOW_TICKS = 300;
    /** 破柱之后停手多久。 */
    public static final int PASSIVE_TICKS = 200;
    /** 失败后飞到多高。 */
    public static final double SKY_HEIGHT = 30.0D;
    /** 地爆天星持续多久。 */
    public static final int PULL_TICKS = 120;
    /** 吸附范围（整场）。 */
    public static final double PULL_RADIUS = 120.0D;

    private static final Map<UUID, Round> ROUNDS = new ConcurrentHashMap<>();

    private static final class Round {
        final long deadline;
        final UUID pillar;
        boolean resolved;
        long pullUntil;
        long lastPullDamage;

        Round(long deadline, UUID pillar) {
            this.deadline = deadline;
            this.pillar = pillar;
        }
    }

    private EarthPillarTrial() {
    }

    /** 纯逻辑：破柱且没超时 → 停手；否则失败。 */
    public static boolean pacified(boolean pillarBroken, boolean timedOut) {
        return pillarBroken && !timedOut;
    }

    public static boolean active(StoneLancerEntity boss) {
        Round round = ROUNDS.get(boss.getUUID());
        return round != null && !round.resolved;
    }

    /** 触发：回到中心、震开玩家、钻地、立起土柱。 */
    public static void start(StoneLancerEntity boss, ServerLevel level) {
        if (ROUNDS.containsKey(boss.getUUID())) {
            return;
        }
        double cx = com.tnc.tnc.world.eartharena.EarthArenaLayout.X + 0.5;
        double cy = com.tnc.tnc.world.eartharena.EarthArenaLayout.FLOOR + 1;
        double cz = com.tnc.tnc.world.eartharena.EarthArenaLayout.Z + 0.5;
        Vec3 centre = new Vec3(cx, cy, cz);
        boss.setPos(cx, cy, cz);
        boss.setDeltaMovement(Vec3.ZERO);
        boss.getNavigation().stop();

        // 震到场地边缘（不是真的抛 100 格：专属维度有边界，直接送到边缘环上更可控）
        double edge = com.tnc.tnc.world.eartharena.EarthArenaLayout.RADIUS - 8;
        int index = 0;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(centre, centre).inflate(com.tnc.tnc.world.eartharena.EarthArenaLayout.RADIUS),
                p -> p.isAlive() && !p.isSpectator())) {
            double angle = Math.PI * 2 * index++ / 6 + 0.4;
            player.teleportTo(cx + Math.cos(angle) * edge, cy, cz + Math.sin(angle) * edge);
            player.setDeltaMovement(Vec3.ZERO);
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.6, player.getZ(),
                    24, 0.6, 0.4, 0.6, 0.15);
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE,
                    SoundSource.HOSTILE, 1.0F, 0.8F);
        }

        // 钻地：复用 Boss 自己的状态机
        if (boss.getTarget() != null) {
            boss.beginBurrow(boss.getTarget());
        }

        TNEarthPillarEntity pillar = TNEarthPillarEntity.spawn(level, boss, cx, cy, cz);
        ROUNDS.put(boss.getUUID(), new Round(level.getGameTime() + WINDOW_TICKS, pillar.getUUID()));

        level.sendParticles(ParticleTypes.FLASH, cx, cy + 2, cz, 6, 0.6, 0.8, 0.6, 0.0);
        level.playSound(null, boss.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.HOSTILE, 2.0F, 0.6F);
        com.tnc.tnc.boss.BossTrialCue.begin(level, centre, 60.0D, "§6击碎场地中心的土柱",
                0.86F, 0.68F, 0.36F, WINDOW_TICKS);
        announce(level, centre, "§6[山君] §r他退回场地中心，把你们震开—— §e15 秒内打碎那根土柱！");
    }

    /** 每 tick 由 Boss 的 aiStep 调。 */
    public static void tick(StoneLancerEntity boss) {
        Round round = ROUNDS.get(boss.getUUID());
        if (round == null || !(boss.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (!round.resolved) {
            Entity pillar = level.getEntity(round.pillar);
            boolean broken = pillar == null || !pillar.isAlive();
            if (broken) {
                succeed(level, boss, round);
                return;
            }
            com.tnc.tnc.boss.BossTrialCue.warn(level, boss.position(), 60.0D,
                    (int) (round.deadline - now));
            if (now >= round.deadline) {
                fail(level, boss, round);
            }
            return;
        }
        // 地爆天星：持续把玩家往 Boss 身上拽
        if (round.pullUntil > now) {
            Vec3 centre = boss.position();
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                    new AABB(centre, centre).inflate(PULL_RADIUS), p -> p.isAlive() && !p.isSpectator())) {
                Vec3 to = centre.subtract(player.position());
                double len = Math.max(1.0D, to.length());
                Vec3 pull = to.scale(1.1D / len);
                player.setDeltaMovement(player.getDeltaMovement().add(pull.x, pull.y * 0.6D + 0.18D, pull.z));
                player.hurtMarked = true;
            }
            if (now - round.lastPullDamage >= 20) {
                round.lastPullDamage = now;
                for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                        new AABB(centre, centre).inflate(PULL_RADIUS), p -> p.isAlive() && !p.isSpectator())) {
                    if (player.position().distanceTo(centre) < 6.0D) {
                        player.hurt(level.damageSources().magic(), 4.0F);
                    }
                }
            }
        } else {
            ROUNDS.remove(boss.getUUID());
        }
    }

    public static void onPillarDestroyed(ServerLevel level, UUID bossId) {
        Entity entity = level.getEntity(bossId);
        if (entity instanceof StoneLancerEntity boss) {
            Round round = ROUNDS.get(bossId);
            if (round != null && !round.resolved) {
                succeed(level, boss, round);
            }
        } else {
            ROUNDS.remove(bossId);
        }
    }

    /** 破柱成功：Boss 出来，停手 10 秒。 */
    private static void succeed(ServerLevel level, StoneLancerEntity boss, Round round) {
        round.resolved = true;
        boss.setBurrowPhase(0);
        boss.suppressAttacks(PASSIVE_TICKS);
        Vec3 at = boss.position();
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 1, at.z, 40, 1.0, 1.2, 1.0, 0.05);
        level.playSound(null, boss.blockPosition(), SoundEvents.STONE_BREAK,
                SoundSource.HOSTILE, 1.8F, 0.7F);
        announce(level, at, "§b[山君] §r土柱碎了 —— 他愣在原地，10 秒内不会出手。");
    }

    /** 破柱失败：Boss 出来并飞上 30 格，地爆天星。 */
    private static void fail(ServerLevel level, StoneLancerEntity boss, Round round) {
        round.resolved = true;
        boss.setBurrowPhase(0);
        Vec3 centre = boss.position();
        double sky = centre.y + SKY_HEIGHT;
        boss.teleportTo(centre.x, sky, centre.z);
        boss.setDeltaMovement(Vec3.ZERO);
        boss.setNoGravity(true);
        round.pullUntil = level.getGameTime() + PULL_TICKS;
        round.lastPullDamage = level.getGameTime();
        level.sendParticles(ParticleTypes.FLASH, centre.x, sky, centre.z, 8, 0.5, 0.5, 0.5, 0.0);
        level.playSound(null, boss.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.HOSTILE, 2.4F, 0.55F);
        announce(level, centre, "§4[山君] §r土柱没碎 —— 他升上高空，地爆天星！");
        // 落地恢复重力
        level.getServer().execute(() -> {
            if (boss.isAlive()) {
                boss.setNoGravity(false);
            }
        });
    }

    private static void announce(ServerLevel level, Vec3 at, String message) {
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(at, at).inflate(160.0D))) {
            player.displayClientMessage(Component.literal(message), false);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof StoneLancerEntity boss) {
            ROUNDS.remove(boss.getUUID());
        }
    }
}
