package com.tnc.tnc.dungeon.ecology;

import com.tnc.tnc.dungeon.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/** Encounter identity stays compatible with DungeonService; behavior belongs to each species. */
public abstract class EcologyMob extends DungeonMob {
    private static final EntityDataAccessor<Integer> SUPPLY=SynchedEntityData.defineId(EcologyMob.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION=SynchedEntityData.defineId(EcologyMob.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SMALL=SynchedEntityData.defineId(EcologyMob.class,EntityDataSerializers.BOOLEAN);
    private static final UUID CUT_SPEED=UUID.fromString("ca729dd2-6920-4110-8ab0-adc62582c011");
    private static final UUID CUT_DAMAGE=UUID.fromString("ca729dd2-6920-4110-8ab0-adc62582c012");
    private BlockPos supply;
    private int overloadTicks;
    protected EcologyMob(EntityType<? extends Monster> type,Level level,String kind){super(type,level,EcologyContent.species(kind).id());configure(1,1,false);}
    public abstract String ecologyKind();
    @Override public MobCatalog.Species species(){return EcologyContent.species(ecologyKind());}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(SUPPLY,0);entityData.define(ACTION,0);entityData.define(SMALL,false);}
    public void bindSupply(BlockPos source){supply=source==null?null:source.immutable();}
    public BlockPos supply(){return supply;}
    public SupplyState supplyState(){return SupplyState.values()[Math.floorMod(entityData.get(SUPPLY),SupplyState.values().length)];}
    public int action(){return entityData.get(ACTION);}
    protected void action(int value){entityData.set(ACTION,Math.max(0,value));}
    public boolean captureWindow(){return entityData.get(SMALL);}
    public boolean captive(){return getPersistentData().hasUUID("CageOwner");}
    protected boolean canFight(){return isAlive()&&!captive()&&!captureWindow();}
    public void applySupply(SupplyState state){
        if(state==null||level().isClientSide||captive())return;
        SupplyState before=supplyState();entityData.set(SUPPLY,state.ordinal());
        modifier(Attributes.MOVEMENT_SPEED,CUT_SPEED,state==SupplyState.CUT);modifier(Attributes.ATTACK_DAMAGE,CUT_DAMAGE,state==SupplyState.CUT);
        if(state==SupplyState.TAINTED&&before!=state){setHealth(Math.min(getHealth(),getMaxHealth()*.1f));getAttribute(Attributes.ARMOR).setBaseValue(0);}
        if(state==SupplyState.OVERLOAD&&before!=state)overloadTicks=30;
        if(state!=SupplyState.OVERLOAD)overloadTicks=0;
    }
    private void modifier(Attribute attribute,UUID id,boolean active){var a=getAttribute(attribute);if(a==null)return;if(active&&a.getModifier(id)==null)a.addTransientModifier(new AttributeModifier(id,"Severed dungeon artery",-.5,AttributeModifier.Operation.MULTIPLY_TOTAL));else if(!active&&a.getModifier(id)!=null)a.removeModifier(id);}
    public boolean canReceivePotion(ServerPlayer player){
        if(player==null||!player.isAlive()||player.isSpectator()||!isAlive()||player.level()!=level()||captive()||player.isAlliedTo(this)||com.tnc.tnc.combat.DownedCombat.isDowned(player)||DungeonService.recovering(player)||com.tnc.tnc.home.TownProtection.denied(player,blockPosition()))return false;
        if(!getPersistentData().hasUUID("DungeonRun"))return true;
        var run=DungeonService.run(player);return run!=null&&run.id.equals(getPersistentData().getUUID("DungeonRun"));
    }
    /** No item consumption here: one flask can splash several entities. */
    public boolean applyPotion(ServerPlayer player,String potion){
        if(!canReceivePotion(player)||!EcologyHooks.capturePotion(potion)||guardian()||getHealth()>getMaxHealth()*.25f)return false;
        var tag=getPersistentData();long now=level().getGameTime();
        if(tag.getLong("CaptureUntil")>now&&tag.hasUUID("CaptureOwner")&&!player.getUUID().equals(tag.getUUID("CaptureOwner")))return false;
        tag.putUUID("CaptureOwner",player.getUUID());tag.putLong("CaptureUntil",now+200);entityData.set(SMALL,true);refreshDimensions();getNavigation().stop();setTarget(null);return true;
    }
    /** Called before a serialized specimen is deployed; dungeon power must not follow it home. */
    public void prepareForCage(){
        bindSupply(null);getPersistentData().remove("CaptureUntil");getPersistentData().remove("CaptureOwner");entityData.set(SMALL,false);entityData.set(SUPPLY,SupplyState.NORMAL.ordinal());overloadTicks=0;
        modifier(Attributes.MOVEMENT_SPEED,CUT_SPEED,false);modifier(Attributes.ATTACK_DAMAGE,CUT_DAMAGE,false);setTarget(null);refreshDimensions();
    }
    @Override public EntityDimensions getDimensions(Pose pose){var dimensions=super.getDimensions(pose);return captureWindow()?dimensions.scale(.3f):dimensions;}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(SMALL.equals(key))refreshDimensions();}
    @Override public void tick(){
        super.tick();if(level().isClientSide)return;
        if(captureWindow()&&getPersistentData().getLong("CaptureUntil")<=level().getGameTime()){entityData.set(SMALL,false);getPersistentData().remove("CaptureUntil");getPersistentData().remove("CaptureOwner");refreshDimensions();}
        if(captive()){setTarget(null);return;}
        if(overloadTicks>0&&--overloadTicks==0){DungeonService.killed(this);level().explode(this,getX(),getY()+.3,getZ(),2.0f,Level.ExplosionInteraction.NONE);discard();}
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putString("EcologySupplyState",supplyState().name());if(supply!=null)tag.putLong("EcologySource",supply.asLong());tag.putInt("EcologyAction",action());tag.putInt("EcologyOverload",overloadTicks);tag.putBoolean("EcologySmall",captureWindow());}
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);supply=tag.contains("EcologySource")?BlockPos.of(tag.getLong("EcologySource")):null;
        SupplyState state;try{state=SupplyState.valueOf(tag.getString("EcologySupplyState"));}catch(IllegalArgumentException e){state=SupplyState.NORMAL;}
        entityData.set(SUPPLY,state.ordinal());action(tag.getInt("EcologyAction"));overloadTicks=Math.max(0,Math.min(30,tag.getInt("EcologyOverload")));entityData.set(SMALL,tag.getBoolean("EcologySmall"));
        modifier(Attributes.MOVEMENT_SPEED,CUT_SPEED,state==SupplyState.CUT);modifier(Attributes.ATTACK_DAMAGE,CUT_DAMAGE,state==SupplyState.CUT);refreshDimensions();
    }
}
