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

import java.util.UUID;

/**
 * <b>熔岩地</b>：火球砸下来之后在落点留下的持续灼烧地带。
 *
 * <h2>规则（作者 2026-10-05 定稿）</h2>
 * <ul>
 *   <li>火球命中点<b>下方</b>生成，持续 {@link FireSpellRules#LAVA_FIELD_LIFE_TICKS} tick</li>
 *   <li>每秒对范围内的敌人造成一次灼烧伤害 —— 数值 = 那一发火球的伤害 ×
 *       {@link FireSpellRules#LAVA_FIELD_PERCENT}（和焚身同一个比率）</li>
 *   <li>⚠️ <b>这份伤害和敌人身上挂的焚身不冲突，可以同时触发</b>（作者明确要求）：
 *       站在熔岩地里又中了焚身 = 每秒吃两份，这正是设计意图 ✓</li>
 * </ul>
 *
 * <h2>为什么做成独立实体而不是方块</h2>
 * 真的放熔岩方块会改地形、会流走、还会烧到我们自己人；
 * 这里只做"一块看不见但会持续烫人的区域" + 粒子表现，和
 * {@code TNWaterFieldEntity} 的做法一致（它们都明确不动地形）。
 */
public final class TNLavaFieldEntity extends Entity {

    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(TNLavaFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> AGE =
            SynchedEntityData.defineId(TNLavaFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(TNLavaFieldEntity.class, EntityDataSerializers.INT);

    private UUID owner;
    private float perSecond;

    public TNLavaFieldEntity(EntityType<? extends TNLavaFieldEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** 由火球的命中处理调用（只在服务端）。 */
    public static TNLavaFieldEntity spawn(ServerLevel level, Vec3 center, ServerPlayer caster,
                                          float perSecond, double radius, int lifeTicks) {
        TNLavaFieldEntity field = new TNLavaFieldEntity(TNOrbEntities.LAVA_FIELD.get(), level);
        field.owner = caster == null ? null : caster.getUUID();
        field.perSecond = perSecond;
        field.entityData.set(RADIUS, (float) radius);
        field.entityData.set(LIFE, lifeTicks);
        field.setPos(center.x, center.y, center.z);
        level.addFreshEntity(field);
        return field;
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public int age() {
        return entityData.get(AGE);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(RADIUS, 3.0F);
        entityData.define(AGE, 0);
        entityData.define(LIFE, FireSpellRules.LAVA_FIELD_LIFE_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        int age = age() + 1;
        entityData.set(AGE, age);

        if (level().isClientSide) {
            // 地面上一圈闷烧的火星：越低越稀，看起来"贴地"
            if (age % 3 == 0) {
                double r = radius();
                for (int i = 0; i < 3; i++) {
                    double a = random.nextDouble() * Math.PI * 2.0D;
                    double d = Math.sqrt(random.nextDouble()) * r;
                    level().addParticle(ParticleTypes.FLAME,
                            getX() + Math.cos(a) * d, getY() + 0.08D, getZ() + Math.sin(a) * d,
                            0.0D, 0.01D, 0.0D);
                }
            }
            if (age % 10 == 0) {
                double r = radius() * 0.8D;
                double a = random.nextDouble() * Math.PI * 2.0D;
                level().addParticle(ParticleTypes.LAVA,
                        getX() + Math.cos(a) * r * random.nextDouble(), getY() + 0.05D,
                        getZ() + Math.sin(a) * r * random.nextDouble(), 0.0D, 0.0D, 0.0D);
            }
            return;
        }

        if (age > life()) {
            discard();
            return;
        }
        // 每秒跳一次
        if (age % FireSpellRules.LAVA_FIELD_TICK_INTERVAL != 0) {
            return;
        }
        ServerLevel server = (ServerLevel) level();
        LivingEntity caster = resolveOwner(server);
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class,
                FireSpellRules.uprightArea(position(), radius(), 1.5D),
                t -> FireSpellRules.hittable(caster, t))) {
            target.invulnerableTime = 0;      // 和焚身同一个坑：不清无敌帧会被吞
            target.hurt(server.damageSources().indirectMagic(this, caster == null ? this : caster),
                    perSecond);
        }
    }

    /**
     * 施法者可能已下线/换维度 —— 取不到就返回 null。
     *
     * <p>{@link FireSpellRules#enemy} <b>接受 null 主人</b>：这时只跳过"同队"那一条判据，
     * 区域不会因为"找不到主人"就整个失效 ✓
     */
    private LivingEntity resolveOwner(ServerLevel level) {
        if (owner == null) {
            return null;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player != null && player.level() == level) {
            return player;
        }
        return null;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // ⚠️ 这里**不能**调 super：直接继承 Entity 时这两个方法是**抽象**的
        // （水系那几个继承 Projectile，Projectile 给了实现，所以那边可以 super）。
        // 本实体根本不需要存档（shouldBeSaved = false），读进来直接作废即可。
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // 同上：不持久化，没有要写的东西
    }
}
