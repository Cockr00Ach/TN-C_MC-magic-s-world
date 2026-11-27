package com.tnc.tnc.dungeon.mirror;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Entity origin is on the exposed mirror's lower edge; the outer shell is entirely visual. */
public final class EchoCore extends Mob {
    private static final EntityDataAccessor<Long> OPENED = SynchedEntityData.defineId(EchoCore.class, EntityDataSerializers.LONG);
    private UUID run;

    public EchoCore(EntityType<? extends Mob> type, Level level) {
        super(type,level);
        setNoGravity(true);
        setNoAi(true);
        setPersistenceRequired();
        noPhysics=true;
    }
    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OPENED,-1L);
    }
    public void bind(UUID id,BlockPos floor) {
        run=id;
        setPos(floor.getX()+.5,floor.getY()+1.78,floor.getZ()+.5);
        setHealth(1);
    }
    public UUID runId() { return run; }
    public void expose() {
        if (entityData.get(OPENED) < 0) entityData.set(OPENED,level().getGameTime());
    }
    public int openingAge() {
        long start=entityData.get(OPENED);
        return start<0 ? -1 : (int)Math.min(100000,Math.max(0,level().getGameTime()-start));
    }
    public boolean exposed() { return openingAge()>=36; }
    @Override public void tick() {
        super.tick();
        if(exposed()) {
            Vec3 outward=Vec3.directionFromRotation(0,getYRot()).scale(.89);
            setBoundingBox(getDimensions(getPose()).makeBoundingBox(getX()+outward.x,getY(),getZ()+outward.z));
        }
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        if (level().isClientSide || !isAlive() || run==null || amount<=0 || !exposed()) return false;
        if (!MirrorFight.strike(this,source)) return false;
        discard();
        return true;
    }
    @Override public boolean isPickable() { return exposed() && isAlive(); }
    @Override public boolean isPushable() { return false; }
    @Override public void push(Entity entity) {}
    @Override public void push(double x,double y,double z) {}
    @Override public void travel(Vec3 motion) { setDeltaMovement(Vec3.ZERO); }
    @Override public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(4,5,4); }
    @Override public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        if (run!=null) t.putUUID("MirrorRun",run);
        t.putLong("Opened",entityData.get(OPENED));
    }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        if(t.hasUUID("MirrorRun")) run=t.getUUID("MirrorRun");
        entityData.set(OPENED,t.contains("Opened") ? t.getLong("Opened") : -1L);
    }
}
