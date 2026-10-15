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
    private static final EntityDataAccessor<Float> SPELL_SCALE=SynchedEntityData.defineId(TNWaterBoltEntity.class,EntityDataSerializers.FLOAT);
    public float spellScale(){return entityData.get(SPELL_SCALE);}
    public double visualRadius(){return WaterSpellRules.boltRadius(tier())*spellScale();}
    public double collisionRadius(){return spellScale()>1 ? visualRadius() : .15D;}
    public void setSpellScale(float scale){entityData.set(SPELL_SCALE,net.minecraft.util.Mth.clamp(scale,1F,20F));}
    private float damage;
    private double remaining=48;
    public TNWaterBoltEntity(EntityType<? extends TNWaterBoltEntity> type,Level level) { super(type,level); setNoGravity(true); }
    public void configure(LivingEntity owner,int tier,Vec3 at,Vec3 velocity) {
        setOwner(owner); entityData.set(TIER,tier); setPos(at); setDeltaMovement(velocity);
        damage=WaterSpellRules.damage(tier)*WaterSpellRules.power(owner); remaining=owner instanceof com.tnc.tnc.boss.TNWaterLeviathanEntity ? 96 : WaterSpellRules.range(tier);
    }
    public int tier() { return entityData.get(TIER); }
    @Override protected void defineSynchedData() { entityData.define(TIER,1);entityData.define(SPELL_SCALE,1F); }
    @Override public void tick() {
        super.tick();
        if(level().isClientSide) {
            trail();
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
        boolean blocked=wall.getType()!=HitResult.Type.MISS;
        if(spellScale()>1) {
            double distance=from.distanceToSqr(limit);
            for(var shape:level.getBlockCollisions(this,new AABB(from,to).inflate(collisionRadius())))
                for(AABB solid:shape.toAabbs()) {
                    var contact=solid.inflate(collisionRadius()).clip(from,to);
                    if(solid.inflate(collisionRadius()).contains(from)) contact=java.util.Optional.of(from);
                    if(contact.isPresent() && from.distanceToSqr(contact.get())<=distance) {
                        limit=contact.get();distance=from.distanceToSqr(limit);blocked=true;
                    }
                }
        }
        LivingEntity hit=null; Vec3 impact=limit; double closest=from.distanceToSqr(limit)+1e-7;
        for(LivingEntity target:level.getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(Math.max(.4,collisionRadius())),t->WaterSpellRules.enemy(owner,t))) {
            var box=target.getBoundingBox().inflate(collisionRadius());
            var point=box.contains(from) ? java.util.Optional.of(from) : box.clip(from,limit);
            if(point.isPresent() && point.get().distanceToSqr(from)<closest) { closest=point.get().distanceToSqr(from);hit=target;impact=point.get(); }
        }
        if(hit!=null || blocked) {
            if(hit!=null) hit.hurt(level.damageSources().indirectMagic(this,owner),damage);
            // 水花朝被打中的面翻回来（normal = 入射方向的反向）✓
            WaterFx.impact(level,impact,motion.normalize().scale(-1),tier());
            discard(); return;
        }
        setPos(to); remaining-=motion.length();
    }
    /**
     * 弹体拖尾（2026-10-07 特效增强）：水滴环 + 气泡 + 亮星 + 白色核心 + 水雾。
     *
     * <p>全部在<b>客户端本地</b>生成 ✓（不走网络 ✗）：服务端只负责命中那一下的爆发 ✓。
     * 档位决定密度 —— 龙王吼一次连射 150 发 ✗，所以 3 档单发刻意做得更省 ✓，
     * 免得 10 秒里几千颗粒子把帧数吃掉 ✗。
     */
    private void trail() {
        Vec3 dir=getDeltaMovement();
        if(dir.lengthSqr()<.5)dir=new Vec3(0,0,1);
        dir=dir.normalize();
        Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
        double radius=visualRadius();
        boolean heavy=tier()==2;
        int drops=heavy?10:tier()==3?3:5;
        int glints=heavy?3:tier()==3?1:2;
        if(tickCount%2==0)for(int i=0;i<drops;i++) {
            double angle=tickCount*.5+i*Math.PI*2/drops;
            Vec3 p=position().add(right.scale(Math.cos(angle)*radius*1.3)).add(up.scale(Math.sin(angle)*radius*1.3));
            level().addParticle(i%2==0?ParticleTypes.SPLASH:ParticleTypes.BUBBLE_POP,p.x,p.y,p.z,-dir.x*.1,.03-dir.y*.1,-dir.z*.1);
        }
        for(int i=0;i<glints;i++) {
            // 1.20.1 的 Entity 上没有 getRandom() ✗，用 protected 的 random 字段 ✓（火系弹体也是这么写的 ✓）
            double a=random.nextDouble()*Math.PI*2,r=radius*(1.1+random.nextDouble()*.7);
            Vec3 p=position().add(right.scale(Math.cos(a)*r)).add(up.scale(Math.sin(a)*r));
            level().addParticle(ParticleTypes.ELECTRIC_SPARK,p.x,p.y,p.z,-dir.x*.06,0,-dir.z*.06);
        }
        if(tickCount%(heavy?2:3)==0)
            level().addParticle(ParticleTypes.END_ROD,getX(),getY(),getZ(),-dir.x*.06,0,-dir.z*.06);
        if(heavy) {
            double a=tickCount*.8;
            Vec3 p=position().add(right.scale(Math.cos(a)*radius*1.5)).add(up.scale(Math.sin(a)*radius*1.5));
            level().addParticle(ParticleTypes.DOLPHIN,p.x,p.y,p.z,-dir.x*.04,-.02,-dir.z*.04);
            if(tickCount%6==0)level().addParticle(ParticleTypes.NAUTILUS,getX(),getY(),getZ(),0,0,0);
        } else if(tier()!=3 && tickCount%4==0) {
            level().addParticle(ParticleTypes.BUBBLE,getX()-dir.x*.5,getY()-dir.y*.5,getZ()-dir.z*.5,-dir.x*.03,.02,-dir.z*.03);
        }
    }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); }
}
