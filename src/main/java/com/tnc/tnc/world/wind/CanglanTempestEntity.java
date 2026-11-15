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
  // ★ 2026-11-15：和玩家龙卷风同一套"强力吸力"（作者上一轮：四格内的敌人被卷到飞起）✓
  //   Boss 的旋风更大 ⇒ 吸附半径跟着 visualScale 走，但**力度只给玩家的 0.9 倍**，
  //   免得把玩家直接甩出战场 ✗。
  if(tornado())swirlNearby();
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
 /** 吸力半径（格）✓ —— 比伤害半径略大一点，读作"风先拽进来、再挨打" ✓。 */
 private double suctionRadius(){return Math.max(3.0D,2.7D*visualScale()*1.15D);}

 /** 吸力作用的高度（格）✓（和渲染的线旋风一致）。 */
 private double suctionHeight(){return Math.max(3.0D,2.7D*visualScale()*2.6D);}

 /**
  * ★ <b>强力吸力</b>：把范围内的敌人拽向旋风轴并往上卷（2026-11-15 跟着玩家龙卷风一起做）✓
  *
  * <p>速度完全由 {@link com.tnc.tnc.magic.TornadoSwirlRules} 给（切向转＋向内收＋上升，有封顶），
  * 这里只乘 0.9 稍微收着点 —— Boss 的吸力不该让玩家完全失去操作 ✗。
  */
 private void swirlNearby(){
  double radius=suctionRadius(),height=suctionHeight();
  for(var v:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(radius,height+2,radius),this::enemy)){
   if(v instanceof CanglanWarlordEntity)continue;
   double dx=v.getX()-getX(),dz=v.getZ()-getZ(),dy=v.getY()-getY();
   if(dx*dx+dz*dz>radius*radius||dy<-1||dy>height+1)continue;
   double[] vel=com.tnc.tnc.magic.TornadoSwirlRules.velocity(dx,dz,Math.max(0,dy),height,5);
   v.setDeltaMovement(vel[0]*0.9D,vel[1]*0.9D,vel[2]*0.9D);
   v.fallDistance=0;v.hurtMarked=true;
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
