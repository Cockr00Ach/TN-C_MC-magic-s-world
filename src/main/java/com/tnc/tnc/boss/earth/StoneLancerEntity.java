package com.tnc.tnc.boss.earth;

import com.tnc.tnc.boss.GiantApproachGoal;
import com.tnc.tnc.boss.GiantSimulation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Rock-built boss with fixed attack budgets; the world terrain is never destroyed. */
public final class StoneLancerEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Boolean> CASTING=SynchedEntityData.defineId(StoneLancerEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> BURROW=SynchedEntityData.defineId(StoneLancerEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> BURROW_START=SynchedEntityData.defineId(StoneLancerEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> ARENA_BOUND=SynchedEntityData.defineId(StoneLancerEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ARENA_AWAKE=SynchedEntityData.defineId(StoneLancerEntity.class,EntityDataSerializers.BOOLEAN);
    private final StoneLancerBurrow burrow=new StoneLancerBurrow(this);
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bar=new ServerBossEvent(getDisplayName(),BossEvent.BossBarColor.YELLOW,BossEvent.BossBarOverlay.PROGRESS);
    final java.util.List<com.tnc.tnc.magic.EarthAttackEntity> attacks=new java.util.ArrayList<>();
    public static final float SCALE=5;
    public static final double QUAKE_RADIUS=200;
    private int quakeTicks;
    public boolean isArenaBound(){return entityData.get(ARENA_BOUND)||getPersistentData().getBoolean("TncEarthArenaGuardian");}
    public boolean arenaAwake(){return entityData.get(ARENA_AWAKE);}
    public void bindToArena(){getPersistentData().putBoolean("TncEarthArenaGuardian",true);entityData.set(ARENA_BOUND,true);setArenaAwake(false);setPersistenceRequired();setNoGravity(true);}
    public void setArenaAwake(boolean awake){
        if(!isArenaBound())return;
        entityData.set(ARENA_BOUND,true);
        if(arenaAwake()!=awake||!awake&&(!isNoAi()||pending>=0)){
            for(var attack:attacks)if(attack.isAlive())attack.discard();attacks.clear();
            burrow.reset();pending=-1;coreChannel=0;quakeTicks=0;setCasting(false);cooldown=awake?35:60;
            if(!awake){setHealth(getMaxHealth());setTarget(null);}
        }
        entityData.set(ARENA_AWAKE,awake);setNoAi(!awake);setNoGravity(true);getNavigation().stop();setDeltaMovement(Vec3.ZERO);
        if(!awake)bar.removeAllPlayers();
    }
    private Vec3 quakeCentre=Vec3.ZERO;
    private final java.util.Set<java.util.UUID> quakeHits=new java.util.HashSet<>();
    public float quakeAge(float partial){return quakeTicks>0?80-quakeTicks+partial:-1;}
    public Vec3 quakeCentre(){return quakeCentre;}
    public void startQuake(){quakeTicks=80;quakeCentre=position();quakeHits.clear();level().broadcastEntityEvent(this,(byte)61);}
    @Override public void handleEntityEvent(byte event){if(event==61){quakeTicks=80;quakeCentre=position();}else super.handleEntityEvent(event);}
    private int cooldown=60,pending=-1,windup,casts,coreChannel,distantResponses,distantCooldown;
    private Vec3 aim=Vec3.ZERO;
    public StoneLancerEntity(EntityType<? extends StoneLancerEntity> type,Level level){super(type,level);xpReward=650;setPersistenceRequired();}
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,1000)
            .add(Attributes.ATTACK_DAMAGE,22).add(Attributes.ARMOR,16).add(Attributes.ARMOR_TOUGHNESS,8)
            .add(Attributes.MOVEMENT_SPEED,.17).add(Attributes.FOLLOW_RANGE,400).add(Attributes.KNOCKBACK_RESISTANCE,1);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(CASTING,false);entityData.define(BURROW,0);entityData.define(BURROW_START,0L);entityData.define(ARENA_BOUND,false);entityData.define(ARENA_AWAKE,false);}
    public int burrowPhase(){return entityData.get(BURROW);}
    void setBurrowPhase(int phase){entityData.set(BURROW_START,level().getGameTime());entityData.set(BURROW,phase);}
    void setCasting(boolean value){entityData.set(CASTING,value);}
    boolean beginBurrow(LivingEntity target){return burrow.begin(target);}
    void finishBurrowWithoutTarget(){cooldown=50;}
    public double burrowOffset(float partial){
        int phase=burrowPhase();if(phase==0)return 0;
        double age=level().isClientSide?Math.max(0,level().getGameTime()-entityData.get(BURROW_START)+partial):burrow.age()+partial;
        double t=Math.min(1,age/(phase==1?24.0:28.0));t=t*t*(3-2*t);
        return phase==1?-36*t:phase==2?-36:-36*(1-t);
    }
    @Override public boolean isInvulnerableTo(DamageSource source){return isArenaBound()&&!arenaAwake()||burrowPhase()==2&&!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)||super.isInvulnerableTo(source);}
    @Override public boolean isPushable(){return !isArenaBound()&&burrowPhase()==0&&super.isPushable();}
    @Override public boolean isPickable(){return burrowPhase()!=2&&super.isPickable();}
    public boolean isCasting(){return entityData.get(CASTING);}
    public boolean enraged(){return getHealth()<=getMaxHealth()*.5F;}
    public static int moveFor(int casts){return new int[]{0,1,4,2,1,3}[Math.floorMod(casts,6)];}
    public static int moveFor(int casts,double distanceSquared){
        int move=moveFor(casts);
        if(move==4&&distanceSquared>200*200)move=2;
        if(move==1&&distanceSquared>110*110)move=0;
        if(move==0&&distanceSquared>200*200)move=2+Math.floorMod(casts,2);
        return move;
    }
    static int distantMoveFor(int count){return Math.floorMod(count,2)==0?-1:6;}
    boolean tryDistantResponse(LivingEntity target){
        if(target==null||!target.isAlive()||target.isSpectator()||target.level()!=level()||distantCooldown>0||isCasting()||burrowPhase()!=0)return false;
        double distance=distanceToSqr(target);if(distance<=80*80||distance>360*360)return false;
        if(isArenaBound()) {if(!hasLineOfSight(target))return false;beginCast(6,target.position());}
        else if(distantMoveFor(distantResponses)==6&&hasLineOfSight(target))beginCast(6,target.position());
        else if(!beginBurrow(target))return false;
        distantResponses++;distantCooldown=260;return true;
    }
    @Override protected void registerGoals(){
        goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(2,new GiantApproachGoal(this,.9,()->!isCasting()&&!isArenaBound()));
        goalSelector.addGoal(8,new LookAtPlayerGoal(this,Player.class,320));goalSelector.addGoal(9,new RandomLookAroundGoal(this));
        targetSelector.addGoal(1,new HurtByTargetGoal(this));targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true));
    }
    @Override public void aiStep(){
        super.aiStep();if(!(level() instanceof ServerLevel server)||!isAlive())return;
        if(isArenaBound()){
            double centreX=com.tnc.tnc.world.eartharena.EarthArenaLayout.X+.5, centreY=com.tnc.tnc.world.eartharena.EarthArenaLayout.FLOOR+1;
            if(distanceToSqr(centreX,centreY,.5)>.000001)setPos(centreX,centreY,.5);
            getNavigation().stop();setDeltaMovement(Vec3.ZERO);
            if(!arenaAwake())return;
            if(!com.tnc.tnc.world.eartharena.EarthArenaEncounter.participant(getTarget()))setTarget(null);
            for(var player:server.players()){
                if(com.tnc.tnc.world.eartharena.EarthArenaEncounter.participant(player))bar.addPlayer(player);else bar.removePlayer(player);
            }
        }
        bar.setName(getDisplayName());bar.setProgress(getHealth()/getMaxHealth());
        if(tickCount%20==0)attacks.removeIf(e->!e.isAlive());
        if(distantCooldown>0)distantCooldown--;
        if(burrow.tick())return;
        if(coreChannel>0){
            getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);
            if(--coreChannel==0){setCasting(false);cooldown=enraged()?55:85;}return;
        }
        if(pending>=0){
            getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);
            if(getTarget()!=null)getLookControl().setLookAt(getTarget(),30,30);
            if(--windup<=0){StoneLancerCombat.release(this,pending,aim);coreChannel=pending==6?84:0;pending=-1;setCasting(coreChannel>0);casts++;cooldown=enraged()?55:85;}
            return;
        }
        if(tryDistantResponse(getTarget()))return;
        if(cooldown>0){cooldown--;return;}
        var target=getTarget();if(target==null||!target.isAlive()||distanceToSqr(target)>360*360||!hasLineOfSight(target))return;
        beginCast(moveFor(casts,distanceToSqr(target)),target.position());
    }
    void beginCast(int move,Vec3 target){
        double dx=target.x-getX(),dz=target.z-getZ();setYRot((float)Math.toDegrees(Math.atan2(-dx,dz)));yBodyRot=getYRot();
        pending=move;aim=StoneLancerCombat.ground(this,target);windup=new int[]{28,32,44,36,32,24,60}[move];
        entityData.set(CASTING,true);getNavigation().stop();triggerAnim("action",new String[]{"volley","fissure","sky","core","fissure","core_followup","gravity_core"}[move]);
        if(move!=5)StoneLancerCombat.seal(this,position(),move==6?80:25,move==6?150:windup+16);
        if(move==2)StoneLancerCombat.seal(this,aim,20,windup+24);
        level().playSound(null,blockPosition(),SoundEvents.IRON_GOLEM_REPAIR,SoundSource.HOSTILE,1.5F,.55F);
    }
    @Override public boolean doHurtTarget(Entity target){
        if(burrowPhase()!=0)return false;
        boolean hit=super.doHurtTarget(target);if(hit){triggerAnim("action","melee");playSound(SoundEvents.IRON_GOLEM_ATTACK,1.4F,.6F);}return hit;
    }
    @Override protected AABB makeBoundingBox(){return GiantSimulation.active(this)?GiantSimulation.bounds(this):super.makeBoundingBox();}
    @Override public void move(MoverType type,Vec3 motion){if(isArenaBound()){setDeltaMovement(Vec3.ZERO);return;}GiantSimulation.run(this,()->super.move(type,motion));}
    @Override protected void checkInsideBlocks(){GiantSimulation.run(this,()->super.checkInsideBlocks());}
    @Override public void updateFluidHeightAndDoFluidPushing(java.util.function.Predicate<net.minecraft.world.level.material.FluidState> p){GiantSimulation.run(this,()->super.updateFluidHeightAndDoFluidPushing(p));}
    @Override public AABB getBoundingBoxForCulling(){return super.getBoundingBoxForCulling().inflate(20,10,20).expandTowards(0,40,0).expandTowards(0,-40,0);}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public void startSeenByPlayer(ServerPlayer p){super.startSeenByPlayer(p);if(!isArenaBound()||arenaAwake()&&com.tnc.tnc.world.eartharena.EarthArenaEncounter.participant(p))bar.addPlayer(p);}
    @Override public void stopSeenByPlayer(ServerPlayer p){super.stopSeenByPlayer(p);bar.removePlayer(p);}
    @Override public void remove(RemovalReason reason){burrow.reset();bar.removeAllPlayers();for(var e:attacks)if(e.isAlive())e.discard();attacks.clear();super.remove(reason);}
    @Override public void die(DamageSource source){burrow.reset();triggerAnim("action","death");super.die(source);}
    @Override protected void dropCustomDeathLoot(DamageSource source,int looting,boolean recentlyHit){super.dropCustomDeathLoot(source,looting,recentlyHit);spawnAtLocation(Items.RAW_GOLD,16);spawnAtLocation(Items.DIAMOND,4);}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putInt("StoneCasts",casts);tag.putInt("StoneCooldown",cooldown);burrow.save(tag);tag.putInt("StoneDistantResponses",distantResponses);tag.putInt("StoneDistantCooldown",distantCooldown);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);casts=Math.max(0,tag.getInt("StoneCasts"));cooldown=Math.max(20,Math.min(100,tag.getInt("StoneCooldown")));pending=-1;entityData.set(CASTING,false);burrow.load(tag);coreChannel=0;distantResponses=Math.max(0,tag.getInt("StoneDistantResponses"));distantCooldown=Math.max(0,Math.min(260,tag.getInt("StoneDistantCooldown")));}
    @Override public void tick(){
        super.tick();
        if(level().isClientSide&&isAlive()&&burrowPhase()!=0&&tickCount%2==0)for(int i=0;i<10;i++)
            level().addParticle(com.tnc.tnc.magic.EarthParticles.DUST.get(),getX()+(random.nextDouble()-.5)*12,getY()+.2,getZ()+(random.nextDouble()-.5)*12,(random.nextDouble()-.5)*.16,.08+random.nextDouble()*.1,(random.nextDouble()-.5)*.16);
        if(level().isClientSide&&isAlive()&&tickCount%5==0&&walkAnimation.isMoving())for(int i=0;i<2;i++)
            level().addParticle(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,net.minecraft.world.level.block.Blocks.TUFF.defaultBlockState()),getX()+(random.nextDouble()-.5)*3,getY()+.15,getZ()+(random.nextDouble()-.5)*3,0,.03,0);
        if(!level().isClientSide&&tickCount%18==0&&!isCasting()&&onGround()&&getDeltaMovement().horizontalDistanceSqr()>.0001)playSound(SoundEvents.IRON_GOLEM_STEP,4F,.45F);
        if(quakeTicks>0){
            quakeTicks--;
            if(!level().isClientSide&&quakeTicks%5==0){
                double front=QUAKE_RADIUS*(80-quakeTicks)/80.0;
                for(var enemy:level().getEntitiesOfClass(LivingEntity.class,new AABB(quakeCentre,quakeCentre).inflate(QUAKE_RADIUS,24,QUAKE_RADIUS),
                        e->e!=this&&e.isAlive()&&!e.isSpectator()&&!e.isAlliedTo(this))){
                    double distance=enemy.position().subtract(quakeCentre).horizontalDistance();
                    if(distance<=front&&quakeHits.add(enemy.getUUID())){enemy.hurt(damageSources().mobAttack(this),26);enemy.push(0,.8,0);enemy.hurtMarked=true;}
                }
            }
        }
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c){
        c.add(new AnimationController<>(this,"move",6,s->isCasting()?PlayState.STOP:s.setAndContinue(RawAnimation.begin().thenLoop(s.isMoving()?"march":"idle"))));
        var action=new AnimationController<StoneLancerEntity>(this,"action",2,s->PlayState.STOP);
        for(String a:new String[]{"volley","fissure","sky","core","melee","death","burrow","emerge_core","core_followup","gravity_core"})action.triggerableAnim(a,RawAnimation.begin().thenPlay(a));c.add(action);
    }
}
