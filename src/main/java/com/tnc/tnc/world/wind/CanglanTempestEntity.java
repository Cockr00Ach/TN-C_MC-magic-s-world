package com.tnc.tnc.world.wind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
/** One bounded modelled wave or cyclone; no recursive wind-blade emitters. */
public final class CanglanTempestEntity extends Entity {
 private static final EntityDataAccessor<Float> SCALE=SynchedEntityData.defineId(CanglanTempestEntity.class,EntityDataSerializers.FLOAT);
 private static final EntityDataAccessor<Boolean> TORNADO=SynchedEntityData.defineId(CanglanTempestEntity.class,EntityDataSerializers.BOOLEAN);
 private CanglanWarlordEntity owner;private Vec3 velocity=Vec3.ZERO;private int life=60;
 public CanglanTempestEntity(EntityType<? extends CanglanTempestEntity> t,Level l){super(t,l);noPhysics=true;setNoGravity(true);}
 protected void defineSynchedData(){entityData.define(SCALE,1F);entityData.define(TORNADO,false);}
 public float visualScale(){return entityData.get(SCALE);}public boolean tornado(){return entityData.get(TORNADO);}
 public static CanglanTempestEntity spawn(CanglanWarlordEntity owner,Vec3 at,Vec3 direction,boolean tornado,float scale,int life){
  var e=CanglanWarlordRegistration.TEMPEST.create(owner.level());if(e==null)throw new IllegalStateException("Tempest not registered");
  e.owner=owner;e.life=life;e.entityData.set(SCALE,scale);e.entityData.set(TORNADO,tornado);e.setPos(at);e.velocity=direction.normalize().scale(1.5);e.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));owner.level().addFreshEntity(e);return e;
 }
 public void tick(){super.tick();if(level().isClientSide)return;if(owner==null||!owner.isAlive()||owner.level()!=level()||tickCount>life){discard();return;}
  Vec3 from=position(),to=tornado()?from:from.add(velocity);
  if(!level().hasChunkAt(net.minecraft.core.BlockPos.containing(to))){discard();return;}
  if(!tornado()&&level().clip(new ClipContext(from.add(0,1,0),to.add(0,1,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()!=HitResult.Type.MISS){discard();return;}
  setPos(to);if(tickCount%6!=0)return;
  double r=(tornado()?2.7:6)*visualScale();int budget=0;
  for(var v:level().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(r,tornado()?6*visualScale():2*visualScale(),r),v->v!=owner&&v.isAlive()&&!v.isSpectator()&&!owner.isAlliedTo(v))){
   if(++budget>32)break;if(!owner.hasLineOfSight(v))continue;
   v.hurt(level().damageSources().indirectMagic(this,owner),tornado()?5:18);
   if(tornado()){Vec3 d=position().subtract(v.position()).multiply(1,0,1).normalize().scale(.18);v.push(d.x,.12,d.z);v.hurtMarked=true;}
  }
 }
 protected void readAdditionalSaveData(CompoundTag n){discard();}protected void addAdditionalSaveData(CompoundTag n){}
 public AABB getBoundingBoxForCulling(){return super.getBoundingBoxForCulling().inflate(20*visualScale());}
 public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
