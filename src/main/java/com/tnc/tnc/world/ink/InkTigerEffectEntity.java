package com.tnc.tnc.world.ink;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.*;

/** Bounded transient effects; all collision, suction and damage run only on the server. */
public final class InkTigerEffectEntity extends Entity implements GeoEntity {
    public static final int SHOT=0, BALL=1, GHOST=2, STAIN=3, BURST=4;
    private static final EntityDataAccessor<Integer> MODE=SynchedEntityData.defineId(InkTigerEffectEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(InkTigerEffectEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> DONE=SynchedEntityData.defineId(InkTigerEffectEntity.class,EntityDataSerializers.BOOLEAN);
    private UUID ownerId,targetId;
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private final Deque<Vec3> trail=new ArrayDeque<>();
    private Vec3 lerpTarget=Vec3.ZERO;private int lerpSteps;
    public InkTigerEffectEntity(EntityType<? extends InkTigerEffectEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    public static InkTigerEffectEntity spawn(InkWingedTigerEntity owner,LivingEntity target,int mode,Vec3 at){
        var e=InkBossRegistration.INK_TIGER_EFFECT.create(owner.level());if(e==null)return null;
        e.ownerId=owner.getUUID();e.targetId=target==null?null:target.getUUID();
        e.entityData.set(MODE,mode);e.entityData.set(START,owner.level().getGameTime());
        e.moveTo(at.x,at.y,at.z,owner.getYRot(),0);
        if(mode==SHOT&&target!=null)e.setDeltaMovement(target.getBoundingBox().getCenter().subtract(at).normalize().scale(1.15));
        owner.level().addFreshEntity(e);return e;
    }
    public int mode(){return entityData.get(MODE);}
    public int age(){return (int)Math.max(0,level().getGameTime()-entityData.get(START));}
    public boolean finished(){return entityData.get(DONE);}
    public Deque<Vec3> trail(){return trail;}
    public boolean ownedBy(Entity owner){return owner.getUUID().equals(ownerId);}
    @Override protected void defineSynchedData(){entityData.define(MODE,SHOT);entityData.define(START,0L);entityData.define(DONE,false);}
    private InkWingedTigerEntity owner(){var e=ownerId==null?null:((ServerLevel)level()).getEntity(ownerId);return e instanceof InkWingedTigerEntity t?t:null;}
    public static boolean hostile(LivingEntity owner,Entity e){return e instanceof LivingEntity l&&l.isAlive()&&e!=owner&&!owner.isAlliedTo(e)&&!e.isSpectator()&&!(e instanceof Player p&&p.isCreative());}
    private boolean visible(Vec3 from,Vec3 to){return level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;}
    @Override public void tick(){
        super.tick();
        if(level().isClientSide){
            if(lerpSteps>0)setPos(position().lerp(lerpTarget,1.0/lerpSteps--));
            if(mode()==SHOT||mode()==GHOST){trail.addFirst(position());while(trail.size()>38)trail.removeLast();}return;
        }
        int t=age();
        if(mode()==STAIN||mode()==BURST){if(t>=(mode()==STAIN?60:36))discard();return;}
        var owner=owner();
        if(owner==null||!owner.isAlive()){
            if(mode()!=BALL||t<InkCraneAttackRules.BALL_EXPLODE||t>=InkCraneAttackRules.RITUAL_LIFE)discard();return;
        }
        if(mode()==BALL){
            if(t==InkCraneAttackRules.BALL_EXPLODE){hurtArea(owner,position().add(0,2,0),9,38);playSound(SoundEvents.GENERIC_EXPLODE,3,.5F);}
            if(t>=32&&t<100)pullBall(owner,t);
            if(t>=220)discard();return;
        }
        var target=targetId==null?null:((ServerLevel)level()).getEntity(targetId);
        if(target==null||!target.isAlive()||t>=(mode()==SHOT?100:95)){discard();return;}
        if(mode()==GHOST&&t<InkTigerAttackRules.GHOST_RELEASE){
            setPos(owner.position().subtract(Vec3.directionFromRotation(0,owner.getYRot()).scale(6*InkTigerAttackRules.BODY_MULTIPLIER)).add(0,InkTigerAttackRules.BODY_MULTIPLIER,0));setYRot(owner.getYRot());return;
        }
        if(mode()==GHOST&&finished()){if(t>=65)discard();return;}
        Vec3 start=position(),velocity=getDeltaMovement();
        if(mode()==GHOST){
            Vec3 aim=target.getBoundingBox().getCenter().subtract(start).normalize().scale(1.15);
            velocity=velocity.lengthSqr()<.01?aim:velocity.scale(.92).add(aim.scale(.08));setDeltaMovement(velocity);
        }
        Vec3 end=start.add(velocity);
        var block=level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        if(block.getType()!=HitResult.Type.MISS)end=block.getLocation();
        var hit=ProjectileUtil.getEntityHitResult(level(),this,start,end,getBoundingBox().expandTowards(velocity).inflate(mode()==GHOST?4:.8),
                e->hostile(owner,e)&&visible(start,e.getBoundingBox().getCenter()),mode()==GHOST?4F:.8F);
        setYRot((float)(Math.atan2(-velocity.x,velocity.z)*180/Math.PI));
        if(hit!=null){
            setPos(hit.getLocation());
            if(mode()==SHOT){hit.getEntity().hurt(damageSources().mobAttack(owner),8);anchorBall();}
            else{hurtArea(owner,position(),6,30);entityData.set(DONE,true);setDeltaMovement(Vec3.ZERO);}
        }else if(block.getType()!=HitResult.Type.MISS){
            setPos(end);if(mode()==SHOT)anchorBall();else{entityData.set(DONE,true);setDeltaMovement(Vec3.ZERO);}
        }else setPos(end);
    }
    private void anchorBall(){
        // The painting stays at the impact site, not attached to a fleeing player.
        var ground=net.minecraft.core.BlockPos.containing(position());int limit=Math.max(level().getMinBuildHeight()+1,ground.getY()-16);
        while(ground.getY()>limit&&level().getBlockState(ground.below()).getCollisionShape(level(),ground.below()).isEmpty())ground=ground.below();
        setPos(getX(),ground.getY()+.05,getZ());setDeltaMovement(Vec3.ZERO);entityData.set(MODE,BALL);entityData.set(START,level().getGameTime());
    }
    public void hurtArea(InkWingedTigerEntity owner,Vec3 center,double radius,float damage){
        for(var e:level().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),v->hostile(owner,v)))
            if(e.getBoundingBox().getCenter().distanceToSqr(center)<=radius*radius&&visible(center,e.getEyePosition()))e.hurt(damageSources().mobAttack(owner),damage);
    }
    public static void pull(InkWingedTigerEntity owner,Vec3 center,double radius,double acceleration,double cap){
        if(owner.level().isClientSide)return;
        for(var e:owner.level().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),v->hostile(owner,v))){
            Vec3 delta=center.subtract(e.position()).multiply(1,0,1);double d=delta.length();if(d<2||d>radius||!owner.hasLineOfSight(e))continue;
            Vec3 direction=delta.scale(1/d),v=e.getDeltaMovement();double force=Math.max(0,Math.min(acceleration*(1-.5*d/radius),cap-v.dot(direction)));
            e.setDeltaMovement(v.add(direction.scale(force)));e.hasImpulse=true;e.hurtMarked=true;
        }
    }
    private void pullBall(InkWingedTigerEntity owner,int age){
        Vec3 center=position().add(0,1,0);double radius=InkCraneAttackRules.PULL_RADIUS;
        for(var e:level().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),v->hostile(owner,v))){
            Vec3 delta=center.subtract(e.position()).multiply(1,0,1);double d=delta.length();
            if(d<.9||d>=radius||!visible(center,e.getEyePosition()))continue;
            Vec3 direction=delta.scale(1/d),v=e.getDeltaMovement();
            double force=InkCraneAttackRules.pullAcceleration(age,d,v.dot(direction));
            if(force<=0)continue;e.setDeltaMovement(v.add(direction.scale(force)));e.hasImpulse=true;e.hurtMarked=true;
        }
    }
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps,boolean teleport){lerpTarget=new Vec3(x,y,z);lerpSteps=teleport?1:Math.max(1,steps);setYRot(yaw);setXRot(pitch);}
    @Override public AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(mode()==BALL?128:mode()==GHOST?140:70);}
    @Override public boolean shouldRenderAtSqrDistance(double d){return d<256*256;}
    @Override public boolean shouldBeSaved(){return false;}
    @Override protected void readAdditionalSaveData(CompoundTag tag){discard();}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c){c.add(new AnimationController<>(this,"ghost",0,s->s.setAndContinue(RawAnimation.begin().thenLoop("ghost_charge"))));}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
