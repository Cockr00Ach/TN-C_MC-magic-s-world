package com.tnc.tnc.warrior;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import java.util.Locale;

/** A real living target, without instructor damage caps, rewards, AI or hit immunity. */
public final class TestDummyEntity extends PathfinderMob {
    private double total,last;private int hits;private long first,lastAt;
    public TestDummyEntity(EntityType<? extends TestDummyEntity> type,Level level){super(type,level);setPersistenceRequired();setNoGravity(true);xpReward=0;}
    public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.KNOCKBACK_RESISTANCE,1).add(Attributes.MOVEMENT_SPEED,0).add(Attributes.ARMOR,0);}
    @Override protected void registerGoals(){}
    @Override public boolean isPushable(){return false;}
    @Override public boolean canBeCollidedWith(){return true;}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public void knockback(double strength,double x,double z){}
    @Override public boolean hurt(DamageSource source,float amount){
        if(level().isClientSide||!(source.getEntity() instanceof ServerPlayer p)||!mayUse(p)||!Float.isFinite(amount)||amount<=0)return false;
        setHealth(getMaxHealth());invulnerableTime=0;
        boolean hit=super.hurt(source,amount);
        setHealth(getMaxHealth());invulnerableTime=0;clearFire();setDeltaMovement(Vec3.ZERO);return hit;
    }
    @Override public void die(DamageSource source){setHealth(getMaxHealth());deathTime=0;}
    public boolean mayUse(Player p){return !getPersistentData().hasUUID("DummyOwner")||getPersistentData().getUUID("DummyOwner").equals(p.getUUID());}
    public void record(float amount){
        long now=level().getGameTime();if(hits==0)first=now;lastAt=now;last=amount;total+=amount;hits++;
        setCustomName(Component.literal(String.format(Locale.ROOT,"木桩 · 本次%.1f · 合计%.1f",last,total)));setCustomNameVisible(true);
    }
    public void resetStats(){total=last=0;hits=0;first=lastAt=0;setCustomName(Component.literal("测试木桩 · 右键清零 · 潜行右键收起"));setCustomNameVisible(true);}
    public double totalDamage(){return total;}public int hitCount(){return hits;}
    public double dps(){return total/Math.max(1,(lastAt-first)/20.0);}
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand){
        if(!mayUse(player))return InteractionResult.FAIL;
        if(!level().isClientSide&&hand==InteractionHand.MAIN_HAND){if(player.isShiftKeyDown()){discard();TrainingGroundData.get(((ServerPlayer)player).server).release(player.getUUID());}else resetStats();}
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    @Override public void tick(){
        if(!level().isClientSide){var t=getPersistentData();if(!t.contains("DummyHome")){t.putBoolean("DummyHome",true);t.putDouble("DX",getX());t.putDouble("DY",getY());t.putDouble("DZ",getZ());}setDeltaMovement(Vec3.ZERO);}
        super.tick();setDeltaMovement(Vec3.ZERO);clearFire();
        if(!level().isClientSide){setHealth(getMaxHealth());var t=getPersistentData();
            if(!t.contains("DummyHome")){t.putBoolean("DummyHome",true);t.putDouble("DX",getX());t.putDouble("DY",getY());t.putDouble("DZ",getZ());}
            setPos(t.getDouble("DX"),t.getDouble("DY"),t.getDouble("DZ"));
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.putDouble("DummyTotal",total);t.putDouble("DummyLast",last);t.putInt("DummyHits",hits);t.putLong("DummyFirst",first);t.putLong("DummyLastAt",lastAt);}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);total=t.getDouble("DummyTotal");last=t.getDouble("DummyLast");hits=t.getInt("DummyHits");first=t.getLong("DummyFirst");lastAt=t.getLong("DummyLastAt");}
}
