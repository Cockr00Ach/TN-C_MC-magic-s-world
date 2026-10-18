package com.tnc.tnc.magic.wind;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import com.tnc.tnc.magic.TNEffects;
import net.minecraft.world.effect.MobEffectInstance;
import java.util.*;

/** One networked cast, up to thirty independent swept weapons, one damage aggregate per victim/tick. */
public final class WindVolleyEntity extends Entity {
    private static final EntityDataAccessor<Integer> TIER=SynchedEntityData.defineId(WindVolleyEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(WindVolleyEntity.class,EntityDataSerializers.LONG),DONE=SynchedEntityData.defineId(WindVolleyEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> TX=SynchedEntityData.defineId(WindVolleyEntity.class,EntityDataSerializers.FLOAT),TY=SynchedEntityData.defineId(WindVolleyEntity.class,EntityDataSerializers.FLOAT),TZ=SynchedEntityData.defineId(WindVolleyEntity.class,EntityDataSerializers.FLOAT),SPAN=SynchedEntityData.defineId(WindVolleyEntity.class,EntityDataSerializers.FLOAT);
    private LivingEntity owner,target;private int age,finishedAt=-1;
    public WindVolleyEntity(EntityType<? extends WindVolleyEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    @Override protected void defineSynchedData(){entityData.define(TIER,1);entityData.define(START,0L);entityData.define(DONE,0L);entityData.define(TX,0F);entityData.define(TY,0F);entityData.define(TZ,0F);entityData.define(SPAN,5F);}
    public int tier(){return entityData.get(TIER);}public int count(){return WindChainRules.count(tier());}
    public double visualAge(float partial){return Math.max(0,level().getGameTime()+partial-entityData.get(START));}
    public boolean done(int slot){return (entityData.get(DONE)&(1L<<slot))!=0;}
    public Vec3 aimPoint(){return position().add(entityData.get(TX),entityData.get(TY),entityData.get(TZ));}
    private void aimAt(Vec3 at){var delta=at.subtract(position());entityData.set(TX,(float)delta.x);entityData.set(TY,(float)delta.y);entityData.set(TZ,(float)delta.z);}
    public void configure(LivingEntity caster,int tier,LivingEntity target){
        owner=caster;this.target=target;entityData.set(TIER,WindChainRules.tier(tier));setPos(caster.position());entityData.set(START,level().getGameTime());
        aimAt(target==null?caster.getEyePosition().add(caster.getLookAngle().scale(40)):target.position());
        if(target!=null)entityData.set(SPAN,Math.min(48,Math.max(5,target.getBbWidth()*.7F)));
    }
    public Vec3 impact(int slot){
        double angle=slot*2.3999632297,r=tier()==5?24:Math.sqrt((slot+.5)/count())*Math.min(5,entityData.get(SPAN)*.6);
        return aimPoint().add(Math.cos(angle)*r,.08,Math.sin(angle)*r);
    }
    private Vec3 formation(int slot,double a){
        double angle=slot*Math.PI*2/Math.min(10,count())+a*.015,r=2+WindChainRules.modelScale(tier())*.45+(slot/10)*1.4;
        return position().add(Math.cos(angle)*r,2+(slot/10)*1.4,Math.sin(angle)*r);
    }
    public Vec3 orbitPoint(int slot,double age){
        double a=(age-38)*Math.PI*2/24+slot*Math.PI*2/count();
        double r=entityData.get(SPAN)+WindChainRules.modelScale(tier())*.7;
        double height=Math.max(1.25,Math.min(24,entityData.get(SPAN)*.25));
        return aimPoint().add(Math.cos(a)*r,height+(slot%3)*.30,Math.sin(a)*r);
    }
    public Vec3 launchPoint(int slot){return impact(slot).add(0,WindChainRules.skyHeight(tier()),0);}
    public int flightTime(){return tier()==5?18:16;}
    public double dropTime(int slot){return WindChainRules.dropStart(tier())+(tier()==5?slot*12:0);}
    public boolean visible(int slot,double a){return tier()!=5||a>=WindChainRules.emerge(slot);}
    public double endTime(int slot){return dropTime(slot)+flightTime();}
    public Vec3 weaponAt(int slot,double a){
        if(tier()==5){
            double rise=WindChainRules.emerge(slot);
            if(a<rise)return impact(slot).add(0,-4,0);
            if(a<rise+38){double t=Math.max(0,(a-rise)/38);return impact(slot).add(0,-4+(WindChainRules.skyHeight(5)+4)*t*t*(3-2*t),0);}
            if(a<dropTime(slot))return launchPoint(slot);
        }else{
            if(a<18)return formation(slot,a);
            if(a<38)return formation(slot,18).lerp(orbitPoint(slot,38),(a-18)/20);
            if(a<100)return orbitPoint(slot,a);
            if(a<110)return orbitPoint(slot,100).lerp(launchPoint(slot),(a-100)/10);
        }
        return launchPoint(slot).lerp(impact(slot),Math.max(0,Math.min(1,(a-dropTime(slot))/flightTime())));
    }
    public Vec3 direction(int slot,double a){
        if(tier()==5)return a<WindChainRules.emerge(slot)+38?new Vec3(0,1,0):a<dropTime(slot)?new Vec3(0,Math.cos(Math.PI*Math.min(1,(a-WindChainRules.emerge(slot)-38)/14)),Math.sin(Math.PI*Math.min(1,(a-WindChainRules.emerge(slot)-38)/14))):new Vec3(0,-1,0);
        if(a<18)return new Vec3(0,1,0);
        return weaponAt(slot,a+.15).subtract(weaponAt(slot,a)).normalize();
    }
    @Override public void tick(){
        if(isRemoved())return;super.tick();if(level().isClientSide)return;
        if(owner==null||!owner.isAlive()||owner.level()!=level()||++age>200){discard();return;}
        if(age<WindChainRules.dropStart(tier())){
            if(target!=null&&(!target.isAlive()||target.level()!=level())){discard();return;}
            Vec3 aim=aimPoint();if(age<18)setPos(owner.position());
            aimAt(target==null?aim:target.position());
            if(tier()<5&&age>=38&&age<100&&age%12==0&&target!=null){
                // The weapons stay on their circular track; inward wind cuts carry the attack.
                if(target.getBoundingBox().distanceToSqr(aimPoint())<=1){target.invulnerableTime=0;target.hurt(level().damageSources().indirectMagic(this,owner),WindChainRules.weaponDamage(tier(),1)*.65F);
                    if(WindChainRules.spears(tier())>0)target.addEffect(new MobEffectInstance(TNEffects.VULNERABLE.get(),200,0,false,true,true));}
            }
            return;
        }
        if(finishedAt>=0){if(age-finishedAt>=10)discard();return;}
        int launch=WindChainRules.launch(tier());if(age<=launch)return;
        var damage=new HashMap<LivingEntity,Float>();var spearHits=new HashSet<LivingEntity>();
        var splash=level().getEntitiesOfClass(LivingEntity.class,new AABB(aimPoint(),aimPoint()).inflate(tier()==5?56:16),v->WindChainSpells.enemy(v,owner));
        long mask=entityData.get(DONE);int impacts=0;
        for(int slot=0;slot<count();slot++){
            if((mask&(1L<<slot))!=0||age<=dropTime(slot))continue;
            Vec3 from=weaponAt(slot,age-1),to=weaponAt(slot,age);
            if(!level().hasChunkAt(net.minecraft.core.BlockPos.containing(to))){mask|=1L<<slot;continue;}
            var wall=level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            boolean hit=wall.getType()!=HitResult.Type.MISS;if(hit)to=wall.getLocation();
            LivingEntity victim=null;double nearest=from.distanceToSqr(to)+1E-6;
            for(var e:level().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(tier()==5?2:.45),v->WindChainSpells.enemy(v,owner))){
                var touch=e.getBoundingBox().inflate(tier()==5?1.5:.25).clip(from,to);
                if(e.getBoundingBox().inflate(.25).contains(from))touch=Optional.of(from);
                if(touch.isPresent()&&from.distanceToSqr(touch.get())<nearest){nearest=from.distanceToSqr(touch.get());victim=e;to=touch.get();hit=true;}
            }
            boolean end=age>=endTime(slot);if(!hit&&!end)continue;
            mask|=1L<<slot;++impacts;int form=WindChainRules.form(tier(),slot);float amount=WindChainRules.weaponDamage(tier(),form);
            if(tier()>=1){
                double r=tier()==5?36:Math.max(3,WindChainRules.modelScale(tier()));int n=0;
                for(var e:splash){if(++n>48)break;if(e.getBoundingBox().distanceToSqr(to)>r*r)continue;
                    if(level().clip(new ClipContext(to.add(0,.12,0),e.getEyePosition(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()!=HitResult.Type.MISS)continue;
                    damage.merge(e,amount,Float::sum);if(form==2)spearHits.add(e);
                }
            }else if(victim!=null){damage.merge(victim,amount,Float::sum);if(form==2)spearHits.add(victim);}
        }
        entityData.set(DONE,mask);
        damage.forEach((e,amount)->{e.invulnerableTime=0;e.hurt(level().damageSources().indirectMagic(this,owner),amount);});
        for(var e:spearHits)e.addEffect(new MobEffectInstance(TNEffects.VULNERABLE.get(),200,tier()==5?1:0,false,true,true));
        if(impacts>0)((net.minecraft.server.level.ServerLevel)level()).sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,aimPoint().x,aimPoint().y+1,aimPoint().z,Math.min(16,3+impacts),1,.5,1,.12);
        if(Long.bitCount(mask)>=count())finishedAt=age;
    }
    @Override public AABB getBoundingBoxForCulling(){return new AABB(position(),aimPoint()).inflate(tier()==5?260:90);}
    @Override public boolean shouldRenderAtSqrDistance(double d){return d<384*384;}
    @Override protected void readAdditionalSaveData(CompoundTag tag){discard();}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
