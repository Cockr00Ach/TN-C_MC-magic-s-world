package com.tnc.tnc.magic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.UUID;

/**
 * ★ <b>风灵</b> —— 风系第二条链「风球 / 风灵」的召唤物真身（作者 2026-10-10）。
 *
 * <h2>作者原话 → 这一版改了什么</h2>
 * 「风灵链怎么五个全是放一个围绕着玩家的小风球啊，**风灵呢**，我觉得你可以抄一下小精灵的模型」✗
 * —— 之前这一整条链是<b>纯粒子</b>（{@code TNWindMechanics.tickOrbs} 每 tick 在玩家周围画
 * {@code CLOUD} 粒子），玩家**根本看不见"风灵"** ✗。
 * 现在每一只都是**真实体**：一个小巧的有翼光灵（模型
 * {@code assets/tnc/geo/entity/wind_sprite.geo.json}，程序化生成，见
 * {@code tools/gen_wind_sprite_model.py}）✓。
 *
 * <h2>为什么是 {@link Entity} 而不是 {@code Monster}</h2>
 * 它是**纯服务端控位置、客户端只画外观**的召唤物 ✓（和环绕雷球 {@link TNThunderOrbEntity}
 * 同一套思路 —— 那个类正是本仓记录的"召唤物模板"✓）：不用 AI、不吃重力、不可被攻击、
 * 不会寻路 ⇒ 位置每 tick 自己算，客户端只负责渲染 ✓ 行为**可预期**（不会因为寻路卡住而乱飞 ✗）。
 * 代价：它不是 {@code LivingEntity}，所以没有血条、也不会替玩家挡刀 ✓（这一档不需要）。
 *
 * <h2>档位从哪来</h2>
 * {@link TNWindMechanics} 的那张档位表是**唯一来源** ✓（本类只存一个 {@link #DATA_TIER}
 * 同步给客户端，用来选缩放 ✓）。档位一变，本实体**自己 discard** ✓ ——
 * 于是"升档"不需要维护方去逐个收拾，只管补数量就行 ✓。
 */
public class TNWindSpiritEntity extends Entity implements GeoEntity {

    /** GeckoLib 的动画缓存（每个实体一份 ✓）。 */
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /**
     * 只有一个 {@code idle} 动作 ✓（生成器写死的那个：悬停上下 + 翅膀快扇 + 呆毛/尾巴慢摆）。
     *
     * <p>为什么不做"攻击"动作：它每 10 tick 就打一下，动作会一直在播 ✗ ——
     * 攻击的表现交给命中粒子（{@link #zap}）✓，翅膀一直在扇反而更像"活着的小精灵" ✓。
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 4,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop("idle"))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    /** 档位 1..5（同步给客户端：渲染器按它缩放 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_SLOT =
            SynchedEntityData.defineId(TNWindSpiritEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TIER =
            SynchedEntityData.defineId(TNWindSpiritEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> WIND_ATTACK = SynchedEntityData.defineId(TNWindSpiritEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> WIND_FORM = SynchedEntityData.defineId(TNWindSpiritEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> WIND_DX = SynchedEntityData.defineId(TNWindSpiritEntity.class, EntityDataSerializers.FLOAT),
            WIND_DY = SynchedEntityData.defineId(TNWindSpiritEntity.class, EntityDataSerializers.FLOAT),
            WIND_DZ = SynchedEntityData.defineId(TNWindSpiritEntity.class, EntityDataSerializers.FLOAT);

    /** 环绕半径（格）—— 风灵贴着玩家转，比雷球（6 格）近得多 ✓。 */
    private static final double ORBIT_RADIUS = 1.9D;
    /** 环绕高度（相对玩家脚底，格）。 */
    private static final double ORBIT_HEIGHT = 1.15D;
    /** 绕一圈多久（tick）。 */
    private static final int ORBIT_PERIOD = 80;
    /** 几 tick 打一次最近的敌人（每只风灵**各打各的** ⇒ 只数多 = 伤害高 ✓）。 */
    private static final int ZAP_INTERVAL = 10;

    private UUID ownerId;
    private int slot;
    private double orbitSeed;

