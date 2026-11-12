package com.tnc.tnc.magic.wind;

import com.tnc.tnc.magic.WindEarthVisualClock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

/** A swept, finite-lifetime projectile. Its visible point is also the hit-test point. */
public final class WindWeaponEntity extends Entity {
    private static final EntityDataAccessor<Integer> FORM=SynchedEntityData.defineId(WindWeaponEntity.class,EntityDataSerializers.INT),
            TIER=SynchedEntityData.defineId(WindWeaponEntity.class,EntityDataSerializers.INT),AGE=SynchedEntityData.defineId(WindWeaponEntity.class,EntityDataSerializers.INT);
    private final WindEarthVisualClock clock=new WindEarthVisualClock();
    private LivingEntity owner;private Vec3 origin=Vec3.ZERO;private float damage;private int age;
    private final java.util.Deque<Vec3> trail=new java.util.ArrayDeque<>();
    public Iterable<Vec3> trail(){return trail;}
    public WindWeaponEntity(EntityType<? extends WindWeaponEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    @Override protected void defineSynchedData(){entityData.define(FORM,0);entityData.define(TIER,1);entityData.define(AGE,0);}
    public int form(){return entityData.get(FORM);}public int tier(){return entityData.get(TIER);}
    public double visualAge(float partial){return clock.phase(tickCount,partial,entityData.get(AGE),1);}
    public void configure(LivingEntity owner,Vec3 at,Vec3 direction,int form,int tier,float damage){
        this.owner=owner;origin=at;this.damage=damage;setPos(at);
        entityData.set(FORM,Math.max(0,Math.min(3,form)));entityData.set(TIER,Math.max(1,Math.min(5,tier)));
        var unit=direction.normalize();if(unit.lengthSqr()<.01)unit=new Vec3(0,0,1);
        setDeltaMovement(unit.scale(1.15+tier*.1));
        setYRot((float)Math.toDegrees(Math.atan2(unit.x,unit.z)));setXRot((float)-Math.toDegrees(Math.asin(unit.y)));
    }
    @Override public void tick(){
        if(isRemoved())return;
        super.tick();
        if(level().isClientSide){
            trail.addFirst(position());while(trail.size()>26)trail.removeLast();
            return;
        }
        if(owner==null||!owner.isAlive()||owner.level()!=level()||++age>WindArsenalRules.LIFE){discard();return;}
        if(age%4==0)entityData.set(AGE,age);
        Vec3 from=position(),to=from.add(getDeltaMovement());
        if(!level().hasChunkAt(net.minecraft.core.BlockPos.containing(to))){discard();return;}
        var wall=level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        if(wall.getType()!=HitResult.Type.MISS)to=wall.getLocation();
        LivingEntity victim=null;double nearest=Double.MAX_VALUE;
        for(var enemy:level().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(.6),
                e->e!=owner&&!(e instanceof Player)&&e.isAlive()&&!e.isSpectator()&&!e.isAlliedTo(owner))){
            var hit=enemy.getBoundingBox().inflate(.25).clip(from,to);
            if(enemy.getBoundingBox().inflate(.25).contains(from))hit=java.util.Optional.of(from);
            if(hit.isPresent()&&from.distanceToSqr(hit.get())<nearest){victim=enemy;nearest=from.distanceToSqr(hit.get());to=hit.get();}
        }
        setPos(to);
        if(victim!=null){
            victim.hurt(level().damageSources().indirectMagic(this,owner),damage);
            var pull=origin.subtract(victim.position()).normalize().scale(.08);victim.push(pull.x,.025,pull.z);victim.hurtMarked=true;
            burst(victim);
        }
        if(victim==null&&wall.getType()!=HitResult.Type.MISS&&owner instanceof net.minecraft.server.level.ServerPlayer p)com.tnc.tnc.dungeon.leyline.LeylineEvents.spell(p,wall.getBlockPos(),"wind");
        if(victim!=null||wall.getType()!=HitResult.Type.MISS){
            ((net.minecraft.server.level.ServerLevel)level()).sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,getX(),getY(),getZ(),5,.15,.15,.15,.04);
            discard();
        }
    }
    /**
     * ★ <b>命中炸开</b>（2026-11-11 作者「风灵再改改」）✓
     *
     * <p>以前命中只是"那只怪掉一格血 + 一点云"✗ —— 风灵每 10 tick 打一发，
     * 打起来像在"戳"。现在命中点炸开一小圈风：范围内的敌人吃
     * {@link WindArsenalRules#burstDamageShare()} 的伤害并被轻轻推开，
     * 再补一圈 {@code SWEEP_ATTACK} ⇒ 每一发都看得出"斩到了" ✓。
     */
    private void burst(LivingEntity hit){
        if(!(level() instanceof net.minecraft.server.level.ServerLevel server))return;
        double radius=WindArsenalRules.burstRadius(tier());
        float share=damage*WindArsenalRules.burstDamageShare();
        for(var other:level().getEntitiesOfClass(LivingEntity.class,
                new AABB(hit.position(),hit.position()).inflate(radius),
                e->e!=hit&&e!=owner&&!(e instanceof Player)&&e.isAlive()&&!e.isSpectator()&&!e.isAlliedTo(owner))){
            if(other.distanceTo(hit)>radius)continue;
            other.hurt(level().damageSources().indirectMagic(this,owner),share);
            var out=other.position().subtract(hit.position());
            if(out.lengthSqr()>1.0E-4D){out=out.normalize().scale(.18D);other.push(out.x,.06D,out.z);other.hurtMarked=true;}
        }
        double y=hit.getY()+hit.getBbHeight()*0.5D;
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,hit.getX(),y,hit.getZ(),
                3,.2D,.2D,.2D,0.0D);
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,hit.getX(),y,hit.getZ(),
                8,.25D,.2D,.25D,0.03D);
    }

    @Override public AABB getBoundingBoxForCulling(){return super.getBoundingBoxForCulling().inflate(44);}
    @Override public boolean shouldRenderAtSqrDistance(double d){return d<128*128;}
    @Override public boolean isPickable(){return false;}
    @Override public boolean isAttackable(){return false;}
    @Override protected void readAdditionalSaveData(CompoundTag tag){discard();}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
