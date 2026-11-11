package com.tnc.tnc.mount;

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
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.*;
import net.minecraft.world.damagesource.DamageSource;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Vanilla horse owns input packets, authority, jumps, dismount safety and passenger persistence. */
public final class LandMount extends Horse implements GeoEntity {
    private final AnimatableInstanceCache animationCache=GeckoLibUtil.createInstanceCache(this);
    public LandMount(EntityType<? extends Horse> type,Level level){super(type,level);setPersistenceRequired();setMaxUpStep(1.0f);}
    public String species(){return net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(getType()).getPath();}
    public boolean snow(){return species().equals("snow_wolf");}
    private boolean goat(){return species().equals("mountain_goat");}
    public static AttributeSupplier.Builder attributes(){return Horse.createBaseHorseAttributes().add(Attributes.MAX_HEALTH,40).add(Attributes.MOVEMENT_SPEED,.30).add(Attributes.JUMP_STRENGTH,.65);}
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
    @Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source){return super.causeFallDamage(goat()?Math.max(0,distance-5):distance,multiplier,source);}
    @Override protected float getRiddenSpeed(Player player){
        float base=super.getRiddenSpeed(player);
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
    @Override public double getPassengersRidingOffset(){return (goat()?29:snow()?28:33)*.65/16;}
    @Override protected SoundEvent getAmbientSound(){return goat()?SoundEvents.GOAT_AMBIENT:snow()?SoundEvents.WOLF_AMBIENT:super.getAmbientSound();}
    @Override protected SoundEvent getHurtSound(DamageSource source){return goat()?SoundEvents.GOAT_HURT:snow()?SoundEvents.WOLF_HURT:super.getHurtSound(source);}
    @Override protected SoundEvent getDeathSound(){return goat()?SoundEvents.GOAT_DEATH:snow()?SoundEvents.WOLF_DEATH:super.getDeathSound();}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){
        controllers.add(new AnimationController<LandMount>(this,"movement",3,state->{
            double speed=Math.sqrt(getDeltaMovement().horizontalDistanceSqr());String clip=MountMotion.clip(speed);
            state.getController().setAnimationSpeed(MountMotion.rate(clip,speed));
            return state.setAndContinue(RawAnimation.begin().thenLoop("animation."+species()+"."+clip));
        }));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return animationCache;}
}
