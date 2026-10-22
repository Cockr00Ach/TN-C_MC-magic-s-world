package com.tnc.tnc.world.wind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.UUID;
/** Lightweight, non-colliding visual anchor. No mob AI, attributes, pathfinder or drops. */
public final class CanglanEchoEntity extends Entity implements GeoEntity {
 private static final EntityDataAccessor<Float> SCALE=SynchedEntityData.defineId(CanglanEchoEntity.class,EntityDataSerializers.FLOAT),LX=SynchedEntityData.defineId(CanglanEchoEntity.class,EntityDataSerializers.FLOAT),LY=SynchedEntityData.defineId(CanglanEchoEntity.class,EntityDataSerializers.FLOAT),LZ=SynchedEntityData.defineId(CanglanEchoEntity.class,EntityDataSerializers.FLOAT);
 private static final EntityDataAccessor<Boolean> ACTIVE=SynchedEntityData.defineId(CanglanEchoEntity.class,EntityDataSerializers.BOOLEAN);
 private static final EntityDataAccessor<Integer> POSE=SynchedEntityData.defineId(CanglanEchoEntity.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Long> ACTIVATED=SynchedEntityData.defineId(CanglanEchoEntity.class,EntityDataSerializers.LONG);
 private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
 private CanglanWarlordEntity owner;
 public CanglanEchoEntity(EntityType<? extends CanglanEchoEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
 protected void defineSynchedData(){entityData.define(SCALE,1F);entityData.define(LX,0F);entityData.define(LY,0F);entityData.define(LZ,0F);entityData.define(ACTIVE,false);entityData.define(POSE,0);entityData.define(ACTIVATED,0L);}
 public void configure(CanglanWarlordEntity owner,Vec3 at,float yaw,float scale){this.owner=owner;setPos(at);setYRot(yaw);entityData.set(SCALE,scale);entityData.set(POSE,owner.anchorCount()%3);}
 public String poseAnimation(){return switch(entityData.get(POSE)){case 1->"echo_lunge";case 2->"echo_recover";default->"echo_guard";};}
 public void setPoseIndex(int index){entityData.set(POSE,Math.floorMod(index,3));}
 public float visualScale(){return entityData.get(SCALE);}public boolean active(){return entityData.get(ACTIVE);}
 public void link(Vec3 at){Vec3 d=at.subtract(position());entityData.set(LX,(float)d.x);entityData.set(LY,(float)d.y);entityData.set(LZ,(float)d.z);}
 public Vec3 link(){return new Vec3(entityData.get(LX),entityData.get(LY),entityData.get(LZ));}
 public void activate(){entityData.set(ACTIVATED,level().getGameTime());entityData.set(ACTIVE,true);}
 public float opacity(float partial){float born=Math.min(1,(tickCount+partial)/8F);if(!active())return born;double age=level().getGameTime()-entityData.get(ACTIVATED)+partial;return born*(float)Math.max(0,Math.min(1,(35-age)/10));}
 public void tick(){super.tick();if(!level().isClientSide&&(owner==null||!owner.isAlive()||owner.level()!=level()||tickCount>CanglanWarGodRules.ANCHOR_LIFETIME))discard();}
 public void registerControllers(AnimatableManager.ControllerRegistrar c){c.add(new AnimationController<>(this,"echo",4,s->s.setAndContinue(RawAnimation.begin().thenLoop(active()?"cyclone":poseAnimation()))));}
 public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
 protected void readAdditionalSaveData(CompoundTag n){discard();}protected void addAdditionalSaveData(CompoundTag n){}
 public AABB getBoundingBoxForCulling(){return super.getBoundingBoxForCulling().expandTowards(link()).inflate(12*visualScale(),8*visualScale(),12*visualScale());}
 public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
