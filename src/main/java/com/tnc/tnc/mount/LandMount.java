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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
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
    private static final EntityDataAccessor<Float> SWIM_DISTANCE =
            SynchedEntityData.defineId(LandMount.class, EntityDataSerializers.FLOAT);
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
    private final MountMotion.RunningJump runningJump = new MountMotion.RunningJump();
    private boolean swimming;
    private boolean tiredWarningSent;
    private int swimEntryTicks;
    private int drySwimRecoveryTicks;
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
        entityData.define(SWIM_DISTANCE, 0f);
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
        if (lastMotionPosition != null
                && position().distanceToSqr(lastMotionPosition) > MountMotion.MAX_CONTIGUOUS_TRAVEL_SQR) {
            runningJump.reset();
        }
        boolean startedGrounded = onGround();
        boolean startedSwimming = wantsToSwim();
        super.tick();
        Vec3 here = position();
        Vec3 displacement = lastMotionPosition == null ? Vec3.ZERO : here.subtract(lastMotionPosition);
        lastMotionPosition = here;
        double horizontal = Math.sqrt(displacement.horizontalDistanceSqr());
        boolean immersed = isInWater() || isInLava();
        boolean teleported = horizontal > MountMotion.MAX_CONTIGUOUS_TRAVEL || Math.abs(displacement.y) > 3;
        if (isControlledByLocalInstance()) {
            boolean validRun = startedGrounded && onGround() && !immersed && !teleported
                    && getControllingPassenger() instanceof Player && !horizontalCollision;
            runningJump.recordGround(validRun ? displacement : Vec3.ZERO);
        }
        if (teleported || immersed) {
            runningJump.stop();
        }
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
                    displacement.y, horizontal, riddenJumpThisTick, goat());
            swimming = !teleported && wantsToSwim();
            if (!level().isClientSide) {
                entityData.set(AIR_POSE, (byte) pose.ordinal());
                entityData.set(SWIMMING, swimming);
                if (snow() && !teleported) {
                    updateSwimDistance(startedSwimming && swimming, displacement);
                }
            }
        }
        riddenJumpThisTick = false;
    }

    @Override
    protected void executeRidersJump(float charge, Vec3 input) {
        if (wantsToSwim()) {
            return;
        }
        super.executeRidersJump(charge, input);
        riddenJumpThisTick = getDeltaMovement().y > 0;
        if (riddenJumpThisTick && input.z > 0 && !isInWater() && !isInLava()) {
            runningJump.start(species(), runProgress(), worldIntent(input));
        }
    }

    private Vec3 worldIntent(Vec3 input) {
        return new Vec3(input.x, 0, input.z).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 input) {
        Vec3 vanilla = super.getRiddenInput(player, input);
        if (unicorn() && vanilla.lengthSqr() == 0 && isStanding() && player.zza > 0
                && (runningJump.hasRunUp() || (!isControlledByLocalInstance() && unicornRunTicks > 0))) {
            return new Vec3(player.xxa * .5f, 0, player.zza);
        }
        return vanilla;
    }

    @Override
    public Vec3 handleRelativeFrictionAndCalculateMovement(Vec3 input, float friction) {
        if (!runningJump.active()) {
            return super.handleRelativeFrictionAndCalculateMovement(input, friction);
        }
        // Account for vanilla's moveRelative before filling the remaining forward-speed deficit.
        float acceleration = onGround() ? getSpeed() * (.21600002f / (friction * friction * friction))
                : getFlyingSpeed();
        Vec3 intent = worldIntent(input);
        Vec3 vanillaInput = intent.lengthSqr() < 1e-7 ? Vec3.ZERO
                : intent.scale(acceleration / Math.max(1, input.length()));
        setDeltaMovement(runningJump.beforeMove(getDeltaMovement(), vanillaInput));
        Vec3 from = position();
        Vec3 motion = super.handleRelativeFrictionAndCalculateMovement(input, friction);
        runningJump.afterMove(position().subtract(from), horizontalCollision || onGround() || verticalCollision);
        return motion;
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
            boolean validForward = forward > .025
                    && displacement.horizontalDistanceSqr() <= MountMotion.MAX_CONTIGUOUS_TRAVEL_SQR;
            if (riddenInput.z <= 0 || isInWater() || isInLava() || horizontalCollision) {
                unicornRunTicks = 0;
            } else if (onGround() && !riddenJumpThisTick) {
                unicornRunTicks = MountMotion.advanceUnicornRun(unicornRunTicks, true, validForward);
            }
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
        runningJump.reset();
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
        return snow() && isInWater()
                && MountMotion.swimming(getFluidHeight(FluidTags.WATER), swimming, onGround());
    }

    double swimSpeedFactor() {
        return MountMotion.swimSpeedFactor(entityData.get(SWIM_DISTANCE));
    }

    private void updateSwimDistance(boolean paddling, Vec3 displacement) {
        float before = entityData.get(SWIM_DISTANCE);
        float distance = before;
        if (paddling || !onGround() || isInWater() || isInLava()) {
            drySwimRecoveryTicks = 0;
        }
        if (paddling && getControllingPassenger() instanceof Player rider) {
            Vec3 intent = worldIntent(new Vec3(rider.xxa * .5f, 0, rider.zza > 0 ? rider.zza : rider.zza * .25f));
            BlockPos cell = blockPosition();
            var fluid = level().getFluidState(cell);
            Vec3 current = fluid.is(FluidTags.WATER) ? fluid.getFlow(level(), cell).scale(.014) : Vec3.ZERO;
            double speed = getRiddenSpeed(rider);
            distance = MountMotion.recordSwimDistance(before, displacement, current, intent, speed);
            if (!tiredWarningSent && distance >= MountMotion.SWIM_WARNING_DISTANCE) {
                tiredWarningSent = true;
                rider.displayClientMessage(Component.translatable("message.tnc.snow_wolf.tired"), true);
                playSound(SoundEvents.WOLF_PANT, .65f, .88f);
            }
        } else if (before > 0 && onGround() && !isInWater() && !isInLava()) {
            if (++drySwimRecoveryTicks >= 4) {
                distance = Math.max(0, before - MountMotion.SWIM_RECOVERY_PER_TICK);
                if (distance < MountMotion.SWIM_WARNING_DISTANCE - 2) {
                    tiredWarningSent = false;
                }
            }
        }
        if (distance != before) {
            entityData.set(SWIM_DISTANCE, distance);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (snow()) {
            tag.putFloat("TncSwimDistance", entityData.get(SWIM_DISTANCE));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (snow()) {
            float saved = tag.getFloat("TncSwimDistance");
            float distance = Float.isFinite(saved) ? Mth.clamp(saved, 0, MountMotion.SWIM_LIMIT) : 0;
            entityData.set(SWIM_DISTANCE, distance);
            tiredWarningSent = distance >= MountMotion.SWIM_WARNING_DISTANCE;
        }
    }

    @Override
    public void travel(Vec3 input) {
        Vec3 velocity = getDeltaMovement();
        if (runningJump.active() && (isInWater() || isInLava() || isFallFlying() || onClimbable()
                || input.z < 0 || !(getControllingPassenger() instanceof Player)
                || runningJump.interruptedBy(velocity))) {
            runningJump.stop();
        }
        if (!snow() || !isControlledByLocalInstance()) {
            super.travel(input);
            runningJump.rememberVelocity(getDeltaMovement());
            return;
        }
        boolean ridden = getControllingPassenger() instanceof Player;
        if (!ridden && !wantsToSwim()) {
            super.travel(input);
            return;
        }
        if (riddenJumpThisTick || (lastSnowVelocity != null
                && velocity.subtract(lastSnowVelocity).horizontalDistanceSqr() > .0004)) {
            unassistedUntilTick = tickCount + 4;
        }
        if (wantsToSwim()) {
            travelSwimming(ridden ? input : Vec3.ZERO, velocity);
            if (swimEntryTicks < 5) swimEntryTicks++;
        } else {
            swimEntryTicks = 0;
            if (onGround() && !isInWater() && !isInLava()) {
                travelOnSnowWolfGround(input, velocity);
            } else {
                super.travel(input);
            }
        }
        lastSnowVelocity = getDeltaMovement();
        runningJump.rememberVelocity(lastSnowVelocity);
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
        Vec3 intent = worldIntent(input);
        double surface = waterSurface(getX(), getZ());
        double error = surface - (getY() + MountMotion.WOLF_MUZZLE_HEIGHT - .20);
        boolean clear = Double.isFinite(surface) && hasBreathingSpace(Vec3.ZERO, surface);
        boolean climbing = canClimbBank(intent);
        if (!Double.isFinite(error)) {
            error = .65;
        }
        Vec3 controlled = MountMotion.swimVelocity(velocity, intent, getSpeed(), error,
                entityData.get(SWIM_DISTANCE), clear, climbing, swimEntryTicks < 5);
        Vec3 next = new Vec3(controlled.x, 0, controlled.z);
        if (!climbing && !hasBreathingSpace(next, surface)) {
            Vec3 x = new Vec3(next.x, 0, 0);
            Vec3 z = new Vec3(0, 0, next.z);
            boolean canSlideX = hasBreathingSpace(x, surface);
            boolean canSlideZ = hasBreathingSpace(z, surface);
            if (canSlideX && canSlideZ) {
                next = Math.abs(x.x) >= Math.abs(z.z) ? x : z;
            } else {
                next = canSlideX ? x : canSlideZ ? z : Vec3.ZERO;
            }
        }
        setDeltaMovement(next.x, controlled.y, next.z);
        // Keep vanilla drag, current, collision clipping and shallow-water stepping.
        super.travel(Vec3.ZERO);
    }

    private double waterSurface(double x, double z) {
        int cellY = Mth.floor(getY());
        BlockPos cell = BlockPos.containing(x, cellY, z);
        if (!level().hasChunkAt(cell)) {
            return Double.NaN;
        }
        double surface = Double.NaN;
        for (int dy = 0; dy <= 6; dy++) {
            BlockPos sample = cell.above(dy);
            var fluid = level().getFluidState(sample);
            if (!fluid.is(FluidTags.WATER)) {
                break;
            }
            surface = sample.getY() + fluid.getHeight(level(), sample);
        }
        return surface;
    }

    private boolean hasBreathingSpace(Vec3 step, double currentSurface) {
        Vec3 face = worldIntent(new Vec3(0, 0, 1));
        // Check both the body's path and the mouth, which extends beyond the collision box.
        for (double fraction : new double[] {.5, 1}) {
            double x = getX() + step.x * fraction;
            double z = getZ() + step.z * fraction;
            double nextSurface = waterSurface(x, z);
            double surface = Double.isFinite(nextSurface) ? nextSurface : currentSurface;
            if (!Double.isFinite(surface)) {
                surface = getY() + MountMotion.WOLF_MUZZLE_HEIGHT;
            }
            AABB body = new AABB(x - .46, surface + .03, z - .46, x + .46, surface + .62, z + .46);
            double noseX = x + face.x * 1.15;
            double noseZ = z + face.z * 1.15;
            AABB muzzle = new AABB(noseX - .18, surface + .06, noseZ - .18,
                    noseX + .18, surface + .43, noseZ + .18);
            if (level().getBlockCollisions(this, body).iterator().hasNext()
                    || level().getBlockCollisions(this, muzzle).iterator().hasNext()) {
                return false;
            }
        }
        return true;
    }

    private boolean canClimbBank(Vec3 intent) {
        if (intent.horizontalDistanceSqr() < 1e-6) {
            return false;
        }
        Vec3 edge = intent.normalize().scale(.91);
        double x = getX() + edge.x;
        double z = getZ() + edge.z;
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos cell = BlockPos.containing(x, getY() + dy, z);
            if (!level().hasChunkAt(cell) || !level().getFluidState(cell.above()).isEmpty()) {
                continue;
            }
            var shape = level().getBlockState(cell).getCollisionShape(level(), cell, CollisionContext.of(this));
            if (shape.isEmpty()) {
                continue;
            }
            double top = cell.getY() + shape.bounds().maxY;
            double rise = top - getY();
            AABB standing = getBoundingBox().move(edge.x, rise + .01, edge.z).deflate(.02);
            if (rise > 0 && rise <= 1.6 && !level().getBlockCollisions(this, standing).iterator().hasNext()) {
                return true;
            }
        }
        return false;
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
