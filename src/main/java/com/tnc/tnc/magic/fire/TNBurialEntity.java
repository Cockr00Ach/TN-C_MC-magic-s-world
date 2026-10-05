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
 * <b>炎葬</b>（火球链 t5，作者 2026-10-05 设计）：
 * 以自身为中心张开一座持续 10 秒的法阵，把范围内的敌人按在火里烧。
 *
 * <h2>规则</h2>
 * <ul>
 *   <li>法阵<b>跟着施法者走</b>（"以自身为中心"），半径 {@link FireSpellRules#BURIAL_RADIUS}</li>
 *   <li>每秒对范围内每个敌人做<b>两件事</b>：
 *     <ol>
 *       <li>挂 <b>II 级焚身（重度）</b> —— 数值基数由
 *           {@link FireSpellRules#BURIAL_SCORCH_COEFFICIENT} 决定，
 *           II 级 = 基数 × 20%/秒、持续 10 秒（可以顶掉敌人身上已有的 I 级并刷新时间）</li>
 *       <li>额外扣<b>目标最大生命的 1%</b>（作者定）—— 这一份是"打大血牛"用的，血越厚越痛</li>
 *     </ol>
 *   </li>
 * </ul>
 *
 * <h2>作者要求的"不可被正常手段清除"是怎么做到的</h2>
 * 不是给效果加什么免疫标记，而是<b>每秒重新施加一遍</b>：喝牛奶清掉、无敌帧挡掉，
 * 下一跳就又回来了 —— 站在火里就是一直在火里 ✓
 * 另外这 1% 走的是 {@code magic} 伤害类型（原版该类型<b>不吃护甲</b>），符合作者"无视护甲"的要求。
 */
public final class TNBurialEntity extends Entity {

    private static final EntityDataAccessor<Integer> AGE =
            SynchedEntityData.defineId(TNBurialEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(TNBurialEntity.class, EntityDataSerializers.INT);

    private UUID owner;
    private LivingEntity caster;
    private float scorchBase;

    public TNBurialEntity(EntityType<? extends TNBurialEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** 由法术入口调用（只在服务端）。 */
    public static TNBurialEntity cast(ServerLevel level, LivingEntity owner, float scorchBase) {
        TNBurialEntity field = new TNBurialEntity(TNOrbEntities.BURIAL.get(), level);
        field.owner = owner == null ? null : owner.getUUID();
        field.caster = owner;
        field.scorchBase = scorchBase;
        field.entityData.set(LIFE, FireSpellRules.BURIAL_LIFE_TICKS);
        field.setPos(owner.getX(), owner.getY(), owner.getZ());
        level.addFreshEntity(field);
        return field;
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
        entityData.define(LIFE, FireSpellRules.BURIAL_LIFE_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        int age = age() + 1;
        entityData.set(AGE, age);

        if (caster != null && caster.isAlive() && caster.level() == level()) {
            setPos(caster.getX(), caster.getY(), caster.getZ());
        }

        if (level().isClientSide) {
            // 脚下一圈烧起来的地火 + 缓慢上升的火星
            double r = FireSpellRules.BURIAL_RADIUS;
            for (int i = 0; i < 8; i++) {
                double a = age * 0.08D + i * Math.PI / 4.0D;
                level().addParticle(ParticleTypes.FLAME,
                        getX() + Math.cos(a) * r, getY() + 0.15D, getZ() + Math.sin(a) * r,
                        0.0D, 0.03D, 0.0D);
            }
            if (age % 3 == 0) {
                double a = random.nextDouble() * Math.PI * 2.0D;
                double d = Math.sqrt(random.nextDouble()) * r;
                level().addParticle(ParticleTypes.LAVA,
                        getX() + Math.cos(a) * d, getY() + 0.1D, getZ() + Math.sin(a) * d, 0.0D, 0.05D, 0.0D);
            }
            return;
        }

        if (age > life()) {
            discard();
            return;
        }
        if (age % FireSpellRules.BURIAL_TICK_INTERVAL != 0) {
            return;
        }

        ServerLevel server = (ServerLevel) level();
        LivingEntity source = resolveCaster(server);
        ServerPlayer casterPlayer = source instanceof ServerPlayer sp ? sp : null;
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class,
                FireSpellRules.uprightArea(position(), FireSpellRules.BURIAL_RADIUS, 4.0D),
                t -> FireSpellRules.hittable(source, t))) {
            // 1) 重度灼烧（II 级焚身）：能顶掉已有的 I 级，并按作者确认**刷新时间**
            TNScorch.apply(target, casterPlayer, scorchBase, true);
            // 2) 目标最大生命的 1%（magic 类型不吃护甲）
            float percent = target.getMaxHealth() * FireSpellRules.BURIAL_MAX_HEALTH_PERCENT;
            if (percent > 0.0F) {
                target.invulnerableTime = 0;   // 焚身那一跳可能刚设过无敌帧
                target.hurt(server.damageSources().magic(), percent);
            }
        }
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

    /** 炎葬的灼烧基数（给命令/自检看）。 */
    public float scorchBase() {
        return scorchBase;
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
