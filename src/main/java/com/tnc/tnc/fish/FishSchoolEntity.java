package com.tnc.tnc.fish;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;

/** One synchronized school, twelve separately blocked lanes, one hit per target per cast. */
public final class FishSchoolEntity extends Entity {
    private static final EntityDataAccessor<Integer> AGE=SynchedEntityData.defineId(FishSchoolEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MASK=SynchedEntityData.defineId(FishSchoolEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> METAL=SynchedEntityData.defineId(FishSchoolEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> YAW=SynchedEntityData.defineId(FishSchoolEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH=SynchedEntityData.defineId(FishSchoolEntity.class,EntityDataSerializers.FLOAT);
    private UUID owner;
    private final Set<UUID> hit=new HashSet<>();
    public FishSchoolEntity(EntityType<? extends FishSchoolEntity> type,Level level) { super(type,level);noPhysics=true;setNoGravity(true); }
    public void initialize(ServerPlayer player,boolean metal) {
        owner=player.getUUID();setPos(player.getX(),player.getEyeY()-.35,player.getZ());
        setYRot(player.getYRot());setXRot(player.getXRot());entityData.set(YAW,player.getYRot());entityData.set(PITCH,player.getXRot());entityData.set(METAL,metal);
    }
    @Override protected void defineSynchedData() { entityData.define(AGE,0);entityData.define(MASK,(1<<FishSchoolGeometry.COUNT)-1);entityData.define(METAL,false);entityData.define(YAW,0f);entityData.define(PITCH,0f); }
    public int age() { return entityData.get(AGE); }
    public boolean metal() { return entityData.get(METAL); }
    public boolean active(int lane) { return (entityData.get(MASK)&(1<<lane))!=0; }
    public float facingYaw() { return entityData.get(YAW); }
    public float facingPitch() { return entityData.get(PITCH); }
    public Vec3 forward() { return Vec3.directionFromRotation(facingPitch(),facingYaw()); }
    public Vec3 right() {
        double yaw=Math.toRadians(facingYaw());return new Vec3(Math.cos(yaw),0,Math.sin(yaw));
    }
    public Vec3 up() { return forward().cross(right()).normalize(); }
    public Vec3 fish(int lane,double age) { return FishSchoolGeometry.world(position(),forward(),right(),up(),lane,age,metal()); }
    public boolean visuallyClear(int lane,double age) {
        Vec3 from=age<1 ? position() : fish(lane,Math.floor(age));
        return wall(from,fish(lane,age))==null;
    }
    @Override public AABB getBoundingBoxForCulling() { return new AABB(position(),position().add(forward().scale(FishSchoolGeometry.RANGE))).inflate(2); }
    @Override public boolean isPickable() { return false; }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) { clientParticles();return; }
        ServerLevel level=(ServerLevel)level();
        ServerPlayer player=owner==null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (player==null || player.level()!=level || !player.isAlive() || age()>=FishSchoolGeometry.LIFETIME) { discard();return; }
        int oldAge=age(),next=oldAge+1,mask=entityData.get(MASK);
        if (oldAge==FishSchoolGeometry.GATHER_TICKS) level.playSound(null,getX(),getY(),getZ(),SoundEvents.PLAYER_SPLASH_HIGH_SPEED,SoundSource.PLAYERS,.85f,metal()?.8f:1.15f);
        for (int lane=0;lane<FishSchoolGeometry.COUNT;lane++) {
            if (!active(lane)) continue;
            Vec3 from=oldAge==0 ? position() : fish(lane,oldAge),to=fish(lane,next);
            Vec3 wall=wall(from,to);
            Vec3 end=wall==null ? to : wall;
            if (next>FishSchoolGeometry.GATHER_TICKS) impact(player,from,end,wall);
            if (wall!=null) {
                mask&=~(1<<lane);
                level.sendParticles(ParticleTypes.SPLASH,wall.x,wall.y,wall.z,4,.12,.12,.12,.03);
            }
        }
        entityData.set(MASK,mask);entityData.set(AGE,next);
        if (next>=FishSchoolGeometry.LIFETIME) {
            for (int lane=0;lane<FishSchoolGeometry.COUNT;lane++) if ((mask&(1<<lane))!=0) {
                Vec3 p=fish(lane,next);level.sendParticles(ParticleTypes.SPLASH,p.x,p.y,p.z,2,.08,.08,.08,.02);
            }
            discard();
        } else if (mask==0) discard();
    }
    /** Sweep the oriented fish-sized hull against actual voxel collision shapes, including thin walls. */
    private Vec3 wall(Vec3 from,Vec3 to) {
        Vec3 best=null;
        Vec3 hull=FishSchoolGeometry.halfExtents(forward(),right(),up());
        for (var shape:level().getBlockCollisions(this,new AABB(from,to).inflate(hull.x,hull.y,hull.z))) {
            for (AABB box:shape.toAabbs()) {
                var candidate=FishSchoolGeometry.segment(box.inflate(hull.x,hull.y,hull.z),from,to);
                if (candidate.isPresent() && (best==null || from.distanceToSqr(candidate.get())<from.distanceToSqr(best))) best=candidate.get();
            }
        }
        return best;
    }
    private void impact(ServerPlayer player,Vec3 from,Vec3 end,Vec3 wall) {
        Vec3 hull=FishSchoolGeometry.halfExtents(forward(),right(),up());
        for (LivingEntity target:level().getEntitiesOfClass(LivingEntity.class,new AABB(from,end).inflate(hull.x,hull.y,hull.z),t->!hit.contains(t.getUUID())&&com.tnc.tnc.warrior.WarriorBronzeBridge.validTarget(player,t))) {
            var contact=FishSchoolGeometry.segment(target.getBoundingBox().inflate(hull.x,hull.y,hull.z),from,end);
            if (contact.isEmpty() || !FishSchoolGeometry.beforeWall(from,contact.get(),wall)) continue;
            AABB box=target.getBoundingBox();Vec3 c=contact.get();
            Vec3 visible=new Vec3(Math.max(box.minX,Math.min(box.maxX,c.x)),Math.max(box.minY,Math.min(box.maxY,c.y)),Math.max(box.minZ,Math.min(box.maxZ,c.z)));
            if (level().clip(new ClipContext(from,visible,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()!=HitResult.Type.MISS) continue;
            hit.add(target.getUUID());
            if (!target.hurt(damageSources().thrown(this,player),FishSchoolGeometry.damage(metal()))) continue;
            Vec3 direction=FishSchoolGeometry.horizontalKnockback(forward(),right());
            target.knockback(FishSchoolGeometry.knockback(metal()),-direction.x,-direction.z);target.hurtMarked=true;
            var server=(ServerLevel)level();
            server.sendParticles(ParticleTypes.SPLASH,c.x,c.y,c.z,8,.2,.2,.2,.08);
            if (metal()) server.sendParticles(ParticleTypes.CRIT,c.x,c.y,c.z,4,.15,.15,.15,.03);
            server.playSound(null,target.blockPosition(),metal()?SoundEvents.TRIDENT_HIT:SoundEvents.PLAYER_SPLASH,SoundSource.PLAYERS,.7f,metal()?1.3f:1.0f);
        }
    }
    private void clientParticles() {
        int age=age();
        if (age<FishSchoolGeometry.GATHER_TICKS && tickCount%2==0) {
            for (int i=0;i<12;i++) {
                double angle=i*Math.PI/6+age*.15;
                Vec3 p=position().add(right().scale(Math.cos(angle)*.65)).add(up().scale(Math.sin(angle)*.65)).add(forward().scale(.35));
                level().addParticle(ParticleTypes.BUBBLE_POP,p.x,p.y,p.z,0,.02,0);
            }
        }
        if (tickCount%3==0) for (int lane=0;lane<FishSchoolGeometry.COUNT;lane++) if (active(lane) && visuallyClear(lane,age)) {
            Vec3 p=fish(lane,age).subtract(forward().scale(.3));
            level().addParticle(ParticleTypes.BUBBLE_POP,p.x,p.y,p.z,0,0,0);
            if (metal() && lane%3==0) level().addParticle(ParticleTypes.ELECTRIC_SPARK,p.x,p.y,p.z,0,0,0);
        }
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) { owner=null; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
