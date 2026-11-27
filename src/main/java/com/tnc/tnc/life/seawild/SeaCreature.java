package com.tnc.tnc.life.seawild;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;

public final class SeaCreature extends WaterAnimal {
    private static final EntityDataAccessor<Boolean> ALARM=SynchedEntityData.defineId(SeaCreature.class,EntityDataSerializers.BOOLEAN);
    public final SeaKind kind;
    private Vec3 destination, reef, flee=Vec3.ZERO;
    private int retarget, searchCooldown, fearTicks;
    private float spread, previousSpread;

    public SeaCreature(EntityType<? extends SeaCreature> type, Level level, SeaKind kind) {
        super(type,level);
        this.kind=kind;
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ALARM,false);
    }

    @Override public AABB getBoundingBoxForCulling() {
        return kind!=null && kind.school() ? super.getBoundingBoxForCulling().inflate(5,2.5,5) : super.getBoundingBoxForCulling();
    }

    @Override public boolean shouldRenderAtSqrDistance(double distance) {
        if(kind==null || !kind.school()) return super.shouldRenderAtSqrDistance(distance);
        double range=Math.min(92,72*getViewScale());
        return distance<range*range;
    }

    public int schoolSize() {
        int n=Math.floorMod(getUUID().getLeastSignificantBits(),9);
        return kind==SeaKind.REEF ? 21+n : 36+n;
    }

    public float spread(float partial) { return Mth.lerp(partial,previousSpread,spread); }

    public static boolean swimmable(Level l, Vec3 point) {
        var p=BlockPos.containing(point);
        if (!l.hasChunkAt(p)) return false;
        var state=l.getBlockState(p);
        return state.is(net.minecraft.world.level.block.Blocks.WATER)
                || state.getFluidState().is(FluidTags.WATER) && state.getCollisionShape(l,p).isEmpty();
    }

    @Override public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            previousSpread=spread;
            spread=Mth.approach(spread,entityData.get(ALARM)?1:0,entityData.get(ALARM)?.095f:.022f);
        }
    }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (!isInWater()) return;
        if (kind.school() && (tickCount+getId())%12==0) {
            var visitors=level().getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,
                    getBoundingBox().inflate(4.5),p -> p.isInWater() && !p.isSpectator() && p.distanceToSqr(this)<=20.25
                    && (p.isSprinting() || p.distanceToSqr(this)<4 || p.getDeltaMovement().lengthSqr()>.037));
            if (!visitors.isEmpty()) {
                var closest=visitors.get(0);
                for(var visitor:visitors) if(visitor.distanceToSqr(this)<closest.distanceToSqr(this))closest=visitor;
                alarm(closest.position());
            }
        }
        if (fearTicks>0) fearTicks--;
        entityData.set(ALARM,fearTicks>0);
        if (searchCooldown>0) searchCooldown--;
        boolean due=--retarget<=0;
        boolean arrivedOrBlocked=destination!=null &&
                (position().distanceToSqr(destination)<.6 || horizontalCollision || verticalCollision);
        if (searchCooldown==0 && (due || arrivedOrBlocked)) {
            destination=chooseTarget();
            if (destination==null) {
                // In an enclosed pool a persistent collision cannot bypass the next retry time.
                searchCooldown=10+random.nextInt(7);
                retarget=searchCooldown;
            } else {
                searchCooldown=4;
                retarget=kind==SeaKind.SLUG ? 36 : 17+random.nextInt(14);
            }
        }
        if (destination==null) {
            setDeltaMovement(getDeltaMovement().scale(.30));
            return;
        }
        Vec3 dir=destination.subtract(position()).normalize();
        double speed=switch(kind) {
            case REEF -> fearTicks>0?.21:.068;
            case SILVER -> fearTicks>0?.24:.10;
            case JELLY -> .010+.026*Math.max(0, Math.sin((tickCount+getId())*.13));
            case SLUG -> .019;
        };
        var velocity=getDeltaMovement().lerp(dir.scale(speed),.16);
        if (!swimmable(level(),position().add(velocity.scale(3)).add(0,.08,0))) {
            velocity=velocity.scale(.15);retarget=0;
        }
        setDeltaMovement(velocity);
        if (velocity.horizontalDistanceSqr()>.000007) {
            float yaw=(float)(Math.atan2(-velocity.x,velocity.z)*Mth.RAD_TO_DEG);
            setYRot(Mth.approachDegrees(getYRot(),yaw,kind==SeaKind.SLUG?3f:8f));
            setYBodyRot(getYRot()); setYHeadRot(getYRot());
        }
    }

    private void alarm(Vec3 origin) {
        if (!kind.school()) return;
        flee=position().subtract(origin).multiply(1,.4,1).normalize();
        fearTicks=36;retarget=0;
    }

    @Override public boolean hurt(DamageSource source,float amount) {
        if (!level().isClientSide && source.getEntity()!=null) alarm(source.getEntity().position());
        return super.hurt(source,amount);
    }

    private Vec3 chooseTarget() {
        Vec3 p=position();
        if (kind==SeaKind.REEF && (reef==null || p.distanceToSqr(reef)>140 || random.nextInt(22)==0)) reef=findReef();
        for (int i=0;i<8;i++) {
            double turn=(random.nextDouble()-.5)*(i<3?1.55:5.5);
            double heading=Math.toRadians(-getYRot())+turn;
            Vec3 dir=new Vec3(Math.sin(heading),0,Math.cos(heading));
            if(fearTicks>0 && i<4) dir=flee.yRot((float)turn*.6f);
            else if(kind==SeaKind.REEF && reef!=null && i<4) {
                Vec3 radial=p.subtract(reef).multiply(1,0,1);
                if (radial.horizontalDistanceSqr()>.1) {
                    var n=radial.normalize();
                    double inward=Mth.clamp((radial.length()-3)*.48,-1,1);
                    double sign=(getUUID().getLeastSignificantBits()&1)==0?1:-1;
                    dir=new Vec3(-n.z*sign,0,n.x*sign).subtract(n.scale(inward)).normalize().yRot((float)turn*.4f);
                }
            }
            double length=kind==SeaKind.SLUG?1.6:kind==SeaKind.JELLY?2:3.4;
            Vec3 target=p.add(dir.scale(length));
            var b=BlockPos.containing(target);
            if (!level().hasChunkAt(b)) continue;
            int floor=level().getHeight(Heightmap.Types.OCEAN_FLOOR,b.getX(),b.getZ());
            double y=kind==SeaKind.SLUG ? floor+.05 : Mth.clamp(p.y+(random.nextDouble()-.5)*2.2,
                    floor+(kind==SeaKind.SILVER?1.2:.35),Math.max(floor+.35,level().getSeaLevel()-1.4));
            target=new Vec3(target.x,y,target.z);
            if (swimmable(level(),target) && swimmable(level(),p.lerp(target,.5).add(0,.16,0))) return target;
        }
        return null;
    }

    private Vec3 findReef() {
        // Four nearby bed elevations establish a headland to circle. No chunk loads.
        Vec3 center=position();int highest=Integer.MIN_VALUE;
        for (int i=0;i<4;i++) {
            double a=i*Math.PI/2+getId()*.7;
            var p=blockPosition().offset((int)Math.round(Math.sin(a)*4),0,(int)Math.round(Math.cos(a)*4));
            if (!level().hasChunkAt(p)) continue;
            int floor=level().getHeight(Heightmap.Types.OCEAN_FLOOR,p.getX(),p.getZ());
            if (floor>highest){highest=floor;center=new Vec3(p.getX()+.5,getY(),p.getZ()+.5);}
        }
        return center;
    }

    @Override public void travel(Vec3 input) {
        if (!isInWater()) { super.travel(input); return; }
        if (isControlledByLocalInstance()) {
            moveRelative(.01f,input);
            move(MoverType.SELF,getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(.96));
        }
        calculateEntityAnimation(false);
    }
}
