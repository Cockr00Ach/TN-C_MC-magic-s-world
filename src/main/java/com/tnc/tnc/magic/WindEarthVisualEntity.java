package com.tnc.tnc.magic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Non-colliding presentation; never edits terrain, attacks, or grants effects. */
public final class WindEarthVisualEntity extends Entity {
    private static final EntityDataAccessor<Integer> KIND=SynchedEntityData.defineId(WindEarthVisualEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIER=SynchedEntityData.defineId(WindEarthVisualEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE=SynchedEntityData.defineId(WindEarthVisualEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER=SynchedEntityData.defineId(WindEarthVisualEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AGE=SynchedEntityData.defineId(WindEarthVisualEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS=SynchedEntityData.defineId(WindEarthVisualEntity.class,EntityDataSerializers.FLOAT);
    private final WindEarthVisualClock clock=new WindEarthVisualClock();
    private boolean follows;
    private String[] effects=new String[0];
    public WindEarthVisualEntity(EntityType<? extends WindEarthVisualEntity> type,Level level) {
        super(type,level);noPhysics=true;setNoGravity(true);
    }
    protected void defineSynchedData() {
        entityData.define(KIND,0);entityData.define(TIER,1);entityData.define(LIFE,40);
        entityData.define(OWNER,-1);entityData.define(AGE,0);entityData.define(RADIUS,1F);
    }
    public void configure(WindEarthVisualRules.Spec spec,LivingEntity owner) {
        entityData.set(KIND,spec.kind());entityData.set(TIER,spec.tier());entityData.set(LIFE,spec.life());
        entityData.set(RADIUS,(float)spec.radius());entityData.set(OWNER,owner.getId());
        follows=spec.followsOwner();effects=spec.effects().isEmpty()?new String[0]:spec.effects().split(",");
        setPos(owner.position());setYRot(owner.getYRot());
    }
    public int kind(){return entityData.get(KIND);}
    public int tier(){return entityData.get(TIER);}
    public int life(){return entityData.get(LIFE);}
    public int ownerId(){return entityData.get(OWNER);}
    public double radius(){return entityData.get(RADIUS);}
    public boolean followsOwner(){return kind()==WindEarthVisualRules.WIND_FLIGHT || kind()==WindEarthVisualRules.EARTH_RING;}
    public double visualAge(float partial){return clock.phase(tickCount,partial,entityData.get(AGE),1);}
    @Override public void tick() {
        super.tick();if(level().isClientSide()) {
            if(kind()>=3)EarthSpellParticles.tick(this);
            return;
        }
        if(follows) {
            Entity found=level().getEntity(ownerId());
            if(!(found instanceof LivingEntity owner)||!owner.isAlive()){discard();return;}
            if(tickCount%4==0)setPos(owner.position());
            int remaining=0;
            for(String id:effects) {
                var effect=net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(net.minecraft.resources.ResourceLocation.tryParse(id));
                var active=effect==null?null:owner.getEffect(effect);
                if(active!=null)remaining=Math.max(remaining,active.getDuration()<0?Integer.MAX_VALUE:active.getDuration());
            }
            if(tickCount>3 && remaining==0){discard();return;}
            if(remaining>0)entityData.set(LIFE,(int)Math.min(Integer.MAX_VALUE,(long)tickCount+remaining));
        }
        if(tickCount%10==0)entityData.set(AGE,tickCount);
        if(tickCount>life())discard();
    }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }
    @Override public AABB getBoundingBoxForCulling(){return super.getBoundingBoxForCulling().inflate(radius()+3);}
    @Override public boolean shouldRenderAtSqrDistance(double distance){return distance<128*128;}
    @Override public boolean isPickable(){return false;}
    @Override public boolean isAttackable(){return false;}
    protected void readAdditionalSaveData(CompoundTag tag){discard();}
    protected void addAdditionalSaveData(CompoundTag tag){}
}
