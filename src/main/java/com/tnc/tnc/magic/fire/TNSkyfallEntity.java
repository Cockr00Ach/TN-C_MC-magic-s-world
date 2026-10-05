package com.tnc.tnc.magic.fire;

import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.UUID;

/**
 * <b>熔岳天倾</b>（火球链 t4，作者 2026-10-05 设计）：
 * 在<b>视角上方</b>生成一座持续 10 秒的法阵，期间不停向下砸熔岩火球。
 *
 * <h2>规则</h2>
 * <ul>
 *   <li>法阵落在<b>选定目标的头顶</b>（由 {@code TNFireFields.skyfallAnchor} 选点），
 *       生成之后<b>位置固定不变</b> —— 不是跟着谁走 ✓（作者 2026-10-05 明确要求）</li>
 *   <li>每 {@link FireSpellRules#SKYFALL_INTERVAL_TICKS} tick 砸一发熔岩火球；
 *       在法阵周围找人，<b>找不到就直直往下砸</b>（不会因为没目标就整个失效）</li>
 *   <li>砸下去的火球会爆炸（伤害 = 火球伤害 × 50%）<b>并且留下熔岩地</b> ——
 *       两件事都写在 {@code TNFireBoltEntity} 的命中处理里，靠 {@code Bolt} 上的标志触发</li>
 * </ul>
 *
 * <h2>为什么不直接放方块</h2>
 * 和熔岩地同一个理由：不动地形、不流走、不烧自己人。
 */
public final class TNSkyfallEntity extends Entity {

