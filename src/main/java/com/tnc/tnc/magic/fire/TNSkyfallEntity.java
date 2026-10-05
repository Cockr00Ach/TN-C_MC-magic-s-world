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
 *   <li>法阵悬在施法者上方，<b>跟着施法者走</b>（"视角上方"的意思就是它一直在你头顶）</li>
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

        // 跟着施法者走：法阵一直在你头顶
        if (caster != null && caster.isAlive() && caster.level() == level()) {
            setPos(caster.getX(), caster.getY() + FireSpellRules.SKYFALL_HEIGHT, caster.getZ());
        }

        if (level().isClientSide) {
            // 一圈缓慢旋转的火环 + 中心往下淌的火星，暗示"它在往下砸"
            double r = 3.0D;
            for (int i = 0; i < 6; i++) {
                double a = age * 0.12D + i * Math.PI / 3.0D;
                level().addParticle(ParticleTypes.FLAME,
                        getX() + Math.cos(a) * r, getY() + 0.1D, getZ() + Math.sin(a) * r,
                        0.0D, -0.02D, 0.0D);
            }
            if (age % 4 == 0) {
                level().addParticle(ParticleTypes.LAVA, getX(), getY() - 0.2D, getZ(), 0.0D, -0.05D, 0.0D);
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

        Vec3 from = position();
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