    public TNWindSpiritEntity(EntityType<? extends TNWindSpiritEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);          // 不可被攻击（它不是战斗单位 ✓）
        this.orbitSeed = 0.0D;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_TIER, 1);
        this.entityData.define(DATA_SLOT, 0);
        entityData.define(WIND_ATTACK,-1000L);entityData.define(WIND_FORM,0);
        entityData.define(WIND_DX,0F);entityData.define(WIND_DY,0F);entityData.define(WIND_DZ,1F);
    }

    public long windAttackTime(){return entityData.get(WIND_ATTACK);}
    public int windWeaponForm(){return entityData.get(WIND_FORM);}
    public Vec3 windAttackDirection(){return new Vec3(entityData.get(WIND_DX),entityData.get(WIND_DY),entityData.get(WIND_DZ));}
    public void markWindAttack(int form,Vec3 direction){
        entityData.set(WIND_ATTACK,level().getGameTime());entityData.set(WIND_FORM,form);
        entityData.set(WIND_DX,(float)direction.x);entityData.set(WIND_DY,(float)direction.y);entityData.set(WIND_DZ,(float)direction.z);
    }

    // ---------------- 绑定 / 查询 ----------------

    /** 由维护方（{@link TNWindMechanics}）在生成时调用 ✓。 */
    public void bind(ServerPlayer owner, int slot, int tier, double orbitSeed) {
        this.ownerId = owner.getUUID();
        this.slot = slot;
        this.entityData.set(DATA_SLOT, slot);
        this.orbitSeed = orbitSeed;
        this.entityData.set(DATA_TIER, tier);
    }

    /** 档位（1..5）。 */
    public int tier() {
        return Math.max(1, this.entityData.get(DATA_TIER));
    }

    /** 这一只是不是 {@code owner} 的（维护方用它数数量 ✓）。 */
    public boolean isOwnedBy(ServerPlayer owner) {
        return this.ownerId != null && this.ownerId.equals(owner.getUUID());
    }

    /** 槽位（0..count-1）—— 决定它在环上的相位 ✓。 */
    public int slot() {
        return this.entityData.get(DATA_SLOT);
    }

    private ServerPlayer resolveOwner() {
        if (this.ownerId == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(this.ownerId);
        if (owner == null || owner.isRemoved() || owner.level() != this.level()) {
            return null;
        }
        return owner;
    }

    // ---------------- 每 tick ----------------

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;                                   // 位置由服务端同步过来 ✓
        }
        ServerPlayer owner = resolveOwner();
        if (owner == null || !TNWindMechanics.hasSpirits(owner)) {
            discard();
            return;
        }
        if (TNWindMechanics.currentTier(owner) != tier()) {
            discard();                                // 升/降档：本体自己退场，维护方补新的 ✓
            return;
        }

        // 绕主人转：相位 = 全局时间 + 自己的槽位 + 一点点随机，免得几只完全重叠 ✓
        int count = Math.max(1, TNWindMechanics.orbsForTier(tier()));
        double phase = (this.level().getGameTime() % ORBIT_PERIOD) / (double) ORBIT_PERIOD * Math.PI * 2.0;
        double angle = phase + this.slot * (Math.PI * 2.0 / count) + this.orbitSeed;
        double x = owner.getX() + Math.cos(angle) * ORBIT_RADIUS;
        double z = owner.getZ() + Math.sin(angle) * ORBIT_RADIUS;
        double bob = Math.sin((this.level().getGameTime() + this.slot * 13L) * 0.12D) * 0.16D;
        setPos(x, owner.getY() + ORBIT_HEIGHT + bob, z);
        setYRot(owner.getYRot());                     // 跟着主人的朝向，像"护法"✓
        setXRot(0.0F);

        if (this.tickCount % ZAP_INTERVAL == 0) {
            zap(owner);
        }
    }

    /** Orb crescents and elemental weapons share one real projectile damage path. */
    private void zap(ServerPlayer owner) {
        com.tnc.tnc.magic.wind.WindArsenal.attack(this, owner);
    }

    @Override public AABB getBoundingBoxForCulling(){return super.getBoundingBoxForCulling().inflate(2.2);}

    // ---------------- 存档 ----------------

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.slot = tag.getInt("Slot");
        this.entityData.set(DATA_SLOT, slot);
        this.orbitSeed = tag.getDouble("Seed");
        this.entityData.set(DATA_TIER, Math.max(1, tag.getInt("Tier")));
        if (tag.hasUUID("Owner")) {
            this.ownerId = tag.getUUID("Owner");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Slot", this.slot);
        tag.putDouble("Seed", this.orbitSeed);
        tag.putInt("Tier", tier());
        if (this.ownerId != null) {
            tag.putUUID("Owner", this.ownerId);
        }
    }

    /** 没有"受到伤害"这回事 ✓（不可被攻击；留这个覆写是为了不被别的效果打死）。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }
}
