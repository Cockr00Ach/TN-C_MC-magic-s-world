package com.tnc.tnc.world.wind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
/** One bounded modelled wave or seeking, fusing cyclone; no recursive emitters. */
public final class CanglanTempestEntity extends Entity {
 private static final EntityDataAccessor<Float> SCALE=SynchedEntityData.defineId(CanglanTempestEntity.class,EntityDataSerializers.FLOAT);
 private static final EntityDataAccessor<Boolean> TORNADO=SynchedEntityData.defineId(CanglanTempestEntity.class,EntityDataSerializers.BOOLEAN),ANCHORED=SynchedEntityData.defineId(CanglanTempestEntity.class,EntityDataSerializers.BOOLEAN);
 private CanglanWarlordEntity owner;private LivingEntity seekTarget;private Vec3 velocity=Vec3.ZERO;private int life=60;
 private float displayScale=1,oldDisplayScale=1;
 public CanglanTempestEntity(EntityType<? extends CanglanTempestEntity> t,Level l){super(t,l);noPhysics=true;setNoGravity(true);}
 protected void defineSynchedData(){entityData.define(SCALE,1F);entityData.define(TORNADO,false);entityData.define(ANCHORED,false);}
 public float visualScale(){return entityData.get(SCALE);}public boolean tornado(){return entityData.get(TORNADO);}
 public boolean anchored(){return entityData.get(ANCHORED);}
 public float renderScale(float partial){return net.minecraft.util.Mth.lerp(partial,oldDisplayScale,displayScale);}
 @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(SCALE.equals(key)&&tickCount<2)oldDisplayScale=displayScale=visualScale();}
 public static CanglanTempestEntity spawn(CanglanWarlordEntity owner,Vec3 at,Vec3 direction,boolean tornado,float scale,int life){
  var e=CanglanWarlordRegistration.TEMPEST.create(owner.level());if(e==null)throw new IllegalStateException("Tempest not registered");
  e.owner=owner;e.seekTarget=owner.getTarget();e.life=life;e.entityData.set(SCALE,tornado?scale*2:scale);e.entityData.set(TORNADO,tornado);e.setPos(at);e.velocity=direction.normalize().scale(1.5);e.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));owner.level().addFreshEntity(e);return e;
 }
 private boolean enemy(LivingEntity v){return v!=owner&&v.isAlive()&&!v.isSpectator()&&!owner.isAlliedTo(v);}
 public void tick(){
  super.tick();
  if(level().isClientSide){oldDisplayScale=displayScale;displayScale+=(visualScale()-displayScale)*.3F;return;}
  if(owner==null||!owner.isAlive()||owner.level()!=level()||tickCount>life){discard();return;}
  Vec3 from=position(),to=from;
  if(!tornado())to=from.add(velocity);
  else if(!anchored()){
   if(seekTarget==null||!enemy(seekTarget)||seekTarget.level()!=level())seekTarget=owner.getTarget();
   if(seekTarget==null)seekTarget=level().getNearestPlayer(this,128);
   if(seekTarget!=null&&enemy(seekTarget)){
    Vec3 delta=seekTarget.position().subtract(from).multiply(1,0,1);
    double speed=Math.min(4,2.2*Math.sqrt(visualScale()/2));
    to=from.add(delta.normalize().scale(Math.min(speed,delta.length())));
   }
  }
  AABB corridor=new AABB(from,to).inflate(1,2,1);
  if(!level().hasChunksAt(net.minecraft.core.BlockPos.containing(corridor.minX,corridor.minY,corridor.minZ),net.minecraft.core.BlockPos.containing(corridor.maxX,corridor.maxY,corridor.maxZ))){discard();return;}
  if(from.distanceToSqr(to)>1E-8){
   HitResult hit=level().clip(new ClipContext(from.add(0,1,0),to.add(0,1,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
   if(hit.getType()!=HitResult.Type.MISS){
    if(!tornado()){discard();return;}
    to=hit.getLocation().subtract(0,1,0);entityData.set(ANCHORED,true);
   }
  }
  if(tornado()&&!anchored()){
   double radius=2.7*visualScale(),closest=Double.POSITIVE_INFINITY;Vec3 contact=null;
   for(var v:level().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(radius,6*visualScale(),radius),v->enemy(v)&&(v instanceof Player||v==seekTarget))){
    AABB body=v.getBoundingBox().inflate(radius,0,radius);Vec3 start=from.add(0,1,0),end=to.add(0,1,0);
    Vec3 point=body.contains(start)?start:body.clip(start,end).orElse(null);
    if(point!=null&&from.distanceToSqr(point)<closest&&owner.hasLineOfSight(v)){closest=from.distanceToSqr(point);contact=point.subtract(0,1,0);}
   }
   if(contact!=null){to=contact.add(to.subtract(from).normalize().scale(.05));entityData.set(ANCHORED,true);}
  }
  setPos(to);
  if(tornado()&&tickCount%4==0)fuseNearby();
  if(tickCount%6!=0)return;
  double r=(tornado()?2.7:8.5)*visualScale();int budget=0;
  for(var v:level().getEntitiesOfClass(LivingEntity.class,new AABB(from,position()).inflate(r,tornado()?6*visualScale():5*visualScale(),r),this::enemy)){
   if(++budget>32)break;if(!owner.hasLineOfSight(v))continue;
   float damage=tornado()?5*Math.max(1,Math.min(3,visualScale()/(2.5F*owner.battleScale()))):18;
   v.hurt(level().damageSources().indirectMagic(this,owner),damage);
   if(tornado()){Vec3 d=position().subtract(v.position()).multiply(1,0,1).normalize().scale(.18);v.push(d.x,.12,d.z);v.hurtMarked=true;}
  }
 }
 private void fuseNearby(){
  boolean combined=false;int budget=0;
  for(var other:level().getEntitiesOfClass(CanglanTempestEntity.class,getBoundingBox().inflate(12*owner.battleScale()+1.5*visualScale()),e->e!=this&&!e.isRemoved()&&e.tornado()&&e.owner==owner&&e.getId()>getId())){
   if(++budget>12)break;
   if(position().distanceToSqr(other.position())>Math.pow(1.5*(visualScale()+other.visualScale()),2))continue;
   float a=visualScale(),b=other.visualScale();
   if(!anchored()){
    if(other.anchored()){setPos(other.position());entityData.set(ANCHORED,true);}
    else setPos(position().lerp(other.position(),b*b/(a*a+b*b)));
   }
   entityData.set(SCALE,Math.min(8*owner.battleScale(),(float)Math.sqrt(a*a+b*b)));
   life=tickCount+Math.min(120,Math.max(life-tickCount,other.life-other.tickCount));
   if(seekTarget==null)seekTarget=other.seekTarget;
   other.discard();combined=true;
  }
  if(combined){
   level().playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP,net.minecraft.sounds.SoundSource.HOSTILE,1.4F,.65F);
   ((net.minecraft.server.level.ServerLevel)level()).sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,getX(),getY()+1,getZ(),8,visualScale(),.5,visualScale(),.1);
  }
 }
 protected void readAdditionalSaveData(CompoundTag n){discard();}protected void addAdditionalSaveData(CompoundTag n){}
 @Override public boolean shouldRenderAtSqrDistance(double distance){return distance<512D*512D;}
 public AABB getBoundingBoxForCulling(){return super.getBoundingBoxForCulling().inflate(20*visualScale());}
 public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
