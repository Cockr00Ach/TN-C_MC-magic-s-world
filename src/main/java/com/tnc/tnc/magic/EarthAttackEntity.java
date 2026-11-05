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
    private static final EntityDataAccessor<Boolean> LAUNCH=SynchedEntityData.defineId(EarthAttackEntity.class,EntityDataSerializers.BOOLEAN);
    public static final double LAUNCH_RISE=EarthLaunchLayout.RISE;
    private Vec3 start=Vec3.ZERO,end=Vec3.ZERO,velocity=Vec3.ZERO;
    private int owner=-1,ttl=100,hitAt=-1;private float damage;private double radius;
    private DamageSource source;private final WindEarthVisualClock clock=new WindEarthVisualClock();
    Vec3 damageCentre;
    public EarthAttackEntity(EntityType<? extends EarthAttackEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    protected void defineSynchedData(){entityData.define(MODE,0);entityData.define(WAIT,0);entityData.define(AGE,0);entityData.define(BURST_AGE,-100);entityData.define(MODEL,"earth_clod");entityData.define(SCALE,1F);entityData.define(HEIGHT,1F);entityData.define(DROP_HEIGHT,0F);entityData.define(DONE,false);entityData.define(LAUNCH,false);}
    public int mode(){return entityData.get(MODE);}public int waitTicks(){return entityData.get(WAIT);}
    public String model(){return entityData.get(MODEL);}public float scale(){return entityData.get(SCALE);}public float height(){return entityData.get(HEIGHT);}
    public boolean gravityCore(){return model().equals("stone_lancer_gravity_core");}
    public float dropHeight(){return entityData.get(DROP_HEIGHT);}
    public int burstAge(){return entityData.get(BURST_AGE);}
    public int burstLife(){return mode()==2?24:14;}
    public boolean done(){return entityData.get(DONE);}public double age(float partial){return clock.phase(tickCount,partial,entityData.get(AGE),1);}
    public boolean launch(){return entityData.get(LAUNCH);}public void setLaunch(boolean value){entityData.set(LAUNCH,value);}
    public void configure(LivingEntity caster,int mode,String model,Vec3 start,Vec3 end,float scale,float height,int wait,int ttl,float damage,double radius,DamageSource source) {
        owner=caster.getId();this.start=start;this.end=end;this.ttl=ttl;this.damage=damage;this.radius=radius;this.source=source;
        entityData.set(MODE,mode);entityData.set(MODEL,model);entityData.set(SCALE,scale);entityData.set(HEIGHT,height);entityData.set(WAIT,wait);
        entityData.set(DROP_HEIGHT,mode==3?(float)Math.max(0,start.y-end.y):0F);
        velocity=end.subtract(start).normalize().scale(mode==3?start.distanceTo(end)+2:model.startsWith("stone_lancer_")?9:1.8);
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
            double hitWidth=model().startsWith("stone_lancer_")?4:.8;
            var pathBounds=mode()==3?new AABB(origin,next).inflate(hitWidth):getBoundingBox().expandTowards(velocity).inflate(hitWidth);
            for(var e:level().getEntitiesOfClass(LivingEntity.class,pathBounds,
                    e->e.isAlive()&&e!=living&&!e.isAlliedTo(living)&&!e.isSpectator())) {
                var hit=e.getBoundingBox().inflate(hitWidth*.5).clip(origin,next);
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
        } else if(mode()==8) {
            launch(living,age);
        } else if(mode()==2) {
            double t=Math.max(0,Math.min(1,age/60.0));t=t*t*(3-2*t);setPos(start.lerp(end,t));
            if(age<80&&tickCount%2==0)attract(living,age);
            if(age>=80)burst(living);
        }
    }
    /** Height of the rising pillar top at a given age (ease-out). */
    private double launchTop(int age) {
        return EarthLaunchLayout.top(age,height());
    }
    /** Giant pillar that erupts under the hit target and carries it skyward. */
    private void launch(LivingEntity caster,int age) {
        double top=launchTop(age);
        if(age==1) {
            var server=(ServerLevel)level();
            server.playSound(null,getX(),getY(),getZ(),SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,1.4F,.5F);
            server.playSound(null,getX(),getY(),getZ(),SoundEvents.STONE_BREAK,SoundSource.PLAYERS,2.0F,.55F);
        }
        if(age<=LAUNCH_RISE) {
            double r=Math.max(2.2,scale()+.6);
            double lift=EarthLaunchLayout.lift(age,height());
            var area=new AABB(getX()-r,getY()-.5,getZ()-r,getX()+r,getY()+top+2.6,getZ()+r);
            for(var e:level().getEntitiesOfClass(LivingEntity.class,area,
                    e->e.isAlive()&&e!=caster&&!e.isAlliedTo(caster)&&!e.isSpectator())) {
                double dx=getX()-e.getX(),dz=getZ()-e.getZ();
                if(dx*dx+dz*dz>r*r)continue;
                // Ride the moving top instead of a fixed speed, so nobody slides off.
                e.setDeltaMovement(dx*.22,lift,dz*.22);
                e.fallDistance=0;e.hurtMarked=true;
            }
        }
        if(age==(int)LAUNCH_RISE) {
            var server=(ServerLevel)level();
            for(int i=0;i<24;i++) {
                double a=i*Math.PI*2/24,rr=Math.max(1.5,scale()*.7);
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.STONE.defaultBlockState()),
                        getX()+Math.cos(a)*rr,getY()+top,getZ()+Math.sin(a)*rr,2,.2,.2,.2,.2);
            }
        }
        if(age>LAUNCH_RISE+40) {
            // Collapse: the whole column lets go at once instead of blinking out.
            var server=(ServerLevel)level();
            for(int i=0;i<20;i++) {
                double y=random.nextDouble()*top;
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.STONE.defaultBlockState()),
                        getX()+(random.nextDouble()-.5)*2,y+getY(),getZ()+(random.nextDouble()-.5)*2,2,.25,.1,.25,.35);
            }
            discard();
        }
    }
    private void attract(LivingEntity caster,int age) {        Vec3 centre=position();
        Vec3 ground=damageCentre==null?end:damageCentre;
        double reach=gravityCore()?420:Math.max(radius*1.7,scale()*.75);
        // Keep catching ground targets as the sphere rises, and retain lifted targets near it.
        var area=AABB.ofSize(centre,reach*2,reach*2,reach*2).minmax(AABB.ofSize(ground,reach*2,reach*2,reach*2));
        var enemies=level().getEntitiesOfClass(LivingEntity.class,area,
                e->e.isAlive()&&e!=caster&&!e.isAlliedTo(caster)&&!e.isSpectator());
        if(gravityCore())enemies.sort(java.util.Comparator.<LivingEntity>comparingInt(e->e instanceof net.minecraft.world.entity.player.Player?0:1).thenComparingDouble(e->e.distanceToSqr(centre)));
        int pulled=0;
        for(var enemy:enemies) {
            Vec3 body=enemy.getBoundingBox().getCenter();
            Vec3 delta=centre.subtract(body);double distance=delta.length();
            if(Math.min(distance,ground.distanceTo(body))>reach||!gravityCore()&&!caster.hasLineOfSight(enemy))continue;
            if(gravityCore()&&pulled++>=64)break;
            if(gravityCore()){
                enemy.setDeltaMovement(com.tnc.tnc.boss.earth.StoneGravityPull.motion(enemy.getDeltaMovement(),delta,enemy.isNoGravity(),age));
                enemy.hurtMarked=true;continue;
            }
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
        if(mode()==8){launchFx(age);return;}
        int count=mode()==2?4:(mode()==0||mode()==3||mode()==5?3:2);
        for(int i=0;i<count;i++) {
            double a=(tickCount*.71+i*Math.PI*2/count),r=mode()==2?Math.max(1,scale()*.22):Math.max(.25,scale()*.12);
            level().addParticle(EarthParticles.DUST.get(),getX()+Math.cos(a)*r,getY()+.25+Math.sin(a*1.7)*r*.3,getZ()+Math.sin(a)*r,
                    Math.cos(a)*.025,.035,Math.sin(a)*.025);
        }
    }
    /** Dust streamers up the whole column, so the launch is readable from far away. */
    private void launchFx(int age) {
        double top=launchTop(age);
        for(int i=0;i<3;i++) {
            double a=tickCount*.9+i*Math.PI*2/3,r=Math.max(1.2,scale()*.85);
            level().addParticle(EarthParticles.DUST.get(),getX()+Math.cos(a)*r,getY()+.2,getZ()+Math.sin(a)*r,
                    Math.cos(a)*.13,.06,Math.sin(a)*.13);
        }
        if(top<.5)return;
        for(int i=0;i<4;i++) {
            double a=tickCount*.6+i*Math.PI/2,r=Math.max(.8,scale()*.55);
            level().addParticle(EarthParticles.DUST.get(),getX()+Math.cos(a)*r,getY()+top-.2+Math.sin(a*1.3)*.4,getZ()+Math.sin(a)*r,
                    Math.cos(a)*.06,.03,Math.sin(a)*.06);
        }
        if(age%3==0)level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.STONE.defaultBlockState()),
                getX()+(Math.random()-.5)*1.4,getY()+top,getZ()+(Math.random()-.5)*1.4,0,.02,0);
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
        // Ground ring: chips are kicked outward from several points so the blast
        // reads as the floor breaking apart, not just a puff at the centre.
        int ring=mode()==2?14:9;
        var ringChip=new BlockParticleOption(ParticleTypes.BLOCK,
                (mode()==2?Blocks.STONE:Blocks.COARSE_DIRT).defaultBlockState());
        for(int i=0;i<ring;i++) {
            double a=i*Math.PI*2/ring,d=Math.max(1,radius*(.35+.45*(i%3)/2));
            double x=centre.x+Math.cos(a)*d,z=centre.z+Math.sin(a)*d;
            server.sendParticles(ringChip,x,centre.y+.12,z,5,.18,.16,.18,.2);
            server.sendParticles(EarthParticles.DUST.get(),x,centre.y+.1,z,2,.18,.05,.18,.04);
            server.sendParticles(ringChip,x,centre.y+.12,z,0,Math.cos(a)*.24,.3,Math.sin(a)*.24,.6);
            if(mode()==2&&i%5==0)server.sendParticles(ParticleTypes.EXPLOSION,x,centre.y+.25,z,1,0,0,0,0);
        }
        int rays=mode()==2?48:24;
        for(int i=0;i<rays;i++) {
            double a=i*Math.PI*2/rays,s=(mode()==2?.85:.35)+(i%5)*.08;
            server.sendParticles(EarthParticles.DUST.get(),centre.x,centre.y+.45,centre.z,0,Math.cos(a)*s,.10+(i%3)*.08,Math.sin(a)*s,1);
        }
        // Two low rings plus a short vertical jet: even a single-target blast
        // leaves the floor with a visible shockwave instead of only a puff.
        for(int i=0;i<20;i++) {
            double a=i*Math.PI*2/20,r=radius*.95;
            server.sendParticles(EarthParticles.DUST.get(),centre.x+Math.cos(a)*r,centre.y+.08,centre.z+Math.sin(a)*r,
                    1,Math.cos(a)*.42,.02,Math.sin(a)*.42,.55);
        }
        for(int i=0;i<12;i++) {
            double a=i*Math.PI*2/12+.13,r=radius*.35;
            server.sendParticles(EarthParticles.DUST.get(),centre.x+Math.cos(a)*r,centre.y+.08,centre.z+Math.sin(a)*r,
                    1,Math.cos(a)*.55,.03,Math.sin(a)*.55,.42);
        }
        for(int i=0;i<6;i++) {
            double a=i*Math.PI*2/6;
            server.sendParticles(EarthParticles.DUST.get(),centre.x+Math.cos(a)*.3,centre.y+.15,centre.z+Math.sin(a)*.3,
                    0,Math.cos(a)*.05,.45+(i%3)*.07,Math.sin(a)*.05,.9);
        }
        // Dust wall: a ring of slow-rising dust gives the blast a visible boundary,
        // and rocks knocked loose above keep raining after the flash.
        int wall=mode()==2?18:14;
        for(int i=0;i<wall;i++) {
            double a=i*Math.PI*2/wall,rr=radius*1.05;
            server.sendParticles(EarthParticles.DUST.get(),centre.x+Math.cos(a)*rr,centre.y+.2,centre.z+Math.sin(a)*rr,2,.15,.25,.15,.08);
            server.sendParticles(EarthParticles.DUST.get(),centre.x+Math.cos(a)*rr,centre.y+.9,centre.z+Math.sin(a)*rr,0,Math.cos(a)*.06,.28+(i%3)*.06,Math.sin(a)*.06,.55);
        }
        for(int i=0;i<10;i++) {
            double a=random.nextDouble()*Math.PI*2,rr=radius*(.3+.7*random.nextDouble());
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.STONE.defaultBlockState()),
                    centre.x+Math.cos(a)*rr,centre.y+5+random.nextDouble()*5,centre.z+Math.sin(a)*rr,1,.15,1.2,.15,.28);
        }
        server.playSound(null,centre.x,centre.y,centre.z,SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,1.55F,.58F);
        server.playSound(null,centre.x,centre.y,centre.z,SoundEvents.STONE_BREAK,SoundSource.PLAYERS,1.8F,.72F);
        if(launch()) {
            // The thin pillar only marks the spot: the real hit is the giant column
            // erupting under the target and carrying it into the sky.
            var ground=groundBelow(centre);
            var casterEntity=level().getEntity(owner);
            var pillar=WindEarthVisualMechanics.ATTACK.get().create(level());
            if(pillar!=null&&casterEntity instanceof LivingEntity living) {
                pillar.configure(living,8,"earth_pillar",ground,ground.add(0,1,0),2.6F,96F,0,170,0,3.0,source);
                level().addFreshEntity(pillar);
            }
        }
    }
    private Vec3 groundBelow(Vec3 point) {
        var hit=level().clip(new ClipContext(point.add(0,2,0),point.add(0,-40,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        return hit.getType()==HitResult.Type.MISS?point:hit.getLocation();
    }
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
    public boolean isPickable(){return false;}public boolean isAttackable(){return false;}
    public boolean shouldRenderAtSqrDistance(double distance){return distance<512*512;}
    public AABB getBoundingBoxForCulling(){
        if(mode()==8)return new AABB(getX()-6,getY()-1,getZ()-6,getX()+6,getY()+height()+3,getZ()+6);
        if(mode()==3) {
            double lift=done()?0:dropHeight();
            double half=scale()*.5+1;
            return new AABB(getX()-half,getY()-1,getZ()-half,getX()+half,getY()+lift+height()+1,getZ()+half);
        }
        return super.getBoundingBoxForCulling().inflate(Math.max(scale(),height())*2+3);
    }
    protected void readAdditionalSaveData(CompoundTag tag){discard();}protected void addAdditionalSaveData(CompoundTag tag){}
}
