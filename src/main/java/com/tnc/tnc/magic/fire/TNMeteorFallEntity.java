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
 * <b>陨星坠</b>（火球链 t5，作者 2026-10-05 定稿；原名「炎葬」，改名时机制一起重写了）：
 * <b>地上先张开一座巨大的法阵，随后天上一颗巨大的陨石砸下来</b>。
 *
 * <h2>规则</h2>
 * <ol>
 *   <li>法阵落在<b>选定目标脚下的地面</b>（由 {@code TNFireFields.groundAnchor} 选点），
 *       <b>位置固定不变</b> —— 不跟着谁走</li>
 *   <li>法阵亮 {@link #CHARGE_TICKS} tick（2 秒）作为"预警"，
 *       然后从它<b>正上方 {@link FireSpellRules#METEOR_DROP_HEIGHT} 格</b>砸下一颗大陨石</li>
 *   <li>陨石命中后由 {@code TNFireBoltEntity} 统一处理：
 *       <b>10% 最大生命</b> + 范围内挂 <b>II 级焚身</b> + 爆炸 + 留熔岩地
 *       （见 {@link FireSpellRules#METEOR_BOLT}）</li>
 *   <li>陨石落地后再留 {@link #LINGER_TICKS} 让法阵收尾，然后消失</li>
 * </ol>
 *
 * <h2>法阵长什么样</h2>
 * 由 {@code client/TNSigilRenderer} 画<b>真正的魔法阵</b>（多层同心环 + 六芒星 +
 * 三个卫星圆 + 符文刻痕 + 中心螺旋），<b>不是一圈火焰粒子</b> ——
 * 作者 2026-10-05 明确要求过 ✗ 而且这座比熔岳天倾那座<b>更复杂</b>（{@code grand = true}）。
 *
 * <p>本类只负责"什么时候砸、砸哪"，外观与伤害分别归渲染器与火球实体管。
 */
public final class TNMeteorFallEntity extends Entity {

    /** 法阵半径（格）—— 作者要求"巨大的法阵"，所以比熔岳天倾的 3 格大一圈。 */
    public static final double SIGIL_RADIUS = 7.0D;
    /** 法阵亮多久之后陨石落下（2 秒，够看清法阵，也够躲）。 */
    public static final int CHARGE_TICKS = 40;
    /** 陨石落下后法阵再留多久（1 秒收尾）。 */
    public static final int LINGER_TICKS = 20;
    /** 陨石从生成到落地要多久（tick）—— 由高度 ÷ 速度算，别写死。 */
    public static final int DROP_TICKS =
            (int) Math.ceil(FireSpellRules.METEOR_DROP_HEIGHT / FireSpellRules.METEOR_FALL_SPEED);
    /**
     * 法阵总寿命。
     *
     * <p>⚠️ <b>必须覆盖到陨石落地</b>：作者 2026-10-05 实测「法阵消失了一会儿目标才收到伤害」——
     * 原来是写死的 {@code CHARGE + LINGER = 60} tick，而陨石落地要
     * {@code CHARGE + DROP} = 40 + 36 = 76 tick，法阵提前 16 tick 就没了 ✗
     */
    public static final int LIFE_TICKS = CHARGE_TICKS + DROP_TICKS + LINGER_TICKS;

    private static final EntityDataAccessor<Integer> AGE =
            SynchedEntityData.defineId(TNMeteorFallEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(TNMeteorFallEntity.class, EntityDataSerializers.INT);

    private UUID owner;
    private LivingEntity caster;
    private boolean dropped;

    public TNMeteorFallEntity(EntityType<? extends TNMeteorFallEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** 由法术入口调用（只在服务端）。{@code ground} 是"目标脚下的地面"。 */
    public static TNMeteorFallEntity cast(ServerLevel level, LivingEntity owner, Vec3 ground) {
        TNMeteorFallEntity sigil = new TNMeteorFallEntity(TNOrbEntities.METEOR_FALL.get(), level);
        sigil.owner = owner == null ? null : owner.getUUID();
        sigil.caster = owner;
        sigil.entityData.set(LIFE, LIFE_TICKS);
        sigil.setPos(ground.x, ground.y, ground.z);
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
        entityData.define(LIFE, LIFE_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        int age = age() + 1;
        entityData.set(AGE, age);

        // ⚠️ 位置**生成后不再变**（作者 2026-10-05）—— 法阵钉在目标的脚下地面上
        if (level().isClientSide) {
            // 法阵的形状由 TNSigilRenderer 用几何体画；粒子是**叠加的火焰装饰** ——
            // 作者 2026-10-05 要求「两个法阵加上火焰粒子效果凸显其实火系魔法」✓
            // ⚠️ 粒子和几何体是两件事，别用粒子代替几何体 ✗
            int ring = age >= CHARGE_TICKS ? 16 : 12;   // 陨石落下后法阵烧得更旺
            for (int i = 0; i < ring; i++) {
                double a = age * 0.015D + i * Math.PI * 2.0D / ring;
                level().addParticle(ParticleTypes.FLAME,
                        getX() + Math.cos(a) * SIGIL_RADIUS * 0.96D, getY() + 0.12D,
                        getZ() + Math.sin(a) * SIGIL_RADIUS * 0.96D,
                        0.0D, 0.012D, 0.0D);
            }
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * Math.PI * 2.0D;
                double d = Math.sqrt(random.nextDouble()) * SIGIL_RADIUS;
                level().addParticle(ParticleTypes.SMALL_FLAME,
                        getX() + Math.cos(a) * d, getY() + 0.15D, getZ() + Math.sin(a) * d,
                        0.0D, 0.025D, 0.0D);
            }
            return;
        }

        ServerLevel server = (ServerLevel) level();
        if (!dropped && age >= CHARGE_TICKS) {
            dropped = true;
            dropMeteor(server);
        }
        if (age > life()) {
            discard();
        }
    }

    /** 从法阵正上方砸下一颗大陨石（瞄准法阵中心，垂直落下）。 */
    private void dropMeteor(ServerLevel server) {
        Vec3 from = position().add(0.0D, FireSpellRules.METEOR_DROP_HEIGHT, 0.0D);
        TNFireBoltEntity meteor = new TNFireBoltEntity(TNOrbEntities.FIRE_BOLT.get(), server);
        meteor.configure(caster, FireSpellRules.METEOR_BOLT, "meteor_fall",
                from, new Vec3(0.0D, -FireSpellRules.METEOR_FALL_SPEED, 0.0D));
        server.addFreshEntity(meteor);

        // 跟随的**装饰小陨石**（作者 2026-10-05：「旁边可以跟随大小不一的陨石一起落下，
        // 跟随的陨石起装饰作用」）—— 系数 0，所以纯视觉、零伤害 ✓
        for (int i = 0; i < FireSpellRules.METEOR_SHARD_COUNT; i++) {
            double a = random.nextDouble() * Math.PI * 2.0D;
            double d = 0.6D + random.nextDouble() * FireSpellRules.METEOR_SHARD_SPREAD;
            double scale = FireSpellRules.METEOR_SHARD_MIN_SCALE
                    + random.nextDouble() * (FireSpellRules.METEOR_SHARD_MAX_SCALE
                            - FireSpellRules.METEOR_SHARD_MIN_SCALE);
            // 位置围着主陨石散开、高度也错开 —— 看着才像"一伙的"，不是并排的列队
            Vec3 at = from.add(Math.cos(a) * d, (random.nextDouble() - 0.5D) * 6.0D, Math.sin(a) * d);
            // 速度各自差一点 ⇒ 落地时间不同，不会"整齐划一"地砸下来
            double speed = FireSpellRules.METEOR_FALL_SPEED * (0.85D + random.nextDouble() * 0.35D);
            TNFireBoltEntity shard = new TNFireBoltEntity(TNOrbEntities.FIRE_BOLT.get(), server);
            shard.configureShard(caster, FireSpellRules.METEOR_SHARD, "meteor_shard",
                    at, new Vec3(0.0D, -speed, 0.0D), (float) scale);
            server.addFreshEntity(shard);
        }
        // 落下瞬间的一声"轰"之前，先在法阵中心攒一团火星
        server.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.3D, getZ(),
                30, SIGIL_RADIUS * 0.5D, 0.3D, SIGIL_RADIUS * 0.5D, 0.05D);
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
