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
            HEIGHT=SynchedEntityData.defineId(EarthAttackEntity.class,EntityDataSerializers.FLOAT),
            DROP_HEIGHT=SynchedEntityData.defineId(EarthAttackEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DONE=SynchedEntityData.defineId(EarthAttackEntity.class,EntityDataSerializers.BOOLEAN);
    private Vec3 start=Vec3.ZERO,end=Vec3.ZERO,velocity=Vec3.ZERO;
    private int owner=-1,ttl=100,hitAt=-1;private float damage;private double radius;
    private DamageSource source;private final WindEarthVisualClock clock=new WindEarthVisualClock();
    Vec3 damageCentre;
    public EarthAttackEntity(EntityType<? extends EarthAttackEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    protected void defineSynchedData(){entityData.define(MODE,0);entityData.define(WAIT,0);entityData.define(AGE,0);entityData.define(BURST_AGE,-100);entityData.define(MODEL,"earth_clod");entityData.define(SCALE,1F);entityData.define(HEIGHT,1F);entityData.define(DROP_HEIGHT,0F);entityData.define(DONE,false);}
    public int mode(){return entityData.get(MODE);}public int waitTicks(){return entityData.get(WAIT);}
    public String model(){return entityData.get(MODEL);}public float scale(){return entityData.get(SCALE);}public float height(){return entityData.get(HEIGHT);}
    public float dropHeight(){return entityData.get(DROP_HEIGHT);}
    public int burstAge(){return entityData.get(BURST_AGE);}
    public int burstLife(){return mode()==2?24:14;}
    public boolean done(){return entityData.get(DONE);}public double age(float partial){return clock.phase(tickCount,partial,entityData.get(AGE),1);}
    public void configure(LivingEntity caster,int mode,String model,Vec3 start,Vec3 end,float scale,float height,int wait,int ttl,float damage,double radius,DamageSource source) {
        owner=caster.getId();this.start=start;this.end=end;this.ttl=ttl;this.damage=damage;this.radius=radius;this.source=source;
        entityData.set(MODE,mode);entityData.set(MODEL,model);entityData.set(SCALE,scale);entityData.set(HEIGHT,height);entityData.set(WAIT,wait);
        entityData.set(DROP_HEIGHT,mode==3?(float)Math.max(0,start.y-end.y):0F);
        velocity=end.subtract(start).normalize().scale(mode==3?start.distanceTo(end)+2:1.8);
        // Track the target on the ground while the renderer shows the suspended spear above it.
        setPos(mode==3?end:start);
        setYRot((float)Math.toDegrees(Math.atan2(velocity.x,velocity.z)));
        setXRot((float)-Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,velocity.normalize().y)))));
    }
    public void tick(){super.tick();if(level().isClientSide()){clientFx();return;}
        if(tickCount%5==0)entityData.set(AGE,tickCount);
        var caster=level().getEntity(owner);if(!(caster instanceof LivingEntity living)||!living.isAlive()||tickCount>ttl&&!done()){discard();return;}
        if(done()){if(tickCount>hitAt+burstLife())discard();return;}
        int age=tickCount-waitTicks();if(age<=0)return;
        if(mode()==0||mode()==3||mode()==5) {
            if(age>24){discard();return;}
            if(age==1)((ServerLevel)level()).playSound(null,position().x,position().y,position().z,SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,.65F,1.2F);
            Vec3 origin=mode()==3?start:position();
            Vec3 next=origin.add(velocity);
            var wall=level().clip(new ClipContext(origin,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            if(wall.getType()!=HitResult.Type.MISS)next=wall.getLocation();
            LivingEntity target=null;double best=Double.MAX_VALUE;
            var pathBounds=mode()==3?new AABB(origin,next).inflate(.8):getBoundingBox().expandTowards(velocity).inflate(.8);
            for(var e:level().getEntitiesOfClass(LivingEntity.class,pathBounds,
                    e->e.isAlive()&&e!=living&&!e.isAlliedTo(living)&&!e.isSpectator())) {
                var hit=e.getBoundingBox().inflate(.4).clip(origin,next);
                if(hit.isPresent()&&origin.distanceToSqr(hit.get())<best){target=e;best=origin.distanceToSqr(hit.get());next=hit.get();}
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
            if(age<80&&tickCount%2==0)attract(living,age);
            if(age>=80)burst(living);
        }
    }
    private void attract(LivingEntity caster,int age) {
        Vec3 centre=position();
        Vec3 ground=damageCentre==null?end:damageCentre;
        double reach=Math.max(radius*1.7,scale()*.75);
        // Keep catching ground targets as the sphere rises, and retain lifted targets near it.
        var area=AABB.ofSize(centre,reach*2,reach*2,reach*2).minmax(AABB.ofSize(ground,reach*2,reach*2,reach*2));
        for(var enemy:level().getEntitiesOfClass(LivingEntity.class,area,
                e->e.isAlive()&&e!=caster&&!e.isAlliedTo(caster)&&!e.isSpectator())) {
            Vec3 body=enemy.getBoundingBox().getCenter();
            Vec3 delta=centre.subtract(body);double distance=delta.length();
            if(Math.min(distance,ground.distanceTo(body))>reach||!caster.hasLineOfSight(enemy))continue;
            double force=3*(.18+Math.min(1,age/60.0)*.28)*Math.min(1,distance/2);
            Vec3 pull=delta.normalize().scale(force);
            // Attraction runs every two ticks: counter gravity so upward suction can lift mobs.
            if(delta.y>-.25&&!enemy.isNoGravity())pull=pull.add(0,.16,0);
            Vec3 motion=enemy.getDeltaMovement().scale(.72).add(pull);
            double speedLimit=Math.min(3*.85,Math.max(.16,distance*.5));
            if(motion.lengthSqr()>speedLimit*speedLimit)motion=motion.normalize().scale(speedLimit);
            enemy.setDeltaMovement(motion);enemy.hurtMarked=true;
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
        var area=AABB.ofSize(centre,radius*2,radius*2,radius*2);
        if(mode()==2)area=area.minmax(AABB.ofSize(position(),radius*2,radius*2,radius*2));
        for(var e:level().getEntitiesOfClass(LivingEntity.class,area,
                e->e.isAlive()&&e!=caster&&!e.isAlliedTo(caster)&&!e.isSpectator())) {
            var box=e.getBoundingBox();
            Vec3 closest=new Vec3(net.minecraft.util.Mth.clamp(centre.x,box.minX,box.maxX),net.minecraft.util.Mth.clamp(centre.y,box.minY,box.maxY),net.minecraft.util.Mth.clamp(centre.z,box.minZ,box.maxZ));
            Vec3 impactCentre=centre;
            if(closest.distanceToSqr(centre)>radius*radius) {
                if(mode()!=2)continue;
                Vec3 airborneClosest=new Vec3(net.minecraft.util.Mth.clamp(getX(),box.minX,box.maxX),net.minecraft.util.Mth.clamp(getY(),box.minY,box.maxY),net.minecraft.util.Mth.clamp(getZ(),box.minZ,box.maxZ));
                if(airborneClosest.distanceToSqr(position())>radius*radius)continue;
                impactCentre=position();
            }
            e.invulnerableTime=0;e.hurt(source==null?level().damageSources().indirectMagic(this,caster):source,damage);
            Vec3 delta=e.position().subtract(impactCentre).normalize();e.setDeltaMovement(e.getDeltaMovement().add(delta.scale(mode()==2?1.35:.3)).add(0,mode()==2?.65:.25,0));e.hurtMarked=true;
            if(mode()==1||mode()==4||mode()==6)e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,60,0));
        }
    }
    private void burst(LivingEntity caster){hit(caster);
        Vec3 centre=damageCentre==null?position():damageCentre;
        setPos(centre);
        entityData.set(DONE,true);hitAt=tickCount;entityData.set(BURST_AGE,tickCount);
        var server=(ServerLevel)level();
        int cloud=mode()==2?260:mode()==3?100:55;
        if(mode()==2) {
            server.sendParticles(ParticleTypes.EXPLOSION_EMITTER,centre.x,centre.y+1,centre.z,1,0,0,0,0);
            server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,centre.x,centre.y+1,centre.z,54,radius*.35,2,radius*.35,.14);
        }
        server.sendParticles(EarthParticles.DUST.get(),centre.x,centre.y+.35,centre.z,cloud,radius*.5,Math.min(3,radius*.3),radius*.5,.34);
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.COARSE_DIRT.defaultBlockState()),centre.x,centre.y+.25,centre.z,
                mode()==2?88:22,radius*.35,1.2,radius*.35,mode()==2?.65:.26);
        int rays=mode()==2?48:24;
        for(int i=0;i<rays;i++) {
            double a=i*Math.PI*2/rays,s=(mode()==2?.85:.35)+(i%5)*.08;
            server.sendParticles(EarthParticles.DUST.get(),centre.x,centre.y+.45,centre.z,0,Math.cos(a)*s,.10+(i%3)*.08,Math.sin(a)*s,1);
        }
        server.playSound(null,centre.x,centre.y,centre.z,SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,1.55F,.58F);
        server.playSound(null,centre.x,centre.y,centre.z,SoundEvents.STONE_BREAK,SoundSource.PLAYERS,1.8F,.72F);
    }
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
    public boolean isPickable(){return false;}public boolean isAttackable(){return false;}
    public boolean shouldRenderAtSqrDistance(double distance){return distance<512*512;}
    public AABB getBoundingBoxForCulling(){
        if(mode()==3) {
            double lift=done()?0:dropHeight();
            double half=scale()*.5+1;
            return new AABB(getX()-half,getY()-1,getZ()-half,getX()+half,getY()+lift+height()+1,getZ()+half);
        }
        return super.getBoundingBoxForCulling().inflate(Math.max(scale(),height())*2+3);
    }
    protected void readAdditionalSaveData(CompoundTag tag){discard();}protected void addAdditionalSaveData(CompoundTag tag){}
}
