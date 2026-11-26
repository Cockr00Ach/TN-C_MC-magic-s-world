package com.tnc.tnc.mount;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.*;
import net.minecraft.world.damagesource.DamageSource;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Vanilla horse owns input packets, authority, jumps, dismount safety and passenger persistence. */
public final class LandMount extends Horse implements GeoEntity {
    private static final EntityDataAccessor<Integer> UNICORN_RUN_TICKS =
            SynchedEntityData.defineId(LandMount.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> AIR_POSE =
            SynchedEntityData.defineId(LandMount.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> SWIMMING =
            SynchedEntityData.defineId(LandMount.class, EntityDataSerializers.BOOLEAN);
    private static final int RUN_SYNC_INTERVAL = 3;
    private final AnimatableInstanceCache animationCache=GeckoLibUtil.createInstanceCache(this);
    // Server state and local rider prediction use the same tick rule.
    private int unicornRunTicks;
    private int lastRunUpdateTick = -1;
    private Vec3 lastRunPosition;
    private Vec3 lastMotionPosition;
    private final MountMotion.GroundMotion groundMotion = new MountMotion.GroundMotion();
    private final MountMotion.AirMotion airMotion = new MountMotion.AirMotion();
    private boolean riddenJumpThisTick;
    private boolean swimming;
    private Vec3 lastSnowVelocity;
    private int unassistedUntilTick;
    private float previousRunLean;
    private float runLean;

    public LandMount(EntityType<? extends Horse> type,Level level){super(type,level);setPersistenceRequired();setMaxUpStep(1.0f);}
    public String species(){return net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(getType()).getPath();}
    public boolean snow(){return species().equals("snow_wolf");}
    private boolean goat(){return species().equals("mountain_goat");}
    private boolean unicorn(){return getType() == LandMounts.UNICORN.get();}
    public static AttributeSupplier.Builder attributes(){return Horse.createBaseHorseAttributes().add(Attributes.MAX_HEALTH,40).add(Attributes.MOVEMENT_SPEED,.30).add(Attributes.JUMP_STRENGTH,.65);}
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(UNICORN_RUN_TICKS, 0);
        entityData.define(AIR_POSE, (byte) MountMotion.AirPose.GROUND.ordinal());
        entityData.define(SWIMMING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (UNICORN_RUN_TICKS.equals(key) && level().isClientSide && isControlledByLocalInstance()) {
            unicornRunTicks = MountMotion.reconcileUnicornRun(unicornRunTicks, entityData.get(UNICORN_RUN_TICKS));
        }
    }

    @Override protected void randomizeAttributes(RandomSource random){
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(snow()?44:40);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(goat()?.27:snow()?.30:.34);
        getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(goat()?1.0:snow()?.55:.65);
    }
    @Override public boolean canMate(Animal other){return false;}
    @Override public boolean canWearArmor(){return false;}
    // Constant on both logical sides: vanilla riding/jump control works without an item or saddle flag.
    @Override public boolean isSaddled(){return true;}
    @Override public boolean isSaddleable(){return false;}
    @Override public void equipSaddle(SoundSource source){}
    @Override public void openCustomInventoryScreen(Player player){}

    @Override public boolean canFreeze(){return !snow()&&super.canFreeze();}
    @Override public boolean canBeRiddenUnderFluidType(net.minecraftforge.fluids.FluidType type,net.minecraft.world.entity.Entity rider){return (snow()&&type==net.minecraftforge.common.ForgeMod.WATER_TYPE.get())||super.canBeRiddenUnderFluidType(type,rider);}
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand){
        ItemStack held=player.getItemInHand(hand);
        if(!isBaby()&&!isVehicle()&&!player.isSecondaryUseActive()&&held.isEmpty()){
            if(!level().isClientSide){
                if(!isTamed())tameWithName(player);
                doPlayerRide(player);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player,hand);
    }
    @Override
    public void tick() {
        super.tick();
        Vec3 here = position();
        Vec3 displacement = lastMotionPosition == null ? Vec3.ZERO : here.subtract(lastMotionPosition);
        lastMotionPosition = here;
        double horizontal = Math.sqrt(displacement.horizontalDistanceSqr());
        boolean immersed = isInWater() || isInLava();
        boolean teleported = horizontal > MountMotion.MAX_CONTIGUOUS_TRAVEL || Math.abs(displacement.y) > 3;
        if (level().isClientSide) {
            groundMotion.update(teleported ? Double.POSITIVE_INFINITY : horizontal,
                    MountMotion.Cadence.forSpecies(species()));
            previousRunLean = runLean;
            float target = unicorn() ? runProgress() : 0;
            runLean = Mth.approach(runLean, target, .2f);
        }
        if (!level().isClientSide || isControlledByLocalInstance()) {
            // Server sees accepted vehicle displacement. The local rider can predict its visual pose.
            MountMotion.AirPose pose = airMotion.update(onGround(), immersed || teleported,
                    displacement.y, riddenJumpThisTick, goat());
            swimming = !teleported && wantsToSwim();
            if (!level().isClientSide) {
                entityData.set(AIR_POSE, (byte) pose.ordinal());
                entityData.set(SWIMMING, swimming);
            }
        }
        riddenJumpThisTick = false;
    }

    @Override
    protected void executeRidersJump(float charge, Vec3 input) {
        super.executeRidersJump(charge, input);
        riddenJumpThisTick = getDeltaMovement().y > 0;
    }

    MountMotion.Gait visualGait() {
        return groundMotion.gait();
    }

    double visualSpeed() {
        return groundMotion.speed();
    }

    MountMotion.AirPose visualAirPose() {
        return isControlledByLocalInstance() ? airMotion.pose()
                : MountMotion.AirPose.fromWire(entityData.get(AIR_POSE));
    }

    boolean visualSwimming() {
        return isControlledByLocalInstance() ? swimming : entityData.get(SWIMMING);
    }

    private float runProgress() {
        int ticks = isControlledByLocalInstance() ? unicornRunTicks : entityData.get(UNICORN_RUN_TICKS);
        return MountMotion.unicornRunProgress(ticks);
    }

    public float visualRunLean(float partialTick) {
        return Mth.lerp(partialTick, previousRunLean, runLean);
    }

    @Override
    protected Vec2 getRiddenRotation(LivingEntity rider) {
        Vec2 desired = super.getRiddenRotation(rider);
        if (!unicorn()) {
            return desired;
        }
        // For a remote rider the server already receives the stepped yaw with the vanilla vehicle move.
        if (!isControlledByLocalInstance()) {
            return new Vec2(desired.x, getYRot());
        }
        return rider.zza > 0 ? new Vec2(desired.x, MountMotion.steeredYaw(getYRot(), desired.y, runProgress()))
                : desired;
    }

    @Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source){return super.causeFallDamage(goat()?Math.max(0,distance-5):distance,multiplier,source);}
    @Override
    protected void tickRidden(Player player, Vec3 riddenInput) {
        super.tickRidden(player, riddenInput);
        if (unicorn() && lastRunUpdateTick != tickCount
                && (!level().isClientSide || isControlledByLocalInstance())) {
            lastRunUpdateTick = tickCount;
            // An uninterrupted run needs both rider input and real forward travel.
            Vec3 here = position();
            Vec3 displacement = lastRunPosition == null ? Vec3.ZERO : here.subtract(lastRunPosition);
            lastRunPosition = here;
            float yaw = getYRot() * Mth.DEG_TO_RAD;
            double forward = displacement.z * Mth.cos(yaw) - displacement.x * Mth.sin(yaw);
            int previous = unicornRunTicks;
            unicornRunTicks = MountMotion.advanceUnicornRun(unicornRunTicks,
                    riddenInput.z > 0, forward > 0.025
                            && displacement.horizontalDistanceSqr() <= MountMotion.MAX_CONTIGUOUS_TRAVEL_SQR);
            if (!level().isClientSide && MountMotion.isUnicornRunAtCap(unicornRunTicks)
                    && !MountMotion.isUnicornRunAtCap(previous)) {
                playSound(SoundEvents.HORSE_BREATHE, .42f, 1.24f);
            }
            if (!level().isClientSide && (tickCount % RUN_SYNC_INTERVAL == 0
                    || unicornRunTicks == 0 || MountMotion.isUnicornRunAtCap(unicornRunTicks))) {
                entityData.set(UNICORN_RUN_TICKS, unicornRunTicks);
            }
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        unicornRunTicks = 0;
        lastRunPosition = null;
        lastRunUpdateTick = -1;
        lastSnowVelocity = null;
        unassistedUntilTick = 0;
        swimming = false;
        if (!level().isClientSide) {
            entityData.set(UNICORN_RUN_TICKS, 0);
            entityData.set(SWIMMING, false);
        }
    }

    @Override protected float getRiddenSpeed(Player player){
        return super.getRiddenSpeed(player) * switch (species()) {
            case "mountain_goat" -> 1.5f;
            case "snow_wolf" -> 1.7f;
            case "rainbow_unicorn" -> MountMotion.unicornSpeedMultiplier(unicornRunTicks);
            default -> 1.0f;
        };
    }

    private boolean wantsToSwim() {
        return snow() && getControllingPassenger() instanceof Player && isInWater()
                && MountMotion.swimming(getFluidHeight(FluidTags.WATER), swimming);
    }

    @Override
    public void travel(Vec3 input) {
        if (!snow() || !isControlledByLocalInstance() || !(getControllingPassenger() instanceof Player)) {
            super.travel(input);
            return;
        }
        Vec3 velocity = getDeltaMovement();
        if (riddenJumpThisTick || (lastSnowVelocity != null
                && velocity.subtract(lastSnowVelocity).horizontalDistanceSqr() > .0004)) {
            unassistedUntilTick = tickCount + 4;
        }
        if (wantsToSwim()) {
            travelSwimming(input, velocity);
        } else if (onGround() && !isInWater() && !isInLava()) {
            travelOnSnowWolfGround(input, velocity);
        } else {
            super.travel(input);
        }
        lastSnowVelocity = getDeltaMovement();
    }

    private void travelOnSnowWolfGround(Vec3 input, Vec3 velocity) {
        BlockPos under = getBlockPosBelowThatAffectsMyMovement();
        BlockState surface = level().getBlockState(under);
        if (!surface.is(BlockTags.ICE)) {
            super.travel(input);
            return;
        }
        float friction = surface.getFriction(level(), under, this);
        if (friction <= MountMotion.NORMAL_FRICTION) {
            super.travel(input);
            return;
        }
        boolean useGrip = tickCount >= unassistedUntilTick && velocity.y <= .04;
        if (useGrip && input.horizontalDistanceSqr() > 1e-6) {
            setDeltaMovement(MountMotion.iceSteer(velocity, input.yRot(-getYRot() * Mth.DEG_TO_RAD)));
        }
        float speed = getSpeed();
        setSpeed(speed * MountMotion.iceInputCompensation(friction));
        try {
            super.travel(input);
        } finally {
            setSpeed(speed);
        }
        if (useGrip && onGround()) {
            Vec3 moved = getDeltaMovement();
            double grip = MountMotion.NORMAL_FRICTION / friction;
            setDeltaMovement(moved.x * grip, moved.y, moved.z * grip);
        }
    }

    private void travelSwimming(Vec3 input, Vec3 velocity) {
        Vec3 intent = new Vec3(input.x, 0, input.z).yRot(-getYRot() * Mth.DEG_TO_RAD);
        double height = getFluidHeight(FluidTags.WATER);
        Vec3 controlled = MountMotion.swimVelocity(velocity, intent, getSpeed(), height, tickCount >= unassistedUntilTick);
        setDeltaMovement(controlled);
        // Vanilla still handles fluid drag, shore stepping, currents and collision.
        super.travel(new Vec3(0, input.y, 0));
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (!unicorn() || !isVehicle()) {
            super.playStepSound(pos, state);
        }
    }

    void playAnimatedHoofbeat() {
        if (!level().isClientSide || !unicorn() || !isVehicle() || isSilent() || isInWater()
                || visualAirPose() != MountMotion.AirPose.GROUND || visualSpeed() < .015) {
            return;
        }
        BlockPos under = getOnPos();
        BlockState block = level().getBlockState(under);
        if (!block.isAir()) {
            var sound = block.getSoundType(level(), under, this);
            level().playLocalSound(getX(), getY(), getZ(), sound.getStepSound(), getSoundSource(),
                    .40f * sound.getVolume(), .93f * sound.getPitch(), false);
        }
    }
    @Override public double getPassengersRidingOffset() {
        return MountRiderPose.forSpecies(species()).ridingOffset();
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (passenger instanceof Player player && hasPassenger(player)) {
            float yaw = getYRot() * Mth.DEG_TO_RAD;
            double rear = MountRiderPose.forSpecies(species()).rearOffset();
            move.accept(player, getX() + rear * Mth.sin(yaw),
                    getY() + getPassengersRidingOffset() + player.getMyRidingOffset(),
                    getZ() - rear * Mth.cos(yaw));
        } else {
            super.positionRider(passenger, move);
        }
    }
    @Override protected SoundEvent getAmbientSound(){return goat()?SoundEvents.GOAT_AMBIENT:snow()?SoundEvents.WOLF_AMBIENT:super.getAmbientSound();}
    @Override protected SoundEvent getHurtSound(DamageSource source){return goat()?SoundEvents.GOAT_HURT:snow()?SoundEvents.WOLF_HURT:super.getHurtSound(source);}
    @Override protected SoundEvent getDeathSound(){return goat()?SoundEvents.GOAT_DEATH:snow()?SoundEvents.WOLF_DEATH:super.getDeathSound();}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){
        controllers.add(new LandMountAnimationController(this));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return animationCache;}
}
