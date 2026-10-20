package com.tnc.tnc.boss.wind;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Model-first wind general. No natural spawns or combat AI until the encounter is designed. */
public final class WindGeneralEntity extends PathfinderMob implements GeoEntity {
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    public WindGeneralEntity(EntityType<? extends WindGeneralEntity> type,Level level){super(type,level);setPersistenceRequired();}
    public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,300)
            .add(Attributes.MOVEMENT_SPEED,.24).add(Attributes.ARMOR,12).add(Attributes.FOLLOW_RANGE,32);}
    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(5,new WaterAvoidingRandomStrollGoal(this,.7));goalSelector.addGoal(6,new LookAtPlayerGoal(this,Player.class,12));goalSelector.addGoal(7,new RandomLookAroundGoal(this));}
    @Override public void registerControllers(software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar controllers){
        controllers.add(new AnimationController<>(this,"movement",8,state->state.setAndContinue(RawAnimation.begin().thenLoop(state.isMoving()?"walk":"idle"))));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public boolean removeWhenFarAway(double distance){return false;}
}
