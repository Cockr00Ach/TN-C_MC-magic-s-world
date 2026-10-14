package com.tnc.tnc.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 圣渊利维坦：深海兽形水系首领。深蓝鳞甲和外露獠牙负责“凶兽”，蓝金圣甲、
 * 发光额冠和脊柱符文负责“神圣”。水中游泳，离水后飞行，始终不用地面步行寻路。
 */
public final class TNWaterLeviathanEntity extends Monster implements GeoEntity {
    public static final float MAX_HP = 4200.0F;
    public static final float HITBOX_WIDTH = 4.8F;
    public static final float HITBOX_HEIGHT = 4.1F;

    private static final int PHASE_ONE = 1;
    private static final int PHASE_TWO = 2;
    private static final int ACTION_NONE = 0;
    private static final int ACTION_SPELL = 1;
    private static final String ACTION_CONTROLLER = "action";

    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(TNWaterLeviathanEntity.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int pendingAction = ACTION_NONE;
    private int actionTicks;
    private int attackCooldown = 70;
    private String pendingSpell;
    private int basicCasts;
    private int specialCooldown;
    private int nextSpecial;
    private PathNavigation airNavigation;
    private PathNavigation waterNavigation;
    private boolean swimmingMovement;

    public TNWaterLeviathanEntity(EntityType<? extends TNWaterLeviathanEntity> type, Level level) {
        super(type, level);
        this.airNavigation = this.navigation;
        this.waterNavigation = new WaterBoundPathNavigation(this, level);
        this.moveControl = new FlyingMoveControl(this, 12, true);
        this.setNoGravity(true);
        this.xpReward = 1200;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanPassDoors(false);
        navigation.setCanFloat(false);
        return navigation;
    }

    @Override
    public void tick() {
        this.setNoGravity(this.isAlive());
        if (!this.level().isClientSide && this.swimmingMovement != this.isInWater()) {
            this.navigation.stop();
            this.swimmingMovement = this.isInWater();
            this.navigation = this.swimmingMovement ? this.waterNavigation : this.airNavigation;
            this.moveControl = this.swimmingMovement
                    ? new SmoothSwimmingMoveControl(this, 85, 10, 0.035F, 0.1F, false)
                    : new FlyingMoveControl(this, 12, true);
        }
        super.tick();
        this.fallDistance = 0.0F;
    }

    @Override
    public void travel(Vec3 input) {
        if (this.isEffectiveAi() && this.isAlive()) {
            this.moveRelative(this.isInWater() ? 0.035F : 0.045F, input);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(this.isInWater() ? 0.88D : 0.91D));
        } else {
            super.travel(input);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ATTACK_DAMAGE, 26.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.19D)
                .add(Attributes.FLYING_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, 56.0D)
                .add(Attributes.ARMOR, 18.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 10.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PHASE, PHASE_ONE);
    }

    public int phase() {
        return this.entityData.get(DATA_PHASE);
    }

