package com.tnc.tnc.magic;

import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraft.network.syncher.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.*;

/** Timed solid-model attacks. Terrain is never deleted or permanently changed. */
public final class EarthAttackEntity extends Entity {
    private static final EntityDataAccessor<Integer> MODE=SynchedEntityData.defineId(EarthAttackEntity.class,EntityDataSerializers.INT),
            WAIT=SynchedEntityData.defineId(EarthAttackEntity.class,EntityDataSerializers.INT),AGE=SynchedEntityData.defineId(EarthAttackEntity.class,EntityDataSerializers.INT),BURST_AGE=SynchedEntityData.defineId(EarthAttackEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> MODEL=SynchedEntityData.defineId(EarthAttackEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> SCALE=SynchedEntityData.defineId(EarthAttackEntity.class,EntityDataSerializers.FLOAT),
            HEIGHT=SynchedEntityData.defineId(EarthAttackEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DONE=SynchedEntityData.defineId(EarthAttackEntity.class,EntityDataSerializers.BOOLEAN);
    private Vec3 start=Vec3.ZERO,end=Vec3.ZERO,velocity=Vec3.ZERO;
    private int owner=-1,ttl=100,hitAt=-1;private float damage;private double radius;
    private DamageSource source;private final WindEarthVisualClock clock=new WindEarthVisualClock();
    Vec3 damageCentre;
    public EarthAttackEntity(EntityType<? extends EarthAttackEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    protected void defineSynchedData(){entityData.define(MODE,0);entityData.define(WAIT,0);entityData.define(AGE,0);entityData.define(BURST_AGE,-100);entityData.define(MODEL,"earth_clod");entityData.define(SCALE,1F);entityData.define(HEIGHT,1F);entityData.define(DONE,false);}
    public int mode(){return entityData.get(MODE);}public int waitTicks(){return entityData.get(WAIT);}
    public String model(){return entityData.get(MODEL);}public float scale(){return entityData.get(SCALE);}public float height(){return entityData.get(HEIGHT);}
    public int burstAge(){return entityData.get(BURST_AGE);}
    public boolean done(){return entityData.get(DONE);}public double age(float partial){return clock.phase(tickCount,partial,entityData.get(AGE),1);}
    public void configure(LivingEntity caster,int mode,String model,Vec3 start,Vec3 end,float scale,float height,int wait,int ttl,float damage,double radius,DamageSource source) {
        owner=caster.getId();this.start=start;this.end=end;this.ttl=ttl;this.damage=damage;this.radius=radius;this.source=source;
        entityData.set(MODE,mode);entityData.set(MODEL,model);entityData.set(SCALE,scale);entityData.set(HEIGHT,height);entityData.set(WAIT,wait);
        velocity=end.subtract(start).normalize().scale(mode==3?start.distanceTo(end)+2:1.8);setPos(start);
        setYRot((float)Math.toDegrees(Math.atan2(velocity.x,velocity.z)));
        setXRot((float)-Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,velocity.normalize().y)))));
    }
    public void tick(){super.tick();if(level().isClientSide())return;
        if(tickCount%5==0)entityData.set(AGE,tickCount);
        var caster=level().getEntity(owner);if(!(caster instanceof LivingEntity living)||!living.isAlive()||tickCount>ttl){discard();return;}
        if(done()){if(tickCount>hitAt+6)discard();return;}
        int age=tickCount-waitTicks();if(age<=0)return;
        if(mode()==0||mode()==3||mode()==5) {
            if(age>24){discard();return;}
            if(age==1)((ServerLevel)level()).playSound(null,position().x,position().y,position().z,SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,.65F,1.2F);
            Vec3 next=position().add(velocity);
            var wall=level().clip(new ClipContext(position(),next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            if(wall.getType()!=HitResult.Type.MISS)next=wall.getLocation();
            LivingEntity target=null;double best=Double.MAX_VALUE;
            for(var e:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().expandTowards(velocity).inflate(.8),
                    e->e.isAlive()&&e!=living&&!e.isAlliedTo(living)&&!e.isSpectator())) {
                var hit=e.getBoundingBox().inflate(.4).clip(position(),next);
                if(hit.isPresent()&&position().distanceToSqr(hit.get())<best){target=e;best=position().distanceToSqr(hit.get());next=hit.get();}
            }
            setPos(next);
            if(target!=null||wall.getType()!=HitResult.Type.MISS||mode()==3&&age>=1)burst(living);
        } else if(mode()==1||mode()==4) {
            if(age==(mode()==4?1:8))hit(living);
        } else if(mode()==6) {
            double t=Math.min(1,age/40.0);t=t*t*(3-2*t);setPos(start.lerp(end,t));
            if(age==40)hit(living);
        } else if(mode()==2) {
            double t=Math.max(0,Math.min(1,age/60.0));t=t*t*(3-2*t);setPos(start.lerp(end,t));
            if(age>=80)burst(living);
        }
    }
    private void hit(LivingEntity caster) {
        Vec3 centre=damageCentre==null?position():damageCentre;
        for(var e:level().getEntitiesOfClass(LivingEntity.class,AABB.ofSize(centre,radius*2,radius*2,radius*2),
                e->e.isAlive()&&e!=caster&&!e.isAlliedTo(caster)&&!e.isSpectator())) {
            var box=e.getBoundingBox();
            Vec3 closest=new Vec3(net.minecraft.util.Mth.clamp(centre.x,box.minX,box.maxX),net.minecraft.util.Mth.clamp(centre.y,box.minY,box.maxY),net.minecraft.util.Mth.clamp(centre.z,box.minZ,box.maxZ));
            if(closest.distanceToSqr(centre)>radius*radius)continue;
            e.invulnerableTime=0;e.hurt(source==null?level().damageSources().indirectMagic(this,caster):source,damage);
            Vec3 delta=e.position().subtract(position()).normalize();e.setDeltaMovement(e.getDeltaMovement().add(delta.scale(.3)).add(0,.25,0));e.hurtMarked=true;
            if(mode()==1||mode()==4||mode()==6)e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,60,0));
        }
    }
    private void burst(LivingEntity caster){hit(caster);entityData.set(DONE,true);hitAt=tickCount;entityData.set(BURST_AGE,tickCount);
        var server=(ServerLevel)level();server.sendParticles(EarthParticles.DUST.get(),getX(),getY(),getZ(),mode()==2||mode()==3?70:18,radius*.4,Math.min(3,radius*.25),radius*.4,.25);
        server.playSound(null,getX(),getY(),getZ(),SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,1.2F,.65F);
    }
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
    public boolean isPickable(){return false;}public boolean isAttackable(){return false;}
    public boolean shouldRenderAtSqrDistance(double distance){return distance<512*512;}
    public AABB getBoundingBoxForCulling(){return super.getBoundingBoxForCulling().inflate(Math.max(scale(),height())*2+3);}
    protected void readAdditionalSaveData(CompoundTag tag){discard();}protected void addAdditionalSaveData(CompoundTag tag){}
}
