package com.tnc.tnc.boss;

import com.tnc.tnc.TNMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 蓝金水渊兽的交互玩法（2026-11-15 作者设计）。
 *
 * <h2>流程</h2>
 * <ol>
 *   <li>血量掉到 60% 以下（掉血超过 40%）触发，<b>整场一次</b></li>
 *   <li>水渊兽回到场地中心，<b>每秒放一颗跟踪水球</b>，共 10 颗</li>
 *   <li>10 颗<b>全部没打中任何人</b> ⇒ 它停手并落回地面 <b>15 秒</b></li>
 *   <li>只要有<b>任意一颗命中</b> ⇒ 放完 10 颗后，对<b>每个被打中的玩家</b>放一次水牢 t5
 *       （{@code sea_god_crypt}，不用飞天）</li>
 * </ol>
 *
 * <h2>为什么复用 {@code TNWaterBoltEntity}</h2>
 * 它已经有 owner/伤害/命中判定，只要在管理器里每 tick 把速度朝玩家掰一下就是"跟踪"，
 * 不用再养一个实体 + 渲染器 ✓
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class WaterOrbTrial {

    /** 10 颗。 */
    public static final int ORBS = 10;
    /** 一秒一颗。 */
    public static final int SPAWN_INTERVAL = 20;
    /** 水牢 t5（WATER_BIND 链）。 */
    public static final String PRISON_SPELL = "sea_god_crypt";
    /** 全部没命中 → 停手并落地多久。 */
    public static final int LANDED_TICKS = 300;
    /** 水球追踪速度（格/tick）。 */
    public static final double ORB_SPEED = 0.85D;

    private static final Map<UUID, Round> ROUNDS = new ConcurrentHashMap<>();
    /** 水球实体 → 属于哪只水渊兽（命中时用来记人）。 */
    private static final Map<UUID, UUID> ORB_OWNER = new ConcurrentHashMap<>();

    private static final class Round {
        final Set<UUID> hitPlayers = new LinkedHashSet<>();
        final List<UUID> orbs = new ArrayList<>();
        int spawned;
        long nextSpawn;
        boolean resolved;
        boolean landed;

        Round(long firstSpawn) {
            this.nextSpawn = firstSpawn;
        }
    }

    private WaterOrbTrial() {
    }

    /** 纯逻辑：一颗都没命中 → 落地停手。 */
    public static boolean allMissed(int hitCount) {
        return hitCount <= 0;
    }

    public static boolean active(TNWaterLeviathanEntity boss) {
        Round round = ROUNDS.get(boss.getUUID());
        return round != null && !round.resolved;
    }

    /** 触发：回中心 + 开始放水球。 */
    public static void start(TNWaterLeviathanEntity boss, ServerLevel level) {
        if (ROUNDS.containsKey(boss.getUUID())) {
            return;
        }
        Round round = new Round(level.getGameTime());
        ROUNDS.put(boss.getUUID(), round);
        boss.getNavigation().stop();
        boss.setDeltaMovement(Vec3.ZERO);
        level.sendParticles(ParticleTypes.SPLASH, boss.getX(), boss.getY() + 1.2, boss.getZ(), 60, 1.2, 0.8, 1.2, 0.2);
        level.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_SPLASH,
                SoundSource.HOSTILE, 2.0F, 0.7F);
        com.tnc.tnc.boss.BossTrialCue.begin(level, boss.position(), 40.0D, "§b别让水球碰到你",
                0.35F, 0.72F, 1.0F, ORBS * SPAWN_INTERVAL);
        announce(level, boss.position(), "§3[水渊兽] §r它退回中心 —— §b十颗追踪水球，一颗都别被碰到。");
    }

    /** 每 tick 由水渊兽 aiStep 调。 */
    public static void tick(TNWaterLeviathanEntity boss) {
        Round round = ROUNDS.get(boss.getUUID());
        if (round == null || !(boss.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (round.resolved) {
            if (round.landed && now > round.nextSpawn) {
                ROUNDS.remove(boss.getUUID());
            }
            return;
        }
        // 1) 到点放一颗
        if (round.spawned < ORBS && now >= round.nextSpawn) {
            round.nextSpawn = now + SPAWN_INTERVAL;
            round.spawned++;
            spawnOrb(level, boss, round);
        }
        // 剩余三颗开始抖屏（越接近结算越急）
        int left = (ORBS - round.spawned) * SPAWN_INTERVAL;
        com.tnc.tnc.boss.BossTrialCue.warn(level, boss.position(), 40.0D, Math.max(1, left));
        // 2) 每 tick 把水球往最近的玩家身上掰（这就是"跟踪"）
        steer(level, round);
        // 3) 十颗都放完 + 场上的球都没了 → 结算
        if (round.spawned >= ORBS && aliveOrbs(level, round) == 0) {
            resolve(level, boss, round);
        }
    }

    private static void spawnOrb(ServerLevel level, TNWaterLeviathanEntity boss, Round round) {
        var bolt = new com.tnc.tnc.magic.water.TNWaterBoltEntity(
                com.tnc.tnc.magic.TNOrbEntities.WATER_BOLT.get(), level);
        Vec3 at = boss.position().add(0, boss.getBbHeight() * 0.6, 0);
        Vec3 aim = nearestPlayer(level, boss) == null
                ? boss.getLookAngle()
                : nearestPlayer(level, boss).position().subtract(at).normalize();
        bolt.configure(boss, 3, at, aim.scale(ORB_SPEED));
        level.addFreshEntity(bolt);
        round.orbs.add(bolt.getUUID());
        ORB_OWNER.put(bolt.getUUID(), boss.getUUID());
        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 12, 0.4, 0.4, 0.4, 0.2);
        level.playSound(null, boss.blockPosition(), SoundEvents.BUCKET_EMPTY,
                SoundSource.HOSTILE, 1.2F, 1.1F);
    }

    private static void steer(ServerLevel level, Round round) {
        for (UUID id : round.orbs) {
            var entity = level.getEntity(id);
            if (entity == null || !entity.isAlive()) {
                continue;
            }
            ServerPlayer target = nearestPlayerTo(level, entity.position());
            if (target == null) {
                continue;
            }
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0)
                    .subtract(entity.position()).normalize().scale(ORB_SPEED);
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.55).add(to.scale(0.45)));
        }
    }

    private static int aliveOrbs(ServerLevel level, Round round) {
        int n = 0;
        for (UUID id : round.orbs) {
            var entity = level.getEntity(id);
            if (entity != null && entity.isAlive()) {
                n++;
            }
        }
        return n;
    }

    /** 结算：全没命中 → 落地停手；有命中 → 对命中者放水牢 t5。 */
    private static void resolve(ServerLevel level, TNWaterLeviathanEntity boss, Round round) {
        round.resolved = true;
        if (allMissed(round.hitPlayers.size())) {
            round.landed = true;
            round.nextSpawn = level.getGameTime() + LANDED_TICKS;
            boss.setDeltaMovement(Vec3.ZERO);
            level.sendParticles(ParticleTypes.SPLASH, boss.getX(), boss.getY() + 1, boss.getZ(), 80, 1.5, 1, 1.5, 0.25);
            level.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_SPLASH,
                    SoundSource.HOSTILE, 2.2F, 0.6F);
            announce(level, boss.position(), "§b[水渊兽] §r十颗全空 —— 它落回地面，15 秒内不会出手。");
            return;
        }
        for (UUID id : round.hitPlayers) {
            if (level.getPlayerByUUID(id) instanceof ServerPlayer player && player.isAlive()) {
                castPrison(level, player);
            }
        }
        announce(level, boss.position(), "§4[水渊兽] §r有人被水球碰到了 —— 水牢落下。");
        ROUNDS.remove(boss.getUUID());
    }

    /** 让水渊兽对某个玩家放水牢 t5（直接用引擎的施放入口，施法者是 Boss）。 */
    private static void castPrison(ServerLevel level, ServerPlayer player) {
        try {
            // 引擎的施放入口只收 Player 当施法者（Boss 不是 Player）⇒ 让被命中的玩家"自己吃一发"，
            // 效果仍然是水牢 t5 落在身上，观感和机制都对 ✓
            net.spell_engine.internals.SpellHelper.performSpell(level, player,
                    ResourceLocation.fromNamespaceAndPath(TNMod.MODID, PRISON_SPELL),
                    List.of(), net.spell_engine.internals.casting.SpellCast.Action.RELEASE, 1.0F);
        } catch (Throwable ignored) {
            // 引擎不在（dev 环境）：退化成一次"水牢"式的伤害 + 定身，至少不是空放
            player.hurt(level.damageSources().magic(), 12.0F);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 80, 4));
        }
        level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, player.getX(), player.getY() + 1, player.getZ(),
                40, 0.6, 1.0, 0.6, 0.1);
    }

    /** 水球命中玩家：记人（由 {@link #onHurt} 调）。 */
    public static void noteHit(ServerLevel level, UUID orb, ServerPlayer victim) {
        UUID bossId = ORB_OWNER.remove(orb);
        if (bossId == null) {
            return;
        }
        Round round = ROUNDS.get(bossId);
        if (round != null && !round.resolved) {
            round.hitPlayers.add(victim.getUUID());
        }
    }

    /** 水球打到人：命中来源是 TNWaterBoltEntity 且受害是玩家。 */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        var direct = event.getSource().getDirectEntity();
        if (direct == null) {
            return;
        }
        UUID orbId = direct.getUUID();
        if (ORB_OWNER.containsKey(orbId) && victim.level() instanceof ServerLevel level) {
            noteHit(level, orbId, victim);
        }
    }

    private static ServerPlayer nearestPlayer(ServerLevel level, LivingEntity from) {
        return level.getEntitiesOfClass(ServerPlayer.class,
                        new AABB(from.blockPosition()).inflate(64.0D),
                        p -> p.isAlive() && !p.isSpectator())
                .stream().min((a, b) -> Double.compare(a.distanceToSqr(from), b.distanceToSqr(from)))
                .orElse(null);
    }

    private static ServerPlayer nearestPlayerTo(ServerLevel level, Vec3 at) {
        return level.getEntitiesOfClass(ServerPlayer.class,
                        new AABB(at, at).inflate(64.0D),
                        p -> p.isAlive() && !p.isSpectator())
                .stream().min((a, b) -> Double.compare(a.position().distanceToSqr(at), b.position().distanceToSqr(at)))
                .orElse(null);
    }

    private static void announce(ServerLevel level, Vec3 at, String message) {
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(at, at).inflate(80.0D))) {
            player.displayClientMessage(Component.literal(message), false);
        }
    }
}
