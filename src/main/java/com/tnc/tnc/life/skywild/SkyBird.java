package com.tnc.tnc.life.skywild;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative flight. Followers keep lateral slots as their leader banks. */
public final class SkyBird extends FlyingMob {
    public final SkySpecies species;
    private BlockPos origin;
    private long flock;
    private int slot;
    private double heading, routePhase;
    private Vec3 waypoint, desired=Vec3.ZERO;
    private SkyBird leader;
    private List<SkyBird> neighbors=List.of();

    public SkyBird(EntityType<? extends SkyBird> type, Level level, SkySpecies species) {
        super(type,level);
        this.species=species;setNoGravity(true);xpReward=0;
    }
    public static AttributeSupplier.Builder attributes(SkySpecies kind) {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,kind==SkySpecies.EAGLE?18:kind==SkySpecies.CRANE?12:6)
                .add(Attributes.MOVEMENT_SPEED,kind.speed).add(Attributes.FOLLOW_RANGE,32);
    }
    public void joinFlock(long key,int rank,BlockPos anchor,double bearing) {
        flock=key;slot=rank;origin=anchor.immutable();heading=bearing;
        routePhase=bearing;leader=null;waypoint=null;desired=Vec3.ZERO;
    }
    public long flockKey() {return flock;}
    public int flockSlot() {return slot;}
    @Override public void addAdditionalSaveData(CompoundTag data) {
        super.addAdditionalSaveData(data);
        data.putLong("FlightFlock",flock);data.putInt("FlightSlot",slot);
        data.putDouble("FlightHeading",heading);data.putDouble("FlightPhase",routePhase);
        if (origin!=null) data.putLong("FlightAnchor",origin.asLong());
    }
    @Override public void readAdditionalSaveData(CompoundTag data) {
        super.readAdditionalSaveData(data);setNoGravity(true);
        flock=data.getLong("FlightFlock");slot=Mth.clamp(data.getInt("FlightSlot"),0,8);
        heading=data.getDouble("FlightHeading");routePhase=data.getDouble("FlightPhase");
        if (data.contains("FlightAnchor")) origin=BlockPos.of(data.getLong("FlightAnchor"));
    }
    @Override public boolean causeFallDamage(float distance,float multiplier,net.minecraft.world.damagesource.DamageSource source){return false;}
    @Override public boolean removeWhenFarAway(double distance){return !hasCustomName();}
    @Override public boolean shouldRenderAtSqrDistance(double distance){return distance<128*128;}
    @Override public boolean isPushable(){return false;}

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level)) return;
        if (origin==null) {
            int floor=SkyHabitats.roof(level,getBlockX(),getBlockZ());
            joinFlock(getUUID().getLeastSignificantBits()|1,0,blockPosition().atY(floor),getRandom().nextDouble()*Math.PI*2);
        }
        if (tickCount%24==0 || tickCount==1) refreshFlock(level);
        if (tickCount%17==0 || waypoint==null || distanceToSqr(waypoint)<12 || horizontalCollision || verticalCollision) {
            waypoint=route(level);
        }
        if (tickCount%4==0 || desired==Vec3.ZERO || horizontalCollision || verticalCollision) desired=steer(level);
        var current=getDeltaMovement();
        Vec3 next=current.lerp(desired,species==SkySpecies.BAT?.28:.17);
        setDeltaMovement(next);
        if (next.horizontalDistanceSqr()>.0004) {
            float yaw=(float)(Math.atan2(-next.x,next.z)*180/Math.PI);
            setYRot(Mth.approachDegrees(getYRot(),yaw,species==SkySpecies.BAT?9:5));
            yBodyRot=getYRot();yHeadRot=yBodyRot;
            float pitch=(float)-Math.toDegrees(Math.atan2(next.y,next.horizontalDistance()));
            setXRot(Mth.approach(getXRot(),Mth.clamp(pitch,-26,24),2));
        }
        if (tickCount%120==0 && !species.active(level.getDayTime()) && !hasCustomName() && !isPersistenceRequired()
                && level.getNearestPlayer(this,58)==null) discard();
    }
    private void refreshFlock(ServerLevel level) {
        if (species.maxGroup==1) {leader=null;neighbors=List.of();return;}
        neighbors=level.getEntitiesOfClass(SkyBird.class,getBoundingBox().inflate(32,18,32),
                bird->bird!=this && bird.isAlive() && bird.species==species && bird.flock==flock);
        leader=null;
        for (var other : neighbors)
            if (other.slot<slot && (leader==null || other.slot<leader.slot)) leader=other;
    }
    private Vec3 route(ServerLevel level) {
        if (leader!=null && leader.isAlive() && distanceToSqr(leader)<45*45) {
            var dir=leader.getDeltaMovement().multiply(1,0,1);
            if (dir.lengthSqr()<.005) dir=Vec3.directionFromRotation(0,leader.getYRot());
            dir=dir.normalize();var flank=new Vec3(dir.z,0,-dir.x);
            int rank=(slot+1)/2;
            double spread=species==SkySpecies.CRANE?5.8:2.5;
            var target=leader.position().add(dir.scale(3-rank*2.5)).add(flank.scale((slot%2==1?1:-1)*rank*spread))
                    .add(0,Math.sin(tickCount*.038+slot)*.8,0);
            return followGround(level,target,true);
        }
        routePhase+=switch(species){case EAGLE -> .26;case CRANE -> .25;case BAT -> .64;case SWALLOW -> .48;case STARLING -> .34;};
        double a=heading+routePhase;
        double x,z;
        if(species==SkySpecies.EAGLE) {
            x=origin.getX()+Math.cos(a)*species.radius;z=origin.getZ()+Math.sin(a)*species.radius;
        } else {
            double along=species.radius*Math.sin(routePhase);
            double across=species.radius*(species==SkySpecies.CRANE?.23:species==SkySpecies.BAT?.56:.41)*Math.sin(2*routePhase+.42);
            x=origin.getX()+along*Math.cos(heading)-across*Math.sin(heading);
            z=origin.getZ()+along*Math.sin(heading)+across*Math.cos(heading);
        }
        return followGround(level,new Vec3(x,getY(),z),false);
    }
    private Vec3 followGround(ServerLevel level,Vec3 target,boolean follow) {
        var column=BlockPos.containing(target);
        if (!level.hasChunkAt(column) || !level.getWorldBorder().isWithinBounds(column)) return safeAnchor(level);
        int floor=SkyHabitats.roof(level,column.getX(),column.getZ());
        if (floor<=level.getMinBuildHeight()+1) return safeAnchor(level);
        int Y=species.altitude(floor,.48+.34*Math.sin(routePhase*(species==SkySpecies.EAGLE?.52:1.17)),level.getMaxBuildHeight());
        if(Y==Integer.MIN_VALUE) return position().add(0,-1,0);
        double loft=follow?Math.max(Y-3,Math.min(Y+3,target.y)):Y;
        return new Vec3(target.x,loft,target.z);
    }
    private Vec3 safeAnchor(ServerLevel level) {
        // Commands, unloaded islands and old save data may leave the remembered shore behind a border.
        if (level.hasChunkAt(origin) && level.getWorldBorder().isWithinBounds(origin)) {
            int floor=SkyHabitats.roof(level,origin.getX(),origin.getZ());
            int y=species.altitude(floor,.4,level.getMaxBuildHeight());
            if (floor>level.getMinBuildHeight()+1 && y!=Integer.MIN_VALUE) {
                return new Vec3(origin.getX()+.5,y,origin.getZ()+.5);
            }
        }
        return position().add(Vec3.directionFromRotation(0,getYRot()+155).scale(3));
    }
    private Vec3 steer(ServerLevel level) {
        Vec3 aim=waypoint.subtract(position()).normalize();
        if (aim.lengthSqr()<.1) aim=Vec3.directionFromRotation(0,getYRot());
        for (var other : neighbors) {
            if (!other.isAlive()) continue;
            Vec3 away=position().subtract(other.position());double sq=away.lengthSqr();
            if (sq>.015 && sq<9) aim=aim.add(away.scale(.65/(sq+.2)));
        }
        double length=aim.horizontalDistance();
        if (length>.01) aim=new Vec3(aim.x/length,Mth.clamp(aim.y/length,-.66,.78),aim.z/length);
        if (horizontalCollision || verticalCollision) aim=aim.add(0,1.1,0);
        if (clear(level,aim)) return aim.normalize().scale(species.speed);
        double side=slot%2==0?1:-1;
        for (double turn : new double[]{side*.72,-side*.72,side*1.5,-side*1.5,Math.PI}) {
            Vec3 test=aim.yRot((float)turn).add(0,.56,0);
            if (clear(level,test)) return test.normalize().scale(species.speed*.73);
        }
        return clear(level,new Vec3(0,1,0))?new Vec3(0,species.speed*.7,0):Vec3.ZERO;
    }
    private boolean clear(ServerLevel level,Vec3 dir) {
        var check=dir.normalize().scale(3.2);
        var end=BlockPos.containing(position().add(check));
        var swept=getBoundingBox().expandTowards(check);
        return end.getY()>=level.getMinBuildHeight()+1 && end.getY()<level.getMaxBuildHeight()-2
                && level.getWorldBorder().isWithinBounds(end)
                && level.hasChunksAt(BlockPos.containing(swept.minX,swept.minY,swept.minZ),BlockPos.containing(swept.maxX,swept.maxY,swept.maxZ))
                && level.getFluidState(end).isEmpty() && level.noCollision(this,swept);
    }
}

