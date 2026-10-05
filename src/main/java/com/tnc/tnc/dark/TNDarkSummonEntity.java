package com.tnc.tnc.dark;

import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * <b>暗系第五条链「召唤」的召唤物</b> ✓ —— 小恶魔 / 暗卫 / 暗之统领 / 暗之国王 / 邪神 ✓
 * （作者 2026-10-02："把暗魔法里面的五个召唤物都配上动作吧" ✓；
 * 2026-10-04："暗魔法的召唤流没实装吗" ⇒ 这就是实装 ✓）。
 *
 * <h2>一个类管五档 ✓ —— 档位由**实体类型**决定 ✗ 不是同步字段</h2>
 * 五个 geo 差别很大（5 / 8 / 10 / 11 / 18 骨骼 ✗），所以注册的是**五个实体类型** ✓
 * （{@code tnc:dark_imp} … {@code tnc:evil_god} ✓），渲染器按类型挑 geo/贴图/动画 ✓
 * —— 这样每档还能有自己的名字（小恶魔 / 暗卫 / … ✓）和中英文 lang ✓。
 *
 * <h2>行为（和光系的战斗天使同一套 ✓）</h2>
 * <ul>
 *   <li><b>悬空跟着主人</b> ✓：无重力 + 飞行移动控制 + 飞行寻路（它们是暗影，飘着走 ✓
 *       和 {@code walk} 动画那段"滑行"对得上 ✓）；</li>
 *   <li><b>替你打敌对生物</b> ✓（不打玩家、不打同类召唤物、不打天使/龙/阿波罗 ✓）；</li>
 *   <li>攻击时触发 {@code attack} 动作 ✓、寿命到点播 {@code death} 后消散 ✓。</li>
 * </ul>
 */
public class TNDarkSummonEntity extends Monster implements GeoEntity {