    public boolean isPhaseTwo() {
        return this.phase() >= PHASE_TWO;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("LeviathanPhase", this.phase());
        tag.putInt("LeviathanSpecialCooldown", specialCooldown);
        tag.putInt("LeviathanNextSpecial", nextSpecial);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_PHASE,
                tag.getInt("LeviathanPhase") >= PHASE_TWO ? PHASE_TWO : PHASE_ONE);
        // Attributes are already restored by LivingEntity; never multiply them again on reload.
        this.pendingAction = ACTION_NONE;
        this.pendingSpell = null;
        this.specialCooldown = Math.max(0,tag.getInt("LeviathanSpecialCooldown"));
        this.nextSpecial = Math.floorMod(tag.getInt("LeviathanNextSpecial"),3);
        this.attackCooldown = 70;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new LeviathanMoveGoal());
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 56.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public MobType getMobType() {
        return MobType.WATER;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(7.0D, 3.0D, 7.0D);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !this.level().isClientSide()) {
            this.triggerAnim(ACTION_CONTROLLER, "bite");
        }
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.bossEvent.setName(this.getDisplayName());
        this.bossEvent.setProgress(Math.max(0.0F, Math.min(1.0F,
                this.getHealth() / Math.max(1.0F, this.getMaxHealth()))));
        this.bossEvent.setVisible(this.isAlive() && !this.isDeadOrDying());
        if (!this.isAlive() || this.isDeadOrDying() || this.isNoAi()) return;

        if (!this.isPhaseTwo() && this.getHealth() <= this.getMaxHealth() * 0.5F) {
            this.enterPhaseTwo(level);
        }
        this.sacredAura(level);
        this.tickSpecialAttack(level);
    }

    private void enterPhaseTwo(ServerLevel level) {
        this.entityData.set(DATA_PHASE, PHASE_TWO);
        this.pendingAction = ACTION_NONE;
        this.pendingSpell = null;
        this.attackCooldown = 50;
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(speed.getBaseValue() * 1.18D);
        AttributeInstance damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(damage.getBaseValue() * 1.25D);
        AttributeInstance flight = this.getAttribute(Attributes.FLYING_SPEED);
        if (flight != null) flight.setBaseValue(flight.getBaseValue() * 1.18D);

        this.triggerAnim(ACTION_CONTROLLER, "roar");
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 3.5F, 0.72F);
        level.sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 1.5D, this.getZ(),
                220, 4.8D, 2.2D, 4.8D, 0.24D);
        level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 3.4D, this.getZ(),
                110, 3.2D, 1.8D, 3.2D, 0.12D);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                this.getBoundingBox().inflate(72.0D))) {
            player.displayClientMessage(Component.literal(
                    "§3[圣渊] §b利维坦的蓝金圣甲正在苏醒"), false);
        }
    }

    private void sacredAura(ServerLevel level) {
        int interval = this.isPhaseTwo() ? 3 : 7;
        if (this.tickCount % interval != 0) return;
        int count = this.isPhaseTwo() ? 7 : 3;
        for (int i = 0; i < count; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double radius = 1.4D + this.random.nextDouble() * 3.8D;
            double x = this.getX() + Math.cos(angle) * radius;
            double z = this.getZ() + Math.sin(angle) * radius;
            double y = this.getY() + 0.4D + this.random.nextDouble() * 4.0D;
            level.sendParticles(i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.BUBBLE_POP,
                    x, y, z, 1, 0.03D, 0.05D, 0.03D, 0.01D);
        }
    }

    private void tickSpecialAttack(ServerLevel level) {
        if (this.specialCooldown>0) --this.specialCooldown;
        var target=this.getTarget();
        if (this.pendingAction != ACTION_NONE) {
            if (--this.actionTicks <= 0) {
                boolean cast=target!=null && this.canStrike(target) && this.hasLineOfSight(target)
                        && LeviathanSpells.cast(this,target,this.pendingSpell);
                if (cast) {
                    if (LeviathanSpellRules.BASIC.equals(this.pendingSpell)) {
                        this.basicCasts=Math.min(3,this.basicCasts+1);
                    } else {
                        this.specialCooldown=LeviathanSpellRules.specialCooldown(this.pendingSpell,this.isPhaseTwo());
                        this.basicCasts=0;
                        this.nextSpecial=(this.nextSpecial+1)%3;
                    }
                }
                this.pendingAction = ACTION_NONE;
                this.pendingSpell=null;
                this.attackCooldown=LeviathanSpellRules.recovery(this.isPhaseTwo());
            }
            return;
        }

        if (target==null || !this.canStrike(target) || this.distanceToSqr(target)>48.0D*48.0D
                || !this.hasLineOfSight(target) || --this.attackCooldown>0) return;
        this.pendingSpell=LeviathanSpellRules.next(this.basicCasts,this.specialCooldown,this.nextSpecial);
        this.pendingAction=ACTION_SPELL;
        this.actionTicks=LeviathanSpellRules.windup(this.pendingSpell,this.isPhaseTwo());
        this.getLookControl().setLookAt(target, 30, 30);
        this.triggerAnim(ACTION_CONTROLLER, "breath");
        boolean basic=LeviathanSpellRules.BASIC.equals(this.pendingSpell);
        level.playSound(null,this.getX(),this.getY(),this.getZ(),SoundEvents.CONDUIT_ACTIVATE,
                SoundSource.HOSTILE,basic?1.4F:2.5F,basic?0.9F:0.58F);
        level.sendParticles(ParticleTypes.END_ROD,this.getX(),this.getY()+2.6D,this.getZ(),
                basic?8:28,1.1D,0.65D,1.1D,0.04D);
    }

    private boolean canStrike(net.minecraft.world.entity.LivingEntity victim) {
        return victim != this && victim.isAlive() && !this.isAlliedTo(victim)
                && !(victim instanceof TNWaterLeviathanEntity)
                && !(victim instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 4, state -> {
            if (this.isInWater()) {
                state.getController().setAnimationSpeed(1.15F);
                return state.setAndContinue(RawAnimation.begin().thenLoop("swim"));
            }
            state.getController().setAnimationSpeed(state.isMoving() ? 1.0F : 0.72F);
            return state.setAndContinue(RawAnimation.begin().thenLoop("glide"));
        }));
        AnimationController<TNWaterLeviathanEntity> action = new AnimationController<>(
                this, ACTION_CONTROLLER, 2, state -> PlayState.STOP);
        action.triggerableAnim("bite", RawAnimation.begin().thenPlay("bite"));
        action.triggerableAnim("roar", RawAnimation.begin().thenPlay("roar"));
        action.triggerableAnim("breath", RawAnimation.begin().thenPlay("breath"));
        action.triggerableAnim("tail", RawAnimation.begin().thenPlay("tail_sweep"));
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    public static ResourceLocation modelResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "geo/entity/water_leviathan.geo.json");
    }

    public static ResourceLocation textureResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/water_leviathan_bedrock.png");
    }

    public static ResourceLocation animationResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "animations/entity/water_leviathan.animation.json");
    }

    /** One three-dimensional movement goal; neither mode asks for a walkable ground node. */
    private final class LeviathanMoveGoal extends Goal {
        private int repath;
        private Vec3 destination;

        private LeviathanMoveGoal() {
            this.setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override public boolean canUse() { return true; }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void stop() { navigation.stop(); }

        @Override public void tick() {
            var target = getTarget();
            boolean chasing = target != null && target.isAlive() && canStrike(target);
            if (chasing) {
                getLookControl().setLookAt(target, 12.0F, 12.0F);

            }
            if (pendingAction != ACTION_NONE) {
                navigation.stop();
                setDeltaMovement(getDeltaMovement().scale(0.8D));
                return;
            }
            if (chasing && swimmingMovement) {
                // Large water mobs can finish a path while their feet are still below
                // its final node. Keep depth control independent of that path tolerance.
                double depth = target.getY()+(target.isInWater() ? 0 : 3.0D)-getY();
                double vertical = LeviathanMovementRules.swimVerticalAcceleration(depth);
                Vec3 velocity = getDeltaMovement();
                setDeltaMovement(velocity.x,
                        net.minecraft.util.Mth.clamp(velocity.y+vertical, -0.20D, 0.20D), velocity.z);
            }
            if (--repath > 0) return;
            repath = 12;
            if (chasing) {
                destination = swimmingMovement
                        ? target.position().add(0, target.isInWater() ? 0 : 3.0D, 0)
                        : target.position().add(0, LeviathanMovementRules.AIR_TARGET_HEIGHT, 0);
            } else if (destination == null || position().distanceToSqr(destination) < 9.0D
                    || tickCount % 120 < 12) {
                destination = position().add((random.nextDouble()-0.5D)*16.0D,
                        (random.nextDouble()-0.5D)*3.0D, (random.nextDouble()-0.5D)*16.0D);
            }
            if (!level().hasChunkAt(BlockPos.containing(destination))) {
                navigation.stop();
                destination = null;
                return;
            }
            if (!swimmingMovement) {
                double floor = nearbyFloor();
                destination = new Vec3(destination.x,
                        LeviathanMovementRules.airDestinationY(destination.y, floor,
                                level().getMaxBuildHeight()-HITBOX_HEIGHT-1), destination.z);
            }
            double speed = chasing ? 1.0D : 0.60D;
            if (!navigation.moveTo(destination.x, destination.y, destination.z, speed)) {
                // Direct steering also permits crossing the air/water boundary when the
                // current medium's pathfinder cannot yet accept the next medium's node.
                moveControl.setWantedPosition(destination.x, destination.y, destination.z, speed);
            }
        }

        private double nearbyFloor() {
            double floor = getY()-10.0D;
            for (double[] offset : new double[][]{{0, 0}, {-2, 0}, {2, 0}, {0, -2}, {0, 2}}) {
                Vec3 from = position().add(offset[0], 0.25D, offset[1]);
                HitResult hit = level().clip(new ClipContext(from, from.add(0, -10, 0),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, TNWaterLeviathanEntity.this));
                if (hit.getType() != HitResult.Type.MISS) floor = Math.max(floor, hit.getLocation().y);
            }
            return floor;
        }
    }
}
