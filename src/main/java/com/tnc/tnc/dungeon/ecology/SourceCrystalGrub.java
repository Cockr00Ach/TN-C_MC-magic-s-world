package com.tnc.tnc.dungeon.ecology;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/** Its liquid astral crop is harvested only while the actual living specimen remains restrained. */
public final class SourceCrystalGrub extends EcologyMob {
    private boolean detonated;
    public SourceCrystalGrub(EntityType<? extends Monster> type,Level level){super(type,level,"worm");xpReward=0;}
    @Override public String ecologyKind(){return "worm";}
    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(7,new RandomStrollGoal(this,.45){@Override public boolean canUse(){return canFight()&&!captive()&&super.canUse();}});goalSelector.addGoal(8,new RandomLookAroundGoal(this));}
    @Override public void tick(){
        super.tick();if(level().isClientSide||!isAlive())return;
        if(captive()){if(onGround()&&tickCount%35==0)setDeltaMovement(0,.18,0);return;}
        if(captureWindow()){getNavigation().stop();return;}
        var threat=getLastHurtByMob();if(threat!=null&&distanceToSqr(threat)<36&&tickCount%15==0){var away=position().subtract(threat.position()).normalize().scale(3);getNavigation().moveTo(getX()+away.x,getY(),getZ()+away.z,.7);}
        else if(supply()!=null&&tickCount%60==0&&blockPosition().distSqr(supply())<64){getNavigation().moveTo(supply().getX()+.5,supply().getY(),supply().getZ()+.5,.4);playSound(SoundEvents.GENERIC_EAT,.35f,.7f);}
    }
    public int batteryRate(){return 48;}
    public String batteryElement(){return "astral";}
    @Override public void die(DamageSource source){if(!level().isClientSide&&!detonated&&!captive()){detonated=true;level().explode(this,getX(),getY()+.3,getZ(),2.5f,Level.ExplosionInteraction.NONE);}super.die(source);}
    @Override protected boolean shouldDropLoot(){return false;}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putBoolean("EcologyDetonated",detonated);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);detonated=tag.getBoolean("EcologyDetonated");}
}
