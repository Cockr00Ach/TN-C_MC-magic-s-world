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
import net.minecraft.world.level.block.Blocks;
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
        boolean teleported = horizontal > 1.5 || Math.abs(displacement.y) > 3;
        if (level().isClientSide) {
            groundMotion.update(teleported ? 2 : horizontal, MountMotion.Cadence.forSpecies(species()));
        }
        if (!level().isClientSide || isControlledByLocalInstance()) {
            // Server sees accepted vehicle displacement. The local rider can predict its visual pose.
            MountMotion.AirPose pose = airMotion.update(onGround(), immersed || teleported,
                    displacement.y, riddenJumpThisTick, goat());
            if (!level().isClientSide) {
                entityData.set(AIR_POSE, (byte) pose.ordinal());
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
            unicornRunTicks = MountMotion.advanceUnicornRun(unicornRunTicks,
                    riddenInput.z > 0, forward > 0.025 && displacement.horizontalDistanceSqr() < 2.25);
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
        if (!level().isClientSide) {
            entityData.set(UNICORN_RUN_TICKS, 0);
        }
    }

    @Override protected float getRiddenSpeed(Player player){
        float base = super.getRiddenSpeed(player) * switch (species()) {
            case "mountain_goat" -> 1.5f;
            case "snow_wolf" -> 1.7f;
            case "rainbow_unicorn" -> MountMotion.unicornSpeedMultiplier(unicornRunTicks);
            default -> 1.0f;
        };
        if(snow()&&onGround()){
            var state=level().getBlockState(getBlockPosBelowThatAffectsMyMovement());
            if(state.is(Blocks.ICE)||state.is(Blocks.PACKED_ICE)||state.is(Blocks.BLUE_ICE)||state.is(Blocks.FROSTED_ICE)){
                double friction=state.is(Blocks.BLUE_ICE)?.989:.98;
                return (float)(base*Math.pow(friction/.6,3));
            }
        }
        return base;
    }
    @Override public void travel(Vec3 input){
        super.travel(input);
        if(snow()&&isControlledByLocalInstance()){
            var state=level().getBlockState(getBlockPosBelowThatAffectsMyMovement());
            if(onGround()&&(state.is(Blocks.ICE)||state.is(Blocks.PACKED_ICE)||state.is(Blocks.BLUE_ICE)||state.is(Blocks.FROSTED_ICE))){
                // Compensate ice's .98/.989 horizontal carry to match normal .6 ground friction.
                double carry=state.is(Blocks.BLUE_ICE)?.989:.98;
                Vec3 v=getDeltaMovement();setDeltaMovement(v.x*.6/carry,v.y,v.z*.6/carry);
            }
            if(isInWater()&&isVehicle())setDeltaMovement(getDeltaMovement().add(0,.025,0));
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
