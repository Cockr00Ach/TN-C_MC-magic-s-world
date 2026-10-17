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
            if(tickCount%3==0)level().addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD,getX(),getY(),getZ(),0,.015,0);
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
        }
        if(victim!=null||wall.getType()!=HitResult.Type.MISS){
            ((net.minecraft.server.level.ServerLevel)level()).sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,getX(),getY(),getZ(),5,.15,.15,.15,.04);
            discard();
        }
    }
    @Override public AABB getBoundingBoxForCulling(){return super.getBoundingBoxForCulling().inflate(3);}
    @Override public boolean shouldRenderAtSqrDistance(double d){return d<128*128;}
    @Override public boolean isPickable(){return false;}
    @Override public boolean isAttackable(){return false;}
    @Override protected void readAdditionalSaveData(CompoundTag tag){discard();}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
