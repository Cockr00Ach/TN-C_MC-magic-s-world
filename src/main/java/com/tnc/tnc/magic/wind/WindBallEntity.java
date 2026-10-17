package com.tnc.tnc.magic.wind;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraft.core.particles.ParticleTypes;

/** Flight -> anchored suction/compression -> one air blast. The rising centre also drives Y pull. */
public final class WindBallEntity extends Entity {
    private static final EntityDataAccessor<Integer> TIER=SynchedEntityData.defineId(WindBallEntity.class,EntityDataSerializers.INT),
            PHASE=SynchedEntityData.defineId(WindBallEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(WindBallEntity.class,EntityDataSerializers.LONG);
    private LivingEntity owner;private int age,phaseAge;
    public WindBallEntity(EntityType<? extends WindBallEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    @Override protected void defineSynchedData(){entityData.define(TIER,1);entityData.define(PHASE,0);entityData.define(START,0L);}
    public int tier(){return entityData.get(TIER);}public int phase(){return entityData.get(PHASE);}
    public double phaseAge(float partial){return Math.max(0,level().getGameTime()+partial-entityData.get(START));}
    public double radius(float partial){return phase()==0?WindChainRules.ballRadius(tier()):phase()==2?WindChainRules.ballRadius(tier())*(1+phaseAge(partial)*.3):WindChainRules.ballSize(tier(),phaseAge(partial));}
    public void configure(LivingEntity caster,int tier){owner=caster;entityData.set(TIER,WindChainRules.tier(tier));setPos(caster.getEyePosition().add(caster.getLookAngle().scale(1.5)));setDeltaMovement(caster.getLookAngle().scale(1.25));entityData.set(START,level().getGameTime());}
    public void anchor(){if(phase()!=0)return;phaseAge=0;entityData.set(PHASE,1);entityData.set(START,level().getGameTime());setDeltaMovement(Vec3.ZERO);}
    @Override public void tick(){
        if(isRemoved())return;super.tick();if(level().isClientSide)return;
        if(owner==null||!owner.isAlive()||owner.level()!=level()||++age>180){discard();return;}
        if(phase()==0){
            Vec3 from=position(),to=from.add(getDeltaMovement());if(!level().hasChunkAt(net.minecraft.core.BlockPos.containing(to))){discard();return;}
            var wall=level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            boolean contact=wall.getType()!=HitResult.Type.MISS;if(contact)to=wall.getLocation();
            double distance=from.distanceToSqr(to);
            for(var e:level().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(.8),v->WindChainSpells.enemy(v,owner))){
                var hit=e.getBoundingBox().inflate(.45).clip(from,to);if(e.getBoundingBox().inflate(.45).contains(from))hit=java.util.Optional.of(from);
                if(hit.isPresent()&&from.distanceToSqr(hit.get())<=distance){to=hit.get();distance=from.distanceToSqr(to);contact=true;}
            }
            setPos(to);if(contact||age>=32)anchor();return;
        }
        ++phaseAge;
        if(phase()==2){if(phaseAge>=12)discard();return;}
        if(tier()==5)setPos(position().add(0,.48,0));
        double reach=WindChainRules.pullRange(tier());
        var enemies=level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(reach),e->WindChainSpells.enemy(e,owner));
        enemies.sort(java.util.Comparator.comparingDouble(e->e.distanceToSqr(this)));
        for(int i=0;i<Math.min(24,enemies.size());i++){
            var e=enemies.get(i);Vec3 delta=position().subtract(e.getBoundingBox().getCenter());if(e.getBoundingBox().distanceToSqr(position())>reach*reach)continue;
            Vec3 surface=new Vec3(net.minecraft.util.Mth.clamp(getX(),e.getBoundingBox().minX,e.getBoundingBox().maxX),net.minecraft.util.Mth.clamp(getY(),e.getBoundingBox().minY,e.getBoundingBox().maxY),net.minecraft.util.Mth.clamp(getZ(),e.getBoundingBox().minZ,e.getBoundingBox().maxZ));
            if(level().clip(new ClipContext(position(),surface,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()!=HitResult.Type.MISS)continue;
            double speed=Math.min(tier()==5?2.8:1.8,delta.length()*.18+.08);
            Vec3 force=delta.normalize().scale(speed);e.setDeltaMovement(force);WindPullControl.request(e,force);e.hurtMarked=true;e.fallDistance=0;
            if(phaseAge%8==0){e.invulnerableTime=0;e.hurt(level().damageSources().indirectMagic(this,owner),2+tier()*1.5F);}
        }
        if(phaseAge>=(tier()>=3?WindChainRules.compressionTicks(tier()):48)){
            if(tier()>=3)explode();else discard();
        }
    }
    private void explode(){
        double range=Math.min(112,WindChainRules.ballRadius(tier())*3);
        int hits=0;for(var e:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(range),v->WindChainSpells.enemy(v,owner))){
            if(e.distanceToSqr(this)>range*range||++hits>32)continue;
            WindPullControl.cancel(e);
            e.invulnerableTime=0;e.hurt(level().damageSources().indirectMagic(this,owner),tier()==5?55:tier()==4?34:22);
            var motion=e.getBoundingBox().getCenter().subtract(position()).normalize().scale(1.15);e.push(motion.x,.4+Math.max(0,motion.y),motion.z);e.hurtMarked=true;
        }
        var server=(ServerLevel)level();server.sendParticles(ParticleTypes.EXPLOSION_EMITTER,getX(),getY(),getZ(),1,0,0,0,0);
        server.sendParticles(ParticleTypes.CLOUD,getX(),getY(),getZ(),32,1,1,1,.65);
        level().playSound(null,getX(),getY(),getZ(),net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,net.minecraft.sounds.SoundSource.PLAYERS,1,tier()==5?.65F:.95F);
        phaseAge=0;entityData.set(PHASE,2);entityData.set(START,level().getGameTime());
    }
    @Override public AABB getBoundingBoxForCulling(){return super.getBoundingBoxForCulling().inflate(WindChainRules.ballRadius(tier())*5+8);}
    @Override public boolean shouldRenderAtSqrDistance(double d){return d<384*384;}
    @Override protected void readAdditionalSaveData(CompoundTag t){discard();}
    @Override protected void addAdditionalSaveData(CompoundTag t){}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