    private static final EntityDataAccessor<Integer> AGE =
            SynchedEntityData.defineId(TNSkyfallEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(TNSkyfallEntity.class, EntityDataSerializers.INT);

    private UUID owner;
    private LivingEntity caster;

    /**
     * 法阵的视觉半径（格）—— 给渲染器 {@code TNSigilRenderer} 用，决定画多大一座。
     *
     * <p>这是「魔法阵」的尺寸，和 {@link FireSpellRules#SKYFALL_SEEK_RADIUS}（打多大范围）
     * 是两件事，所以分开写 ✓
     */
    public static final double SIGIL_RADIUS = 3.0D;

    /**
     * 砸下来的火球<b>不从正中心出</b>（作者 2026-10-05：「火球不要固定在正中心落下」）——
     * 在法阵圆盘内随机取一个落点，看着才像"整座法阵在开火"而不是"一根管子往下吐"。
     */
    private static final double LAUNCH_SPREAD = 2.4D;

    public TNSkyfallEntity(EntityType<? extends TNSkyfallEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** 由法术入口调用（只在服务端）。 */
    public static TNSkyfallEntity cast(ServerLevel level, LivingEntity owner, Vec3 at) {
        TNSkyfallEntity sigil = new TNSkyfallEntity(TNOrbEntities.SKYFALL.get(), level);
        sigil.owner = owner == null ? null : owner.getUUID();
        sigil.caster = owner;
        sigil.entityData.set(LIFE, FireSpellRules.SKYFALL_LIFE_TICKS);
        sigil.setPos(at.x, at.y, at.z);
        level.addFreshEntity(sigil);
        return sigil;
    }

    public int age() {
        return entityData.get(AGE);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(AGE, 0);
        entityData.define(LIFE, FireSpellRules.SKYFALL_LIFE_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        int age = age() + 1;
        entityData.set(AGE, age);

        // ⚠️ 位置**生成后就不再变**（作者 2026-10-05 明确要求）。
        //    早先这里是"跟着施法者走"，那样法阵会一直挂在玩家头顶 —— 与设计不符 ✗
        //    caster 现在只用于判断"打谁"（自己的召唤物/队友不能砸），不再影响位置。

        if (level().isClientSide) {
            // 法阵的形状由 TNSigilRenderer 用几何体画；粒子是**叠加的火焰装饰** ——
            // 作者 2026-10-05 要求「两个法阵加上火焰粒子效果凸显其实火系魔法」✓
            // ⚠️ 粒子和几何体是两件事，别用粒子代替几何体 ✗
            for (int i = 0; i < 8; i++) {
                double a = age * 0.02D + i * Math.PI / 4.0D;
                level().addParticle(ParticleTypes.FLAME,
                        getX() + Math.cos(a) * SIGIL_RADIUS * 0.96D, getY() + 0.12D,
                        getZ() + Math.sin(a) * SIGIL_RADIUS * 0.96D,
                        0.0D, 0.012D, 0.0D);
            }
            if (age % 2 == 0) {
                double a = random.nextDouble() * Math.PI * 2.0D;
                double d = Math.sqrt(random.nextDouble()) * SIGIL_RADIUS;
                level().addParticle(ParticleTypes.SMALL_FLAME,
                        getX() + Math.cos(a) * d, getY() + 0.1D, getZ() + Math.sin(a) * d,
                        0.0D, 0.03D, 0.0D);
            }
            return;
        }

        if (age > life()) {
            discard();
            return;
        }
        if (age % FireSpellRules.SKYFALL_INTERVAL_TICKS != 0) {
            return;
        }
        ServerLevel server = (ServerLevel) level();
        LivingEntity shooter = resolveCaster(server);
        // ⚠️ 用的是专用的 SKYFALL_BOLT，不是 bolt("molten_skyfall") ——
        //   熔岳天倾不在"直接投放"那张表里（见 FireSpellRules.SKYFALL_BOLT 的注释）
        FireSpellRules.Bolt bolt = FireSpellRules.SKYFALL_BOLT;

        // 找人：法阵下方一圈里最近的敌人；找不到就直直往下砸
        LivingEntity target = shooter == null ? null : server.getEntitiesOfClass(LivingEntity.class,
                        FireSpellRules.uprightArea(position(), FireSpellRules.SKYFALL_SEEK_RADIUS, 48.0D),
                        t -> FireSpellRules.hittable(shooter, t))
                .stream()
                .min(Comparator.comparingDouble(t -> t.distanceToSqr(position())))
                .orElse(null);

        // 作者 2026-10-05：**火球不要固定在正中心落下** ——
        // 在法阵圆盘里随机取一点当发射点（面积均匀取点，所以是 sqrt(random) 而不是 random）
        double spreadAngle = random.nextDouble() * Math.PI * 2.0D;
        double spreadDist = Math.sqrt(random.nextDouble()) * LAUNCH_SPREAD;
        Vec3 from = position().add(Math.cos(spreadAngle) * spreadDist, 0.0D,
                Math.sin(spreadAngle) * spreadDist);
        // 速度跟手扔的那颗保持一致（作者 2026-10-05 要求降速）—— 别自己另定一个数
        Vec3 velocity = target == null
                ? new Vec3(0.0D, -TNFireBoltEntity.LAUNCH_SPEED, 0.0D)
                : target.getEyePosition().subtract(from).normalize().scale(TNFireBoltEntity.LAUNCH_SPEED);

        TNFireBoltEntity shot = new TNFireBoltEntity(TNOrbEntities.FIRE_BOLT.get(), server);
        shot.configure(shooter, bolt, "molten_skyfall", from, velocity);
        server.addFreshEntity(shot);
        server.sendParticles(ParticleTypes.LAVA, from.x, from.y, from.z, 6, 0.3D, 0.1D, 0.3D, 0.05D);
    }

    private LivingEntity resolveCaster(ServerLevel level) {
        if (caster != null && caster.isAlive() && caster.level() == level) {
            return caster;
        }
        if (owner == null) {
            return null;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        return player != null && player.level() == level ? player : null;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // ⚠️ 不能调 super：直接继承 Entity 时这两个方法是抽象的（见 TNLavaFieldEntity 的同类注释）
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // 不持久化，没有要写的东西
    }
}
