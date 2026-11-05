package com.tnc.tnc.bronze;

import com.tnc.tnc.warrior.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Companion uses the existing warrior catalog and poses; no fake player or second player input channel. */
public final class BronzePuppetEntity extends PathfinderMob implements OwnableEntity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER=SynchedEntityData.defineId(BronzePuppetEntity.class,EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> ANIMATION=SynchedEntityData.defineId(BronzePuppetEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(BronzePuppetEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> FADE_END=SynchedEntityData.defineId(BronzePuppetEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> TOTAL_HEALTH=SynchedEntityData.defineId(BronzePuppetEntity.class,EntityDataSerializers.FLOAT);
    private long expiresAt,nextAttack;private WarriorSkill active;private int attackIndex;private boolean struck;
    public BronzePuppetEntity(EntityType<? extends BronzePuppetEntity> type,Level level){super(type,level);setPersistenceRequired();xpReward=0;}
    public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,200).add(Attributes.ATTACK_DAMAGE,PuppetBondData.ATTACK).add(Attributes.MOVEMENT_SPEED,.27).add(Attributes.FOLLOW_RANGE,24).add(Attributes.KNOCKBACK_RESISTANCE,.65);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(OWNER,Optional.empty());entityData.define(ANIMATION,-1);entityData.define(START,0L);entityData.define(FADE_END,0L);entityData.define(TOTAL_HEALTH,200f);}
    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));}
    @Override public UUID getOwnerUUID(){return entityData.get(OWNER).orElse(null);}
    @Override public LivingEntity getOwner(){var id=getOwnerUUID();if(id==null)return null;return level().isClientSide?level().getPlayerByUUID(id):getServer().getPlayerList().getPlayer(id);}
    public void bind(ServerPlayer owner,long now){
        entityData.set(OWNER,Optional.of(owner.getUUID()));expiresAt=now+PuppetBondData.LIFETIME;
        float health=PuppetBondData.health(owner.getMaxHealth());entityData.set(TOTAL_HEALTH,health);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.min(1024,health));setHealth(getMaxHealth());
        var weapon=new ItemStack(BronzeWeapons.WEAPONS.get(BronzeProfile.HALBERD).get());
        weapon.getOrCreateTag().putBoolean("Unbreakable",true);weapon.getOrCreateTag().putInt(BronzeWeaponItem.UPGRADE,10);
        // Equipment is visual: the companion's provisional attack remains exactly 12 before warrior multipliers.
        weapon.getOrCreateTag().put("AttributeModifiers",new ListTag());setItemSlot(EquipmentSlot.MAINHAND,weapon);setDropChance(EquipmentSlot.MAINHAND,0);
    }
    public long fadeEnd(){return entityData.get(FADE_END);}
    public WarriorAnimation motion(){int id=entityData.get(ANIMATION);return id<0||id>=WarriorAnimation.values().length?null:WarriorAnimation.values()[id];}
    public float progress(float partial){var motion=motion();return motion==null?1:Math.max(0,Math.min(1,(level().getGameTime()+partial-entityData.get(START))/motion.durationTicks()));}
    public void dissolve(){
        if(level().isClientSide||fadeEnd()!=0)return;
        long end=Math.min(BronzeSummons.time(getServer())+PuppetBondData.FADE,expiresAt);
        entityData.set(FADE_END,end);setTarget(null);navigation.stop();active=null;
        if(getOwnerUUID()!=null)PuppetBondData.get(getServer()).fading(getOwnerUUID(),getUUID(),end);
    }
    @Override public void aiStep(){
        super.aiStep();if(level().isClientSide)return;
        long now=BronzeSummons.time(getServer());var ownerId=getOwnerUUID();
        if(ownerId==null||!PuppetBondData.get(getServer()).owns(ownerId,getUUID())){discard();return;}
        if(now>=expiresAt-PuppetBondData.FADE)dissolve();
        if(fadeEnd()!=0){navigation.stop();if(now>=fadeEnd())discard();return;}
        if(!isAlive())return;
        if(!BronzeTwinWeapon.active(this)){active=null;entityData.set(ANIMATION,-1);navigation.stop();return;}
        if(!(getOwner() instanceof ServerPlayer owner)||!owner.isAlive()){setTarget(null);active=null;entityData.set(ANIMATION,-1);navigation.stop();return;}
        if(owner.level()!=level()){dissolve();return;}
        if(com.tnc.tnc.boss.fire.TortoiseStun.isStunned(this)){active=null;entityData.set(ANIMATION,-1);navigation.stop();return;}
        if(tickCount%10==0){
            LivingEntity target=null;
            if(owner.tickCount-owner.getLastHurtMobTimestamp()<200&&valid(owner,owner.getLastHurtMob()))target=owner.getLastHurtMob();
            else if(owner.tickCount-owner.getLastHurtByMobTimestamp()<200&&valid(owner,owner.getLastHurtByMob()))target=owner.getLastHurtByMob();
            else target=level().getEntitiesOfClass(Monster.class,getBoundingBox().inflate(16),m->valid(owner,m)).stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            setTarget(target);
        }
        var target=getTarget();
        if(target==null||!valid(owner,target)){setTarget(null);active=null;entityData.set(ANIMATION,-1);if(distanceToSqr(owner)>9)navigation.moveTo(owner,1.15);else navigation.stop();return;}
        getLookControl().setLookAt(target,35,35);
        if(active!=null){
            navigation.stop();long age=level().getGameTime()-entityData.get(START);
            if(!struck&&age>=Math.max(3,active.animation().durationTicks()/2)){struck=true;WarriorBronzeBridge.puppetMelee(this,owner,active);}
            if(age>=active.animation().durationTicks())active=null;
        }else if(distanceTo(target)>2.65)navigation.moveTo(target,1.1);
        else if(now>=nextAttack&&hasLineOfSight(target)){
            var choices=WarriorBronzeBridge.puppetSkills();active=choices.get(attackIndex++%choices.size());struck=false;
            entityData.set(ANIMATION,active.animation().ordinal());entityData.set(START,level().getGameTime());nextAttack=now+active.animation().durationTicks()+16;navigation.stop();
            level().playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,net.minecraft.sounds.SoundSource.NEUTRAL,.65f,.65f);
        }
    }
    private boolean valid(ServerPlayer owner,LivingEntity target){return target!=null&&target!=this&&distanceToSqr(target)<24*24&&target.level()==level()&&!isAlliedTo(target)&&WarriorBronzeBridge.validTarget(owner,target);}
    @Override public boolean isAlliedTo(Entity other){
        var owner=getOwner();if(other==this||other.getUUID().equals(getOwnerUUID()))return true;
        if(other instanceof OwnableEntity companion&&Objects.equals(getOwnerUUID(),companion.getOwnerUUID()))return true;
        return owner!=null&&(owner.isAlliedTo(other)||other instanceof net.minecraft.world.entity.player.Player player&&!((net.minecraft.world.entity.player.Player)owner).canHarmPlayer(player));
    }
    @Override public boolean hurt(DamageSource source,float amount){
        if(fadeEnd()!=0||source.getEntity()!=null&&isAlliedTo(source.getEntity()))return false;
        // Preserve 10x effective HP even above vanilla's 1024 max-health attribute ceiling.
        float scale=getMaxHealth()/entityData.get(TOTAL_HEALTH);return super.hurt(source,amount*scale);
    }
    @Override public void heal(float amount){super.heal(amount*getMaxHealth()/entityData.get(TOTAL_HEALTH));}
    @Override public void die(DamageSource source){super.die(source);if(!level().isClientSide)dissolve();}
    @Override protected void tickDeath(){deathTime++;if(!level().isClientSide&&fadeEnd()!=0&&BronzeSummons.time(getServer())>=fadeEnd())discard();}
    @Override protected void dropCustomDeathLoot(DamageSource source,int looting,boolean hitByPlayer){}
    @Override public void remove(RemovalReason reason){
        if(!level().isClientSide&&reason.shouldDestroy()&&getOwnerUUID()!=null&&getServer()!=null)PuppetBondData.get(getServer()).vanished(getOwnerUUID(),getUUID(),BronzeSummons.time(getServer()));
        super.remove(reason);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);if(getOwnerUUID()!=null)tag.putUUID("BronzeOwner",getOwnerUUID());tag.putLong("BronzeExpires",expiresAt);tag.putLong("BronzeFade",fadeEnd());tag.putFloat("BronzeTotalHealth",entityData.get(TOTAL_HEALTH));}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);entityData.set(OWNER,tag.hasUUID("BronzeOwner")?Optional.of(tag.getUUID("BronzeOwner")):Optional.empty());expiresAt=tag.getLong("BronzeExpires");entityData.set(FADE_END,tag.getLong("BronzeFade"));entityData.set(TOTAL_HEALTH,Math.max(1,tag.getFloat("BronzeTotalHealth")));}
}
