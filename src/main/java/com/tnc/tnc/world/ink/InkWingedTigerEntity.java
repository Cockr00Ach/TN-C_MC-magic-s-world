package com.tnc.tnc.world.ink;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/** 玄墨翼虎: aerial ink shot, giant tiger apparition and gathering roar/wing slashes. */
public final class InkWingedTigerEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> MOVE=SynchedEntityData.defineId(InkWingedTigerEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(InkWingedTigerEntity.class,EntityDataSerializers.LONG);
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bar=new ServerBossEvent(Component.translatable("entity.tnc.ink_winged_tiger"),BossEvent.BossBarColor.WHITE,BossEvent.BossBarOverlay.NOTCHED_10);
    private LivingEntity victim;
    private Vec3 home,landing;
    private int age,cooldown=80,nextMove=1,blinkCooldown=150;
    private boolean oldGravity,landed;
    public InkWingedTigerEntity(EntityType<? extends InkWingedTigerEntity> type,Level level){super(type,level);xpReward=950;setPersistenceRequired();}
    public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH,1000).add(Attributes.ATTACK_DAMAGE,26).add(Attributes.ARMOR,14)
            .add(Attributes.ARMOR_TOUGHNESS,8).add(Attributes.MOVEMENT_SPEED,.29)
            .add(Attributes.FOLLOW_RANGE,64).add(Attributes.KNOCKBACK_RESISTANCE,1);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(MOVE,0);entityData.define(START,0L);}
    public int inkMove(){return entityData.get(MOVE);}
    public int moveAge(){return (int)Math.max(0,level().getGameTime()-entityData.get(START));}
    @Override protected void registerGoals(){
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(2,new MeleeAttackGoal(this,1.1,true){
            @Override public boolean canUse(){return inkMove()==0&&super.canUse();}
            @Override public boolean canContinueToUse(){return inkMove()==0&&super.canContinueToUse();}});
        goalSelector.addGoal(7,new RandomStrollGoal(this,.75){@Override public boolean canUse(){return inkMove()==0&&super.canUse();}});
        goalSelector.addGoal(8,new LookAtPlayerGoal(this,Player.class,32));
        goalSelector.addGoal(9,new RandomLookAroundGoal(this));
        targetSelector.addGoal(1,new HurtByTargetGoal(this));targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true));
    }
    public boolean startInkAttack(int move,LivingEntity target){
        if(level().isClientSide || inkMove()!=0 || !isAlive() || move<1||move>3||target==null||!target.isAlive()||target.level()!=level())return false;
        victim=target;home=position();landing=home;oldGravity=isNoGravity();landed=false;age=0;
        entityData.set(MOVE,move);entityData.set(START,level().getGameTime());getNavigation().stop();
        if(move==1){
            double rise=12;
            // Check the whole tiger's volume, not just a ray through the ceiling.
            while(rise>0&&!level().noCollision(this,getBoundingBox().expandTowards(0,rise,0)))rise--;
            landing=home.add(0,rise,0);setNoGravity(true);
        }
        if(move==2)InkTigerEffectEntity.spawn(this,target,InkTigerEffectEntity.GHOST,home);
        triggerAnim("action",move==1?"sky_spit":move==2?"phantom_cast":"ink_roar");
        playSound(move==3?SoundEvents.RAVAGER_ROAR:SoundEvents.ENDER_DRAGON_FLAP,2,.65F);
        return true;
    }
    @Override public void aiStep(){super.aiStep();
        if(level().isClientSide){if(tickCount%3==0)level().addParticle(net.minecraft.core.particles.ParticleTypes.ASH,getRandomX(.8),getY()+2,getRandomZ(.8),0,.025,0);return;}
        bar.setProgress(getHealth()/getMaxHealth());
        if(blinkCooldown>0)blinkCooldown--;
        if(inkMove()==0){
            if(blinkCooldown==0&&getTarget()!=null&&distanceToSqr(getTarget())<48*48)tryCombatBlink(getTarget());
            if(cooldown>0)cooldown--;else if(getTarget()!=null&&distanceToSqr(getTarget())<48*48&&hasLineOfSight(getTarget())
                &&startInkAttack(nextMove,getTarget()))nextMove=nextMove%3+1;return;}
        if(victim==null||!victim.isAlive()||victim.level()!=level()){cancelMove();return;}
        getNavigation().stop();setDeltaMovement(Vec3.ZERO);
        if(inkMove()!=3||age<InkTigerAttackRules.SPIN){
            Vec3 d=victim.position().subtract(position());setYRot((float)(Math.atan2(-d.x,d.z)*180/Math.PI));yBodyRot=getYRot();yHeadRot=getYRot();
        }
        if(inkMove()==1){
            double t=age<25?age/25.0:age<85?1:Math.max(0,1-(age-85)/30.0);
            setPos(home.lerp(landing,t));fallDistance=0;
            if(age==InkTigerAttackRules.SPIT)InkTigerEffectEntity.spawn(this,victim,InkTigerEffectEntity.SHOT,mouthPosition(false));
            if(age>=115){landed=true;setNoGravity(oldGravity);}
        }
        if(inkMove()==3){
            if(age>=InkTigerAttackRules.GATHER&&age<InkTigerAttackRules.DETONATE)
                InkTigerEffectEntity.pull(this,position(),InkTigerAttackRules.SUCTION_RADIUS,InkTigerAttackRules.SUCTION_ACCELERATION,InkTigerAttackRules.SUCTION_CAP);
            if(age==InkTigerAttackRules.DETONATE){
                strike(position(),12,28,false,false);
                InkTigerEffectEntity.spawn(this,null,InkTigerEffectEntity.BURST,mouthPosition(true));
                playSound(SoundEvents.GENERIC_EXPLODE,3,.5F);
            }
            if(age>=InkTigerAttackRules.SPIN&&age<InkTigerAttackRules.SPIN_END){
                setYRot(getYRot()+30);yBodyRot=getYRot();yHeadRot=getYRot();
                if(InkTigerAttackRules.slashAt(age))strike(position(),14,16,false,true);
            }
        }
        if(++age>=InkTigerAttackRules.duration(inkMove()))cancelMove();
    }
    public Vec3 mouthPosition(boolean raised){
        return position().add(Vec3.directionFromRotation(0,getYRot()).scale((raised?2.3:4.15)*InkTigerAttackRules.BODY_MULTIPLIER))
                .add(0,(raised?5.3:2.6)*InkTigerAttackRules.BODY_MULTIPLIER,0);
    }
    private void tryCombatBlink(LivingEntity target){
        for(int i=0;i<12;i++){
            double a=random.nextDouble()*Math.PI*2;Vec3 p=target.position().add(Math.cos(a)*8,3,Math.sin(a)*8);
            for(int y=0;y<8;y++)if(blinkTo(p.add(0,-y,0)))return;
        }
        blinkCooldown=40;
    }
    /** Reject unloaded chunks, liquids, unsupported floors and blocked body-sized destinations. */
    public boolean blinkTo(Vec3 at){
        if(level().isClientSide||!isAlive()||inkMove()!=0||at.distanceToSqr(position())>48*48)return false;
        var box=getBoundingBox().move(at.subtract(position()));
        if(!level().getWorldBorder().isWithinBounds(box)||box.minY<=level().getMinBuildHeight()||box.maxY>=level().getMaxBuildHeight())return false;
        for(double x:new double[]{box.minX+.05,box.maxX-.05})for(double z:new double[]{box.minZ+.05,box.maxZ-.05}){
            var floor=net.minecraft.core.BlockPos.containing(x,at.y-.1,z);
            if(!level().hasChunkAt(floor)||!level().getBlockState(floor).isFaceSturdy(level(),floor,net.minecraft.core.Direction.UP))return false;
        }
        if(!level().noCollision(this,box)||level().containsAnyLiquid(box))return false;
        InkTigerEffectEntity.spawn(this,null,InkTigerEffectEntity.STAIN,position());
        teleportTo(at.x,at.y,at.z);fallDistance=0;setDeltaMovement(Vec3.ZERO);getNavigation().stop();
        InkTigerEffectEntity.spawn(this,null,InkTigerEffectEntity.STAIN,position());blinkCooldown=160;
        playSound(SoundEvents.ENDERMAN_TELEPORT,1.3F,.6F);return true;
    }
    private void strike(Vec3 origin,double radius,float damage,boolean front,boolean slow){
        Vec3 forward=Vec3.directionFromRotation(0,getYRot());
        for(LivingEntity enemy:level().getEntitiesOfClass(LivingEntity.class,new AABB(origin,origin).inflate(radius),
                e->InkTigerEffectEntity.hostile(this,e))){
            Vec3 delta=enemy.position().subtract(origin);
            if(delta.lengthSqr()>radius*radius || (front&&delta.multiply(1,0,1).dot(forward)<0) || !hasLineOfSight(enemy))continue;
            enemy.hurt(damageSources().mobAttack(this),damage);
            if(slow)enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,0));
        }
    }
    private void cancelMove(){
        if(inkMove()==1){if(home!=null)setPos(home);setNoGravity(oldGravity);fallDistance=0;}
        entityData.set(MOVE,0);victim=null;home=null;landing=null;cooldown=85;setDeltaMovement(Vec3.ZERO);
    }
    @Override public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag){super.addAdditionalSaveData(tag);
        if(inkMove()==1&&home!=null){Vec3 recovery=home;tag.putBoolean("InkTigerAirborne",true);
            tag.putBoolean("InkTigerGravity",oldGravity);tag.putDouble("InkTigerX",recovery.x);tag.putDouble("InkTigerY",recovery.y);tag.putDouble("InkTigerZ",recovery.z);}}
    @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag){super.readAdditionalSaveData(tag);
        if(tag.getBoolean("InkTigerAirborne")){setPos(tag.getDouble("InkTigerX"),tag.getDouble("InkTigerY"),tag.getDouble("InkTigerZ"));setNoGravity(tag.getBoolean("InkTigerGravity"));fallDistance=0;}
        entityData.set(MOVE,0);}
    @Override public boolean doHurtTarget(Entity target){if(inkMove()!=0)return false;triggerAnim("action","claw");return super.doHurtTarget(target);}
    @Override public boolean removeWhenFarAway(double d){return false;}
    @Override public void startSeenByPlayer(ServerPlayer p){super.startSeenByPlayer(p);bar.addPlayer(p);}
    @Override public void stopSeenByPlayer(ServerPlayer p){super.stopSeenByPlayer(p);bar.removePlayer(p);}
    @Override public void remove(RemovalReason r){bar.removeAllPlayers();super.remove(r);}
    @Override public void die(DamageSource source){cancelMove();bar.removeAllPlayers();super.die(source);}
    @Override public AABB getBoundingBoxForCulling(){return super.getBoundingBoxForCulling().inflate(24);}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar registrar){
        registrar.add(new AnimationController<>(this,"locomotion",6,state->inkMove()!=0?PlayState.STOP:
                state.setAndContinue(RawAnimation.begin().thenLoop(state.isMoving()?"walk":"idle"))));
        var action=new AnimationController<InkWingedTigerEntity>(this,"action",3,state->PlayState.STOP);
        for(String name:new String[]{"claw","pounce","roar","sky_spit","phantom_cast","ink_roar"})action.triggerableAnim(name,RawAnimation.begin().thenPlay(name));registrar.add(action);
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