    /** 个头微调（×100 同步 ✓）—— 法术召唤时写入 ✓（默认 1.0 = 模型原尺寸 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_SCALE =
            SynchedEntityData.defineId(TNDarkSummonEntity.class, EntityDataSerializers.INT);

    private static final double HOVER_DISTANCE = 2.2D;
    private static final double HOVER_HEIGHT = 1.4D;
    private static final double TELEPORT_DISTANCE = 20.0D;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID ownerId;
    private int lifeTicks;

    public TNDarkSummonEntity(EntityType<? extends TNDarkSummonEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
        this.setCanPickUpLoot(false);
    }

    /**
     * 五档的属性表 ✓（作者没给数值，这张表是我定的 ✓，要改就改这里 ✓）。
     * 档位越高：血越厚、打得越疼 ✓。
     */
    public static AttributeSupplier.Builder createAttributes(int tier) {
        double hp = switch (tier) {
            case 1 -> 20.0D;
            case 2 -> 45.0D;
            case 3 -> 80.0D;
            case 4 -> 130.0D;
            default -> 220.0D;
        };
        double dmg = switch (tier) {
            case 1 -> 4.0D;
            case 2 -> 7.0D;
            case 3 -> 11.0D;
            case 4 -> 15.0D;
            default -> 21.0D;
        };
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, hp)
                .add(Attributes.ATTACK_DAMAGE, dmg)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FLYING_SPEED, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 2.0D * tier);
    }

    /** ★ 档位从**实体类型**读 ✓（一个类管五档 ✓）。 */
    public int tier() {
        EntityType<?> t = this.getType();
        if (t == TNOrbEntities.DARK_IMP.get()) return 1;
        if (t == TNOrbEntities.DARK_GUARD.get()) return 2;
        if (t == TNOrbEntities.DARK_LORD.get()) return 3;
        if (t == TNOrbEntities.DARK_KING.get()) return 4;
        return 5;
    }

    /** 这一档的 geo / 贴图 / 动画 ✓（文件名和档位一一对应 ✓，见 {@code dark/client}）。 */
    public String modelName() {
        return switch (tier()) {
            case 1 -> "dark_imp";
            case 2 -> "dark_guard";
            case 3 -> "dark_lord";
            case 4 -> "dark_king";
            default -> "evil_god";
        };
    }

    public static ResourceLocation geoOf(String name) {
        return ResourceLocation.fromNamespaceAndPath("tnc", "geo/entity/" + name + ".geo.json");
    }

    public static ResourceLocation textureOf(String name) {
        return ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/" + name + "_bedrock.png");
    }

    public static ResourceLocation animationOf(String name) {
        return ResourceLocation.fromNamespaceAndPath("tnc", "animations/entity/" + name + ".animation.json");
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SCALE, 100);
    }

    public double scale() {
        return this.entityData.get(DATA_SCALE) / 100.0D;
    }

    public void setScale(double scale) {
        this.entityData.set(DATA_SCALE, (int) Math.round(Mth.clamp(scale, 0.1D, 8.0D) * 100.0D));
    }

    public void setOwner(UUID owner) {
        this.ownerId = owner;
    }

    public UUID owner() {
        return this.ownerId;
    }

    public void setLifetime(int ticks) {
        this.lifeTicks = Math.max(0, ticks);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15D, true));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
        // 只打敌对生物 ✓（不打玩家、不打别的暗召/天使/龙/阿波罗 ✓）
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true,
                this::isValidDarkTarget));
    }

    /** 这个召唤物能打谁 ✓（不打玩家、不打别的召唤物/天使/阿波罗 ✓）。 */
    public boolean isValidDarkTarget(LivingEntity target) {
        // ⚠️ 这里**不能**写 instanceof TNDragonEntity ✗ —— 龙是投射物（不是 LivingEntity ✗），
        //    对 LivingEntity 写"不相干类型的 instanceof"是**编译错误** ✓（而且它本来也进不了这个目标表 ✓）
        if (target instanceof TNDarkSummonEntity || target instanceof com.tnc.tnc.light.TNFightingAngelEntity
                || target instanceof com.tnc.tnc.light.TNAngelEntity
                || target instanceof com.tnc.tnc.boss.TNApolloEntity) {
            return false;
        }
        return this.ownerId == null || !this.ownerId.equals(target.getUUID());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerId != null) {
            tag.putUUID("Owner", this.ownerId);
        }
        tag.putInt("Life", this.lifeTicks);
        tag.putInt("Scale", this.entityData.get(DATA_SCALE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            this.ownerId = tag.getUUID("Owner");
        }
        this.lifeTicks = tag.getInt("Life");
        if (tag.contains("Scale")) {
            this.entityData.set(DATA_SCALE, tag.getInt("Scale"));
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /** 主人的伤害不生效 ✓（自己人 ✗）。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (this.ownerId != null && attacker != null && this.ownerId.equals(attacker.getUUID())) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide() || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        // 寿命（走 tick 递减 ✓ —— 读档后 tickCount 归零 ✗）
        if (this.lifeTicks > 0) {
            this.lifeTicks--;
            if (this.lifeTicks == 0) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                        this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ(),
                        24, 0.5D, 0.6D, 0.5D, 0.03D);
                this.discard();
                return;
            }
        }
        // 暗影拖尾 ✓（黑烟 + 一点灵魂火 ✓）
        if (this.tickCount % 5 == 0) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                    this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ(),
                    1, 0.3D, 0.35D, 0.3D, 0.006D);
        }
        if (this.ownerId == null) {
            return;
        }
        Player owner = level.getPlayerByUUID(this.ownerId);
        if (owner == null || !owner.isAlive() || owner.level() != this.level()) {
            this.discard();
            return;
        }
        double d = this.distanceTo(owner);
        if (d > TELEPORT_DISTANCE) {
            this.getNavigation().stop();
            this.teleportTo(owner.getX(), owner.getY() + HOVER_HEIGHT, owner.getZ());
        } else if (this.getTarget() == null) {
            double angle = Math.toRadians(owner.getYRot() + 135.0F);
            this.getMoveControl().setWantedPosition(
                    owner.getX() + Math.cos(angle) * HOVER_DISTANCE,
                    owner.getY() + HOVER_HEIGHT + Math.sin(this.tickCount * 0.09D) * 0.15D,
                    owner.getZ() + Math.sin(angle) * HOVER_DISTANCE, 1.0D);
        } else if (this.tickCount % 20 == 0) {
            this.triggerAnim("action", "attack");
        }
    }

    @Override
    public void die(DamageSource source) {
        this.triggerAnim("action", "death");
        super.die(source);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 体态：移动播 walk（滑行 ✓）、静止播 idle ✓
        controllers.add(new AnimationController<>(this, "move", 4, state ->
                state.setAndContinue(RawAnimation.begin().thenLoop(
                        this.walkAnimation.isMoving() || this.getDeltaMovement().lengthSqr() > 0.0025D
                                ? "walk" : "idle"))));
        // 一次性动作 ✓（动作文件是这一档自己那份 ✓）
        AnimationController<TNDarkSummonEntity> action = new AnimationController<>(this, "action", 2,
                state -> PlayState.STOP);
        action.triggerableAnim("attack", RawAnimation.begin().thenPlay("attack"));
        action.triggerableAnim("cast", RawAnimation.begin().thenPlay("cast"));
        action.triggerableAnim("death", RawAnimation.begin().thenPlay("death"));
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }
}
