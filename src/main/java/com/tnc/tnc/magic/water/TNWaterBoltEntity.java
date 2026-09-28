package com.tnc.tnc.magic.water;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Swept single-hit water projectile: no area damage or chain reaction. */
public final class TNWaterBoltEntity extends Projectile {
    private static final EntityDataAccessor<Integer> TIER=SynchedEntityData.defineId(TNWaterBoltEntity.class,EntityDataSerializers.INT);
    private float damage;
    private double remaining=48;
    public TNWaterBoltEntity(EntityType<? extends TNWaterBoltEntity> type,Level level) { super(type,level); setNoGravity(true); }
    public void configure(LivingEntity owner,int tier,Vec3 at,Vec3 velocity) {
        setOwner(owner); entityData.set(TIER,tier); setPos(at); setDeltaMovement(velocity);
        damage=WaterSpellRules.damage(tier)*WaterSpellRules.power(owner); remaining=WaterSpellRules.range(tier);
    }
    public int tier() { return entityData.get(TIER); }
    @Override protected void defineSynchedData() { entityData.define(TIER,1); }
    @Override public void tick() {
        super.tick();
        if(level().isClientSide) {
            if(tickCount%2==0)for(int i=0;i<(tier()==2?6:3);i++) {
                double angle=tickCount*.5+i*Math.PI*2/3;
                Vec3 dir=getDeltaMovement().normalize(),right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
                double r=WaterSpellRules.boltRadius(tier())*1.2;
                Vec3 p=position().add(right.scale(Math.cos(angle)*r)).add(up.scale(Math.sin(angle)*r));
                level().addParticle(i%2==0?ParticleTypes.SPLASH:ParticleTypes.BUBBLE_POP,p.x,p.y,p.z,-dir.x*.08,.03-dir.y*.08,-dir.z*.08);
            }
            setPos(position().add(getDeltaMovement())); return;
        }
        if(!(getOwner() instanceof LivingEntity owner) || !owner.isAlive() || tickCount>60 || remaining<=0
                || owner instanceof net.minecraft.world.entity.player.Player p&&com.tnc.tnc.combat.DownedCombat.isDowned(p)
                || owner.level()!=level()) { discard(); return; }
        ServerLevel level=(ServerLevel)level();
        Vec3 from=position(), motion=getDeltaMovement(), to=from.add(motion);
        if(!level.hasChunkAt(net.minecraft.core.BlockPos.containing(to))) { discard(); return; }
        BlockHitResult wall=level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        Vec3 limit=wall.getType()==HitResult.Type.MISS ? to : wall.getLocation();
        LivingEntity hit=null; Vec3 impact=limit; double closest=from.distanceToSqr(limit)+1e-7;
        for(LivingEntity target:level.getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(.4),t->WaterSpellRules.enemy(owner,t))) {
            var box=target.getBoundingBox().inflate(.15);
            var point=box.contains(from) ? java.util.Optional.of(from) : box.clip(from,limit);
            if(point.isPresent() && point.get().distanceToSqr(from)<closest) { closest=point.get().distanceToSqr(from);hit=target;impact=point.get(); }
        }
        if(hit!=null || wall.getType()!=HitResult.Type.MISS) {
            if(hit!=null) hit.hurt(level.damageSources().indirectMagic(this,owner),damage);
            level.sendParticles(ParticleTypes.SPLASH,impact.x,impact.y,impact.z,12,.25,.25,.25,.12);
            discard(); return;
        }
        setPos(to); remaining-=motion.length();
    }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); }
}
