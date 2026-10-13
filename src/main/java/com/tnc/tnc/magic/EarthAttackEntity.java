package com.tnc.tnc.magic;

import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraft.network.syncher.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.sounds.*;
import net.minecraft.world.level.block.Blocks;

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
    public void tick(){super.tick();if(level().isClientSide()){clientFx();return;}
        if(tickCount%5==0)entityData.set(AGE,tickCount);
        var caster=level().getEntity(owner);if(!(caster instanceof LivingEntity living)||!living.isAlive()||tickCount>ttl){discard();return;}
        if(done()){if(tickCount>hitAt+14)discard();return;}
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
        } else if(mode()==7) {
            double t=Math.min(1,age/18.0);t=1-Math.pow(1-t,3);setPos(start.lerp(end,t));
            if(age==18)burst(living);
        } else if(mode()==2) {
            double t=Math.max(0,Math.min(1,age/60.0));t=t*t*(3-2*t);setPos(start.lerp(end,t));
            if(age>=80)burst(living);
        }
    }
    private void clientFx() {
        int age=tickCount-waitTicks();if(age<0||done())return;
        int count=mode()==2?4:(mode()==0||mode()==3||mode()==5?3:2);
        for(int i=0;i<count;i++) {
            double a=(tickCount*.71+i*Math.PI*2/count),r=mode()==2?Math.max(1,scale()*.22):Math.max(.25,scale()*.12);
            level().addParticle(EarthParticles.DUST.get(),getX()+Math.cos(a)*r,getY()+.25+Math.sin(a*1.7)*r*.3,getZ()+Math.sin(a)*r,
                    Math.cos(a)*.025,.035,Math.sin(a)*.025);
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
    private void burst(LivingEntity caster){hit(caster);
        Vec3 centre=damageCentre==null?position():damageCentre;
        setPos(centre);
        entityData.set(DONE,true);hitAt=tickCount;entityData.set(BURST_AGE,tickCount);
        var server=(ServerLevel)level();
        int cloud=mode()==2?150:mode()==3?100:55;
        server.sendParticles(EarthParticles.DUST.get(),centre.x,centre.y+.35,centre.z,cloud,radius*.5,Math.min(3,radius*.3),radius*.5,.34);
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.COARSE_DIRT.defaultBlockState()),centre.x,centre.y+.25,centre.z,
                mode()==2?44:22,radius*.35,1.2,radius*.35,.26);
        for(int i=0;i<24;i++) {
            double a=i*Math.PI*2/24,s=.35+(i%5)*.08;
            server.sendParticles(EarthParticles.DUST.get(),centre.x,centre.y+.45,centre.z,0,Math.cos(a)*s,.10+(i%3)*.08,Math.sin(a)*s,1);
        }
        server.playSound(null,centre.x,centre.y,centre.z,SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,1.55F,.58F);
        server.playSound(null,centre.x,centre.y,centre.z,SoundEvents.STONE_BREAK,SoundSource.PLAYERS,1.8F,.72F);
    }
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
    public boolean isPickable(){return false;}public boolean isAttackable(){return false;}
    public boolean shouldRenderAtSqrDistance(double distance){return distance<512*512;}
    public AABB getBoundingBoxForCulling(){return super.getBoundingBoxForCulling().inflate(Math.max(scale(),height())*2+3);}
    protected void readAdditionalSaveData(CompoundTag tag){discard();}protected void addAdditionalSaveData(CompoundTag tag){}
}
