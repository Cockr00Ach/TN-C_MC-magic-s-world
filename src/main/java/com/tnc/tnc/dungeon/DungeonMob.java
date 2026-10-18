package com.tnc.tnc.dungeon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.*;

/** Shared encounter plumbing; family geometry, movement and roles remain distinct. */
public class DungeonMob extends Monster implements RangedAttackMob {
    private static final EntityDataAccessor<Boolean> GUARDIAN=SynchedEntityData.defineId(DungeonMob.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> AWAKE=SynchedEntityData.defineId(DungeonMob.class,EntityDataSerializers.BOOLEAN);
    private final String speciesId;
    public DungeonMob(EntityType<? extends Monster> type,Level level,String id){super(type,level);speciesId=id;setPersistenceRequired();xpReward=5;}
    public MobCatalog.Species species(){return MobCatalog.find(speciesId==null?"goblin_warrior":speciesId);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(GUARDIAN,false);entityData.define(AWAKE,false);}
    public boolean guardian(){return entityData.get(GUARDIAN);}public boolean visuallyAwake(){return entityData.get(AWAKE);}public void guardian(boolean v){entityData.set(GUARDIAN,v);refreshDimensions();}
    @Override public EntityDimensions getDimensions(Pose p){var d=super.getDimensions(p);return guardian()?d.scale(1.8f):d;}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(key.equals(GUARDIAN))refreshDimensions();}
    @Override public SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor l,net.minecraft.world.DifficultyInstance difficulty,MobSpawnType type,SpawnGroupData group,CompoundTag tag){var out=super.finalizeSpawn(l,difficulty,type,group,tag);configure(1,1,false);return out;}
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,30).add(Attributes.ATTACK_DAMAGE,5).add(Attributes.MOVEMENT_SPEED,.26).add(Attributes.FOLLOW_RANGE,28).add(Attributes.ARMOR,2).add(Attributes.KNOCKBACK_RESISTANCE,.1);}
    @Override protected void registerGoals(){
      goalSelector.addGoal(0,new FloatGoal(this));
      // The constructor is invoked before speciesId is assigned. Roles are selected by goal canUse.
      goalSelector.addGoal(2,new RangedAttackGoal(this,1,38,12){@Override public boolean canUse(){return ranged()&&super.canUse();}@Override public boolean canContinueToUse(){return ranged()&&super.canContinueToUse();}});
      goalSelector.addGoal(3,new MeleeAttackGoal(this,1.1,false){@Override public boolean canUse(){return !ranged()&&awake()&&super.canUse();}@Override public boolean canContinueToUse(){return !ranged()&&awake()&&super.canContinueToUse();}});
      goalSelector.addGoal(7,new LookAtPlayerGoal(this,Player.class,10));goalSelector.addGoal(8,new RandomLookAroundGoal(this));
      targetSelector.addGoal(1,new HurtByTargetGoal(this));targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,10,true,false,p->p instanceof net.minecraft.server.level.ServerPlayer sp&&DungeonService.mayTarget(this,sp)));
    }
    private boolean ranged(){var r=species().role();return r==MobCatalog.Role.CASTER||r==MobCatalog.Role.ARCHER;}
    private boolean awake(){return species().role()!=MobCatalog.Role.AMBUSH||getTarget()!=null&&distanceToSqr(getTarget())<36||getLastHurtByMob()!=null;}
    public void configure(int floor,int players,boolean guardian){var s=species();double stage=1+(floor-1)*.065;getAttribute(Attributes.MAX_HEALTH).setBaseValue(s.health()*stage*(guardian?5:1)*(1+(players-1)*.55));getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(s.damage()*(1+(floor-1)*.025)*(guardian?1.4:1));getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(s.speed());getAttribute(Attributes.ARMOR).setBaseValue(s.role()==MobCatalog.Role.TANK?10:2);getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(s.role()==MobCatalog.Role.TANK?.65:.08);setHealth(getMaxHealth());guardian(guardian);if(guardian)setCustomName(net.minecraft.network.chat.Component.literal("守关试炼 · "+s.name()+"（基础版）"));}
    @Override public void tick(){super.tick();if(level().isClientSide)return;entityData.set(AWAKE,awake());
      if(getTarget()!=null&&!DungeonService.mayTarget(this,(getTarget() instanceof net.minecraft.server.level.ServerPlayer p)?p:null))setTarget(null);
      if(getTarget()!=null&&awake()&&onGround()){
        int period=species().family()==MobCatalog.Family.SLIME?25:species().role()==MobCatalog.Role.LEAPER?65:species().role()==MobCatalog.Role.AMBUSH?50:0;
        if(period>0&&tickCount%period==0){Vec3 delta=getTarget().position().subtract(position());if(delta.lengthSqr()<100){Vec3 hop=delta.normalize().scale(species().family()==MobCatalog.Family.SLIME?.32:.55);setDeltaMovement(hop.x,.42,hop.z);hasImpulse=true;}}
      }
      if(getPersistentData().contains("DungeonRoomOrigin")){var origin=net.minecraft.core.BlockPos.of(getPersistentData().getLong("DungeonRoomOrigin"));if(distanceToSqr(Vec3.atBottomCenterOf(origin))>34*34||getY()<origin.getY()-3){teleportTo(origin.getX()+.5,origin.getY()+1,origin.getZ()+.5);getNavigation().stop();}}
    }
    @Override public boolean hurt(DamageSource source,float amount){
      if(species().role()==MobCatalog.Role.TANK&&source.getSourcePosition()!=null){Vec3 dir=source.getSourcePosition().subtract(position()).normalize();if(getLookAngle().dot(dir)>.5)amount*=.6f;}
      return super.hurt(source,amount);
    }
    @Override public void performRangedAttack(LivingEntity target,float strength){if(level().isClientSide)return;var bolt=DungeonContent.BOLT.create(level());if(bolt==null)return;bolt.setOwner(this);bolt.setPos(getX(),getEyeY()-.15,getZ());bolt.configure(species().accent(),(float)getAttributeValue(Attributes.ATTACK_DAMAGE),speciesId.contains("frost")?1:speciesId.contains("venom")?2:0);Vec3 dir=target.getEyePosition().subtract(bolt.position());bolt.shoot(dir.x,dir.y,dir.z,1.05f,2);level().addFreshEntity(bolt);swing(net.minecraft.world.InteractionHand.MAIN_HAND);playSound(SoundEvents.EVOKER_CAST_SPELL,.5f,1.3f);}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override protected SoundEvent getHurtSound(DamageSource s){return switch(species().family()){case SKELETON->SoundEvents.SKELETON_HURT;case SLIME->SoundEvents.SLIME_HURT;case MIMIC->SoundEvents.CHEST_CLOSE;default->SoundEvents.PILLAGER_HURT;};}
    @Override protected SoundEvent getDeathSound(){return species().family()==MobCatalog.Family.SKELETON?SoundEvents.SKELETON_DEATH:SoundEvents.SLIME_DEATH;}
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.putBoolean("DungeonGuardian",guardian());}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);guardian(t.getBoolean("DungeonGuardian"));}
}
